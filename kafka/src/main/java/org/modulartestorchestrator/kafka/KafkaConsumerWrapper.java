package org.modulartestorchestrator.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class KafkaConsumerWrapper {

    private final KafkaConsumer<String, String> consumer;
    private final String topic;
    private volatile boolean warmedUp = false;

    public KafkaConsumerWrapper(KafkaConfig config) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  config.bootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG,            config.groupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,   "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,   "1");

        this.consumer = new KafkaConsumer<>(props);
        this.topic    = config.defaultTopic();
        this.consumer.subscribe(config.topics());
    }

    public Optional<ConsumerRecord<String, String>> poll(Duration timeout) {
        if (!warmedUp) {
            throw new IllegalStateException(
                    "KafkaConsumerWrapper: call warmup() before consuming messages — " +
                    "AUTO_OFFSET_RESET=latest requires partition assignment to be established first");
        }
        ConsumerRecords<String, String> records = consumer.poll(timeout);
        for (ConsumerRecord<String, String> record : records) {
            return Optional.of(record);
        }
        return Optional.empty();
    }

    // Anchors the consumer to the current end of the topic. Safe to call multiple times —
    // use it once at suite start and again before any test section that uses consumeMatching,
    // so stale events from previous sections are not consumed.
    public void warmup() {
        while (consumer.assignment().isEmpty()) {
            consumer.poll(Duration.ofMillis(500));
        }
        consumer.seekToEnd(consumer.assignment());
        consumer.poll(Duration.ofMillis(100)); // trigger lazy seek
        warmedUp = true;
    }

    public void close() {
        consumer.close();
    }
}
