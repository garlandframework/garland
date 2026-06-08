package dev.garlandframework.kafka;

import dev.garlandframework.base.PipelineContext;
import dev.garlandframework.base.Step;
import dev.garlandframework.base.checks.CheckSteps;
import dev.garlandframework.base.retry.Retry;
import dev.garlandframework.base.retry.RetryConfig;
import dev.garlandframework.kafka.model.KafkaMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Test client for Kafka assertions. Combines consuming, deserializing, and asserting
 * messages into single {@link Step}s, with retry wrapping for eventually-consistent
 * scenarios.
 *
 * <p><strong>Call {@link #warmup()} before consuming any messages.</strong> The underlying
 * consumer starts at the end of all subscribed topics after warmup — without it, the consumer
 * may read from an earlier offset and pick up events produced by previous test sections.
 * Re-call {@link #warmup()} between test sections (e.g. in {@code @BeforeTest}) to reset
 * the position and prevent stale event contamination.
 *
 * <p><strong>Timestamp tolerance:</strong> Events often carry timestamps that originated in
 * a database (already truncated to µs or ms). Use
 * {@link #withTemporalTolerance(Duration) withTemporalTolerance(Duration.ofMillis(1))}
 * to absorb that truncation globally. Reserve the explicit
 * {@link #consumeMatching(Class, Duration)} overload for SLA-style assertions where the
 * tolerance represents an acceptable processing delay rather than a precision correction.
 */
public class KafkaTestClient {

    private static final Logger log = LoggerFactory.getLogger("KAFKA");

    private final KafkaSteps kafkaSteps;
    private final KafkaConsumerWrapper consumer;
    private final KafkaProducerWrapper producer;
    private final CheckSteps check;
    private final RetryConfig retryConfig;
    private final Duration defaultTemporalTolerance;

    private KafkaTestClient(KafkaConsumerWrapper consumer, KafkaProducerWrapper producer,
                            KafkaSteps kafkaSteps, RetryConfig retryConfig,
                            Duration defaultTemporalTolerance) {
        this.consumer                = consumer;
        this.producer                = producer;
        this.kafkaSteps              = kafkaSteps;
        this.check                   = new CheckSteps();
        this.retryConfig             = retryConfig;
        this.defaultTemporalTolerance = defaultTemporalTolerance;
    }

    public KafkaTestClient(KafkaConfig config, RetryConfig retryConfig) {
        this.consumer                = new KafkaConsumerWrapper(config);
        this.producer                = new KafkaProducerWrapper(config);
        this.kafkaSteps              = new KafkaSteps(consumer, producer, config.defaultTopic());
        this.check                   = new CheckSteps();
        this.retryConfig             = retryConfig;
        this.defaultTemporalTolerance = null;
    }

    public KafkaTestClient(KafkaConfig config) {
        this(config, RetryConfig.attempts(1));
    }

    /**
     * Returns a new client that applies {@code tolerance} to every
     * {@link #consumeMatching(Class)} and {@link #consume(Class, Object)} call by default.
     * Individual calls can still override with the explicit {@code Duration} overloads.
     *
     * <p>The consumer and producer connections are shared with the original client — no
     * reconnection occurs. Use {@code Duration.ofMillis(1)} to absorb DB-echo timestamp
     * truncation globally:
     * <pre>
     * kafkaClient = new KafkaTestClient(config, retryConfig)
     *         .withTemporalTolerance(Duration.ofMillis(1));
     * </pre>
     */
    public KafkaTestClient withTemporalTolerance(Duration tolerance) {
        return new KafkaTestClient(consumer, producer, kafkaSteps, retryConfig, tolerance);
    }

    /**
     * Reads the next available record and deserializes it to {@code type}. No assertion
     * is performed — use the overloads below if you also want to verify the content.
     */
    public <I, T> Step<I, T> consume(Class<T> type) {
        return (input, outerCtx) -> {
            log.info(KafkaTestClientLogTemplates.CONSUME, type.getSimpleName());
            T result = Retry.of(kafkaSteps.consume(type), retryConfig)
                    .apply(input, outerCtx);
            log.info(KafkaTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * Reads the next record, deserializes it, and asserts it matches {@code expected}
     * (null fields ignored).
     *
     * <p>If a default tolerance was set via {@link #withTemporalTolerance(Duration)}, it is
     * applied automatically. Use {@link #consume(Class, Object, Duration)} to override.
     */
    public <I, T> Step<I, T> consume(Class<T> type, T expected) {
        return defaultTemporalTolerance != null ? consume(type, expected, defaultTemporalTolerance) : (input, outerCtx) -> {
            log.info(KafkaTestClientLogTemplates.CONSUME, type.getSimpleName());
            T result = Retry.of(
                    kafkaSteps.<I, T>consume(type).andThen(check.matchingNonNull(expected)),
                    retryConfig
            ).apply(input, outerCtx);
            log.info(KafkaTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * Same as {@link #consume(Class, Object)} but overrides the tolerance for this specific
     * call. Use for SLA-style assertions where the tolerance represents an acceptable
     * processing delay rather than a precision correction.
     */
    public <I, T> Step<I, T> consume(Class<T> type, T expected, Duration temporalTolerance) {
        return (input, outerCtx) -> {
            log.info(KafkaTestClientLogTemplates.CONSUME, type.getSimpleName());
            T result = Retry.of(
                    kafkaSteps.<I, T>consume(type).andThen(check.matchingNonNull(expected, temporalTolerance)),
                    retryConfig
            ).apply(input, outerCtx);
            log.info(KafkaTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * The step input <em>is</em> the expected value. Reads records with retry until one
     * matches, then returns it. Use this — rather than {@link #consume(Class, Object)} —
     * when other events may arrive on the topic before the expected one, because it
     * tolerates interleaved messages by keep polling and comparing until the retry budget
     * is exhausted.
     *
     * <p>If a default tolerance was set via {@link #withTemporalTolerance(Duration)}, it is
     * applied automatically. Use {@link #consumeMatching(Class, Duration)} to override for
     * SLA-style assertions (e.g. a service-generated timestamp within a 2-minute window).
     */
    public <T> Step<T, T> consumeMatching(Class<T> type) {
        return defaultTemporalTolerance != null ? consumeMatching(type, defaultTemporalTolerance) : (expected, outerCtx) -> {
            log.info(KafkaTestClientLogTemplates.CONSUME_MATCHING, type.getSimpleName());
            T result = Retry.of(
                    (T exp, PipelineContext ctx) -> check.matchingNonNull(exp).apply(kafkaSteps.consume(type).apply(exp, ctx), ctx),
                    retryConfig
            ).apply(expected, outerCtx);
            log.info(KafkaTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * Same as {@link #consumeMatching(Class)} but overrides the tolerance for this specific
     * call. Use when a particular assertion needs a higher tolerance than the client default
     * (e.g. asserting a service-generated timestamp within an SLA window).
     */
    public <T> Step<T, T> consumeMatching(Class<T> type, Duration temporalTolerance) {
        return (expected, outerCtx) -> {
            log.info(KafkaTestClientLogTemplates.CONSUME_MATCHING, type.getSimpleName());
            T result = Retry.of(
                    (T exp, PipelineContext ctx) -> check.matchingNonNull(exp, temporalTolerance).apply(kafkaSteps.consume(type).apply(exp, ctx), ctx),
                    retryConfig
            ).apply(expected, outerCtx);
            log.info(KafkaTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /** Produces a {@link KafkaMessage} to the default topic (the first topic in {@link KafkaConfig}). */
    public <T> Step<KafkaMessage<T>, KafkaMessage<T>> publish() {
        return (message, outerCtx) -> {
            log.info(KafkaTestClientLogTemplates.PUBLISH, message.value().getClass().getSimpleName());
            KafkaMessage<T> result = kafkaSteps.<T>produce().apply(message, outerCtx);
            log.info(KafkaTestClientLogTemplates.PUBLISHED);
            return result;
        };
    }

    /**
     * Seeks all subscribed partitions to their current end. Must be called before any
     * {@code consume*} call — see class-level javadoc for why. Safe to call multiple
     * times; subsequent calls simply re-seek to the latest offset.
     */
    public void warmup() {
        consumer.warmup();
    }

    public void close() {
        consumer.close();
        producer.close();
    }
}
