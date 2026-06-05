package org.modulartestorchestrator.base.checks;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.modulartestorchestrator.base.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assertion steps backed by AssertJ recursive comparison. The public API surface is
 * {@link Verify} — instantiate this class only when building custom test client extensions.
 */
public class CheckSteps {

    private static final Logger log = LoggerFactory.getLogger(CheckSteps.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    public <T> Step<T, T> equalTo(T expected) {
        return (actual, ctx) -> {
            log.info(CheckLogTemplates.CHECKING, toJson(expected), toJson(actual));
            assertThat(actual)
                    .usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .ignoringCollectionOrderInFields()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
            log.info(CheckLogTemplates.PASSED);
            return actual;
        };
    }

    public <T> Step<T, T> matchingNonNull(T expected) {
        return (actual, ctx) -> {
            log.info(CheckLogTemplates.CHECKING, toJson(expected), toJson(actual));
            assertThat(actual)
                    .usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .ignoringCollectionOrderInFields()
                    .ignoringExpectedNullFields()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
            log.info(CheckLogTemplates.PASSED);
            return actual;
        };
    }

    /**
     * Same as matchingNonNull but temporal fields (Instant, LocalDateTime, ZonedDateTime,
     * OffsetDateTime) are accepted if they fall within ±temporalTolerance of the expected value.
     *
     * Two use cases:
     *   1. Storage precision loss — MongoDB truncates nanoseconds to milliseconds.
     *      Use Duration.ofMillis(1).
     *   2. SLA window for server-generated timestamps — set expected = Instant.now() at test
     *      start, tolerance = max acceptable processing delay (e.g. Duration.ofMinutes(1.5)).
     */
    public <T> Step<T, T> matchingNonNull(T expected, Duration temporalTolerance) {
        long toleranceNanos = temporalTolerance.toNanos();
        return (actual, ctx) -> {
            log.info(CheckLogTemplates.CHECKING, toJson(expected), toJson(actual));
            assertThat(actual)
                    .usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .ignoringCollectionOrderInFields()
                    .ignoringExpectedNullFields()
                    .withComparatorForType(
                            (Instant a, Instant b) -> withinTolerance(a, b, toleranceNanos),
                            Instant.class)
                    .withComparatorForType(
                            (LocalDateTime a, LocalDateTime b) -> withinTolerance(a, b, toleranceNanos),
                            LocalDateTime.class)
                    .withComparatorForType(
                            (ZonedDateTime a, ZonedDateTime b) -> withinTolerance(a, b, toleranceNanos),
                            ZonedDateTime.class)
                    .withComparatorForType(
                            (OffsetDateTime a, OffsetDateTime b) -> withinTolerance(a, b, toleranceNanos),
                            OffsetDateTime.class)
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
            log.info(CheckLogTemplates.PASSED);
            return actual;
        };
    }

    public <T> Step<List<T>, List<T>> containsAll(Collection<T> expected) {
        return (actual, ctx) -> {
            log.info(CheckLogTemplates.CHECKING, toJson(expected), toJson(actual));
            RecursiveComparisonConfiguration config = RecursiveComparisonConfiguration.builder()
                    .withIgnoreCollectionOrder(true)
                    .withIgnoreAllExpectedNullFields(true)
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .build();
            assertThat(actual)
                    .usingRecursiveFieldByFieldElementComparator(config)
                    .containsAll(expected);
            log.info(CheckLogTemplates.PASSED);
            return actual;
        };
    }

    public <T> Step<List<T>, List<T>> doesNotContain(Collection<T> unexpected) {
        return (actual, ctx) -> {
            log.info(CheckLogTemplates.CHECKING, toJson(unexpected), toJson(actual));
            RecursiveComparisonConfiguration config = RecursiveComparisonConfiguration.builder()
                    .withIgnoreCollectionOrder(true)
                    .withIgnoreAllExpectedNullFields(true)
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .build();
            assertThat(actual)
                    .usingRecursiveFieldByFieldElementComparator(config)
                    .doesNotContainAnyElementsOf(unexpected);
            log.info(CheckLogTemplates.PASSED);
            return actual;
        };
    }

    private static int withinTolerance(Instant a, Instant b, long toleranceNanos) {
        long diffNanos = Math.abs(Duration.between(a, b).toNanos());
        return diffNanos <= toleranceNanos ? 0 : a.compareTo(b);
    }

    private static int withinTolerance(LocalDateTime a, LocalDateTime b, long toleranceNanos) {
        long diffNanos = Math.abs(Duration.between(a, b).toNanos());
        return diffNanos <= toleranceNanos ? 0 : a.compareTo(b);
    }

    private static int withinTolerance(ZonedDateTime a, ZonedDateTime b, long toleranceNanos) {
        long diffNanos = Math.abs(Duration.between(a, b).toNanos());
        return diffNanos <= toleranceNanos ? 0 : a.compareTo(b);
    }

    private static int withinTolerance(OffsetDateTime a, OffsetDateTime b, long toleranceNanos) {
        long diffNanos = Math.abs(Duration.between(a, b).toNanos());
        return diffNanos <= toleranceNanos ? 0 : a.compareTo(b);
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
