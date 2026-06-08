package dev.garlandframework.http;

import dev.garlandframework.http.model.Header;
import dev.garlandframework.http.model.MultipartBody;

import java.util.List;

final class CurlBuilder {

    private CurlBuilder() {}

    static String from(String method, String url, List<Header> headers, String body) {
        StringBuilder sb = new StringBuilder("curl -X ").append(method)
                .append(" '").append(url).append("'");

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

    static String fromMultipart(String method, String url, List<Header> headers, MultipartBody body) {
        StringBuilder sb = new StringBuilder("curl -X ").append(method)
                .append(" '").append(url).append("'");

        if (headers != null) {
            for (Header h : headers) {
                sb.append(" \\\n  -H '").append(h.name()).append(": ").append(h.value()).append("'");
            }
        }

        for (MultipartBody.FieldPart f : body.fieldParts()) {
            sb.append(" \\\n  -F '").append(f.name()).append("=").append(f.value()).append("'");
        }
        for (MultipartBody.FilePart f : body.fileParts()) {
            sb.append(" \\\n  -F '").append(f.name()).append("=@").append(f.filename()).append("'");
        }

        return sb.toString();
    }
}
