package org.modulartestorchestrator.http;

import org.modulartestorchestrator.base.Step;
import org.modulartestorchestrator.http.model.Header;
import org.modulartestorchestrator.http.model.HttpCallResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP-specific assertion steps: status code and response header checks. Used internally
 * by {@link HttpTestClient}.
 */
public class HttpCheckSteps {

    private static final Logger log = LoggerFactory.getLogger(HttpCheckSteps.class);

    public Step<HttpResponse<String>, HttpResponse<String>> statusCode(int expected) {
        return (response, ctx) -> {
            log.info(HttpCheckStepsLogTemplates.STATUS_CHECKING, expected, response.statusCode());
            assertThat(response.statusCode())
                    .as("HTTP status code")
                    .isEqualTo(expected);
            log.info(HttpCheckStepsLogTemplates.STATUS_PASSED, response.statusCode());
            return response;
        };
    }

    public <T> Step<HttpCallResponse<T>, HttpCallResponse<T>> headersContain(List<Header> expected) {
        return (response, ctx) -> {
            log.info(HttpCheckStepsLogTemplates.HEADERS_CHECKING, expected);
            expected.forEach(h ->
                assertThat(response.headers().getOrDefault(h.name().toLowerCase(), List.of()))
                        .as("Header '%s'", h.name())
                        .contains(h.value())
            );
            log.info(HttpCheckStepsLogTemplates.HEADERS_PASSED);
            return response;
        };
    }
}
