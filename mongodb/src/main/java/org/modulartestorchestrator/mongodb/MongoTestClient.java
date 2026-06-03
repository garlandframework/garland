package org.modulartestorchestrator.mongodb;

import org.modulartestorchestrator.base.PipelineContext;
import org.modulartestorchestrator.base.Step;
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
 * {@link org.modulartestorchestrator.postgres.PostgresTestClient} but operates on MongoDB
 * collections registered in {@link MongoConfig}.
 *
 * <p>Document classes must be registered via {@link MongoConfig.Builder#collection} before
 * constructing this client. The ID field is identified by the name {@code "id"} or any
 * annotation named {@code @Id}.
 *
 * <p><strong>Timestamp precision:</strong> MongoDB stores {@code Instant} with millisecond
 * resolution. If documents contain nanosecond-precision timestamps (e.g. {@code Instant.now()}
 * from Java 9+), use {@link #withTemporalTolerance(Duration) withTemporalTolerance(Duration.ofMillis(1))}
 * to absorb the truncation difference globally, or use the {@link #findById(Duration)} overload
 * for a single call.
 */
public class MongoTestClient {

    private static final Logger log = LoggerFactory.getLogger("MONGO");

    private final MongoWrapper mongo;
    private final MongoSteps mongoSteps;
    private final MongoCheckSteps mongoCheck;
    private final CheckSteps check;
    private final RetryConfig retryConfig;
    private final Duration defaultTemporalTolerance;

    private MongoTestClient(MongoWrapper mongo, RetryConfig retryConfig, Duration defaultTemporalTolerance) {
        this.mongo                   = mongo;
        this.mongoSteps              = new MongoSteps(mongo);
        this.mongoCheck              = new MongoCheckSteps();
        this.check                   = new CheckSteps();
        this.retryConfig             = retryConfig;
        this.defaultTemporalTolerance = defaultTemporalTolerance;
    }

    public MongoTestClient(MongoWrapper mongo, RetryConfig retryConfig) {
        this(mongo, retryConfig, null);
    }

    public MongoTestClient(MongoWrapper mongo) {
        this(mongo, RetryConfig.attempts(1), null);
    }

    /**
     * Returns a new client that applies {@code tolerance} to every {@link #findById()} call
     * by default. Individual calls can still override with {@link #findById(Duration)}.
     *
     * <p>Use {@code Duration.ofMillis(1)} to absorb MongoDB's millisecond truncation globally
     * instead of annotating every assertion site:
     * <pre>
     * mongoClient = new MongoTestClient(mongo, retryConfig)
     *         .withTemporalTolerance(Duration.ofMillis(1));
     * </pre>
     */
    public MongoTestClient withTemporalTolerance(Duration tolerance) {
        return new MongoTestClient(mongo, retryConfig, tolerance);
    }

    /**
     * Finds a document by its ID field and asserts it matches the input (null fields
     * ignored). Retries until found — use when the document is written asynchronously.
     *
     * <p>If a default tolerance was set via {@link #withTemporalTolerance(Duration)}, it is
     * applied automatically. Use {@link #findById(Duration)} to override for a specific call.
     */
    public <T> Step<T, T> findById() {
        return defaultTemporalTolerance != null ? findById(defaultTemporalTolerance) : (input, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.FIND_BY_ID, input.getClass().getSimpleName());
            T result = Retry.of(
                            Step.<MongoRequest<T>, MongoResult<T>>of(mongoSteps::findById)
                                    .andThen(mongoCheck.documentExists()),
                            retryConfig
                    )
                    .andThen((MongoResult<T> r, PipelineContext ctx) -> r.document())
                    .andThen(check.matchingNonNull(input))
                    .apply(MongoRequest.findById(input), outerCtx);
            log.info(MongoTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * Same as {@link #findById()} but overrides the tolerance for this specific call.
     * Use when a particular assertion needs a higher tolerance than the client default
     * (e.g. asserting a service-generated timestamp within an SLA window).
     */
    public <T> Step<T, T> findById(Duration temporalTolerance) {
        return (input, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.FIND_BY_ID, input.getClass().getSimpleName());
            T result = Retry.of(
                            Step.<MongoRequest<T>, MongoResult<T>>of(mongoSteps::findById)
                                    .andThen(mongoCheck.documentExists()),
                            retryConfig
                    )
                    .andThen((MongoResult<T> r, PipelineContext ctx) -> r.document())
                    .andThen(check.matchingNonNull(input, temporalTolerance))
                    .apply(MongoRequest.findById(input), outerCtx);
            log.info(MongoTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * Queries by all non-null fields of the input, finds the single matching document,
     * and asserts it matches. Throws {@link IllegalStateException} if more than one
     * document matches — narrow your criteria or use {@link #countByFields()}.
     */
    public <T> Step<T, T> findByFields() {
        return (input, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.FIND_BY_FIELDS, input.getClass().getSimpleName());
            T result = Retry.of(
                            Step.<MongoRequest<T>, MongoResult<T>>of(mongoSteps::findByFields)
                                    .andThen(mongoCheck.documentExists()),
                            retryConfig
                    )
                    .andThen((MongoResult<T> r, PipelineContext ctx) -> r.document())
                    .andThen(check.matchingNonNull(input))
                    .apply(MongoRequest.findByFields(input), outerCtx);
            log.info(MongoTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * Returns the count of documents matching all non-null fields. No assertion is
     * performed — chain {@link org.modulartestorchestrator.base.checks.Verify} steps to assert the count.
     */
    public <T> Step<T, Long> countByFields() {
        return (input, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.COUNT_BY_FIELDS, input.getClass().getSimpleName());
            Long count = Step.<MongoRequest<T>, Long>of(mongoSteps::countByFields)
                    .apply(MongoRequest.countByFields(input), outerCtx);
            log.info(MongoTestClientLogTemplates.VERIFIED);
            return count;
        };
    }

    /**
     * Inserts a document and asserts the stored result matches {@code expectedDocument}.
     * Use for test setup when you need a specific document in the collection before the
     * system-under-test runs.
     */
    public <T> Step<MongoRequest<T>, T> persist(T expectedDocument) {
        return (request, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.PERSIST, expectedDocument.getClass().getSimpleName());
            T result = Step.<MongoRequest<T>, MongoResult<T>>of(mongoSteps::persist)
                    .andThen(mongoCheck.documentExists())
                    .andThen((MongoResult<T> r, PipelineContext ctx) -> r.document())
                    .andThen(check.matchingNonNull(expectedDocument))
                    .apply(request, outerCtx);
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
    public <T> Step<T, T> existsById() {
        return (input, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.EXISTS, input.getClass().getSimpleName());
            Retry.of(
                    Step.<MongoRequest<T>, MongoResult<T>>of(mongoSteps::exists)
                            .andThen(mongoCheck.documentExists()),
                    retryConfig
            ).apply(MongoRequest.exists(input), outerCtx);
            log.info(MongoTestClientLogTemplates.VERIFIED);
            return input;
        };
    }

    /**
     * Asserts a document with the same ID does not exist. No retry — intended for
     * synchronous deletions where absence is immediate.
     */
    public <T> Step<T, T> notExistsById() {
        return (input, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.NOT_EXISTS, input.getClass().getSimpleName());
            Step.<MongoRequest<T>, MongoResult<T>>of(mongoSteps::exists)
                    .andThen(mongoCheck.documentNotExists())
                    .apply(MongoRequest.exists(input), outerCtx);
            log.info(MongoTestClientLogTemplates.ABSENT);
            return input;
        };
    }

    /**
     * Lower-level variant of {@link #existsById()} that accepts a pre-built
     * {@link MongoRequest} and returns the raw {@link MongoResult}. Use when downstream
     * steps need both the document and the exists flag.
     */
    public <T> Step<MongoRequest<T>, MongoResult<T>> exists() {
        return (request, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.EXISTS, request.documentClass().getSimpleName());
            return Step.<MongoRequest<T>, MongoResult<T>>of(mongoSteps::exists)
                    .andThen(mongoCheck.documentExists())
                    .apply(request, outerCtx);
        };
    }

    public <T> Step<MongoRequest<T>, Void> delete() {
        return (request, outerCtx) -> {
            log.info(MongoTestClientLogTemplates.DELETE, request.documentClass().getSimpleName());
            return Step.<MongoRequest<T>, MongoResult<T>>of(mongoSteps::delete)
                    .andThen(mongoCheck.documentNotExists())
                    .andThen((MongoResult<T> r, PipelineContext ctx) -> (Void) null)
                    .apply(request, outerCtx);
        };
    }
}
