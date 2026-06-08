package dev.garlandframework.http;

public final class HttpTestClientLogTemplates {

    private static final String HTTP  = "\033[38;5;75m";
    private static final String DATA  = "\033[38;5;117m";
    private static final String RESET = "\033[0m";

    public static final String CALL =
            HTTP + "▶ " + RESET + DATA + "{} {}" + RESET;

    public static final String VERIFIED =
            HTTP + "✓ response verified" + RESET;

    private HttpTestClientLogTemplates() {}
}
