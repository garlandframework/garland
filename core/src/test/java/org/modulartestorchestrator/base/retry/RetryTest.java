package org.modulartestorchestrator.base.retry;

import org.modulartestorchestrator.base.PipelineContext;
import org.modulartestorchestrator.base.StepFunction;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RetryTest {

    private final PipelineContext ctx = new PipelineContext();

    @Test
    public void succeeds_on_first_attempt() throws Exception {
        StepFunction<String, String> step = Retry.of(
                (input, c) -> input.toUpperCase(),
                RetryConfig.attempts(3));

        assertThat(step.apply("hello", ctx)).isEqualTo("HELLO");
    }

    @Test
    public void retries_and_succeeds_after_failures() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        StepFunction<String, String> step = Retry.of(
                (input, c) -> {
                    if (calls.incrementAndGet() < 3) throw new AssertionError("not yet");
                    return "ok";
                },
                RetryConfig.attempts(5));

        assertThat(step.apply("x", ctx)).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    public void exhausts_retries_and_rethrows_last_exception() {
        AtomicInteger calls = new AtomicInteger();
        StepFunction<String, String> step = Retry.of(
                (input, c) -> { calls.incrementAndGet(); throw new AssertionError("fail " + calls.get()); },
                RetryConfig.attempts(3));

        assertThatThrownBy(() -> step.apply("x", ctx))
                .isInstanceOf(AssertionError.class)
                .hasMessage("fail 3");

        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    public void rethrows_error_directly_without_wrapping() {
        StepFunction<String, String> step = Retry.of(
                (input, c) -> { throw new OutOfMemoryError("oom"); },
                RetryConfig.attempts(2));

        assertThatThrownBy(() -> step.apply("x", ctx))
                .isExactlyInstanceOf(OutOfMemoryError.class)
                .hasMessage("oom");
    }

    @Test
    public void retryConfig_of_stores_attempts_and_delay() {
        RetryConfig config = RetryConfig.of(5, Duration.ofMillis(10));
        assertThat(config.maxAttempts()).isEqualTo(5);
        assertThat(config.delay()).isEqualTo(Duration.ofMillis(10));
    }
}
