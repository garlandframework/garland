package org.modulartestorchestrator.base;

public class BoundPipeline<I, O> {

    private final I input;
    private final Pipeline<I, O> pipeline;

    private BoundPipeline(I input, Pipeline<I, O> pipeline) {
        this.input    = input;
        this.pipeline = pipeline;
    }

    static <T> BoundPipeline<T, T> of(T input) {
        return new BoundPipeline<>(input, Pipeline.start());
    }

    public BoundPipeline<I, O> withContext(PipelineContext ctx) {
        return new BoundPipeline<>(input, pipeline.withContext(ctx));
    }

    public <NO> BoundPipeline<I, NO> then(StepFunction<O, NO> fn) {
        return new BoundPipeline<>(input, pipeline.then(fn));
    }

    public O execute() throws Exception {
        return pipeline.execute(input);
    }
}
