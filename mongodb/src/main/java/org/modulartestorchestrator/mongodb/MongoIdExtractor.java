package org.modulartestorchestrator.mongodb;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

final class MongoIdExtractor {

    private MongoIdExtractor() {}

    static Object extractId(Object document) {
        Field field = findIdField(document.getClass());
        field.setAccessible(true);
        try {
            return field.get(document);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean isIdField(Field field) {
        if (field.getName().equals("id")) return true;
        for (Annotation annotation : field.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals("Id")) return true;
        }
        return false;
    }

    static Field findIdField(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (isIdField(field)) return field;
        }
        throw new IllegalArgumentException(
                "No id field found on " + clazz.getSimpleName() +
                " — name it 'id' or annotate with @Id"
        );
    }
}
