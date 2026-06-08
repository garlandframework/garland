package dev.garlandframework.http;

public final class HttpCheckStepsLogTemplates {

    private static final String CHECK = "\033[38;5;75m";
    private static final String DATA  = "\033[38;5;117m";
    private static final String RESET = "\033[0m";

    public static final String STATUS_CHECKING =
            CHECK + "Checking status code: expected=" + RESET + DATA + "{}" + RESET +
            CHECK + ", actual=" + RESET + DATA + "{}" + RESET;

    public static final String STATUS_PASSED =
            CHECK + "Status code check passed: " + RESET + DATA + "{}" + RESET;

    public static final String HEADERS_CHECKING =
            CHECK + "Checking response headers: expected=" + RESET + DATA + "{}" + RESET;

    public static final String HEADERS_PASSED =
            CHECK + "Headers check passed: all expected headers present" + RESET;

    private HttpCheckStepsLogTemplates() {}
}
