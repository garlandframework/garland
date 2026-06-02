package org.modulartestorchestrator.mongodb.model;

import org.modulartestorchestrator.mongodb.MongoIdExtractor;

/**
 * Input descriptor for a single MongoDB operation. Use the static factory methods rather
 * than the record constructor — each factory sets the correct combination of fields for
 * its operation type and extracts the document ID automatically where needed.
 */
public record MongoRequest<T>(Class<T> documentClass, Object id, T document) {

    /**
     * Builds a by-ID lookup request. Extracts the ID field from {@code document} — throws
     * {@link IllegalArgumentException} if no field named {@code "id"} or annotated with
     * {@code @Id} is found.
     */
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
