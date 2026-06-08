package dev.garlandframework.postgres;

import jakarta.persistence.Id;
import dev.garlandframework.postgres.model.PostgresRequest;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PostgresRequestIdExtractionTest {

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
        PostgresRequest<DirectId> request = PostgresRequest.findById(new DirectId());
        assertThat(request.id()).isEqualTo(42L);
    }

    @Test
    public void extracts_id_from_inherited_superclass_field() {
        PostgresRequest<ChildEntity> request = PostgresRequest.findById(new ChildEntity());
        assertThat(request.id()).isEqualTo(99L);
    }

    @Test
    public void extracts_id_through_multiple_inheritance_levels() {
        PostgresRequest<GrandChildEntity> request = PostgresRequest.findById(new GrandChildEntity());
        assertThat(request.id()).isEqualTo(99L);
    }

    @Test
    public void throws_when_no_id_field_found() {
        assertThatThrownBy(() -> PostgresRequest.findById(new NamedId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Id");
    }
}
