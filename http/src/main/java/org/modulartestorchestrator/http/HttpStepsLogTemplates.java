package org.modulartestorchestrator.http;

public final class HttpStepsLogTemplates {

    // medium blue  — static labels / message framing
    private static final String HTTP  = "\033[38;5;75m";
    // lighter steel-blue — dynamic values: curl body, status codes, response body
    private static final String DATA  = "\033[38;5;117m";
    private static final String RESET = "\033[0m";

    public static final String REQUEST_CURL =
            HTTP + "Outgoing request:" + RESET + "\n" + DATA + "{}" + RESET;

    public static final String RESPONSE_RECEIVED =
            HTTP + "Response received: status=" + RESET + DATA + "{}" + RESET + "\n" + DATA + "{}" + RESET;

    private HttpStepsLogTemplates() {}
}
