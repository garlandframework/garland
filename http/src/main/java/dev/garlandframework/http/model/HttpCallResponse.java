package dev.garlandframework.http.model;

import java.util.List;
import java.util.Map;

/**
 * Describes the expected HTTP response for assertion purposes.
 *
 * <p>{@code status} is asserted as an exact match. {@code headers} are asserted as a
 * subset — the actual response may contain additional headers beyond those listed here.
 * {@code dto} is the expected body: null fields are ignored during comparison
 * (see {@link dev.garlandframework.base.checks.Verify#matching}).
 */
public record HttpCallResponse<T>(int status, Map<String, List<String>> headers, T dto) {}
