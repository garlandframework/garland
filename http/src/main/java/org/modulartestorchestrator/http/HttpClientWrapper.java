package org.modulartestorchestrator.http;

import org.modulartestorchestrator.http.model.Header;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;

/**
 * Thin wrapper around {@link java.net.http.HttpClient} that always sets
 * {@code Content-Type: application/json}. Used internally by {@link HttpSteps}.
 */
public class HttpClientWrapper {

    private final HttpClient client = HttpClient.newHttpClient();

    public java.net.http.HttpResponse<String> send(String method, String url, String body, List<Header> headers) throws Exception {

        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method(method, java.net.http.HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                .header("Content-Type", "application/json");

        if (headers != null) {
            for (Header h : headers) {
                builder.header(h.name(), h.value());
            }
        }

        return client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }
}
