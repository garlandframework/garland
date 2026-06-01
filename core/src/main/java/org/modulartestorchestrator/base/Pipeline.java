package org.modulartestorchestrator.base;

import java.util.ArrayList;
import java.util.List;

public class Pipeline<I, O> {

    private final I input;
    private final List<Step<?, ?>> steps;
    private final PipelineContext ctx;

    private Pipeline(I input, List<Step<?, ?>> steps, PipelineContext ctx) {
        this.input = input;
        this.steps = steps;
        this.ctx = ctx;
    }

    public static <T> Pipeline<T, T> given(T input) {
        return new Pipeline<>(input, new ArrayList<>(), new PipelineContext());
    }

    public Pipeline<I, O> withContext(PipelineContext externalCtx) {
        return new Pipeline<>(input, steps, externalCtx);
    }

    public <NO> Pipeline<I, NO> then(StepFunction<O, NO> fn) {
        List<Step<?, ?>> newSteps = new ArrayList<>(steps);
        newSteps.add(new Step<>(fn));
        return new Pipeline<>(input, newSteps, ctx);
    }

    public O execute() {
        try {
            return executeSteps();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private O executeSteps() throws Exception {
        Object current = input;
        for (Step<?, ?> raw : steps) {
            Step<Object, Object> step = (Step<Object, Object>) raw;
            current = step.apply(current, ctx);
        }
        return (O) current;
    }

    private static class Step<A, B> {

        private final StepFunction<A, B> fn;

        Step(StepFunction<A, B> fn) {
            this.fn = fn;
        }

        B apply(A input, PipelineContext ctx) throws Exception {
            return fn.apply(input, ctx);
        }
    }
}
