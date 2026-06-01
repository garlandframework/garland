package org.modulartestorchestrator.mongodb;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MongoConfigTest {

    @Test
    public void builds_successfully_with_all_required_fields() {
        MongoConfig config = MongoConfig.builder()
                .connectionString("mongodb://localhost:27017")
                .database("mydb")
                .collection(Object.class, "objects")
                .build();

        assertThat(config.connectionString()).isEqualTo("mongodb://localhost:27017");
        assertThat(config.database()).isEqualTo("mydb");
        assertThat(config.collections()).containsEntry(Object.class, "objects");
    }

    @Test
    public void throws_when_connectionString_is_missing() {
        assertThatThrownBy(() -> MongoConfig.builder()
                .database("mydb")
                .collection(Object.class, "objects")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connectionString");
    }

    @Test
    public void throws_when_connectionString_is_blank() {
        assertThatThrownBy(() -> MongoConfig.builder()
                .connectionString("  ")
                .database("mydb")
                .collection(Object.class, "objects")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connectionString");
    }

    @Test
    public void throws_when_database_is_missing() {
        assertThatThrownBy(() -> MongoConfig.builder()
                .connectionString("mongodb://localhost:27017")
                .collection(Object.class, "objects")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database");
    }

    @Test
    public void throws_when_collections_are_empty() {
        assertThatThrownBy(() -> MongoConfig.builder()
                .connectionString("mongodb://localhost:27017")
                .database("mydb")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("collection");
    }
}
