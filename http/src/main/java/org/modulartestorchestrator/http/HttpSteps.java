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

/**
 * Low-level HTTP execution steps: serializes the request DTO to JSON, sends it via
 * {@link HttpClientWrapper}, and deserializes the response body. Used internally by
 * {@link HttpTestClient}.
 */
public class HttpSteps {

    private static final Logger log = LoggerFactory.getLogger(HttpSteps.class);

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final HttpClientWrapper http = new HttpClientWrapper();

    public <T> java.net.http.HttpResponse<String> call(HttpCallRequest<T> request, PipelineContext ctx) throws Exception {
        String body = mapper.writeValueAsString(request.dto());
        String method = request.method();
        String url = request.url();
        List<Header> headers = request.headers();

        log.info(HttpStepsLogTemplates.REQUEST_CURL, CurlBuilder.from(method, url, headers, body));

        java.net.http.HttpResponse<String> response = http.send(method, url, body, headers);

        log.info(HttpStepsLogTemplates.RESPONSE_RECEIVED, response.statusCode(), response.body());

        return response;
    }

    public <T> HttpCallResponse<T> deserialize(java.net.http.HttpResponse<String> response, Class<T> type) throws Exception {
        String body = response.body();
        T dto = (type == Void.class || body == null || body.isBlank())
                ? null
                : mapper.readValue(body, type);
        return new HttpCallResponse<>(response.statusCode(), response.headers().map(), dto);
    }

    public <T> StepFunction<java.net.http.HttpResponse<String>, HttpCallResponse<T>> deserialize(Class<T> type) {
        return (response, ctx) -> deserialize(response, type);
    }

    public <T> HttpCallResponse<T> deserialize(java.net.http.HttpResponse<String> response, TypeReference<T> typeRef) throws Exception {
        String body = response.body();
        T dto = (body == null || body.isBlank())
                ? null
                : mapper.readValue(body, typeRef);
        return new HttpCallResponse<>(response.statusCode(), response.headers().map(), dto);
    }

    public static <T> StepFunction<HttpCallResponse<T>, T> extractDto() {
        return (response, ctx) -> response.dto();
    }
}
