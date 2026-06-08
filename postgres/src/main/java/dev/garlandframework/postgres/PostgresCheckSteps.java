package dev.garlandframework.postgres;

import dev.garlandframework.base.Step;
import dev.garlandframework.postgres.model.PostgresResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assertion steps for database query results: entityExists and entityNotExists checks
 * on {@link PostgresResult}. Used internally by {@link PostgresTestClient}.
 */
public class PostgresCheckSteps {

    private static final Logger log = LoggerFactory.getLogger(PostgresCheckSteps.class);

    public <T> Step<PostgresResult<T>, PostgresResult<T>> entityExists() {
        return (result, ctx) -> {
            log.info(PostgresCheckStepsLogTemplates.CHECKING_EXISTS);
            assertThat(result.exists())
                    .as("Entity should exist in database")
                    .isTrue();
            log.info(PostgresCheckStepsLogTemplates.EXISTS_PASSED);
            return result;
        };
    }

    public <T> Step<PostgresResult<T>, PostgresResult<T>> entityNotExists() {
        return (result, ctx) -> {
            log.info(PostgresCheckStepsLogTemplates.CHECKING_NOT_EXISTS);
            assertThat(result.exists())
                    .as("Entity should not exist in database")
                    .isFalse();
            log.info(PostgresCheckStepsLogTemplates.NOT_EXISTS_PASSED);
            return result;
        };
    }
}
