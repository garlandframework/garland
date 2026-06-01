package org.modulartestorchestrator.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import org.bson.UuidRepresentation;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MongoWrapper {

    private final MongoClient client;
    private final MongoDatabase database;
    private final Map<Class<?>, String> collections;

    public MongoWrapper(MongoConfig config) {
        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
        );
        this.client = MongoClients.create(
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(config.connectionString()))
                        .uuidRepresentation(UuidRepresentation.JAVA_LEGACY)
                        .codecRegistry(codecRegistry)
                        .build()
        );
        this.database    = client.getDatabase(config.database());
        this.collections = config.collections();
    }

    public <T> Optional<T> findById(Class<T> documentClass, Object id) {
        return Optional.ofNullable(
                getCollection(documentClass).find(Filters.eq("_id", id)).first()
        );
    }

    public <T> boolean exists(Class<T> documentClass, Object id) {
        return findById(documentClass, id).isPresent();
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> findByFields(T example) {
        Class<T> documentClass = (Class<T>) example.getClass();
        List<Bson> filters = buildFilters(example);
        if (filters.isEmpty()) return Optional.empty();
        Bson query = filters.size() == 1 ? filters.get(0) : Filters.and(filters);
        List<T> results = getCollection(documentClass).find(query).limit(2).into(new ArrayList<>());
        if (results.size() > 1) {
            throw new IllegalStateException(
                    "findByFields returned more than 1 result for " +
                    documentClass.getSimpleName() + " — expected at most 1. " +
                    "Narrow your criteria or use countByFields/findById.");
        }
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @SuppressWarnings("unchecked")
    public <T> long countByFields(T example) {
        Class<T> documentClass = (Class<T>) example.getClass();
        List<Bson> filters = buildFilters(example);
        if (filters.isEmpty()) return 0L;
        Bson query = filters.size() == 1 ? filters.get(0) : Filters.and(filters);
        return getCollection(documentClass).countDocuments(query);
    }

    private static List<Bson> buildFilters(Object example) {
        List<Bson> filters = new ArrayList<>();
        Class<?> current = example.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    Object value = field.get(example);
                    if (value == null || value instanceof Collection) continue;
                    String key = MongoIdExtractor.isIdField(field) ? "_id" : field.getName();
                    filters.add(Filters.eq(key, value));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            current = current.getSuperclass();
        }
        return filters;
    }

    @SuppressWarnings("unchecked")
    public <T> T persist(T document) {
        getCollection((Class<T>) document.getClass()).insertOne(document);
        return document;
    }

    public <T> void delete(Class<T> documentClass, Object id) {
        getCollection(documentClass).deleteOne(Filters.eq("_id", id));
    }

    private <T> MongoCollection<T> getCollection(Class<T> documentClass) {
        String collectionName = collections.get(documentClass);
        if (collectionName == null) {
            throw new IllegalArgumentException(
                    "No collection registered for " + documentClass.getSimpleName() +
                    " — add .collection(" + documentClass.getSimpleName() + ".class, \"name\") to MongoConfig"
            );
        }
        return database.getCollection(collectionName, documentClass);
    }

    public void close() {
        client.close();
    }
}
