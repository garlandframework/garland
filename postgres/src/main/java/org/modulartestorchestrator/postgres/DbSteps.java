package org.modulartestorchestrator.postgres;

import org.modulartestorchestrator.base.PipelineContext;
import org.modulartestorchestrator.postgres.model.DbRequest;
import org.modulartestorchestrator.postgres.model.DbResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Low-level Hibernate steps: findById, findByFields, countByFields, persist, delete,
 * exists. Used internally by {@link DbTestClient}.
 */
public class DbSteps {

    private static final Logger log = LoggerFactory.getLogger(DbSteps.class);

    private final HibernateWrapper hibernate;

    public DbSteps(HibernateWrapper hibernate) {
        this.hibernate = hibernate;
    }

    public <T> DbResult<T> findByFields(DbRequest<T> input, PipelineContext ctx) {
        log.info(DbStepsLogTemplates.FIND_BY_FIELDS, input.entityClass().getSimpleName());
        Optional<T> entity = hibernate.findByFields(input.entity());
        if (entity.isPresent()) {
            log.info(DbStepsLogTemplates.FOUND, input.entityClass().getSimpleName(), input.entity());
        } else {
            log.info(DbStepsLogTemplates.NOT_FOUND, input.entityClass().getSimpleName(), input.entity());
        }
        return DbResult.of(entity.orElse(null));
    }

    public <T> DbResult<T> findById(DbRequest<T> input, PipelineContext ctx) {
        log.info(DbStepsLogTemplates.FIND_BY_ID, input.entityClass().getSimpleName(), input.id());
        Optional<T> entity = hibernate.findById(input.entityClass(), input.id());
        if (entity.isPresent()) {
            log.info(DbStepsLogTemplates.FOUND, input.entityClass().getSimpleName(), input.id());
        } else {
            log.info(DbStepsLogTemplates.NOT_FOUND, input.entityClass().getSimpleName(), input.id());
        }
        return DbResult.of(entity.orElse(null));
    }

    public <T> DbResult<T> exists(DbRequest<T> input, PipelineContext ctx) {
        log.info(DbStepsLogTemplates.EXISTS_CHECK, input.entityClass().getSimpleName(), input.id());
        boolean exists = hibernate.exists(input.entityClass(), input.id());
        log.info(DbStepsLogTemplates.EXISTS_RESULT, exists, input.entityClass().getSimpleName(), input.id());
        return DbResult.flag(exists);
    }

    public <T> Long countByFields(DbRequest<T> input, PipelineContext ctx) {
        log.info(DbStepsLogTemplates.COUNT_BY_FIELDS, input.entityClass().getSimpleName());
        long count = hibernate.countByFields(input.entity());
        log.info(DbStepsLogTemplates.COUNT_RESULT, count, input.entityClass().getSimpleName());
        return count;
    }

    public <T> DbResult<T> persist(DbRequest<T> input, PipelineContext ctx) {
        log.info(DbStepsLogTemplates.PERSIST, input.entityClass().getSimpleName());
        T persisted = hibernate.persist(input.entity());
        log.info(DbStepsLogTemplates.PERSISTED, input.entityClass().getSimpleName());
        return DbResult.of(persisted);
    }

    public <T> DbResult<T> delete(DbRequest<T> input, PipelineContext ctx) {
        log.info(DbStepsLogTemplates.DELETE, input.entityClass().getSimpleName(), input.id());
        hibernate.delete(input.entityClass(), input.id());
        log.info(DbStepsLogTemplates.DELETED, input.entityClass().getSimpleName(), input.id());
        return DbResult.flag(false);
    }
}
