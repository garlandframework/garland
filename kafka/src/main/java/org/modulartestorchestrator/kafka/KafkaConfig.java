package org.modulartestorchestrator.kafka;

import java.util.ArrayList;
import java.util.List;

/**
 * Connection and topic configuration for {@link KafkaTestClient}.
 *
 * <p>The first topic added via {@link Builder#topic} becomes the default topic used by
 * {@link KafkaTestClient#publish()}. Additional topics are only subscribed for consumption.
 *
 * <p>Always use a random UUID for {@code groupId} to prevent offset reuse between suite runs:
 * <pre>{@code
 * .groupId(UUID.randomUUID().toString())
 * }</pre>
 */
public record KafkaConfig(String bootstrapServers, List<String> topics, String groupId) {

    public String defaultTopic() {
        return topics.get(0);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String bootstrapServers;
        private final List<String> topics = new ArrayList<>();
        private String groupId;

        public Builder bootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; return this; }
        public Builder topic(String topic)                        { this.topics.add(topic);                   return this; }
        public Builder topics(List<String> topics)               { this.topics.addAll(topics);               return this; }
        public Builder groupId(String groupId)                    { this.groupId = groupId;                   return this; }

        public KafkaConfig build() {
            if (bootstrapServers == null || bootstrapServers.isBlank())
                throw new IllegalStateException("KafkaConfig: bootstrapServers is required");
            if (topics.isEmpty())
                throw new IllegalStateException("KafkaConfig: at least one topic is required");
            if (groupId == null || groupId.isBlank())
                throw new IllegalStateException("KafkaConfig: groupId is required");
            return new KafkaConfig(bootstrapServers, List.copyOf(topics), groupId);
        }
    }
}
