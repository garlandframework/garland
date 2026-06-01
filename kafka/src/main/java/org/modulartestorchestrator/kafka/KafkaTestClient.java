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
        this.kafkaSteps  = new KafkaSteps(consumer, producer, config.topic());
        this.retryConfig = retryConfig;
    }

    public KafkaTestClient(KafkaConfig config) {
        this(config, RetryConfig.attempts(1));
    }

    public <I, T> StepFunction<I, T> consume(Class<T> type) {
        return (input, outerCtx) -> Pipeline.given(input)
                .withContext(outerCtx)
                .then(Retry.of(kafkaSteps.consume(type), retryConfig))
                .execute();
    }

    public <I, T> StepFunction<I, T> consume(Class<T> type, T expected) {
        return (input, outerCtx) -> Pipeline.given(input)
                .withContext(outerCtx)
                .then(Retry.of(kafkaSteps.consume(type), retryConfig))
                .then(check.matchingNonNull(expected))
                .execute();
    }

    public <I, T> StepFunction<I, T> consume(Class<T> type, T expected, Duration temporalTolerance) {
        return (input, outerCtx) -> Pipeline.given(input)
                .withContext(outerCtx)
                .then(Retry.of(kafkaSteps.consume(type), retryConfig))
                .then(check.matchingNonNull(expected, temporalTolerance))
                .execute();
    }

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

    public <T> StepFunction<KafkaMessage<T>, KafkaMessage<T>> publish() {
        return kafkaSteps.produce();
    }

    public void warmup() {
        consumer.warmup();
    }

    public void close() {
        consumer.close();
        producer.close();
    }
}
