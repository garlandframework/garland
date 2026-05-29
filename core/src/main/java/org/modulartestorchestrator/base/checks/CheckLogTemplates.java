package org.modulartestorchestrator.base.checks;

public final class CheckLogTemplates {

    private static final String CHECK = "\033[38;5;78m";
    private static final String DATA  = "\033[38;5;120m";
    private static final String RESET = "\033[0m";

    public static final String CHECKING =
            CHECK + "Checking objects recursively:" + RESET +
            "\n  " + CHECK + "expected=" + RESET + DATA + "{}" + RESET +
            "\n  " + CHECK + "actual  =" + RESET + DATA + "{}" + RESET;

    public static final String PASSED =
            CHECK + "Check passed: actual matches expected field by field" + RESET;

    private CheckLogTemplates() {}
}
