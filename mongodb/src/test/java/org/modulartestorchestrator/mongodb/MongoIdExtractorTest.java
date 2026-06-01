package org.modulartestorchestrator.mongodb;

import org.testng.annotations.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MongoIdExtractorTest {

    @Retention(RetentionPolicy.RUNTIME)
    @interface Id {}

    static class FieldNamedId {
        String id = "abc-123";
        String name = "test";
    }

    static class AnnotatedId {
        @Id String documentId = "annotated-id";
        String name = "test";
    }

    static class BaseDoc {
        String id = "base-id";
    }

    static class ChildDoc extends BaseDoc {
        String data = "child";
    }

    static class GrandChildDoc extends ChildDoc {
        String extra = "extra";
    }

    static class NoIdDoc {
        String name = "no-id";
    }

    @Test
    public void isIdField_true_for_field_named_id() throws Exception {
        Field field = FieldNamedId.class.getDeclaredField("id");
        assertThat(MongoIdExtractor.isIdField(field)).isTrue();
    }

    @Test
    public void isIdField_false_for_other_fields() throws Exception {
        Field field = FieldNamedId.class.getDeclaredField("name");
        assertThat(MongoIdExtractor.isIdField(field)).isFalse();
    }

    @Test
    public void isIdField_true_for_annotated_field() throws Exception {
        Field field = AnnotatedId.class.getDeclaredField("documentId");
        assertThat(MongoIdExtractor.isIdField(field)).isTrue();
    }

    @Test
    public void findIdField_finds_field_named_id() {
        Field field = MongoIdExtractor.findIdField(FieldNamedId.class);
        assertThat(field.getName()).isEqualTo("id");
    }

    @Test
    public void findIdField_finds_inherited_id_field() {
        Field field = MongoIdExtractor.findIdField(ChildDoc.class);
        assertThat(field.getName()).isEqualTo("id");
        assertThat(field.getDeclaringClass()).isEqualTo(BaseDoc.class);
    }

    @Test
    public void findIdField_finds_id_through_multiple_inheritance_levels() {
        Field field = MongoIdExtractor.findIdField(GrandChildDoc.class);
        assertThat(field.getDeclaringClass()).isEqualTo(BaseDoc.class);
    }

    @Test
    public void findIdField_throws_when_no_id_field_found() {
        assertThatThrownBy(() -> MongoIdExtractor.findIdField(NoIdDoc.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    public void extractId_returns_id_value() {
        FieldNamedId doc = new FieldNamedId();
        assertThat(MongoIdExtractor.extractId(doc)).isEqualTo("abc-123");
    }

    @Test
    public void extractId_returns_annotated_id_value() {
        AnnotatedId doc = new AnnotatedId();
        assertThat(MongoIdExtractor.extractId(doc)).isEqualTo("annotated-id");
    }

    @Test
    public void extractId_returns_inherited_id_value() {
        ChildDoc doc = new ChildDoc();
        assertThat(MongoIdExtractor.extractId(doc)).isEqualTo("base-id");
    }
}
