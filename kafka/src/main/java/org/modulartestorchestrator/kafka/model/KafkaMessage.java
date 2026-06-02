package org.modulartestorchestrator.kafka.model;

/**
 * A Kafka message with an optional string key and a typed value.
 *
 * <p>Pass to {@link org.modulartestorchestrator.kafka.KafkaTestClient#publish()} to produce
 * a message. The key may be {@code null} for topics that do not require keyed partitioning.
 */
public record KafkaMessage<T>(String key, T value) {}
