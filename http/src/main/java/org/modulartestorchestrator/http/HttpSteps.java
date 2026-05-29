package org.modulartestorchestrator.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.modulartestorchestrator.base.PipelineContext;
import org.modulartestorchestrator.base.StepFunction;
import org.modulartestorchestrator.http.model.Header;
import org.modulartestorchestrator.http.model.HttpCallRequest;
import org.modulartestorchestrator.http.model.HttpCallResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class HttpSteps {

    private static final Logger log = LoggerFactory.getLogger(HttpSteps.class);

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final HttpClientWrapper http = new HttpClientWrapper();

    // 0. unpack HttpCallRequest into context, pass dto downstream
    public <T> StepFunction<HttpCallRequest<T>, T> setup() {
        return (request, ctx) -> {
            ctx.put("url", request.url());
            ctx.put("method", request.method());
            ctx.put("headers", request.headers());
            return request.dto();
        };
    }

    // 1. serialize
    public String serialize(Object input, PipelineContext ctx) throws Exception {

        String json = mapper.writeValueAsString(input);

        ctx.put("body", json);

        return json;
    }

    // 2. send request
    public java.net.http.HttpResponse<String> call(Object input, PipelineContext ctx) throws Exception {

        String method = ctx.get("method");
        String url = ctx.get("url");
        String body = ctx.get("body");
        List<Header> headers = ctx.get("headers");

        log.info(HttpStepsLogTemplates.REQUEST_CURL, CurlBuilder.from(method, url, headers, body));

        java.net.http.HttpResponse<String> response =
                http.send(method, url, body, headers);

        log.info(HttpStepsLogTemplates.RESPONSE_RECEIVED, response.statusCode(), response.body());

        ctx.put("response", response);

        return response;
    }

    // 3. deserialize
    public <T> HttpCallResponse<T> deserialize(Class<T> type, PipelineContext ctx) throws Exception {

        java.net.http.HttpResponse<String> response = ctx.get("response");
        String url = ctx.get("url");

        String body = response.body();
        T dto = (type == Void.class || body == null || body.isBlank())
                ? null
                : mapper.readValue(body, type);

        return new HttpCallResponse<>(url, response.statusCode(), response.headers().map(), dto);
    }

    public <T> StepFunction<java.net.http.HttpResponse<String>, HttpCallResponse<T>> deserialize(Class<T> type) {
        return (response, ctx) -> deserialize(type, ctx);
    }

    public <T> HttpCallResponse<T> deserialize(TypeReference<T> typeRef, PipelineContext ctx) throws Exception {
        java.net.http.HttpResponse<String> response = ctx.get("response");
        String url = ctx.get("url");
        String body = response.body();
        T dto = (body == null || body.isBlank())
                ? null
                : mapper.readValue(body, typeRef);
        return new HttpCallResponse<>(url, response.statusCode(), response.headers().map(), dto);
    }

    public static <T> StepFunction<HttpCallResponse<T>, T> extractDto() {
        return (response, ctx) -> response.dto();
    }
}
