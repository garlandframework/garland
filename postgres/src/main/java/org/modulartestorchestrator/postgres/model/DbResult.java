package org.modulartestorchestrator.postgres.model;

/**
 * Result of a database operation: the entity (or {@code null} if not found) and whether
 * it existed at query time. Produced by {@link org.modulartestorchestrator.postgres.DbTestClient}
 * operations; consumed by downstream assertion steps.
 */
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
