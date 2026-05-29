package org.modulartestorchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.modulartestorchestrator.base.StepFunction;
import org.modulartestorchestrator.kafka.model.KafkaMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

public class KafkaSteps {

    private static final Logger log = LoggerFactory.getLogger(KafkaSteps.class);

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final KafkaConsumerWrapper consumer;
    private final KafkaProducerWrapper producer;
    private final String topic;

    public KafkaSteps(KafkaConsumerWrapper consumer, KafkaProducerWrapper producer, String topic) {
        this.consumer = consumer;
        this.producer = producer;
        this.topic    = topic;
    }

    public <I, T> StepFunction<I, T> consume(Class<T> type) {
        return (input, ctx) -> {
            log.info(KafkaStepsLogTemplates.CONSUMING, topic);
            Optional<ConsumerRecord<String, String>> record = consumer.poll(Duration.ofMillis(500));
            if (record.isEmpty()) {
                log.info(KafkaStepsLogTemplates.NOT_CONSUMED, topic);
                throw new AssertionError("No message received from topic: " + topic);
            }
            ConsumerRecord<String, String> r = record.get();
            log.info(KafkaStepsLogTemplates.CONSUMED, topic, r.key(), r.partition(), r.offset());
            return mapper.readValue(r.value(), type);
        };
    }

    public <T> StepFunction<KafkaMessage<T>, KafkaMessage<T>> produce() {
        return (message, ctx) -> {
            log.info(KafkaStepsLogTemplates.PRODUCING, topic, message.key());
            String json = mapper.writeValueAsString(message.value());
            producer.send(message.key(), json);
            log.info(KafkaStepsLogTemplates.PRODUCED, topic, message.key());
            return message;
        };
    }
}
