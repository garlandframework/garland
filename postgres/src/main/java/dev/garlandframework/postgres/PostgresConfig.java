package dev.garlandframework.postgres;

import java.util.ArrayList;
import java.util.List;

/**
 * Connection and entity configuration for {@link PostgresWrapper}. All entity classes
 * that the test suite will query must be registered via {@link Builder#entity} — Hibernate
 * validates the schema against this list at construction time and will throw if an entity
 * is missing or if the live schema does not match.
 */
public record PostgresConfig(String url, String username, String password, List<Class<?>> entities) {

    public static Builder builder() { return new Builder(); }

    public static class Builder {

        private String url;
        private String username;
        private String password;
        private final List<Class<?>> entities = new ArrayList<>();

        public Builder url(String url)            { this.url = url;           return this; }
        public Builder username(String username)  { this.username = username; return this; }
        public Builder password(String password)  { this.password = password; return this; }
        public Builder entity(Class<?> entity)    { entities.add(entity);     return this; }

        public PostgresConfig build() {
            if (url == null || url.isBlank())
                throw new IllegalStateException("PostgresConfig: url is required");
            if (username == null || username.isBlank())
                throw new IllegalStateException("PostgresConfig: username is required");
            if (entities.isEmpty())
                throw new IllegalStateException("PostgresConfig: at least one entity class is required");
            return new PostgresConfig(url, username, password, List.copyOf(entities));
        }
    }
}
