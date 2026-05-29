package org.modulartestorchestrator.base.retry;

import java.time.Duration;

public class RetryConfig {

    private final int maxAttempts;
    private final Duration delay;

    private RetryConfig(int maxAttempts, Duration delay) {
        this.maxAttempts = maxAttempts;
        this.delay = delay;
    }

    public static RetryConfig of(int maxAttempts, Duration delay) {
        return new RetryConfig(maxAttempts, delay);
    }

    public static RetryConfig attempts(int n) {
        return new RetryConfig(n, Duration.ZERO);
    }

    public RetryConfig withDelay(Duration delay) {
        return new RetryConfig(this.maxAttempts, delay);
    }

    public int maxAttempts() { return maxAttempts; }
    public Duration delay()  { return delay; }
}
