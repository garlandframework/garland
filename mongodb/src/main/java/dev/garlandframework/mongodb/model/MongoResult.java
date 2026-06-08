package dev.garlandframework.mongodb.model;

/**
 * Result of a MongoDB operation: the document (or {@code null} if not found) and whether
 * it existed at query time. Produced by {@link dev.garlandframework.mongodb.MongoTestClient}
 * operations; consumed by downstream assertion steps.
 */
public record MongoResult<T>(T document, boolean exists) {

    public static <T> MongoResult<T> of(T document) {
        return new MongoResult<>(document, document != null);
    }

    public static <T> MongoResult<T> empty() {
        return new MongoResult<>(null, false);
    }

    public static <T> MongoResult<T> flag(boolean exists) {
        return new MongoResult<>(null, exists);
    }
}
