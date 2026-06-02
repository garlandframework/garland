package org.modulartestorchestrator.http.model;

import java.util.List;

/**
 * Carries all parameters for a single HTTP call: URL, HTTP method, request headers, and
 * the DTO to serialize as the request body.
 *
 * <p>Construct via factory methods in your request factory class — not inline in tests.
 * Headers set here are per-request; client-level defaults ({@link HttpTestClient#withBearer}
 * etc.) are merged at call time and take precedence over these.
 */
public record HttpCallRequest<T>(String url, String method, List<Header> headers, T dto) {}
