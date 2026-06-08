package dev.garlandframework.kafka;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class KafkaConfigTest {

    @Test
    public void builds_successfully_with_all_required_fields() {
        KafkaConfig config = KafkaConfig.builder()
                .bootstrapServers("localhost:9092")
                .topic("my-topic")
                .groupId("my-group")
                .build();

        assertThat(config.bootstrapServers()).isEqualTo("localhost:9092");
        assertThat(config.defaultTopic()).isEqualTo("my-topic");
        assertThat(config.groupId()).isEqualTo("my-group");
    }

    @Test
    public void throws_when_bootstrapServers_is_missing() {
        assertThatThrownBy(() -> KafkaConfig.builder()
                .topic("t")
                .groupId("g")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bootstrapServers");
    }

    @Test
    public void throws_when_bootstrapServers_is_blank() {
        assertThatThrownBy(() -> KafkaConfig.builder()
                .bootstrapServers("  ")
                .topic("t")
                .groupId("g")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bootstrapServers");
    }

    @Test
    public void throws_when_topics_are_empty() {
        assertThatThrownBy(() -> KafkaConfig.builder()
                .bootstrapServers("localhost:9092")
                .groupId("g")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("topic");
    }

    @Test
    public void throws_when_groupId_is_missing() {
        assertThatThrownBy(() -> KafkaConfig.builder()
                .bootstrapServers("localhost:9092")
                .topic("t")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("groupId");
    }

    @Test
    public void defaultTopic_returns_first_registered_topic() {
        KafkaConfig config = KafkaConfig.builder()
                .bootstrapServers("localhost:9092")
                .topic("first")
                .topic("second")
                .groupId("g")
                .build();

        assertThat(config.defaultTopic()).isEqualTo("first");
        assertThat(config.topics()).containsExactly("first", "second");
    }
}
