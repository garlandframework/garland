package dev.garlandframework.postgres.model;

import jakarta.persistence.Id;

import java.lang.reflect.Field;

/**
 * Input descriptor for a single database operation. Carries the entity class, an optional
 * ID for lookup and delete operations, and an optional entity instance for persist and
 * query-by-example operations.
 *
 * <p>Use the static factory methods rather than the record constructor — the factories
 * extract the {@code @Id} field automatically and set the correct combination of fields
 * for each operation type.
 */
public record PostgresRequest<T>(
        Class<T> entityClass,
        Object id,
        T entity
) {
    /**
     * Builds a by-ID lookup request. Extracts the {@code @Id} field value from
     * {@code entity} — throws {@link IllegalArgumentException} if no annotated field
     * is found.
     */
    @SuppressWarnings("unchecked")
    public static <T> PostgresRequest<T> findById(T entity) {
        return new PostgresRequest<>((Class<T>) entity.getClass(), extractId(entity), entity);
    }

    private static Object extractId(Object entity) {
        Class<?> current = entity.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    field.setAccessible(true);
                    try {
                        return field.get(entity);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            current = current.getSuperclass();
        }
        throw new IllegalArgumentException("No @Id field found on " + entity.getClass().getSimpleName());
    }

    public static <T> PostgresRequest<T> exists(Class<T> entityClass, Object id) {
        return new PostgresRequest<>(entityClass, id, null);
    }

    @SuppressWarnings("unchecked")
    public static <T> PostgresRequest<T> exists(T entity) {
        return new PostgresRequest<>((Class<T>) entity.getClass(), extractId(entity), null);
    }

    @SuppressWarnings("unchecked")
    public static <T> PostgresRequest<T> persist(T entity) {
        return new PostgresRequest<>((Class<T>) entity.getClass(), null, entity);
    }

    public static <T> PostgresRequest<T> delete(Class<T> entityClass, Object id) {
        return new PostgresRequest<>(entityClass, id, null);
    }

    /** Builds a query-by-example request. All non-null fields of {@code example} become filter predicates. */
    @SuppressWarnings("unchecked")
    public static <T> PostgresRequest<T> findByFields(T example) {
        return new PostgresRequest<>((Class<T>) example.getClass(), null, example);
    }

    @SuppressWarnings("unchecked")
    public static <T> PostgresRequest<T> countByFields(T example) {
        return new PostgresRequest<>((Class<T>) example.getClass(), null, example);
    }
}
