package org.modulartestorchestrator.base.checks;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.modulartestorchestrator.base.StepFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CheckSteps {

    private static final Logger log = LoggerFactory.getLogger(CheckSteps.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    public <T> StepFunction<T, T> equalTo(T expected) {
        return (actual, ctx) -> {
            log.info(CheckLogTemplates.CHECKING, toJson(expected), toJson(actual));
            assertThat(actual)
                    .usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .ignoringCollectionOrderInFields()
                    .ignoringExpectedNullFields()
                    .isEqualTo(expected);
            log.info(CheckLogTemplates.PASSED);
            return actual;
        };
    }

    public <T> StepFunction<T, T> matchingNonNull(T expected) {
        return (actual, ctx) -> {
            log.info(CheckLogTemplates.CHECKING, toJson(expected), toJson(actual));
            assertThat(actual)
                    .usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .ignoringCollectionOrderInFields()
                    .ignoringExpectedNullFields()
                    .isEqualTo(expected);
            log.info(CheckLogTemplates.PASSED);
            return actual;
        };
    }

    public <T> StepFunction<List<T>, List<T>> containsAll(Collection<T> expected) {
        return (actual, ctx) -> {
            log.info(CheckLogTemplates.CHECKING, toJson(expected), toJson(actual));
            RecursiveComparisonConfiguration config = RecursiveComparisonConfiguration.builder()
                    .withIgnoreCollectionOrder(true)
                    .withIgnoredFieldsOfTypes()
                    .build();
            assertThat(actual)
                    .usingRecursiveFieldByFieldElementComparator(config)
                    .containsAll(expected);
            log.info(CheckLogTemplates.PASSED);
            return actual;
        };
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
