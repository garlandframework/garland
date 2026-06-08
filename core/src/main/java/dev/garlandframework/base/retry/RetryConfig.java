package dev.garlandframework.base.retry;

import java.time.Duration;

/**
 * Configuration for {@link Retry}: how many times to attempt a step and how long to
 * wait between attempts. Immutable — modifier methods return new instances.
 */
public class RetryConfig {

    private final int maxAttempts;
    private final Duration delay;

    private RetryConfig(int maxAttempts, Duration delay) {
        this.maxAttempts = maxAttempts;
        this.delay = delay;
    }

    /** Creates a config with {@code maxAttempts} attempts and {@code delay} between each. */
    public static RetryConfig of(int maxAttempts, Duration delay) {
        return new RetryConfig(maxAttempts, delay);
    }

    /**
     * Creates a config with {@code n} attempts and no delay between them.
     * Equivalent to {@code RetryConfig.of(n, Duration.ZERO)}.
     */
    public static RetryConfig attempts(int n) {
        return new RetryConfig(n, Duration.ZERO);
    }

    /** Returns a new {@code RetryConfig} with the same attempt count but a different delay. */
    public RetryConfig withDelay(Duration delay) {
        return new RetryConfig(this.maxAttempts, delay);
    }

    public int maxAttempts() { return maxAttempts; }
    public Duration delay()  { return delay; }
}
