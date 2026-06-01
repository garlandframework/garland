package org.modulartestorchestrator.base;

import java.util.function.Function;

@FunctionalInterface
public interface StepFunction<I, O> {

    O apply(I input, PipelineContext ctx) throws Exception;

    static <I, O> StepFunction<I, O> of(StepFunction<I, O> fn) {
        return fn;
    }

    static <I, O> StepFunction<I, O> lift(Function<I, O> fn) {
        return (input, ctx) -> fn.apply(input);
    }

    static <T> StepFunction<T, T> saveToContext(String key) {
        return (value, ctx) -> {
            ctx.put(key, value);
            return value;
        };
    }

    default <R> StepFunction<I, R> andThen(StepFunction<O, R> next) {
        return (input, ctx) -> next.apply(this.apply(input, ctx), ctx);
    }
}
