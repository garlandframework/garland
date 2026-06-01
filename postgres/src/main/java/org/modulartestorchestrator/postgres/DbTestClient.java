package org.modulartestorchestrator.postgres;

import org.modulartestorchestrator.base.Pipeline;
import org.modulartestorchestrator.base.PipelineContext;
import org.modulartestorchestrator.base.StepFunction;
import org.modulartestorchestrator.base.checks.CheckSteps;
import org.modulartestorchestrator.base.retry.Retry;
import org.modulartestorchestrator.base.retry.RetryConfig;
import org.modulartestorchestrator.postgres.model.DbRequest;
import org.modulartestorchestrator.postgres.model.DbResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class DbTestClient {

    private static final Logger log = LoggerFactory.getLogger("DB");

    private final DbSteps dbSteps;
    private final DbCheckSteps dbCheck;
    private final CheckSteps check;
    private final RetryConfig retryConfig;

    public DbTestClient(HibernateWrapper hibernate, RetryConfig retryConfig) {
        this.dbSteps     = new DbSteps(hibernate);
        this.dbCheck     = new DbCheckSteps();
        this.check       = new CheckSteps();
        this.retryConfig = retryConfig;
    }

    public DbTestClient(HibernateWrapper hibernate) {
        this(hibernate, RetryConfig.attempts(1));
    }

    public <T> StepFunction<T, T> findById() {
        return (input, outerCtx) -> {
            log.info(DbTestClientLogTemplates.FIND_BY_ID, input.getClass().getSimpleName());
            T result = Pipeline.given(DbRequest.findById(input))
                    .withContext(outerCtx)
                    .then(dbSteps.setup())
                    .then(Retry.of(
                            StepFunction.<DbRequest<T>, DbResult<T>>of(dbSteps::findById)
                                    .andThen(dbCheck.entityExists()),
                            retryConfig
                    ))
                    .then((DbResult<T> r, PipelineContext ctx) -> r.entity())
                    .then(check.matchingNonNull(input))
                    .execute();
            log.info(DbTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    public <T> StepFunction<T, T> findById(Duration temporalTolerance) {
        return (input, outerCtx) -> {
            log.info(DbTestClientLogTemplates.FIND_BY_ID, input.getClass().getSimpleName());
            T result = Pipeline.given(DbRequest.findById(input))
                    .withContext(outerCtx)
                    .then(dbSteps.setup())
                    .then(Retry.of(
                            StepFunction.<DbRequest<T>, DbResult<T>>of(dbSteps::findById)
                                    .andThen(dbCheck.entityExists()),
                            retryConfig
                    ))
                    .then((DbResult<T> r, PipelineContext ctx) -> r.entity())
                    .then(check.matchingNonNull(input, temporalTolerance))
                    .execute();
            log.info(DbTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    public <T> StepFunction<T, T> findByFields() {
        return (input, outerCtx) -> {
            log.info(DbTestClientLogTemplates.FIND_BY_FIELDS, input.getClass().getSimpleName());
            T result = Pipeline.given(DbRequest.findByFields(input))
                    .withContext(outerCtx)
                    .then(dbSteps.setup())
                    .then(Retry.of(
                            StepFunction.<DbRequest<T>, DbResult<T>>of(dbSteps::findByFields)
                                    .andThen(dbCheck.entityExists()),
                            retryConfig
                    ))
                    .then((DbResult<T> r, PipelineContext ctx) -> r.entity())
                    .then(check.matchingNonNull(input))
                    .execute();
            log.info(DbTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    public <T> StepFunction<DbRequest<T>, T> persist(T expectedEntity) {
        return (request, outerCtx) -> {
            log.info(DbTestClientLogTemplates.PERSIST, expectedEntity.getClass().getSimpleName());
            T result = Pipeline.given(request)
                    .withContext(outerCtx)
                    .then(dbSteps.setup())
                    .then(dbSteps::persist)
                    .then(dbCheck.entityExists())
                    .then((DbResult<T> r, PipelineContext ctx) -> r.entity())
                    .then(check.matchingNonNull(expectedEntity))
                    .execute();
            log.info(DbTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    public <T> StepFunction<T, T> existsById() {
        return (input, outerCtx) -> {
            log.info(DbTestClientLogTemplates.EXISTS, input.getClass().getSimpleName());
            Pipeline.given(DbRequest.exists(input))
                    .withContext(outerCtx)
                    .then(dbSteps.setup())
                    .then(Retry.of(
                            StepFunction.<DbRequest<T>, DbResult<T>>of(dbSteps::exists)
                                    .andThen(dbCheck.entityExists()),
                            retryConfig
                    ))
                    .execute();
            log.info(DbTestClientLogTemplates.VERIFIED);
            return input;
        };
    }

    public <T> StepFunction<T, Void> notExistsById() {
        return (input, outerCtx) -> {
            log.info(DbTestClientLogTemplates.NOT_EXISTS, input.getClass().getSimpleName());
            Pipeline.given(DbRequest.exists(input))
                    .withContext(outerCtx)
                    .then(dbSteps.setup())
                    .then(dbSteps::exists)
                    .then(dbCheck.entityNotExists())
                    .execute();
            log.info(DbTestClientLogTemplates.ABSENT);
            return null;
        };
    }

    public <T> StepFunction<DbRequest<T>, DbResult<T>> exists() {
        return (request, outerCtx) -> {
            log.info(DbTestClientLogTemplates.EXISTS, request.entityClass().getSimpleName());
            return Pipeline.given(request)
                    .withContext(outerCtx)
                    .then(dbSteps.setup())
                    .then(dbSteps::exists)
                    .then(dbCheck.entityExists())
                    .execute();
        };
    }

    public <T> StepFunction<DbRequest<T>, Void> delete() {
        return (request, outerCtx) -> {
            log.info(DbTestClientLogTemplates.DELETE, request.entityClass().getSimpleName());
            return Pipeline.given(request)
                    .withContext(outerCtx)
                    .then(dbSteps.setup())
                    .then(dbSteps::delete)
                    .then(dbCheck.entityNotExists())
                    .then((DbResult<T> r, PipelineContext ctx) -> (Void) null)
                    .execute();
        };
    }
}
