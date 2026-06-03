package org.modulartestorchestrator.mongodb;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Connection and collection mapping configuration for {@link MongoWrapper}. Each document
 * class used in queries must be mapped to its MongoDB collection name via
 * {@link Builder#collection} — an unmapped class causes an {@link IllegalArgumentException}
 * at query time, not at construction time.
 */
public record MongoConfig(String connectionString, String database, Map<Class<?>, String> collections) {

    public static Builder builder() { return new Builder(); }

    public static class Builder {

        private String connectionString;
        private String database;
        private final Map<Class<?>, String> collections = new HashMap<>();

        public Builder connectionString(String connectionString) {
            this.connectionString = connectionString;
            return this;
        }

        public Builder database(String database) {
            this.database = database;
            return this;
        }

        public Builder collection(Class<?> documentClass, String collectionName) {
            collections.put(documentClass, collectionName);
            return this;
        }

        public MongoConfig build() {
            if (connectionString == null || connectionString.isBlank())
                throw new IllegalStateException("MongoConfig: connectionString is required");
            if (database == null || database.isBlank())
                throw new IllegalStateException("MongoConfig: database is required");
            if (collections.isEmpty())
                throw new IllegalStateException("MongoConfig: at least one collection mapping is required");
            return new MongoConfig(connectionString, database, Collections.unmodifiableMap(new HashMap<>(collections)));
        }
    }
}
