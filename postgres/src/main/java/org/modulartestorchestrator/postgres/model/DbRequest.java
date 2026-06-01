package org.modulartestorchestrator.postgres.model;

import jakarta.persistence.Id;

import java.lang.reflect.Field;

public record DbRequest<T>(
        Class<T> entityClass,
        Object id,
        T entity
) {
    @SuppressWarnings("unchecked")
    public static <T> DbRequest<T> findById(T entity) {
        return new DbRequest<>((Class<T>) entity.getClass(), extractId(entity), entity);
    }

    private static Object extractId(Object entity) {
        for (Field field : entity.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                field.setAccessible(true);
                try {
                    return field.get(entity);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        throw new IllegalArgumentException("No @Id field found on " + entity.getClass().getSimpleName());
    }

    public static <T> DbRequest<T> exists(Class<T> entityClass, Object id) {
        return new DbRequest<>(entityClass, id, null);
    }

    @SuppressWarnings("unchecked")
    public static <T> DbRequest<T> exists(T entity) {
        return new DbRequest<>((Class<T>) entity.getClass(), extractId(entity), null);
    }

    @SuppressWarnings("unchecked")
    public static <T> DbRequest<T> persist(T entity) {
        return new DbRequest<>((Class<T>) entity.getClass(), null, entity);
    }

    public static <T> DbRequest<T> delete(Class<T> entityClass, Object id) {
        return new DbRequest<>(entityClass, id, null);
    }

    @SuppressWarnings("unchecked")
    public static <T> DbRequest<T> findByFields(T example) {
        return new DbRequest<>((Class<T>) example.getClass(), null, example);
    }
}
