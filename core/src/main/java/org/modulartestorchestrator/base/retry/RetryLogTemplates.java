package org.modulartestorchestrator.base.retry;

public final class RetryLogTemplates {

    // amber — retry warnings
    private static final String WARN  = "\033[38;5;214m";
    // red — all attempts exhausted
    private static final String ERROR = "\033[38;5;196m";
    // green — recovered after retry
    private static final String OK    = "\033[38;5;78m";
    // lighter values
    private static final String DATA  = "\033[38;5;229m";
    private static final String RESET = "\033[0m";

    public static final String ATTEMPT_FAILED =
            WARN + "Attempt " + RESET + DATA + "{}/{}" + RESET +
            WARN + " failed: " + RESET + DATA + "{}" + RESET +
            WARN + ". Retrying in " + RESET + DATA + "{}ms" + RESET + WARN + "..." + RESET;

    public static final String RECOVERED =
            OK + "Step succeeded on attempt " + RESET + DATA + "{}/{}" + RESET;

    public static final String ALL_FAILED =
            ERROR + "All " + RESET + DATA + "{}" + RESET +
            ERROR + " attempts failed. Last error: " + RESET + DATA + "{}" + RESET;

    private RetryLogTemplates() {}
}
