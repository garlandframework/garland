package org.modulartestorchestrator.mongodb;

import org.modulartestorchestrator.base.Step;
import org.modulartestorchestrator.mongodb.model.MongoResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assertion steps for MongoDB query results: documentExists and documentNotExists checks
 * on {@link MongoResult}. Used internally by {@link MongoTestClient}.
 */
public class MongoCheckSteps {

    private static final Logger log = LoggerFactory.getLogger(MongoCheckSteps.class);

    public <T> Step<MongoResult<T>, MongoResult<T>> documentExists() {
        return (result, ctx) -> {
            log.info(MongoCheckStepsLogTemplates.CHECKING_EXISTS);
            assertThat(result.exists())
                    .as("Document should exist in MongoDB")
                    .isTrue();
            log.info(MongoCheckStepsLogTemplates.EXISTS_PASSED);
            return result;
        };
    }

    public <T> Step<MongoResult<T>, MongoResult<T>> documentNotExists() {
        return (result, ctx) -> {
            log.info(MongoCheckStepsLogTemplates.CHECKING_NOT_EXISTS);
            assertThat(result.exists())
                    .as("Document should not exist in MongoDB")
                    .isFalse();
            log.info(MongoCheckStepsLogTemplates.NOT_EXISTS_PASSED);
            return result;
        };
    }
}
