package org.modulartestorchestrator.mongodb;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

public final class MongoIdExtractor {

    private MongoIdExtractor() {}

    public static Object extractId(Object document) {
        Field field = findIdField(document.getClass());
        field.setAccessible(true);
        try {
            return field.get(document);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isIdField(Field field) {
        if (field.getName().equals("id")) return true;
        for (Annotation annotation : field.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals("Id")) return true;
        }
        return false;
    }

    public static Field findIdField(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (isIdField(field)) return field;
            }
            current = current.getSuperclass();
        }
        throw new IllegalArgumentException(
                "No id field found on " + clazz.getSimpleName() +
                " — name it 'id' or annotate with @Id"
        );
    }
}
