package dev.garlandframework.mongodb;

import dev.garlandframework.base.PipelineContext;
import dev.garlandframework.mongodb.model.MongoRequest;
import dev.garlandframework.mongodb.model.MongoResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Low-level MongoDB steps: findById, findByFields, countByFields, persist, delete,
 * exists. Used internally by {@link MongoTestClient}.
 */
public class MongoSteps {

    private static final Logger log = LoggerFactory.getLogger(MongoSteps.class);

    private final MongoWrapper mongo;

    public MongoSteps(MongoWrapper mongo) {
        this.mongo = mongo;
    }

    public <T> MongoResult<T> findById(MongoRequest<T> input, PipelineContext ctx) {
        log.info(MongoStepsLogTemplates.FIND_BY_ID, input.documentClass().getSimpleName(), input.id());
        Optional<T> document = mongo.findById(input.documentClass(), input.id());
        if (document.isPresent()) {
            log.info(MongoStepsLogTemplates.FOUND, input.documentClass().getSimpleName(), input.id());
        } else {
            log.info(MongoStepsLogTemplates.NOT_FOUND, input.documentClass().getSimpleName(), input.id());
        }
        return MongoResult.of(document.orElse(null));
    }

    public <T> MongoResult<T> findByFields(MongoRequest<T> input, PipelineContext ctx) {
        log.info(MongoStepsLogTemplates.FIND_BY_FIELDS, input.documentClass().getSimpleName());
        Optional<T> document = mongo.findByFields(input.document());
        if (document.isPresent()) {
            log.info(MongoStepsLogTemplates.FOUND, input.documentClass().getSimpleName(), input.document());
        } else {
            log.info(MongoStepsLogTemplates.NOT_FOUND, input.documentClass().getSimpleName(), input.document());
        }
        return MongoResult.of(document.orElse(null));
    }

    public <T> MongoResult<T> exists(MongoRequest<T> input, PipelineContext ctx) {
        log.info(MongoStepsLogTemplates.EXISTS_CHECK, input.documentClass().getSimpleName(), input.id());
        boolean exists = mongo.exists(input.documentClass(), input.id());
        log.info(MongoStepsLogTemplates.EXISTS_RESULT, exists, input.documentClass().getSimpleName(), input.id());
        return MongoResult.flag(exists);
    }

    public <T> Long countByFields(MongoRequest<T> input, PipelineContext ctx) {
        log.info(MongoStepsLogTemplates.COUNT_BY_FIELDS, input.documentClass().getSimpleName());
        long count = mongo.countByFields(input.document());
        log.info(MongoStepsLogTemplates.COUNT_RESULT, count, input.documentClass().getSimpleName());
        return count;
    }

    public <T> MongoResult<T> persist(MongoRequest<T> input, PipelineContext ctx) {
        log.info(MongoStepsLogTemplates.PERSIST, input.documentClass().getSimpleName());
        T persisted = mongo.persist(input.document());
        log.info(MongoStepsLogTemplates.PERSISTED, input.documentClass().getSimpleName());
        return MongoResult.of(persisted);
    }

    public <T> MongoResult<T> delete(MongoRequest<T> input, PipelineContext ctx) {
        log.info(MongoStepsLogTemplates.DELETE, input.documentClass().getSimpleName(), input.id());
        mongo.delete(input.documentClass(), input.id());
        log.info(MongoStepsLogTemplates.DELETED, input.documentClass().getSimpleName(), input.id());
        return MongoResult.flag(false);
    }
}
