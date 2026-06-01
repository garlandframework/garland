package org.modulartestorchestrator.base.checks;

import org.modulartestorchestrator.base.PipelineContext;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CheckStepsTest {

    private final CheckSteps check = new CheckSteps();
    private final PipelineContext ctx = new PipelineContext();

    // --- equalTo ---

    @Test
    public void equalTo_passes_when_objects_are_equal() throws Exception {
        Sample expected = new Sample("Alice", 30, null);
        Sample actual   = new Sample("Alice", 30, null);

        Sample result = check.equalTo(expected).apply(actual, ctx);
        assertThat(result).isEqualTo(actual);
    }

    @Test
    public void equalTo_fails_when_values_differ() {
        assertThatThrownBy(() ->
                check.equalTo(new Sample("Alice", 30, null))
                     .apply(new Sample("Bob", 30, null), ctx)
        ).isInstanceOf(AssertionError.class);
    }

    @Test
    public void equalTo_is_strict_about_null_fields_in_actual() {
        // expected has name set; actual has name null — should fail (strict)
        assertThatThrownBy(() ->
                check.equalTo(new Sample("Alice", 30, null))
                     .apply(new Sample(null, 30, null), ctx)
        ).isInstanceOf(AssertionError.class);
    }

    @Test
    public void equalTo_compares_bigdecimal_by_value_not_scale() throws Exception {
        check.equalTo(new Sample("x", 1, new BigDecimal("1.0")))
             .apply(new Sample("x", 1, new BigDecimal("1.00")), ctx);
    }

    // --- matchingNonNull ---

    @Test
    public void matchingNonNull_passes_when_objects_are_equal() throws Exception {
        Sample expected = new Sample("Alice", 30, null);
        Sample actual   = new Sample("Alice", 30, null);

        Sample result = check.matchingNonNull(expected).apply(actual, ctx);
        assertThat(result).isEqualTo(actual);
    }

    @Test
    public void matchingNonNull_ignores_null_fields_in_expected() throws Exception {
        Sample expected = new Sample("Alice", 30, null);
        Sample actual   = new Sample("Alice", 30, new BigDecimal("9.99"));

        check.matchingNonNull(expected).apply(actual, ctx);
    }

    @Test
    public void matchingNonNull_fails_when_non_null_field_differs() {
        assertThatThrownBy(() ->
                check.matchingNonNull(new Sample("Alice", 30, null))
                     .apply(new Sample("Bob", 30, null), ctx)
        ).isInstanceOf(AssertionError.class);
    }

    // --- matchingNonNull with temporal tolerance ---

    @Test
    public void matchingNonNull_withTolerance_passes_within_window() throws Exception {
        Instant base     = Instant.parse("2024-01-01T00:00:00Z");
        Instant closeBy  = base.plusMillis(500);

        check.matchingNonNull(new TimestampSample(base), Duration.ofSeconds(1))
             .apply(new TimestampSample(closeBy), ctx);
    }

    @Test
    public void matchingNonNull_withTolerance_fails_outside_window() {
        Instant base    = Instant.parse("2024-01-01T00:00:00Z");
        Instant farAway = base.plusSeconds(5);

        assertThatThrownBy(() ->
                check.matchingNonNull(new TimestampSample(base), Duration.ofSeconds(1))
                     .apply(new TimestampSample(farAway), ctx)
        ).isInstanceOf(AssertionError.class);
    }

    // --- helpers ---

    record Sample(String name, int age, BigDecimal price) {}
    record TimestampSample(Instant ts) {}
}
