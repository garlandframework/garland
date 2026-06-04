package org.modulartestorchestrator.http;

import org.modulartestorchestrator.http.model.Header;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Thin wrapper around {@link java.net.http.HttpClient}. Used internally by {@link HttpSteps}.
 * Callers are responsible for adding the correct {@code Content-Type} header.
 */
public class HttpClientWrapper {

    private final HttpClient client = HttpClient.newHttpClient();
    private final Duration timeout;

    public HttpClientWrapper() {
        this.timeout = null;
    }

    public HttpClientWrapper(Duration timeout) {
        this.timeout = timeout;
    }

    public java.net.http.HttpResponse<String> send(String method, String url, String body, List<Header> headers) throws Exception {

        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method(method, java.net.http.HttpRequest.BodyPublishers.ofString(body == null ? "" : body));

        if (timeout != null) builder.timeout(timeout);

        if (headers != null) {
            for (Header h : headers) {
                builder.header(h.name(), h.value());
            }
        }

        return client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    public java.net.http.HttpResponse<String> sendBytes(String method, String url, byte[] body, List<Header> headers) throws Exception {

        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method(method, java.net.http.HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body));

        if (timeout != null) builder.timeout(timeout);

        if (headers != null) {
            for (Header h : headers) {
                builder.header(h.name(), h.value());
            }
        }

        return client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }
}
