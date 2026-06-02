package org.modulartestorchestrator.base.checks;

import org.modulartestorchestrator.base.Pipeline;
import org.modulartestorchestrator.base.Step;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Static entry point for assertions in test pipelines. Each method returns a
 * {@link Step} that can be passed to {@link Pipeline#then}: the step asserts
 * the condition and passes the value through unchanged on success, or throws
 * {@link AssertionError} on failure.
 *
 * @see CheckSteps for the underlying assertion logic
 */
public final class Verify {

    private static final CheckSteps CHECK = new CheckSteps();

    /**
     * Asserts that {@code actual} matches {@code expected} using recursive field comparison.
     * <strong>Null fields in {@code expected} are ignored</strong> — only non-null fields
     * are checked. Collection order is also ignored.
     *
     * <p>This lets you use partial expected objects: set only the fields you want to verify
     * and leave everything else null. It is the right choice for most happy-path assertions.
     *
     * @see #equalTo(Object) for strict comparison where every field must match
     */
    public static <T> Step<T, T> matching(T expected) {
        return CHECK.matchingNonNull(expected);
    }

    /**
     * Same as {@link #matching(Object)} but temporal fields ({@code Instant},
     * {@code LocalDateTime}, {@code ZonedDateTime}, {@code OffsetDateTime}) are accepted
     * within {@code ±temporalTolerance} of the expected value.
     *
     * <p>Two common use cases:
     * <ul>
     *   <li>Storage precision loss — MongoDB truncates nanoseconds to milliseconds.
     *       Use {@code Duration.ofMillis(1)} to absorb the difference.</li>
     *   <li>Server-generated timestamps — set {@code expected = Instant.now()} at test
     *       start and {@code temporalTolerance} to the maximum acceptable processing delay.</li>
     * </ul>
     */
    public static <T> Step<T, T> matching(T expected, Duration temporalTolerance) {
        return CHECK.matchingNonNull(expected, temporalTolerance);
    }

    /**
     * Strict recursive equality — all fields are compared including nulls, collection
     * order is ignored. Use when the expected object is complete and you want no
     * tolerance for absent fields.
     *
     * @see #matching(Object) for the more commonly useful null-field-ignoring variant
     */
    public static <T> Step<T, T> equalTo(T expected) {
        return CHECK.equalTo(expected);
    }

    /**
     * Asserts that the actual list contains all expected elements. Order-independent;
     * null fields in expected elements are ignored. The actual list may contain
     * additional elements beyond those in {@code expected}.
     */
    public static <T> Step<List<T>, List<T>> containsAll(Collection<T> expected) {
        return CHECK.containsAll(expected);
    }

    /**
     * Runs <em>all</em> branches against the same input, collects every failure, then
     * throws a single combined {@link AssertionError}. Unlike chaining multiple
     * {@link Pipeline#then} calls (which stop at the first failure), {@code allOf}
     * always reports all failing branches together.
     *
     * <p>The canonical use case is fan-out after a write: one HTTP response triggers
     * assertions against DB, Kafka, and MongoDB simultaneously — you want all three
     * results in one failure message, not just the first one that happened to fail.
     *
     * <pre>{@code
     * Pipeline.given(request)
     *         .then(httpClient.makeCall(201, UserDto.class))
     *         .then(Verify.allOf(
     *                 dbClient.findById(),
     *                 kafkaClient.consumeMatching(UserCreatedEvent.class),
     *                 mongoClient.findById()
     *         ))
     *         .execute();
     * }</pre>
     */
    @SafeVarargs
    public static <T> Step<T, T> allOf(Step<T, ?>... branches) {
        return (input, ctx) -> {
            List<Throwable> failures = new ArrayList<>();
            for (Step<T, ?> branch : branches) {
                try {
                    branch.apply(input, ctx);
                } catch (Throwable t) {
                    failures.add(t);
                }
            }
            if (!failures.isEmpty()) {
                String combined = failures.stream()
                        .map(Throwable::getMessage)
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("assertion failed");
                AssertionError error = new AssertionError("allOf failed (" + failures.size() + " branch(es)):\n" + combined);
                failures.forEach(error::addSuppressed);
                throw error;
            }
            return input;
        };
    }

    private Verify() {}
}
