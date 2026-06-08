package dev.garlandframework.postgres;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PostgresConfigTest {

    @Test
    public void builds_successfully_with_all_required_fields() {
        PostgresConfig config = PostgresConfig.builder()
                .url("jdbc:postgresql://localhost/db")
                .username("user")
                .password("pass")
                .entity(Object.class)
                .build();

        assertThat(config.url()).isEqualTo("jdbc:postgresql://localhost/db");
        assertThat(config.username()).isEqualTo("user");
        assertThat(config.entities()).containsExactly(Object.class);
    }

    @Test
    public void throws_when_url_is_missing() {
        assertThatThrownBy(() -> PostgresConfig.builder()
                .username("user")
                .entity(Object.class)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("url");
    }

    @Test
    public void throws_when_url_is_blank() {
        assertThatThrownBy(() -> PostgresConfig.builder()
                .url("   ")
                .username("user")
                .entity(Object.class)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("url");
    }

    @Test
    public void throws_when_username_is_missing() {
        assertThatThrownBy(() -> PostgresConfig.builder()
                .url("jdbc:postgresql://localhost/db")
                .entity(Object.class)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("username");
    }

    @Test
    public void throws_when_entities_are_empty() {
        assertThatThrownBy(() -> PostgresConfig.builder()
                .url("jdbc:postgresql://localhost/db")
                .username("user")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("entity");
    }

    @Test
    public void password_is_optional() {
        PostgresConfig config = PostgresConfig.builder()
                .url("jdbc:postgresql://localhost/db")
                .username("user")
                .entity(Object.class)
                .build();

        assertThat(config.password()).isNull();
    }
}
