package org.modulartestorchestrator.http;

import com.fasterxml.jackson.core.type.TypeReference;
import org.modulartestorchestrator.base.Pipeline;
import org.modulartestorchestrator.base.PipelineContext;
import org.modulartestorchestrator.base.StepFunction;
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

    public HttpTestClient withHeader(String name, String value) {
        Map<String, String> updated = new HashMap<>(defaultHeaders);
        updated.put(name, value);
        return new HttpTestClient(retryConfig, updated);
    }

    public HttpTestClient withoutHeader(String name) {
        Map<String, String> updated = new HashMap<>(defaultHeaders);
        updated.remove(name);
        return new HttpTestClient(retryConfig, updated);
    }

    public HttpTestClient withBearer(String token) {
        return withHeader("Authorization", "Bearer " + token);
    }

    public HttpTestClient withApiKey(String headerName, String key) {
        return withHeader(headerName, key);
    }

    @SuppressWarnings("unchecked")
    public <T, R> StepFunction<HttpCallRequest<T>, R> makeCall(HttpCallResponse<R> expected) {
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

    public <T, R> StepFunction<HttpCallRequest<T>, R> makeCall(HttpCallResponse<R> expected, TypeReference<R> typeRef) {
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

    public <T, R> StepFunction<HttpCallRequest<T>, R> makeCall(HttpCallResponse<R> expected, TypeReference<R> typeRef, Duration temporalTolerance) {
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

    @SuppressWarnings("unchecked")
    public <T, R> StepFunction<HttpCallRequest<T>, R> makeCall(HttpCallResponse<R> expected, Duration temporalTolerance) {
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

    public <T, R> StepFunction<HttpCallRequest<T>, R> makeCall(int expectedStatus, Class<R> responseType) {
        return (request, outerCtx) -> {
            HttpCallRequest<T> merged = mergeHeaders(request);
            log.info(HttpTestClientLogTemplates.CALL, merged.method(), merged.url());
            return Pipeline.given(merged)
                    .withContext(outerCtx)
                    .then(Retry.of(buildCallWithStatusCheck(expectedStatus, responseType), retryConfig))
                    .execute();
        };
    }

    public <T, R> StepFunction<HttpCallRequest<T>, R> makeCall(int expectedStatus, TypeReference<R> typeRef) {
        return (request, outerCtx) -> {
            HttpCallRequest<T> merged = mergeHeaders(request);
            log.info(HttpTestClientLogTemplates.CALL, merged.method(), merged.url());
            return Pipeline.given(merged)
                    .withContext(outerCtx)
                    .then(Retry.of(buildCallWithStatusCheck(expectedStatus, typeRef), retryConfig))
                    .execute();
        };
    }

    @SuppressWarnings("unchecked")
    public <T, R> StepFunction<HttpCallRequest<T>, R> pollingCall(int expectedStatus, R expectedDto, RetryConfig retryConfig) {
        Class<R> responseType = (Class<R>) expectedDto.getClass();
        return (request, outerCtx) -> Pipeline.given(mergeHeaders(request))
                .withContext(outerCtx)
                .then(Retry.of(
                        StepFunction.<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                                .andThen(httpCheck.statusCode(expectedStatus))
                                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, responseType))
                                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                                .andThen(check.matchingNonNull(expectedDto)),
                        retryConfig
                ))
                .execute();
    }

    @SuppressWarnings("unchecked")
    public <T, R> StepFunction<HttpCallRequest<T>, R> pollingCall(int expectedStatus, R expectedDto, RetryConfig retryConfig, Duration temporalTolerance) {
        Class<R> responseType = (Class<R>) expectedDto.getClass();
        return (request, outerCtx) -> Pipeline.given(mergeHeaders(request))
                .withContext(outerCtx)
                .then(Retry.of(
                        StepFunction.<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
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

    private <T, R> StepFunction<HttpCallRequest<T>, R> buildCallAndCheck(
            Class<R> responseType, int expectedStatus, List<Header> expectedHeaders, R expectedDto) {

        return StepFunction
                .<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, responseType))
                .andThen(httpCheck.headersContain(expectedHeaders))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                .andThen(check.matchingNonNull(expectedDto));
    }

    private <T, R> StepFunction<HttpCallRequest<T>, R> buildCallAndCheck(
            Class<R> responseType, int expectedStatus, List<Header> expectedHeaders, R expectedDto, Duration temporalTolerance) {

        return StepFunction
                .<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, responseType))
                .andThen(httpCheck.headersContain(expectedHeaders))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                .andThen(check.matchingNonNull(expectedDto, temporalTolerance));
    }

    private <T, R> StepFunction<HttpCallRequest<T>, R> buildCallAndCheck(
            TypeReference<R> typeRef, int expectedStatus, List<Header> expectedHeaders, R expectedDto) {

        return StepFunction
                .<HttpCallRequest<T>, java.net.http.HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((java.net.http.HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, typeRef))
                .andThen(httpCheck.headersContain(expectedHeaders))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                .andThen(check.matchingNonNull(expectedDto));
    }

    private <T, R> StepFunction<HttpCallRequest<T>, R> buildCallAndCheck(
            TypeReference<R> typeRef, int expectedStatus, List<Header> expectedHeaders, R expectedDto, Duration temporalTolerance) {

        return StepFunction
                .<HttpCallRequest<T>, java.net.http.HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((java.net.http.HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, typeRef))
                .andThen(httpCheck.headersContain(expectedHeaders))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                .andThen(check.matchingNonNull(expectedDto, temporalTolerance));
    }

    private <T, R> StepFunction<HttpCallRequest<T>, R> buildCallWithStatusCheck(int expectedStatus, Class<R> responseType) {
        return StepFunction
                .<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, responseType))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto());
    }

    private <T, R> StepFunction<HttpCallRequest<T>, R> buildCallWithStatusCheck(int expectedStatus, TypeReference<R> typeRef) {
        return StepFunction
                .<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(response, typeRef))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto());
    }
}
