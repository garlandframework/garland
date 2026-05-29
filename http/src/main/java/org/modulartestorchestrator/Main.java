package org.modulartestorchestrator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.modulartestorchestrator.base.checks.CheckSteps;
import org.modulartestorchestrator.base.Pipeline;
import org.modulartestorchestrator.base.PipelineContext;
import org.modulartestorchestrator.base.retry.Retry;
import org.modulartestorchestrator.base.retry.RetryConfig;
import org.modulartestorchestrator.base.StepFunction;
import org.modulartestorchestrator.http.HttpCheckSteps;
import org.modulartestorchestrator.http.HttpSteps;
import org.modulartestorchestrator.http.HttpTestClient;
import org.modulartestorchestrator.http.model.Header;
import org.modulartestorchestrator.http.model.HttpCallRequest;
import org.modulartestorchestrator.http.model.HttpCallResponse;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws Exception {
//        partialRetryExample();
//        fullRetryExample();
        httpTestClientExample();
    }

    // Only call + status check are retried. Deserialization, headers, and body checks run once.
    private static void partialRetryExample() throws Exception {
        HttpSteps httpSteps         = new HttpSteps();
        HttpCheckSteps httpCheck    = new HttpCheckSteps();
        CheckSteps check            = new CheckSteps();

        var request         = buildRequest();
        var expectedBody    = new PostResponse(Map.of("userId", "123"));
        var expectedHeaders = List.of(new Header("content-type", "application/json"));
        var retryConfig     = RetryConfig.of(3, Duration.ofSeconds(2));

        var callWithStatusCheck = StepFunction
                .<String, HttpResponse<String>>of((body, ctx) -> httpSteps.call(body, ctx))
                .andThen(httpCheck.statusCode(200));

        PostResponse result = Pipeline.given(request)
                .then(httpSteps.setup())
                .then(httpSteps::serialize)
                .then(Retry.of(callWithStatusCheck, retryConfig))
                .then((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(PostResponse.class, ctx))
                .then(httpCheck.headersContain(expectedHeaders))
                .then((HttpCallResponse<PostResponse> response, PipelineContext ctx) -> response.dto())
                .then(check.equalTo(expectedBody))
                .execute();
        System.out.println("[partial retry] RESULT: " + result);
    }

    // The entire post-serialize chain is retried as one unit:
    // if any check (status, headers, body) fails, the call is re-issued from scratch.
    private static void fullRetryExample() throws Exception {
        HttpSteps httpSteps         = new HttpSteps();
        HttpCheckSteps httpCheck    = new HttpCheckSteps();
        CheckSteps check            = new CheckSteps();

        var request         = buildRequest();
        var expectedBody    = new PostResponse(Map.of("userId", "123"));
        var expectedHeaders = List.of(new Header("content-type", "application/json"));
        var retryConfig     = RetryConfig.of(3, Duration.ofSeconds(2));

        var fullCallAndCheck = StepFunction
                .<String, HttpResponse<String>>of((body, ctx) -> httpSteps.call(body, ctx))
                .andThen(httpCheck.statusCode(200))
                .andThen((HttpResponse<String> response, PipelineContext ctx) -> httpSteps.deserialize(PostResponse.class, ctx))
                .andThen(httpCheck.headersContain(expectedHeaders))
                .andThen((HttpCallResponse<PostResponse> response, PipelineContext ctx) -> response.dto())
                .andThen(check.equalTo(expectedBody));

        PostResponse result = Pipeline.given(request)
                .then(httpSteps.setup())
                .then(httpSteps::serialize)
                .then(Retry.of(fullCallAndCheck, retryConfig))
                .execute();
        System.out.println("[full retry] RESULT: " + result);
    }

    // Same as fullRetryExample but using HttpTestClient — pipeline is hidden inside the client.
    private static void httpTestClientExample() throws Exception {
        var client = new HttpTestClient(RetryConfig.of(3, Duration.ofSeconds(2)));

        var expected = new HttpCallResponse<>(
                "",
                200,
                Map.of("content-type", List.of("application/json")),
                new PostResponse(Map.of("userId", "123"))
        );

        PostResponse result = Pipeline.given(buildRequest())
                .then(client.makeCall(expected))
                .execute();
        System.out.println("[http test client] RESULT: " + result);
    }

    private static HttpCallRequest<RequestDto> buildRequest() {
        return new HttpCallRequest<>(
                "https://httpbin.org/post",
                "POST",
                List.of(),
                new RequestDto("123")
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PostResponse(Object json) {}



}
