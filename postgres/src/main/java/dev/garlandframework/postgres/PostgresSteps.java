package dev.garlandframework.postgres;

import dev.garlandframework.base.PipelineContext;
import dev.garlandframework.postgres.model.PostgresRequest;
import dev.garlandframework.postgres.model.PostgresResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Low-level Hibernate steps: findById, findByFields, countByFields, persist, delete,
 * exists. Used internally by {@link PostgresTestClient}.
 */
public class PostgresSteps {

    private static final Logger log = LoggerFactory.getLogger(PostgresSteps.class);

    private final PostgresWrapper postgres;

    public PostgresSteps(PostgresWrapper postgres) {
        this.postgres = postgres;
    }

    public <T> PostgresResult<T> findByFields(PostgresRequest<T> input, PipelineContext ctx) {
        log.info(PostgresStepsLogTemplates.FIND_BY_FIELDS, input.entityClass().getSimpleName());
        Optional<T> entity = postgres.findByFields(input.entity());
        if (entity.isPresent()) {
            log.info(PostgresStepsLogTemplates.FOUND, input.entityClass().getSimpleName(), input.entity());
        } else {
            log.info(PostgresStepsLogTemplates.NOT_FOUND, input.entityClass().getSimpleName(), input.entity());
        }
        return PostgresResult.of(entity.orElse(null));
    }

    public <T> PostgresResult<T> findById(PostgresRequest<T> input, PipelineContext ctx) {
        log.info(PostgresStepsLogTemplates.FIND_BY_ID, input.entityClass().getSimpleName(), input.id());
        Optional<T> entity = postgres.findById(input.entityClass(), input.id());
        if (entity.isPresent()) {
            log.info(PostgresStepsLogTemplates.FOUND, input.entityClass().getSimpleName(), input.id());
        } else {
            log.info(PostgresStepsLogTemplates.NOT_FOUND, input.entityClass().getSimpleName(), input.id());
        }
        return PostgresResult.of(entity.orElse(null));
    }

    public <T> PostgresResult<T> exists(PostgresRequest<T> input, PipelineContext ctx) {
        log.info(PostgresStepsLogTemplates.EXISTS_CHECK, input.entityClass().getSimpleName(), input.id());
        boolean exists = postgres.exists(input.entityClass(), input.id());
        log.info(PostgresStepsLogTemplates.EXISTS_RESULT, exists, input.entityClass().getSimpleName(), input.id());
        return PostgresResult.flag(exists);
    }

    public <T> Long countByFields(PostgresRequest<T> input, PipelineContext ctx) {
        log.info(PostgresStepsLogTemplates.COUNT_BY_FIELDS, input.entityClass().getSimpleName());
        long count = postgres.countByFields(input.entity());
        log.info(PostgresStepsLogTemplates.COUNT_RESULT, count, input.entityClass().getSimpleName());
        return count;
    }

    public <T> PostgresResult<T> persist(PostgresRequest<T> input, PipelineContext ctx) {
        log.info(PostgresStepsLogTemplates.PERSIST, input.entityClass().getSimpleName());
        T persisted = postgres.persist(input.entity());
        log.info(PostgresStepsLogTemplates.PERSISTED, input.entityClass().getSimpleName());
        return PostgresResult.of(persisted);
    }

    public <T> PostgresResult<T> delete(PostgresRequest<T> input, PipelineContext ctx) {
        log.info(PostgresStepsLogTemplates.DELETE, input.entityClass().getSimpleName(), input.id());
        postgres.delete(input.entityClass(), input.id());
        log.info(PostgresStepsLogTemplates.DELETED, input.entityClass().getSimpleName(), input.id());
        return PostgresResult.flag(false);
    }
}
