package org.modulartestorchestrator.postgres;

public final class DbCheckStepsLogTemplates {

    private static final String CHECK = "\033[38;5;141m";
    private static final String RESET = "\033[0m";

    public static final String CHECKING_EXISTS =
            CHECK + "Checking entity exists in database" + RESET;

    public static final String EXISTS_PASSED =
            CHECK + "Entity exists check passed" + RESET;

    public static final String CHECKING_NOT_EXISTS =
            CHECK + "Checking entity does not exist in database" + RESET;

    public static final String NOT_EXISTS_PASSED =
            CHECK + "Entity not-exists check passed" + RESET;

    private DbCheckStepsLogTemplates() {}
}
