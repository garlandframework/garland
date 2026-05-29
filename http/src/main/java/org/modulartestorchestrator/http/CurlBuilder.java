package org.modulartestorchestrator.http;

import org.modulartestorchestrator.http.model.Header;

import java.util.List;

final class CurlBuilder {

    private CurlBuilder() {}

    static String from(String method, String url, List<Header> headers, String body) {
        StringBuilder sb = new StringBuilder("curl -X ").append(method)
                .append(" '").append(url).append("'")
                .append(" \\\n  -H 'Content-Type: application/json'");

        if (headers != null) {
            for (Header h : headers) {
                sb.append(" \\\n  -H '").append(h.name()).append(": ").append(h.value()).append("'");
            }
        }

        if (body != null && !body.isBlank()) {
            sb.append(" \\\n  -d '").append(body).append("'");
        }

        return sb.toString();
    }
}
