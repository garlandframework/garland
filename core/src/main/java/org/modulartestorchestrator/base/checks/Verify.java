package org.modulartestorchestrator.base.checks;

import org.modulartestorchestrator.base.StepFunction;

import java.util.Collection;
import java.util.List;

public final class Verify {

    private static final CheckSteps CHECK = new CheckSteps();

    public static <T> StepFunction<T, T> matching(T expected) {
        return CHECK.matchingNonNull(expected);
    }

    public static <T> StepFunction<T, T> equalTo(T expected) {
        return CHECK.equalTo(expected);
    }

    public static <T> StepFunction<List<T>, List<T>> containsAll(Collection<T> expected) {
        return CHECK.containsAll(expected);
    }

    private Verify() {}
}
