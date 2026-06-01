package org.modulartestorchestrator.base.checks;

import org.modulartestorchestrator.base.StepFunction;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class Verify {

    private static final CheckSteps CHECK = new CheckSteps();

    public static <T> StepFunction<T, T> matching(T expected) {
        return CHECK.matchingNonNull(expected);
    }

    public static <T> StepFunction<T, T> matching(T expected, Duration temporalTolerance) {
        return CHECK.matchingNonNull(expected, temporalTolerance);
    }

    public static <T> StepFunction<T, T> equalTo(T expected) {
        return CHECK.equalTo(expected);
    }

    public static <T> StepFunction<List<T>, List<T>> containsAll(Collection<T> expected) {
        return CHECK.containsAll(expected);
    }

    @SafeVarargs
    public static <T> StepFunction<T, T> allOf(StepFunction<T, ?>... branches) {
        return (input, ctx) -> {
            List<Throwable> failures = new ArrayList<>();
            for (StepFunction<T, ?> branch : branches) {
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
