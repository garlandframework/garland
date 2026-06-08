package dev.garlandframework.postgres.model;

/**
 * Result of a database operation: the entity (or {@code null} if not found) and whether
 * it existed at query time. Produced by {@link dev.garlandframework.postgres.PostgresTestClient}
 * operations; consumed by downstream assertion steps.
 */
public record PostgresResult<T>(T entity, boolean exists) {

    public static <T> PostgresResult<T> of(T entity) {
        return new PostgresResult<>(entity, entity != null);
    }

    public static <T> PostgresResult<T> empty() {
        return new PostgresResult<>(null, false);
    }

    public static <T> PostgresResult<T> flag(boolean exists) {
        return new PostgresResult<>(null, exists);
    }
}
