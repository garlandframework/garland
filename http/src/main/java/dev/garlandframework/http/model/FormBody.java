package dev.garlandframework.http.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Request body for {@code application/x-www-form-urlencoded} requests.
 *
 * <p>Pass as the {@code dto} of an {@link HttpCallRequest} — {@link dev.garlandframework.http.HttpSteps}
 * detects this type and serializes it as a percent-encoded key=value string instead of JSON.
 * {@code Content-Type: application/x-www-form-urlencoded} is set automatically.
 *
 * <p>Typical use: OAuth2 token endpoints and any legacy API that expects HTML form encoding.
 *
 * <pre>{@code
 * HttpCallRequest<FormBody> tokenRequest = new HttpCallRequest<>(
 *         Connections.AUTH_URL + "/oauth/token",
 *         "POST",
 *         List.of(),
 *         new FormBody()
 *                 .field("grant_type", "client_credentials")
 *                 .field("client_id",  "my-client")
 *                 .field("client_secret", "secret"));
 * }</pre>
 */
public final class FormBody {

    private final Map<String, String> fields;

    public FormBody() {
        this.fields = new LinkedHashMap<>();
    }

    private FormBody(Map<String, String> fields) {
        this.fields = new LinkedHashMap<>(fields);
    }

    /** Adds a field. Returns {@code this} for chaining. */
    public FormBody field(String name, String value) {
        FormBody copy = new FormBody(fields);
        copy.fields.put(name, value);
        return copy;
    }

    /** Returns the percent-encoded form body string. */
    public String encode() {
        return fields.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public Map<String, String> fields() {
        return Map.copyOf(fields);
    }
}
