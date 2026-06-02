package org.modulartestorchestrator.postgres;

import org.modulartestorchestrator.base.StepFunction;
import org.modulartestorchestrator.postgres.model.DbResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assertion steps for database query results: entityExists and entityNotExists checks
 * on {@link DbResult}. Used internally by {@link DbTestClient}.
 */
public class DbCheckSteps {

    private static final Logger log = LoggerFactory.getLogger(DbCheckSteps.class);

    public <T> StepFunction<DbResult<T>, DbResult<T>> entityExists() {
        return (result, ctx) -> {
            log.info(DbCheckStepsLogTemplates.CHECKING_EXISTS);
            assertThat(result.exists())
                    .as("Entity should exist in database")
                    .isTrue();
            log.info(DbCheckStepsLogTemplates.EXISTS_PASSED);
            return result;
        };
    }

    public <T> StepFunction<DbResult<T>, DbResult<T>> entityNotExists() {
        return (result, ctx) -> {
            log.info(DbCheckStepsLogTemplates.CHECKING_NOT_EXISTS);
            assertThat(result.exists())
                    .as("Entity should not exist in database")
                    .isFalse();
            log.info(DbCheckStepsLogTemplates.NOT_EXISTS_PASSED);
            return result;
        };
    }
}
