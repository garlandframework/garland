package org.modulartestorchestrator.http.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carries all parameters for a single HTTP call: URL, HTTP method, request headers,
 * the DTO to serialize as the request body, and optional query parameters.
 *
 * <p>Construct via factory methods in your request factory class — not inline in tests.
 * Headers set here are per-request; client-level defaults ({@link HttpTestClient#withBearer}
 * etc.) are merged at call time and take precedence over these.
 *
 * <p>Query parameters are kept as a separate map and appended to the URL at call time
 * with proper percent-encoding. Use {@link #withQueryParam} for a single parameter or
 * {@link #withQueryParams} when building a request with several parameters at once:
 *
 * <pre>{@code
 * // single param — typical in adversarial tests
 * TestUserRequests.getUsers().withQueryParam("page", "-1")
 *
 * // several params — typical in factory methods or happy-path tests
 * TestUserRequests.getUsers().withQueryParams(Map.of("page", "0", "size", "10"))
 * }</pre>
 */
public record HttpCallRequest<T>(String url, String method, List<Header> headers, T dto, Map<String, String> queryParams) {

    /** Convenience constructor — no query parameters. Keeps all existing call sites unchanged. */
    public HttpCallRequest(String url, String method, List<Header> headers, T dto) {
        this(url, method, headers, dto, Map.of());
    }

    /**
     * Returns a new request with {@code name=value} added to the query string.
     * If the parameter already exists its value is replaced. Use this in tests to
     * inject a single valid, invalid, or malformed parameter value on top of the
     * default request produced by a factory method.
     */
    public HttpCallRequest<T> withQueryParam(String name, String value) {
        Map<String, String> updated = new HashMap<>(queryParams);
        updated.put(name, value);
        return new HttpCallRequest<>(url, method, headers, dto, Map.copyOf(updated));
    }

    /**
     * Returns a new request with all entries of {@code params} added to the query string.
     * Existing parameters with the same name are replaced. Use this in factory methods
     * when a request naturally carries several parameters.
     */
    public HttpCallRequest<T> withQueryParams(Map<String, String> params) {
        Map<String, String> updated = new HashMap<>(queryParams);
        updated.putAll(params);
        return new HttpCallRequest<>(url, method, headers, dto, Map.copyOf(updated));
    }

    // --- Static factories ---

    public static HttpCallRequest<Void> get(String url) {
        return new HttpCallRequest<>(url, "GET", List.of(), null);
    }

    public static <T> HttpCallRequest<T> post(String url, T body) {
        return new HttpCallRequest<>(url, "POST", List.of(), body);
    }

    public static <T> HttpCallRequest<T> put(String url, T body) {
        return new HttpCallRequest<>(url, "PUT", List.of(), body);
    }

    public static HttpCallRequest<Void> put(String url) {
        return new HttpCallRequest<>(url, "PUT", List.of(), null);
    }

    public static HttpCallRequest<Void> delete(String url) {
        return new HttpCallRequest<>(url, "DELETE", List.of(), null);
    }
}
