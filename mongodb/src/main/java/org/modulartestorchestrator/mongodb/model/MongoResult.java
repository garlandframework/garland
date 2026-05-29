package org.modulartestorchestrator.mongodb.model;

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
