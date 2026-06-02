package org.modulartestorchestrator.base;

import java.util.function.Function;

/**
 * A single transformation step: takes an input, optionally reads or writes the shared
 * {@link PipelineContext}, and returns an output.
 *
 * <p>Designed to be passed as method references or lambdas to {@link Pipeline#then}.
 * The context flows through the entire pipeline execution and is the right place for
 * cross-cutting state that does not fit the return-value chain — use it sparingly.
 *
 * @param <I> input type
 * @param <O> output type
 */
@FunctionalInterface
public interface StepFunction<I, O> {

    O apply(I input, PipelineContext ctx) throws Exception;

    /**
     * Identity wrapper that helps the compiler resolve generic types when starting an
     * {@link #andThen} chain from a method reference. Without it, the compiler often
     * cannot infer {@code I} and {@code O} from the target method alone.
     */
    static <I, O> StepFunction<I, O> of(StepFunction<I, O> fn) {
        return fn;
    }

    /**
     * Adapts a context-unaware {@link Function} into a step. Use when you have a plain
     * transformation that does not need to read or write the context.
     */
    static <I, O> StepFunction<I, O> lift(Function<I, O> fn) {
        return (input, ctx) -> fn.apply(input);
    }

    /**
     * Returns a step that stores the input value under {@code key} in the context and
     * passes it through unchanged. Prefer returning data through step output types over
     * stashing it in context — reserve this for values needed several steps downstream
     * that would otherwise need to be threaded through intermediate steps.
     */
    static <T> StepFunction<T, T> saveToContext(String key) {
        return (value, ctx) -> {
            ctx.put(key, value);
            return value;
        };
    }

    /**
     * Composes this step with {@code next}, producing a step that runs both in sequence.
     * Both steps share the same {@link PipelineContext} instance.
     */
    default <R> StepFunction<I, R> andThen(StepFunction<O, R> next) {
        return (input, ctx) -> next.apply(this.apply(input, ctx), ctx);
    }
}
