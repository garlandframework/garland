package org.modulartestorchestrator.base.retry;

import org.modulartestorchestrator.base.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a {@link Step} with retry-on-failure behavior defined by a
 * {@link RetryConfig}. Each failed attempt is logged as a warning; after all attempts
 * are exhausted the last throwable is rethrown as-is without wrapping.
 */
public final class Retry {

    private static final Logger log = LoggerFactory.getLogger(Retry.class);

    private Retry() {}

    /**
     * Returns a step that attempts {@code step} up to {@code config.maxAttempts()} times,
     * sleeping {@code config.delay()} between attempts. The first successful result is
     * returned immediately; if all attempts fail, the last throwable is rethrown.
     */
    public static <I, O> Step<I, O> of(Step<I, O> step, RetryConfig config) {
        return (input, ctx) -> {
            Throwable last = null;
            for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
                try {
                    O result = step.apply(input, ctx);
                    if (attempt > 1) {
                        log.info(RetryLogTemplates.RECOVERED, attempt, config.maxAttempts());
                    }
                    return result;
                } catch (Throwable t) {
                    last = t;
                    if (attempt < config.maxAttempts()) {
                        log.warn(RetryLogTemplates.ATTEMPT_FAILED,
                                attempt, config.maxAttempts(), t.getMessage(), config.delay().toMillis());
                        if (!config.delay().isZero()) {
                            Thread.sleep(config.delay().toMillis());
                        }
                    } else {
                        log.error(RetryLogTemplates.ALL_FAILED, config.maxAttempts(), t.getMessage());
                    }
                }
            }
            if (last instanceof Error e)     throw e;
            if (last instanceof Exception e) throw e;
            throw new RuntimeException(last);
        };
    }
}
