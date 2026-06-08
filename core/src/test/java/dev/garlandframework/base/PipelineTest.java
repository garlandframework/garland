package dev.garlandframework.base;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PipelineTest {

    @Test
    public void singleStepTransformsInput() {
        String result = Pipeline.given("hello")
                .then((input, ctx) -> input.toUpperCase())
                .execute();

        assertThat(result).isEqualTo("HELLO");
    }

    @Test
    public void multipleStepsChainInOrder() {
        String result = Pipeline.given("hello")
                .then((input, ctx) -> input + " world")
                .then((input, ctx) -> input.toUpperCase())
                .then((input, ctx) -> input + "!")
                .execute();

        assertThat(result).isEqualTo("HELLO WORLD!");
    }

    @Test
    public void contextFlowsThroughAllSteps() {
        String result = Pipeline.given("input")
                .then((input, ctx) -> { ctx.put("key", "stored"); return input; })
                .then((input, ctx) -> input + "_" + ctx.<String>get("key"))
                .execute();

        assertThat(result).isEqualTo("input_stored");
    }

    @Test
    public void withContextPassesExternalContextToSteps() {
        PipelineContext external = new PipelineContext();
        external.put("userId", 42);

        Integer result = Pipeline.given("ignored")
                .withContext(external)
                .then((input, ctx) -> ctx.<Integer>get("userId"))
                .execute();

        assertThat(result).isEqualTo(42);
    }

    @Test
    public void checkedExceptionIsWrappedInRuntimeException() {
        assertThatThrownBy(() ->
                Pipeline.given("x")
                        .then((input, ctx) -> { throw new Exception("checked"); })
                        .execute()
        )
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(Exception.class)
                .hasRootCauseMessage("checked");
    }

    @Test
    public void runtimeExceptionPassesThroughUnwrapped() {
        assertThatThrownBy(() ->
                Pipeline.given("x")
                        .then((input, ctx) -> { throw new IllegalStateException("runtime"); })
                        .execute()
        )
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("runtime");
    }

    @Test
    public void errorPassesThroughUnwrapped() {
        assertThatThrownBy(() ->
                Pipeline.given("x")
                        .then((input, ctx) -> { throw new AssertionError("assertion"); })
                        .execute()
        )
                .isExactlyInstanceOf(AssertionError.class)
                .hasMessage("assertion");
    }
}
