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
import java.util.function.Function;

/**
 * High-level HTTP client for test pipelines. Combines request execution, status assertion,
 * response deserialization, and optional body matching into a single {@link Step}.
 *
 * <h2>Auth — two patterns</h2>
 *
 * <p><strong>Client-level (token known before the pipeline):</strong> {@link #withBearer},
 * {@link #withHeader}, and {@link #withApiKey} each return a new instance — the original
 * is never modified. Use this for a suite-wide token that is fetched once in a {@code @BeforeClass}:
 *
 * <pre>{@code
 * HttpTestClient authed = http.withBearer(token);
 * }</pre>
 *
 * <p><strong>Context-level (token fetched inside the pipeline):</strong> {@link #storeBearer}
 * stores the token in {@link PipelineContext}; every subsequent {@link #makeCall} in the
 * same pipeline picks it up automatically:
 *
 * <pre>{@code
 * Pipeline.given(loginRequest())
 *         .then(http.makeCall(200, TokenDto.class))
 *         .then(HttpTestClient.storeBearer(TokenDto::accessToken))
 *         .then(tokenDto -> buildNextRequest())
 *         .then(http.makeCall(201, ResultDto.class))  // Authorization injected automatically
 *         .execute();
 * }</pre>
 *
 * <p><strong>Priority:</strong> client-level headers always win over context — a
 * suite-wide token set via {@link #withBearer} cannot be overridden mid-pipeline.
 */
public class HttpTestClient {

    private static final Logger log = LoggerFactory.getLogger("HTTP");
    static final String BEARER_CTX_KEY = "http.bearer";

    private final HttpSteps httpSteps;
    private final HttpCheckSteps httpCheck  = new HttpCheckSteps();
    private final CheckSteps check          = new CheckSteps();
    private final RetryConfig retryConfig;
    private final Map<String, String> defaultHeaders;
    private final String baseUrl;
    private final Duration timeout;

    public HttpTestClient(RetryConfig retryConfig) {
        this.retryConfig = retryConfig;
        this.defaultHeaders = Map.of();
        this.baseUrl = null;
        this.timeout = null;
        this.httpSteps = new HttpSteps();
    }

    public HttpTestClient() {
        this(RetryConfig.attempts(1));
    }

    private HttpTestClient(RetryConfig retryConfig, Map<String, String> defaultHeaders, String baseUrl, Duration timeout) {
        this.retryConfig = retryConfig;
        this.defaultHeaders = Map.copyOf(defaultHeaders);
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.httpSteps = timeout != null ? new HttpSteps(new HttpClientWrapper(timeout)) : new HttpSteps();
    }

    /**
     * Returns a new client with {@code name: value} as a default header. If the header
     * was already set, its value is replaced.
     */
    public HttpTestClient withHeader(String name, String value) {
        Map<String, String> updated = new HashMap<>(defaultHeaders);
        updated.put(name, value);
        return new HttpTestClient(retryConfig, updated, baseUrl, timeout);
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
        return new HttpTestClient(retryConfig, updated, baseUrl, timeout);
    }

    /** Returns a new client with {@code Authorization: Bearer <token>} as a default header. */
    public HttpTestClient withBearer(String token) {
        return withHeader("Authorization", "Bearer " + token);
    }

    /** Convenience alias for {@link #withHeader(String, String)} for API-key auth schemes. */
    public HttpTestClient withApiKey(String headerName, String key) {
        return withHeader(headerName, key);
    }

    /** Returns a new client with {@code Cookie: name=value} added to the default headers. */
    public HttpTestClient withCookie(String name, String value) {
        return withHeader("Cookie", name + "=" + value);
    }

    /**
     * Returns a new client that prepends {@code baseUrl} to any request URL that starts
     * with {@code /}. Absolute URLs (starting with {@code http}) are used as-is.
     *
     * <p>Use this to decouple request factories from the host:
     * <pre>{@code
     * HttpTestClient client = new HttpTestClient().withBaseUrl("http://localhost:8080");
     *
     * // request factory uses a relative path
     * new HttpCallRequest<>("/api/users", "POST", List.of(), dto)
     * }</pre>
     *
     * <p>Chaining is safe — all other {@code with*} methods preserve the base URL:
     * <pre>{@code
     * HttpTestClient authed = client.withBearer(token); // base URL carried over
     * }</pre>
     */
    public HttpTestClient withBaseUrl(String baseUrl) {
        return new HttpTestClient(retryConfig, defaultHeaders, baseUrl, timeout);
    }

    /**
     * Returns a new client that applies {@code timeout} to every HTTP request. If the server
     * does not respond within the timeout, the call throws {@link java.net.http.HttpTimeoutException}
     * wrapped in a {@link RuntimeException}.
     *
     * <p>Use in suites that run against slow or unreliable environments to prevent a single
     * hanging call from blocking the entire test run.
     *
     * <pre>{@code
     * HttpTestClient client = httpClient.withTimeout(Duration.ofSeconds(10));
     * }</pre>
     */
    public HttpTestClient withTimeout(Duration timeout) {
        return new HttpTestClient(retryConfig, defaultHeaders, baseUrl, timeout);
    }

    /**
     * Returns a step that stores the input string as a Bearer token in the pipeline context.
     * Any subsequent {@link #makeCall} in the same pipeline will inject it as
     * {@code Authorization: Bearer <token>} unless the client already has an
     * {@code Authorization} header configured via {@link #withBearer}.
     */
    public static Step<String, String> storeBearer() {
        return (token, ctx) -> {
            ctx.put(BEARER_CTX_KEY, token);
            return token;
        };
    }

