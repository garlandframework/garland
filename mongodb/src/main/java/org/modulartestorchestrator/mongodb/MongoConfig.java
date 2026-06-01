package org.modulartestorchestrator.mongodb;

import com.mongodb.client.MongoClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MongoConfig {

    private final String connectionString;
    private final String database;
    private final Map<Class<?>, String> collections;

    private MongoConfig(Builder builder) {
        this.connectionString = builder.connectionString;
        this.database         = builder.database;
        this.collections      = Collections.unmodifiableMap(new HashMap<>(builder.collections));
    }

    public String connectionString()         { return connectionString; }
    public String database()                 { return database; }
    public Map<Class<?>, String> collections() { return collections; }

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
            return new MongoConfig(this);
        }
    }
}
