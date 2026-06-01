package org.modulartestorchestrator.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class KafkaProducerWrapper {

    private final KafkaProducer<String, String> producer;
    private final String topic;

    public KafkaProducerWrapper(KafkaConfig config) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,  config.bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        this.producer = new KafkaProducer<>(props);
        this.topic    = config.defaultTopic();
    }

    public void send(String key, String value) throws Exception {
        producer.send(new ProducerRecord<>(topic, key, value)).get();
    }

    public void close() {
        producer.close();
    }
}
