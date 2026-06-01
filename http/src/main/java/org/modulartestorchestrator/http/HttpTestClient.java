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
import java.util.List;

public class HttpTestClient {

    private static final Logger log = LoggerFactory.getLogger("HTTP");

    private final HttpSteps httpSteps       = new HttpSteps();
    private final HttpCheckSteps httpCheck  = new HttpCheckSteps();
    private final CheckSteps check          = new CheckSteps();
    private final RetryConfig retryConfig;

    public HttpTestClient(RetryConfig retryConfig) {
        this.retryConfig = retryConfig;
    }

    public HttpTestClient() {
        this(RetryConfig.attempts(1));
    }

    @SuppressWarnings("unchecked")
    public <T, R> StepFunction<HttpCallRequest<T>, R> makeCall(HttpCallResponse<R> expected) {
        Class<R> responseType = (Class<R>) expected.dto().getClass();
        List<Header> expectedHeaders = expected.headers().entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(v -> new Header(e.getKey(), v)))
                .toList();

        return (request, outerCtx) -> {
            log.info(HttpTestClientLogTemplates.CALL, request.method(), request.url());
            R result = Pipeline.given(request)
                    .withContext(outerCtx)
                    .then(Retry.of(buildCallAndCheck(responseType, expected.status(), expectedHeaders, expected.dto()), retryConfig))
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
            log.info(HttpTestClientLogTemplates.CALL, request.method(), request.url());
            R result = Pipeline.given(request)
                    .withContext(outerCtx)
                    .then(Retry.of(buildCallAndCheck(responseType, expected.status(), expectedHeaders, expected.dto(), temporalTolerance), retryConfig))
                    .execute();
            log.info(HttpTestClientLogTemplates.VERIFIED);
            return result;
        };
    }

    public <T, R> StepFunction<HttpCallRequest<T>, R> makeCall(int expectedStatus, Class<R> responseType) {
        return (request, outerCtx) -> {
            log.info(HttpTestClientLogTemplates.CALL, request.method(), request.url());
            return Pipeline.given(request)
                    .withContext(outerCtx)
                    .then(Retry.of(buildCallWithStatusCheck(expectedStatus, responseType), retryConfig))
                    .execute();
        };
    }

    public <T, R> StepFunction<HttpCallRequest<T>, R> makeCall(int expectedStatus, TypeReference<R> typeRef) {
        return (request, outerCtx) -> {
            log.info(HttpTestClientLogTemplates.CALL, request.method(), request.url());
            return Pipeline.given(request)
                    .withContext(outerCtx)
                    .then(Retry.of(buildCallWithStatusCheck(expectedStatus, typeRef), retryConfig))
                    .execute();
        };
    }

    @SuppressWarnings("unchecked")
    public <T, R> StepFunction<HttpCallRequest<T>, R> pollingCall(int expectedStatus, R expectedDto, RetryConfig retryConfig) {
        Class<R> responseType = (Class<R>) expectedDto.getClass();
        return (request, outerCtx) -> Pipeline.given(request)
                .withContext(outerCtx)
                .then(Retry.of(
                        StepFunction.<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                                .andThen(httpCheck.statusCode(expectedStatus))
                                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(responseType, ctx))
                                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                                .andThen(check.matchingNonNull(expectedDto)),
                        retryConfig
                ))
                .execute();
    }

    @SuppressWarnings("unchecked")
    public <T, R> StepFunction<HttpCallRequest<T>, R> pollingCall(int expectedStatus, R expectedDto, RetryConfig retryConfig, Duration temporalTolerance) {
        Class<R> responseType = (Class<R>) expectedDto.getClass();
        return (request, outerCtx) -> Pipeline.given(request)
                .withContext(outerCtx)
                .then(Retry.of(
                        StepFunction.<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                                .andThen(httpCheck.statusCode(expectedStatus))
                                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(responseType, ctx))
                                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                                .andThen(check.matchingNonNull(expectedDto, temporalTolerance)),
                        retryConfig
                ))
                .execute();
    }

    private <T, R> StepFunction<HttpCallRequest<T>, R> buildCallAndCheck(
            Class<R> responseType, int expectedStatus, List<Header> expectedHeaders, R expectedDto) {

        return StepFunction
                .<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(responseType, ctx))
                .andThen(httpCheck.headersContain(expectedHeaders))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                .andThen(check.matchingNonNull(expectedDto));
    }

    private <T, R> StepFunction<HttpCallRequest<T>, R> buildCallAndCheck(
            Class<R> responseType, int expectedStatus, List<Header> expectedHeaders, R expectedDto, Duration temporalTolerance) {

        return StepFunction
                .<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(responseType, ctx))
                .andThen(httpCheck.headersContain(expectedHeaders))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto())
                .andThen(check.matchingNonNull(expectedDto, temporalTolerance));
    }

    private <T, R> StepFunction<HttpCallRequest<T>, R> buildCallWithStatusCheck(int expectedStatus, Class<R> responseType) {
        return StepFunction
                .<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(responseType, ctx))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto());
    }

    private <T, R> StepFunction<HttpCallRequest<T>, R> buildCallWithStatusCheck(int expectedStatus, TypeReference<R> typeRef) {
        return StepFunction
                .<HttpCallRequest<T>, HttpResponse<String>>of(httpSteps::call)
                .andThen(httpCheck.statusCode(expectedStatus))
                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(typeRef, ctx))
                .andThen((HttpCallResponse<R> response, PipelineContext ctx) -> response.dto());
    }
}
