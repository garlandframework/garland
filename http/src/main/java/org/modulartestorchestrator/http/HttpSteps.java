package org.modulartestorchestrator.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.modulartestorchestrator.base.PipelineContext;
import org.modulartestorchestrator.base.Step;
import org.modulartestorchestrator.http.model.FormBody;
import org.modulartestorchestrator.http.model.Header;
import org.modulartestorchestrator.http.model.HttpCallRequest;
import org.modulartestorchestrator.http.model.HttpCallResponse;
import org.modulartestorchestrator.http.model.MultipartBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Low-level HTTP execution steps: serializes the request DTO to JSON (or uses the body as-is
 * for {@code String}, {@link org.modulartestorchestrator.http.model.FormBody}, and
 * {@link org.modulartestorchestrator.http.model.MultipartBody} dtos), sends via
 * {@link HttpClientWrapper}, and deserializes the response body. Used internally by
 * {@link HttpTestClient}.
 */
public class HttpSteps {

    private static final Logger log = LoggerFactory.getLogger(HttpSteps.class);

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final HttpClientWrapper http;

    public HttpSteps() {
        this.http = new HttpClientWrapper();
    }

    public HttpSteps(HttpClientWrapper http) {
        this.http = http;
    }

    public <T> java.net.http.HttpResponse<String> call(HttpCallRequest<T> request, PipelineContext ctx) throws Exception {
        String method = request.method();
        String url = buildUrl(request.url(), request.queryParams());
        T dto = request.dto();
        List<Header> baseHeaders = request.headers();

        if (dto instanceof FormBody formBody) {
            String body = formBody.encode();
            List<Header> headers = withContentType(baseHeaders, "application/x-www-form-urlencoded");
            log.info(HttpStepsLogTemplates.REQUEST_CURL, CurlBuilder.from(method, url, headers, body));
            java.net.http.HttpResponse<String> response = http.send(method, url, body, headers);
            log.info(HttpStepsLogTemplates.RESPONSE_RECEIVED, response.statusCode(), response.body());
            return response;
        } else if (dto instanceof MultipartBody multipartBody) {
            byte[] body = multipartBody.toBytes();
            String contentType = "multipart/form-data; boundary=" + multipartBody.boundary();
            List<Header> headers = withContentType(baseHeaders, contentType);
            log.info(HttpStepsLogTemplates.REQUEST_CURL, CurlBuilder.fromMultipart(method, url, headers, multipartBody));
            java.net.http.HttpResponse<String> response = http.sendBytes(method, url, body, headers);
            log.info(HttpStepsLogTemplates.RESPONSE_RECEIVED, response.statusCode(), response.body());
            return response;
        } else if (dto instanceof String rawBody) {
            List<Header> headers = withContentType(baseHeaders, "application/json");
            log.info(HttpStepsLogTemplates.REQUEST_CURL, CurlBuilder.from(method, url, headers, rawBody));
            java.net.http.HttpResponse<String> response = http.send(method, url, rawBody, headers);
            log.info(HttpStepsLogTemplates.RESPONSE_RECEIVED, response.statusCode(), response.body());
            return response;
        } else {
            String body = mapper.writeValueAsString(dto);
            List<Header> headers = withContentType(baseHeaders, "application/json");
            log.info(HttpStepsLogTemplates.REQUEST_CURL, CurlBuilder.from(method, url, headers, body));
            java.net.http.HttpResponse<String> response = http.send(method, url, body, headers);
            log.info(HttpStepsLogTemplates.RESPONSE_RECEIVED, response.statusCode(), response.body());
            return response;
        }
    }

    private static List<Header> withContentType(List<Header> baseHeaders, String contentType) {
        boolean alreadySet = baseHeaders != null && baseHeaders.stream()
                .anyMatch(h -> h.name().equalsIgnoreCase("Content-Type"));
        if (alreadySet) return baseHeaders;
        List<Header> result = new ArrayList<>(baseHeaders == null ? List.of() : baseHeaders);
        result.add(new Header("Content-Type", contentType));
        return List.copyOf(result);
    }

    public <T> HttpCallResponse<T> deserialize(java.net.http.HttpResponse<String> response, Class<T> type) throws Exception {
        String body = response.body();
        T dto = (type == Void.class || body == null || body.isBlank())
                ? null
                : mapper.readValue(body, type);
        return new HttpCallResponse<>(response.statusCode(), response.headers().map(), dto);
    }

    public <T> Step<java.net.http.HttpResponse<String>, HttpCallResponse<T>> deserialize(Class<T> type) {
        return (response, ctx) -> deserialize(response, type);
    }

    public <T> HttpCallResponse<T> deserialize(java.net.http.HttpResponse<String> response, TypeReference<T> typeRef) throws Exception {
        String body = response.body();
        T dto = (body == null || body.isBlank())
                ? null
                : mapper.readValue(body, typeRef);
        return new HttpCallResponse<>(response.statusCode(), response.headers().map(), dto);
    }

    public static <T> Step<HttpCallResponse<T>, T> extractDto() {
        return (response, ctx) -> response.dto();
    }

    private static String buildUrl(String baseUrl, Map<String, String> queryParams) {
        if (queryParams.isEmpty()) return baseUrl;
        String query = queryParams.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + query;
    }
}
