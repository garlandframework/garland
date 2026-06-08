package dev.garlandframework.base;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable, type-safe step chain. Each {@link #then(Step)} call returns a new
 * {@code Pipeline} with an evolved output type — the current instance is never modified.
 *
 * <p>Execution is eager and sequential: steps run in order, each receiving the previous
 * step's output as its input. Checked exceptions from any step are rethrown as
 * {@link RuntimeException}; unchecked exceptions and errors pass through unchanged.
 *
 * <pre>{@code
 * UserDto user = Pipeline.given(createUserRequest())
 *         .then(httpClient.makeCall(201, UserDto.class))
 *         .execute();
 * }</pre>
 *
 * @param <I> the entry-point input type
 * @param <O> the current output type — advances with each {@link #then} call
 */
public class Pipeline<I, O> {

    private final I input;
    private final List<Step<?, ?>> steps;
    private final PipelineContext ctx;

    private Pipeline(I input, List<Step<?, ?>> steps, PipelineContext ctx) {
        this.input = input;
        this.steps = steps;
        this.ctx = ctx;
    }

    /** Creates a pipeline with {@code input} as the initial value and an empty step list. */
    public static <T> Pipeline<T, T> given(T input) {
        return new Pipeline<>(input, new ArrayList<>(), new PipelineContext());
    }

    /**
     * Replaces the default empty context with an external one. Use when the calling code
     * already has a {@link PipelineContext} that downstream steps need to share — for example,
     * when an inner pipeline must read or write state produced by the outer pipeline that
     * invoked it.
     */
    public Pipeline<I, O> withContext(PipelineContext externalCtx) {
        return new Pipeline<>(input, steps, externalCtx);
    }

    /** Returns a new {@code Pipeline} extended with {@code step}. This instance is not modified. */
    public <NO> Pipeline<I, NO> then(Step<O, NO> step) {
        List<Step<?, ?>> newSteps = new ArrayList<>(steps);
        newSteps.add(step);
        return new Pipeline<>(input, newSteps, ctx);
    }

    /**
     * Runs all steps in order and returns the final output. Checked exceptions are wrapped
     * in {@link RuntimeException}; unchecked exceptions and {@link Error}s pass through unchanged.
     */
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
}
