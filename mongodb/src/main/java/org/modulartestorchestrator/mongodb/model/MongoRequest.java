package org.modulartestorchestrator.mongodb.model;

import org.modulartestorchestrator.mongodb.MongoIdExtractor;

public record MongoRequest<T>(Class<T> documentClass, Object id, T document) {

    @SuppressWarnings("unchecked")
    public static <T> MongoRequest<T> findById(T document) {
        return new MongoRequest<>((Class<T>) document.getClass(), MongoIdExtractor.extractId(document), document);
    }

    @SuppressWarnings("unchecked")
    public static <T> MongoRequest<T> findByFields(T document) {
        return new MongoRequest<>((Class<T>) document.getClass(), null, document);
    }

    @SuppressWarnings("unchecked")
    public static <T> MongoRequest<T> countByFields(T document) {
        return new MongoRequest<>((Class<T>) document.getClass(), null, document);
    }

    @SuppressWarnings("unchecked")
    public static <T> MongoRequest<T> persist(T document) {
        return new MongoRequest<>((Class<T>) document.getClass(), null, document);
    }

    public static <T> MongoRequest<T> delete(Class<T> documentClass, Object id) {
        return new MongoRequest<>(documentClass, id, null);
    }

}
