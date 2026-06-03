package org.modulartestorchestrator.postgres;

import org.modulartestorchestrator.base.PipelineContext;
import org.modulartestorchestrator.base.Step;
import org.modulartestorchestrator.base.checks.CheckSteps;
import org.modulartestorchestrator.base.retry.Retry;
import org.modulartestorchestrator.base.retry.RetryConfig;
import org.modulartestorchestrator.postgres.model.PostgresRequest;
import org.modulartestorchestrator.postgres.model.PostgresResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Test client for PostgreSQL assertions via Hibernate. Each method returns a
 * {@link Step} that queries the database and asserts the result, retrying
 * according to the configured {@link RetryConfig}.
 *
 * <p>Entity classes must be registered with {@link PostgresConfig} before constructing this client.
 * Entities used with {@link #findById()} and {@link #existsById()} must have a field
 * annotated with {@code @Id}. Not thread-safe — designed for sequential test execution.
 */
public class PostgresTestClient {

    private static final Logger log = LoggerFactory.getLogger("DB");

    private final PostgresSteps dbSteps;
    private final PostgresCheckSteps dbCheck;
    private final CheckSteps check;
    private final RetryConfig retryConfig;

    public PostgresTestClient(PostgresWrapper postgres, RetryConfig retryConfig) {
        this.dbSteps     = new PostgresSteps(postgres);
        this.dbCheck     = new PostgresCheckSteps();
        this.check       = new CheckSteps();
        this.retryConfig = retryConfig;
    }

    public PostgresTestClient(PostgresWrapper postgres) {
        this(postgres, RetryConfig.attempts(1));
    }

    /**
     * Finds the entity by its {@code @Id} field value and asserts it matches the input
     * (null fields ignored). Retries if the entity is not yet present — useful after an
     * async write where the row may not be immediately visible.
     */
    public <T> Step<T, T> findById() {
        return (input, outerCtx) -> {
            log.info(PostgresTestClientLogTemplates.FIND_BY_ID, input.getClass().getSimpleName());
            T result = Retry.of(
                            Step.<PostgresRequest<T>, PostgresResult<T>>of(dbSteps::findById)
                                    .andThen(dbCheck.entityExists()),
                            retryConfig
                    )
                    .andThen((PostgresResult<T> r, PipelineContext ctx) -> r.entity())
                    .andThen(check.matchingNonNull(input))
                    .apply(PostgresRequest.findById(input), outerCtx);
            log.info(PostgresTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * Same as {@link #findById()} but applies temporal tolerance to timestamp fields.
     * Use when entity timestamps are truncated by the database driver or when comparing
     * server-generated timestamps to locally-constructed expected values.
     */
    public <T> Step<T, T> findById(Duration temporalTolerance) {
        return (input, outerCtx) -> {
            log.info(PostgresTestClientLogTemplates.FIND_BY_ID, input.getClass().getSimpleName());
            T result = Retry.of(
                            Step.<PostgresRequest<T>, PostgresResult<T>>of(dbSteps::findById)
                                    .andThen(dbCheck.entityExists()),
                            retryConfig
                    )
                    .andThen((PostgresResult<T> r, PipelineContext ctx) -> r.entity())
                    .andThen(check.matchingNonNull(input, temporalTolerance))
                    .apply(PostgresRequest.findById(input), outerCtx);
            log.info(PostgresTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * Builds a query from all non-null fields of the input using Hibernate Criteria API,
     * finds the single matching row, and asserts it matches the input. Null fields are
     * excluded from the query and ignored during comparison.
     *
     * <p>Throws {@link IllegalStateException} if more than one row matches — narrow your
     * criteria or switch to {@link #countByFields()} if you need multi-row results.
     * Use this when the entity ID is not known before insertion.
     */
    public <T> Step<T, T> findByFields() {
        return (input, outerCtx) -> {
            log.info(PostgresTestClientLogTemplates.FIND_BY_FIELDS, input.getClass().getSimpleName());
            T result = Retry.of(
                            Step.<PostgresRequest<T>, PostgresResult<T>>of(dbSteps::findByFields)
                                    .andThen(dbCheck.entityExists()),
                            retryConfig
                    )
                    .andThen((PostgresResult<T> r, PipelineContext ctx) -> r.entity())
                    .andThen(check.matchingNonNull(input))
                    .apply(PostgresRequest.findByFields(input), outerCtx);
            log.info(PostgresTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * Returns the count of rows matching all non-null fields of the input. No assertion
     * is performed here — chain {@code Verify} steps to assert the count in a subsequent
     * pipeline step.
     */
    public <T> Step<T, Long> countByFields() {
        return (input, outerCtx) -> {
            log.info(PostgresTestClientLogTemplates.COUNT_BY_FIELDS, input.getClass().getSimpleName());
            Long count = Step.<PostgresRequest<T>, Long>of(dbSteps::countByFields)
                    .apply(PostgresRequest.countByFields(input), outerCtx);
            log.info(PostgresTestClientLogTemplates.VERIFIED);
            return count;
        };
    }

    /**
     * Inserts the entity via a Hibernate session and asserts the stored result matches
     * {@code expectedEntity}. Use for test setup when you need a specific record in the
     * database before the system-under-test runs.
     */
    public <T> Step<PostgresRequest<T>, T> persist(T expectedEntity) {
        return (request, outerCtx) -> {
            log.info(PostgresTestClientLogTemplates.PERSIST, expectedEntity.getClass().getSimpleName());
            T result = Step.<PostgresRequest<T>, PostgresResult<T>>of(dbSteps::persist)
                    .andThen(dbCheck.entityExists())
                    .andThen((PostgresResult<T> r, PipelineContext ctx) -> r.entity())
                    .andThen(check.matchingNonNull(expectedEntity))
                    .apply(request, outerCtx);
            log.info(PostgresTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * Asserts an entity with the same {@code @Id} value exists in the database. Retries
     * until found according to the configured {@link RetryConfig}. Returns the input
     * unchanged so the value can continue through the pipeline.
     *
     * @see #notExistsById() for the negative case
     */
    public <T> Step<T, T> existsById() {
        return (input, outerCtx) -> {
            log.info(PostgresTestClientLogTemplates.EXISTS, input.getClass().getSimpleName());
            Retry.of(
                    Step.<PostgresRequest<T>, PostgresResult<T>>of(dbSteps::exists)
                            .andThen(dbCheck.entityExists()),
                    retryConfig
            ).apply(PostgresRequest.exists(input), outerCtx);
            log.info(PostgresTestClientLogTemplates.VERIFIED);
            return input;
        };
    }

    /**
     * Asserts an entity with the same {@code @Id} value does not exist in the database.
     * No retry — intended for synchronous deletions where absence is immediate. If you
     * need to wait for an async deletion, wrap with {@link Retry#of} directly.
     */
    public <T> Step<T, T> notExistsById() {
        return (input, outerCtx) -> {
            log.info(PostgresTestClientLogTemplates.NOT_EXISTS, input.getClass().getSimpleName());
            Step.<PostgresRequest<T>, PostgresResult<T>>of(dbSteps::exists)
                    .andThen(dbCheck.entityNotExists())
                    .apply(PostgresRequest.exists(input), outerCtx);
            log.info(PostgresTestClientLogTemplates.ABSENT);
            return input;
        };
    }

    /**
     * Lower-level variant of {@link #existsById()} that accepts a pre-built
     * {@link PostgresRequest} and returns the raw {@link PostgresResult}. Use when downstream
     * steps need both the entity and the exists flag, or when you need finer control over the
     * request parameters.
     */
    public <T> Step<PostgresRequest<T>, PostgresResult<T>> exists() {
        return (request, outerCtx) -> {
            log.info(PostgresTestClientLogTemplates.EXISTS, request.entityClass().getSimpleName());
            return Step.<PostgresRequest<T>, PostgresResult<T>>of(dbSteps::exists)
                    .andThen(dbCheck.entityExists())
                    .apply(request, outerCtx);
        };
    }

    public <T> Step<PostgresRequest<T>, Void> delete() {
        return (request, outerCtx) -> {
            log.info(PostgresTestClientLogTemplates.DELETE, request.entityClass().getSimpleName());
            return Step.<PostgresRequest<T>, PostgresResult<T>>of(dbSteps::delete)
                    .andThen(dbCheck.entityNotExists())
                    .andThen((PostgresResult<T> r, PipelineContext ctx) -> (Void) null)
                    .apply(request, outerCtx);
        };
    }
}