    /**
     * Returns a step that extracts a Bearer token from the input using {@code tokenExtractor},
     * stores it in the pipeline context, and returns the input unchanged. Use when a preceding
     * step returns a DTO that contains the token:
     * <pre>{@code
     * Pipeline.given(loginRequest())
     *         .then(http.makeCall(200, TokenDto.class))
     *         .then(HttpTestClient.storeBearer(TokenDto::accessToken))
     *         .then(dto -> buildNextRequest(dto))
     *         .then(http.makeCall(201, ResultDto.class))  // Authorization injected automatically
     *         .execute();
     * }</pre>
     */
    public static <T> Step<T, T> storeBearer(Function<T, String> tokenExtractor) {
        return (input, ctx) -> {
            ctx.put(BEARER_CTX_KEY, tokenExtractor.apply(input));
            return input;
        };
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
            HttpCallRequest<T> merged = mergeHeaders(request, outerCtx);
            log.info(HttpTestClientLogTemplates.CALL, merged.method(), merged.url());
            R result = Retry.<HttpCallRequest<T>, R>of(buildCallAndCheck(responseType, expected.status(), expectedHeaders, expected.dto()), retryConfig)
                    .apply(merged, outerCtx);
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
            HttpCallRequest<T> merged = mergeHeaders(request, outerCtx);
            log.info(HttpTestClientLogTemplates.CALL, merged.method(), merged.url());
            R result = Retry.<HttpCallRequest<T>, R>of(buildCallAndCheck(typeRef, expected.status(), expectedHeaders, expected.dto()), retryConfig)
                    .apply(merged, outerCtx);
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
            HttpCallRequest<T> merged = mergeHeaders(request, outerCtx);
            log.info(HttpTestClientLogTemplates.CALL, merged.method(), merged.url());
            R result = Retry.<HttpCallRequest<T>, R>of(buildCallAndCheck(typeRef, expected.status(), expectedHeaders, expected.dto(), temporalTolerance), retryConfig)
                    .apply(merged, outerCtx);
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
            HttpCallRequest<T> merged = mergeHeaders(request, outerCtx);
            log.info(HttpTestClientLogTemplates.CALL, merged.method(), merged.url());
            R result = Retry.<HttpCallRequest<T>, R>of(buildCallAndCheck(responseType, expected.status(), expectedHeaders, expected.dto(), temporalTolerance), retryConfig)
                    .apply(merged, outerCtx);
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
            HttpCallRequest<T> merged = mergeHeaders(request, outerCtx);
            log.info(HttpTestClientLogTemplates.CALL, merged.method(), merged.url());
            return Retry.<HttpCallRequest<T>, R>of(buildCallWithStatusCheck(expectedStatus, responseType), retryConfig)
                    .apply(merged, outerCtx);
        };
    }

    /**
     * Same as {@link #makeCall(int, Class)} but uses a {@link TypeReference} for
     * deserialization of generic response types.
     */
    public <T, R> Step<HttpCallRequest<T>, R> makeCall(int expectedStatus, TypeReference<R> typeRef) {
        return (request, outerCtx) -> {
            HttpCallRequest<T> merged = mergeHeaders(request, outerCtx);
            log.info(HttpTestClientLogTemplates.CALL, merged.method(), merged.url());
            return Retry.<HttpCallRequest<T>, R>of(buildCallWithStatusCheck(expectedStatus, typeRef), retryConfig)
                    .apply(merged, outerCtx);
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
        return (request, outerCtx) -> Retry.<HttpCallRequest<T>, R>of(
                Step.<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                        .andThen(httpCheck.statusCode(expectedStatus))
                        .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, responseType))
                        .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                        .andThen(check.matchingNonNull(expectedDto)),
                retryConfig
        ).apply(mergeHeaders(request, outerCtx), outerCtx);
    }

    /**
     * Same as {@link #pollingCall(int, Object, RetryConfig)} but applies
     * {@code temporalTolerance} when comparing timestamp fields in the response body.
     */
    @SuppressWarnings("unchecked")
    public <T, R> Step<HttpCallRequest<T>, R> pollingCall(int expectedStatus, R expectedDto, RetryConfig retryConfig, Duration temporalTolerance) {
        Class<R> responseType = (Class<R>) expectedDto.getClass();
        return (request, outerCtx) -> Retry.<HttpCallRequest<T>, R>of(
                Step.<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                        .andThen(httpCheck.statusCode(expectedStatus))
                        .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, responseType))
                        .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                        .andThen(check.matchingNonNull(expectedDto, temporalTolerance)),
                retryConfig
        ).apply(mergeHeaders(request, outerCtx), outerCtx);
    }

    private <T> HttpCallRequest<T> mergeHeaders(HttpCallRequest<T> request, PipelineContext ctx) {
        String url = (baseUrl != null && request.url().startsWith("/"))
                ? baseUrl + request.url()
                : request.url();

        if (defaultHeaders.isEmpty() && !ctx.contains(BEARER_CTX_KEY) && url.equals(request.url())) {
            return request;
        }
        Map<String, String> merged = new HashMap<>();
        request.headers().forEach(h -> merged.put(h.name(), h.value()));
        merged.putAll(defaultHeaders); // client defaults win over per-request headers
        if (!merged.containsKey("Authorization") && ctx.contains(BEARER_CTX_KEY)) {
            merged.put("Authorization", "Bearer " + ctx.<String>get(BEARER_CTX_KEY));
        }
        List<Header> mergedList = merged.entrySet().stream()
                .map(e -> new Header(e.getKey(), e.getValue()))
                .collect(java.util.stream.Collectors.toList());
        return new HttpCallRequest<>(url, request.method(), mergedList, request.dto(), request.queryParams());
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
