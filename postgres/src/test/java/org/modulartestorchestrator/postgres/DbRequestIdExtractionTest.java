package org.modulartestorchestrator.postgres;

import jakarta.persistence.Id;
import org.modulartestorchestrator.postgres.model.DbRequest;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DbRequestIdExtractionTest {

    static class DirectId {
        @Id long id = 42L;
        String name = "test";
    }

    static class NamedId {
        long notId = 0;
    }

    static class BaseEntity {
        @Id long id = 99L;
    }

    static class ChildEntity extends BaseEntity {
        String data = "child";
    }

    static class GrandChildEntity extends ChildEntity {
        String extra = "extra";
    }

    @Test
    public void extracts_id_from_annotated_field() {
        DbRequest<DirectId> request = DbRequest.findById(new DirectId());
        assertThat(request.id()).isEqualTo(42L);
    }

    @Test
    public void extracts_id_from_inherited_superclass_field() {
        DbRequest<ChildEntity> request = DbRequest.findById(new ChildEntity());
        assertThat(request.id()).isEqualTo(99L);
    }

    @Test
    public void extracts_id_through_multiple_inheritance_levels() {
        DbRequest<GrandChildEntity> request = DbRequest.findById(new GrandChildEntity());
        assertThat(request.id()).isEqualTo(99L);
    }

    @Test
    public void throws_when_no_id_field_found() {
        assertThatThrownBy(() -> DbRequest.findById(new NamedId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Id");
    }
}
