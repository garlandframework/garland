package org.modulartestorchestrator.kafka;

import org.modulartestorchestrator.base.Pipeline;
import org.modulartestorchestrator.base.PipelineContext;
import org.modulartestorchestrator.base.StepFunction;
import org.modulartestorchestrator.base.checks.CheckSteps;
import org.modulartestorchestrator.base.retry.Retry;
import org.modulartestorchestrator.base.retry.RetryConfig;
import org.modulartestorchestrator.kafka.model.KafkaMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Test client for Kafka assertions. Combines consuming, deserializing, and asserting
 * messages into single {@link StepFunction}s, with retry wrapping for eventually-consistent
 * scenarios.
 *
 * <p><strong>Call {@link #warmup()} before consuming any messages.</strong> The underlying
 * consumer starts at the end of all subscribed topics after warmup — without it, the consumer
 * may read from an earlier offset and pick up events produced by previous test sections.
 * Re-call {@link #warmup()} between test sections (e.g. in {@code @BeforeTest}) to reset
 * the position and prevent stale event contamination.
 */
public class KafkaTestClient {

    private static final Logger log = LoggerFactory.getLogger("KAFKA");

    private final KafkaSteps kafkaSteps;
    private final KafkaConsumerWrapper consumer;
    private final KafkaProducerWrapper producer;
    private final CheckSteps check      = new CheckSteps();
    private final RetryConfig retryConfig;

    public KafkaTestClient(KafkaConfig config, RetryConfig retryConfig) {
        this.consumer    = new KafkaConsumerWrapper(config);
        this.producer    = new KafkaProducerWrapper(config);
        this.kafkaSteps  = new KafkaSteps(consumer, producer, config.defaultTopic());
        this.retryConfig = retryConfig;
    }

    public KafkaTestClient(KafkaConfig config) {
        this(config, RetryConfig.attempts(1));
    }

    /**
     * Reads the next available record and deserializes it to {@code type}. No assertion
     * is performed — use the overloads below if you also want to verify the content.
     */
    public <I, T> StepFunction<I, T> consume(Class<T> type) {
        return (input, outerCtx) -> Pipeline.given(input)
                .withContext(outerCtx)
                .then(Retry.of(kafkaSteps.consume(type), retryConfig))
                .execute();
    }

    /** Reads the next record, deserializes it, and asserts it matches {@code expected} (null fields ignored). */
    public <I, T> StepFunction<I, T> consume(Class<T> type, T expected) {
        return (input, outerCtx) -> Pipeline.given(input)
                .withContext(outerCtx)
                .then(Retry.of(
                        kafkaSteps.<I, T>consume(type).andThen(check.matchingNonNull(expected)),
                        retryConfig))
                .execute();
    }

    /** Same as {@link #consume(Class, Object)} but applies temporal tolerance to timestamp fields. */
    public <I, T> StepFunction<I, T> consume(Class<T> type, T expected, Duration temporalTolerance) {
        return (input, outerCtx) -> Pipeline.given(input)
                .withContext(outerCtx)
                .then(Retry.of(
                        kafkaSteps.<I, T>consume(type).andThen(check.matchingNonNull(expected, temporalTolerance)),
                        retryConfig))
                .execute();
    }

    /**
     * The step input <em>is</em> the expected value. Reads records with retry until one
     * matches, then returns it. Use this — rather than {@link #consume(Class, Object)} —
     * when other events may arrive on the topic before the expected one, because it
     * tolerates interleaved messages by keep polling and comparing until the retry budget
     * is exhausted.
     */
    public <T> StepFunction<T, T> consumeMatching(Class<T> type) {
        return (expected, outerCtx) -> {
            log.info(KafkaTestClientLogTemplates.CONSUME_MATCHING, type.getSimpleName());
            T result = Retry.of(
                    (T exp, PipelineContext ctx) -> check.matchingNonNull(exp).apply(kafkaSteps.consume(type).apply(exp, ctx), ctx),
                    retryConfig
            ).apply(expected, outerCtx);
            log.info(KafkaTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /** Same as {@link #consumeMatching(Class)} but applies temporal tolerance to timestamp fields. */
    public <T> StepFunction<T, T> consumeMatching(Class<T> type, Duration temporalTolerance) {
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
    public <T> StepFunction<KafkaMessage<T>, KafkaMessage<T>> publish() {
        return kafkaSteps.produce();
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
