package org.modulartestorchestrator.mongodb;

import org.modulartestorchestrator.base.Pipeline;
import org.modulartestorchestrator.base.PipelineContext;
import org.modulartestorchestrator.base.StepFunction;
import org.modulartestorchestrator.base.checks.CheckSteps;
import org.modulartestorchestrator.base.retry.Retry;
import org.modulartestorchestrator.base.retry.RetryConfig;
import org.modulartestorchestrator.mongodb.model.MongoRequest;
import org.modulartestorchestrator.mongodb.model.MongoResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoTestClient {

    private static final Logger log = LoggerFactory.getLogger("MONGO");

    private final MongoSteps mongoSteps;
    private final MongoCheckSteps mongoCheck;
    private final CheckSteps check;
    private final RetryConfig retryConfig;

    public MongoTestClient(MongoWrapper mongo, RetryConfig retryConfig) {
        this.mongoSteps  = new MongoSteps(mongo);
        this.mongoCheck  = new MongoCheckSteps();
        this.check       = new CheckSteps();
        this.retryConfig = retryConfig;
    }

    public MongoTestClient(MongoWrapper mongo) {
        this(mongo, RetryConfig.attempts(1));
    }

    public <T> StepFunction<T, T> findById() {
        return (input, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.FIND_BY_ID, input.getClass().getSimpleName());
            T result = Pipeline.given(MongoRequest.findById(input))
                    .withContext(outerCtx)
                    .then(Retry.of(
                            StepFunction.<MongoRequest<T>, MongoResult<T>>of(mongoSteps::findById)
                                    .andThen(mongoCheck.documentExists()),
                            retryConfig
                    ))
                    .then((MongoResult<T> r, PipelineContext ctx) -> r.document())
                    .then(check.matchingNonNull(input))
                    .execute();
            log.info(MongoTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    public <T> StepFunction<T, T> findByFields() {
        return (input, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.FIND_BY_FIELDS, input.getClass().getSimpleName());
            T result = Pipeline.given(MongoRequest.findByFields(input))
                    .withContext(outerCtx)
                    .then(Retry.of(
                            StepFunction.<MongoRequest<T>, MongoResult<T>>of(mongoSteps::findByFields)
                                    .andThen(mongoCheck.documentExists()),
                            retryConfig
                    ))
                    .then((MongoResult<T> r, PipelineContext ctx) -> r.document())
                    .then(check.matchingNonNull(input))
                    .execute();
            log.info(MongoTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    public <T> StepFunction<MongoRequest<T>, T> persist(T expectedDocument) {
        return (request, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.PERSIST, expectedDocument.getClass().getSimpleName());
            T result = Pipeline.given(request)
                    .withContext(outerCtx)
                    .then(mongoSteps::persist)
                    .then(mongoCheck.documentExists())
                    .then((MongoResult<T> r, PipelineContext ctx) -> r.document())
                    .then(check.matchingNonNull(expectedDocument))
                    .execute();
            log.info(MongoTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    public <T> StepFunction<T, T> existsById() {
        return (input, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.EXISTS, input.getClass().getSimpleName());
            Pipeline.given(MongoRequest.findById(input))
                    .withContext(outerCtx)
                    .then(Retry.of(
                            StepFunction.<MongoRequest<T>, MongoResult<T>>of(mongoSteps::exists)
                                    .andThen(mongoCheck.documentExists()),
                            retryConfig
                    ))
                    .execute();
            log.info(MongoTestClientLogTemplates.VERIFIED);
            return input;
        };
    }

    public <T> StepFunction<T, Void> notExistsById() {
        return (input, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.NOT_EXISTS, input.getClass().getSimpleName());
            Pipeline.given(MongoRequest.findById(input))
                    .withContext(outerCtx)
                    .then(mongoSteps::exists)
                    .then(mongoCheck.documentNotExists())
                    .execute();
            log.info(MongoTestClientLogTemplates.ABSENT);
            return null;
        };
    }

    public <T> StepFunction<MongoRequest<T>, MongoResult<T>> exists() {
        return (request, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.EXISTS, request.documentClass().getSimpleName());
            return Pipeline.given(request)
                    .withContext(outerCtx)
                    .then(mongoSteps::exists)
                    .then(mongoCheck.documentExists())
                    .execute();
        };
    }

    public <T> StepFunction<MongoRequest<T>, Void> delete() {
        return (request, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.DELETE, request.documentClass().getSimpleName());
            return Pipeline.given(request)
                    .withContext(outerCtx)
                    .then(mongoSteps::delete)
                    .then(mongoCheck.documentNotExists())
                    .then((MongoResult<T> r, PipelineContext ctx) -> (Void) null)
                    .execute();
        };
    }
}
