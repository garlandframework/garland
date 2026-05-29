package org.modulartestorchestrator.kafka.model;

public record KafkaMessage<T>(String key, T value) {}
