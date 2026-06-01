package org.modulartestorchestrator.mongodb.model;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

public record MongoRequest<T>(Class<T> documentClass, Object id, T document) {

    @SuppressWarnings("unchecked")
    public static <T> MongoRequest<T> findById(T document) {
        return new MongoRequest<>((Class<T>) document.getClass(), extractId(document), document);
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

    private static Object extractId(Object document) {
        for (Field field : document.getClass().getDeclaredFields()) {
            if (isIdField(field)) {
                field.setAccessible(true);
                try {
                    return field.get(document);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        throw new IllegalArgumentException(
                "No id field found on " + document.getClass().getSimpleName() +
                " — name it 'id' or annotate with @Id"
        );
    }

    static boolean isIdField(Field field) {
        if (field.getName().equals("id")) return true;
        for (Annotation annotation : field.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals("Id")) return true;
        }
        return false;
    }
}
