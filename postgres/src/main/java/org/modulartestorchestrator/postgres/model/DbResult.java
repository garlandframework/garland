package org.modulartestorchestrator.postgres.model;

public record DbResult<T>(T entity, boolean exists) {

    public static <T> DbResult<T> of(T entity) {
        return new DbResult<>(entity, entity != null);
    }

    public static <T> DbResult<T> empty() {
        return new DbResult<>(null, false);
    }

    public static <T> DbResult<T> flag(boolean exists) {
        return new DbResult<>(null, exists);
    }
}
