package org.modulartestorchestrator.base;

import java.util.ArrayList;
import java.util.List;

public class Pipeline<I, O> {

    private final List<Step<?, ?>> steps;
    private final PipelineContext ctx;

    private Pipeline(List<Step<?, ?>> steps, PipelineContext ctx) {
        this.steps = steps;
        this.ctx = ctx;
    }

    public static <T> Pipeline<T, T> start() {
        return new Pipeline<>(new ArrayList<>(), new PipelineContext());
    }

    public static <T> BoundPipeline<T, T> given(T input) {
        return BoundPipeline.of(input);
    }

    // доступ до контексту зовні
    public PipelineContext context() {
        return ctx;
    }

    // можна змінювати контекст зовні
    public Pipeline<I, O> withContext(PipelineContext externalCtx) {
        return new Pipeline<>(steps, externalCtx);
    }

    public <NO> Pipeline<I, NO> then(StepFunction<O, NO> fn) {
        List<Step<?, ?>> newSteps = new ArrayList<>(steps);
        newSteps.add(new Step<>(fn));
        return new Pipeline<>(newSteps, ctx);
    }

    @SuppressWarnings("unchecked")
    public O execute(I input) throws Exception {

        Object current = input;

        for (Step<?, ?> raw : steps) {
            Step<Object, Object> step = (Step<Object, Object>) raw;
            current = step.apply(current, ctx);
        }

        return (O) current;
    }

    private static class Step<I, O> {

        private final StepFunction<I, O> fn;

        Step(StepFunction<I, O> fn) {
            this.fn = fn;
        }

        O apply(I input, PipelineContext ctx) throws Exception {
            return fn.apply(input, ctx);
        }
    }
}