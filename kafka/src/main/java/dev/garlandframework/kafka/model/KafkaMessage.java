package dev.garlandframework.kafka.model;

/**
 * A Kafka message with an optional string key and a typed value.
 *
 * <p>Pass to {@link dev.garlandframework.kafka.KafkaTestClient#publish()} to produce
 * a message. The key may be {@code null} for topics that do not require keyed partitioning.
 */
public record KafkaMessage<T>(String key, T value) {}
