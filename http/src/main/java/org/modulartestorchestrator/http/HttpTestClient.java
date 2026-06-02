package org.modulartestorchestrator.http;

import com.fasterxml.jackson.core.type.TypeReference;
import org.modulartestorchestrator.base.Pipeline;
import org.modulartestorchestrator.base.PipelineContext;
import org.modulartestorchestrator.base.Step;
import org.modulartestorchestrator.base.checks.CheckSteps;
import org.modulartestorchestrator.base.retry.Retry;
import org.modulartestorchestrator.base.retry.RetryConfig;
import org.modulartestorchestrator.http.model.Header;
import org.modulartestorchestrator.http.model.HttpCallRequest;
import org.modulartestorchestrator.http.model.HttpCallResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-level HTTP client for test pipelines. Combines request execution, status assertion,
 * response deserialization, and optional body matching into a single {@link Step}.
 *
 * <p><strong>Auth and headers are stored in the instance.</strong> {@link #withBearer},
 * {@link #withHeader}, and {@link #withApiKey} each return a new instance — the original
 * is never modified. Never reassign the shared client reference; create a new variable:
 *
 * <pre>{@code
 * // correct — shared httpClient is unchanged
 * HttpTestClient authed = httpClient.withBearer(token);
 *
 * // wrong — breaks every other reference to httpClient
 * httpClient = httpClient.withBearer(token);
 * }</pre>
 *
 * <p>When a request carries per-request headers and the client has defaults set, the
 * client's defaults win. This ensures a suite-wide token cannot be accidentally
 * overridden by an individual request factory.
 */
public class HttpTestClient {

    private static final Logger log = LoggerFactory.getLogger("HTTP");

    private final HttpSteps httpSteps       = new HttpSteps();
    private final HttpCheckSteps httpCheck  = new HttpCheckSteps();
    private final CheckSteps check          = new CheckSteps();
    private final RetryConfig retryConfig;
    private final Map<String, String> defaultHeaders;

    public HttpTestClient(RetryConfig retryConfig) {
        this.retryConfig = retryConfig;
        this.defaultHeaders = Map.of();
    }

    public HttpTestClient() {
        this(RetryConfig.attempts(1));
    }

    private HttpTestClient(RetryConfig retryConfig, Map<String, String> defaultHeaders) {
        this.retryConfig = retryConfig;
        this.defaultHeaders = Map.copyOf(defaultHeaders);
    }

    /**
     * Returns a new client with {@code name: value} as a default header. If the header
     * was already set, its value is replaced.
     */
    public HttpTestClient withHeader(String name, String value) {
        Map<String, String> updated = new HashMap<>(defaultHeaders);
        updated.put(name, value);
        return new HttpTestClient(retryConfig, updated);
    }

    /**
     * Returns a new client with {@code name} removed from the default headers. Use this
     * in negative auth tests that must send a request without the suite-wide token:
     * <pre>{@code
     * Pipeline.given(getProfileRequest())
     *         .then(httpClient.withoutHeader("Authorization").makeCall(401, ErrorDto.class))
     *         .execute();
     * }</pre>
     */
    public HttpTestClient withoutHeader(String name) {
        Map<String, String> updated = new HashMap<>(defaultHeaders);
        updated.remove(name);
        return new HttpTestClient(retryConfig, updated);
    }

    /** Returns a new client with {@code Authorization: Bearer <token>} as a default header. */
    public HttpTestClient withBearer(String token) {
        return withHeader("Authorization", "Bearer " + token);
    }

    /** Convenience alias for {@link #withHeader(String, String)} for API-key auth schemes. */
    public HttpTestClient withApiKey(String headerName, String key) {
        return withHeader(headerName, key);
    }

    /**
     * Executes the request and asserts status code, response headers (subset), and body
     * (null fields in the expected DTO are ignored). Use this for happy-path assertions
     * where you want to verify the full response shape.
     *
     * @see #makeCall(int, Class) for error responses where you need only the status
     */
    @SuppressWarnings("unchecked")
    public <T, R> Step<HttpCallRequest<T>, R> makeCall(HttpCallResponse<R> expected) {
        Class<R> responseType = (Class<R>) expected.dto().getClass();
        List<Header> expectedHeaders = expected.headers().entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(v -> new Header(e.getKey(), v)))
                .toList();

        return (request, outerCtx) -> {
            HttpCallRequest<T> merged = mergeHeaders(request);
            log.info(HttpTestClientLogTemplates.CALL, merged.method(), merged.url());
            R result = Pipeline.given(merged)
                    .withContext(outerCtx)
                    .then(Retry.of(buildCallAndCheck(responseType, expected.status(), expectedHeaders, expected.dto()), retryConfig))
                    .execute();
            log.info(HttpTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * Same as {@link #makeCall(HttpCallResponse)} but uses a {@link TypeReference} for
     * deserialization. Use when {@code R} is a generic type (e.g. {@code List<UserDto>})
     * that would be erased to {@code Object} with a plain {@code Class<R>}.
     */
    public <T, R> Step<HttpCallRequest<T>, R> makeCall(HttpCallResponse<R> expected, TypeReference<R> typeRef) {
        List<Header> expectedHeaders = expected.headers().entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(v -> new Header(e.getKey(), v)))
                .toList();

        return (request, outerCtx) -> {
            HttpCallRequest<T> merged = mergeHeaders(request);
            log.info(HttpTestClientLogTemplates.CALL, merged.method(), merged.url());
            R result = Pipeline.given(merged)
                    .withContext(outerCtx)
                    .then(Retry.of(buildCallAndCheck(typeRef, expected.status(), expectedHeaders, expected.dto()), retryConfig))
                    .execute();
            log.info(HttpTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * Same as {@link #makeCall(HttpCallResponse, TypeReference)} but applies
     * {@code temporalTolerance} when comparing timestamp fields. Use when the response
     * body contains server-generated timestamps.
     */
    public <T, R> Step<HttpCallRequest<T>, R> makeCall(HttpCallResponse<R> expected, TypeReference<R> typeRef, Duration temporalTolerance) {
        List<Header> expectedHeaders = expected.headers().entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(v -> new Header(e.getKey(), v)))
                .toList();

        return (request, outerCtx) -> {
            HttpCallRequest<T> merged = mergeHeaders(request);
            log.info(HttpTestClientLogTemplates.CALL, merged.method(), merged.url());
            R result = Pipeline.given(merged)
                    .withContext(outerCtx)
                    .then(Retry.of(buildCallAndCheck(typeRef, expected.status(), expectedHeaders, expected.dto(), temporalTolerance), retryConfig))
                    .execute();
            log.info(HttpTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * Same as {@link #makeCall(HttpCallResponse)} but applies {@code temporalTolerance}
     * when comparing timestamp fields. Use when the response body contains server-generated
     * timestamps — see {@link Verify#matching(Object, Duration)} for details.
     */
    @SuppressWarnings("unchecked")
    public <T, R> Step<HttpCallRequest<T>, R> makeCall(HttpCallResponse<R> expected, Duration temporalTolerance) {
        Class<R> responseType = (Class<R>) expected.dto().getClass();
        List<Header> expectedHeaders = expected.headers().entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(v -> new Header(e.getKey(), v)))
                .toList();

        return (request, outerCtx) -> {
            HttpCallRequest<T> merged = mergeHeaders(request);
            log.info(HttpTestClientLogTemplates.CALL, merged.method(), merged.url());
            R result = Pipeline.given(merged)
                    .withContext(outerCtx)
                    .then(Retry.of(buildCallAndCheck(responseType, expected.status(), expectedHeaders, expected.dto(), temporalTolerance), retryConfig))
                    .execute();
            log.info(HttpTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    /**
     * Executes the request and asserts only the status code; deserializes and returns the
     * response body without further matching. Use for error responses and other cases where
     * you need the deserialized body but do not want to assert its content here.
     */
    public <T, R> Step<HttpCallRequest<T>, R> makeCall(int expectedStatus, Class<R> responseType) {
        return (request, outerCtx) -> {
            HttpCallRequest<T> merged = mergeHeaders(request);
            log.info(HttpTestClientLogTemplates.CALL, merged.method(), merged.url());
            return Pipeline.given(merged)
                    .withContext(outerCtx)
                    .then(Retry.of(buildCallWithStatusCheck(expectedStatus, responseType), retryConfig))
                    .execute();
        };
    }

    /**
     * Same as {@link #makeCall(int, Class)} but uses a {@link TypeReference} for
     * deserialization of generic response types.
     */
    public <T, R> Step<HttpCallRequest<T>, R> makeCall(int expectedStatus, TypeReference<R> typeRef) {
        return (request, outerCtx) -> {
            HttpCallRequest<T> merged = mergeHeaders(request);
            log.info(HttpTestClientLogTemplates.CALL, merged.method(), merged.url());
            return Pipeline.given(merged)
                    .withContext(outerCtx)
                    .then(Retry.of(buildCallWithStatusCheck(expectedStatus, typeRef), retryConfig))
                    .execute();
        };
    }

    /**
     * Polls until the response body matches {@code expectedDto}, retrying according to
     * {@code retryConfig}. Use for endpoints that expose eventually-consistent state — for
     * example, a read model populated by an async Kafka consumer — where the data may not
     * be available immediately after the write that triggered it.
     *
     * <p>Note that this overrides the client's default {@link RetryConfig} for this call only.
     */
    @SuppressWarnings("unchecked")
    public <T, R> Step<HttpCallRequest<T>, R> pollingCall(int expectedStatus, R expectedDto, RetryConfig retryConfig) {
        Class<R> responseType = (Class<R>) expectedDto.getClass();
        return (request, outerCtx) -> Pipeline.given(mergeHeaders(request))
                .withContext(outerCtx)
                .then(Retry.of(
                        Step.<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                                .andThen(httpCheck.statusCode(expectedStatus))
                                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, responseType))
                                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                                .andThen(check.matchingNonNull(expectedDto)),
                        retryConfig
                ))
                .execute();
    }

    /**
     * Same as {@link #pollingCall(int, Object, RetryConfig)} but applies
     * {@code temporalTolerance} when comparing timestamp fields in the response body.
     */
    @SuppressWarnings("unchecked")
    public <T, R> Step<HttpCallRequest<T>, R> pollingCall(int expectedStatus, R expectedDto, RetryConfig retryConfig, Duration temporalTolerance) {
        Class<R> responseType = (Class<R>) expectedDto.getClass();
        return (request, outerCtx) -> Pipeline.given(mergeHeaders(request))
                .withContext(outerCtx)
                .then(Retry.of(
                        Step.<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                                .andThen(httpCheck.statusCode(expectedStatus))
                                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, responseType))
                                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                                .andThen(check.matchingNonNull(expectedDto, temporalTolerance)),
                        retryConfig
                ))
                .execute();
    }

    private <T> HttpCallRequest<T> mergeHeaders(HttpCallRequest<T> request) {
        if (defaultHeaders.isEmpty()) {
            return request;
        }
        Map<String, String> merged = new HashMap<>();
        request.headers().forEach(h -> merged.put(h.name(), h.value()));
        merged.putAll(defaultHeaders); // client defaults win over per-request headers
        List<Header> mergedList = merged.entrySet().stream()
                .map(e -> new Header(e.getKey(), e.getValue()))
                .collect(java.util.stream.Collectors.toList());
        return new HttpCallRequest<>(request.url(), request.method(), mergedList, request.dto());
    }

    private <T, R> Step<HttpCallRequest<T>, R> buildCallAndCheck(
            Class<R> responseType, int expectedStatus, List<Header> expectedHeaders, R expectedDto) {

        return Step
                .<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, responseType))
                .andThen(httpCheck.headersContain(expectedHeaders))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                .andThen(check.matchingNonNull(expectedDto));
    }

    private <T, R> Step<HttpCallRequest<T>, R> buildCallAndCheck(
            Class<R> responseType, int expectedStatus, List<Header> expectedHeaders, R expectedDto, Duration temporalTolerance) {

        return Step
                .<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, responseType))
                .andThen(httpCheck.headersContain(expectedHeaders))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                .andThen(check.matchingNonNull(expectedDto, temporalTolerance));
    }

    private <T, R> Step<HttpCallRequest<T>, R> buildCallAndCheck(
            TypeReference<R> typeRef, int expectedStatus, List<Header> expectedHeaders, R expectedDto) {

        return Step
                .<HttpCallRequest<T>, java.net.http.HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((java.net.http.HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, typeRef))
                .andThen(httpCheck.headersContain(expectedHeaders))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                .andThen(check.matchingNonNull(expectedDto));
    }

    private <T, R> Step<HttpCallRequest<T>, R> buildCallAndCheck(
            TypeReference<R> typeRef, int expectedStatus, List<Header> expectedHeaders, R expectedDto, Duration temporalTolerance) {

        return Step
                .<HttpCallRequest<T>, java.net.http.HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((java.net.http.HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, typeRef))
                .andThen(httpCheck.headersContain(expectedHeaders))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                .andThen(check.matchingNonNull(expectedDto, temporalTolerance));
    }

    private <T, R> Step<HttpCallRequest<T>, R> buildCallWithStatusCheck(int expectedStatus, Class<R> responseType) {
        return Step
                .<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, responseType))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto());
    }

    private <T, R> Step<HttpCallRequest<T>, R> buildCallWithStatusCheck(int expectedStatus, TypeReference<R> typeRef) {
        return Step
                .<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, typeRef))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto());
    }
}
