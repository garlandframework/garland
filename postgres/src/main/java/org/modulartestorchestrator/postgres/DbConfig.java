package org.modulartestorchestrator.postgres;

import java.util.ArrayList;
import java.util.List;

/**
 * Connection and entity configuration for {@link HibernateWrapper}. All entity classes
 * that the test suite will query must be registered via {@link Builder#entity} — Hibernate
 * validates the schema against this list at construction time and will throw if an entity
 * is missing or if the live schema does not match.
 */
public class DbConfig {

    private final String url;
    private final String username;
    private final String password;
    private final List<Class<?>> entities;

    private DbConfig(Builder builder) {
        this.url      = builder.url;
        this.username = builder.username;
        this.password = builder.password;
        this.entities = List.copyOf(builder.entities);
    }

    public String url()              { return url; }
    public String username()         { return username; }
    public String password()         { return password; }
    public List<Class<?>> entities() { return entities; }

    public static Builder builder()  { return new Builder(); }

    public static class Builder {

        private String url;
        private String username;
        private String password;
        private final List<Class<?>> entities = new ArrayList<>();

        public Builder url(String url)            { this.url = url;           return this; }
        public Builder username(String username)  { this.username = username; return this; }
        public Builder password(String password)  { this.password = password; return this; }
        public Builder entity(Class<?> entity)    { entities.add(entity);     return this; }

        public DbConfig build() {
            if (url == null || url.isBlank())
                throw new IllegalStateException("DbConfig: url is required");
            if (username == null || username.isBlank())
                throw new IllegalStateException("DbConfig: username is required");
            if (entities.isEmpty())
                throw new IllegalStateException("DbConfig: at least one entity class is required");
            return new DbConfig(this);
        }
    }
}
