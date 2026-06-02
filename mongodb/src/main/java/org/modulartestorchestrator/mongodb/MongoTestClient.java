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

import java.time.Duration;

/**
 * Test client for MongoDB assertions. Mirrors the API of
 * {@link org.modulartestorchestrator.postgres.DbTestClient} but operates on MongoDB
 * collections registered in {@link MongoConfig}.
 *
 * <p>Document classes must be registered via {@link MongoConfig.Builder#collection} before
 * constructing this client. The ID field is identified by the name {@code "id"} or any
 * annotation named {@code @Id}.
 *
 * <p><strong>Timestamp precision:</strong> MongoDB stores {@code Instant} with millisecond
 * resolution. If documents contain nanosecond-precision timestamps (e.g. {@code Instant.now()}
 * from Java 9+), use the {@link #findById(Duration)} overload with
 * {@code Duration.ofMillis(1)} to absorb the truncation difference.
 */
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

    /**
     * Finds a document by its ID field and asserts it matches the input (null fields
     * ignored). Retries until found — use when the document is written asynchronously.
     *
     * @see #findById(Duration) when the document contains timestamp fields
     */
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

    /**
     * Same as {@link #findById()} but applies temporal tolerance to timestamp fields.
     * Use {@code Duration.ofMillis(1)} to absorb MongoDB's millisecond truncation when
     * your documents are constructed with nanosecond-precision {@code Instant} values.
     */
    public <T> StepFunction<T, T> findById(Duration temporalTolerance) {
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
                    .then(check.matchingNonNull(input, temporalTolerance))
                    .execute();
            log.info(MongoTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * Queries by all non-null fields of the input, finds the single matching document,
     * and asserts it matches. Throws {@link IllegalStateException} if more than one
     * document matches — narrow your criteria or use {@link #countByFields()}.
     */
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

    /**
     * Returns the count of documents matching all non-null fields. No assertion is
     * performed — chain {@link org.modulartestorchestrator.base.checks.Verify} steps to assert the count.
     */
    public <T> StepFunction<T, Long> countByFields() {
        return (input, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.COUNT_BY_FIELDS, input.getClass().getSimpleName());
            Long count = Pipeline.given(MongoRequest.countByFields(input))
                    .withContext(outerCtx)
                    .then(mongoSteps::countByFields)
                    .execute();
            log.info(MongoTestClientLogTemplates.VERIFIED);
            return count;
        };
    }

    /**
     * Inserts a document and asserts the stored result matches {@code expectedDocument}.
     * Use for test setup when you need a specific document in the collection before the
     * system-under-test runs.
     */
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

    /**
     * Asserts a document with the same ID exists. Retries until found. Returns the input
     * unchanged.
     *
     * @see #notExistsById() for the negative case
     */
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

    /**
     * Asserts a document with the same ID does not exist. No retry — intended for
     * synchronous deletions where absence is immediate.
     */
    public <T> StepFunction<T, T> notExistsById() {
        return (input, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.NOT_EXISTS, input.getClass().getSimpleName());
            Pipeline.given(MongoRequest.findById(input))
                    .withContext(outerCtx)
                    .then(mongoSteps::exists)
                    .then(mongoCheck.documentNotExists())
                    .execute();
            log.info(MongoTestClientLogTemplates.ABSENT);
            return input;
        };
    }

    /**
     * Lower-level variant of {@link #existsById()} that accepts a pre-built
     * {@link MongoRequest} and returns the raw {@link MongoResult}. Use when downstream
     * steps need both the document and the exists flag.
     */
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
