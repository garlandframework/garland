package org.modulartestorchestrator.http.model;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code multipart/form-data} requests.
 *
 * <p>Pass as the {@code dto} of an {@link HttpCallRequest} — {@link org.modulartestorchestrator.http.HttpSteps}
 * detects this type and serializes it as a multipart byte stream instead of JSON.
 * {@code Content-Type: multipart/form-data; boundary=...} is set automatically.
 *
 * <p>Typical use: file upload endpoints.
 *
 * <pre>{@code
 * HttpCallRequest<MultipartBody> upload = new HttpCallRequest<>(
 *         Connections.FILES_URL + "/upload",
 *         "POST",
 *         List.of(),
 *         new MultipartBody()
 *                 .field("description", "profile photo")
 *                 .file("photo", Path.of("/tmp/photo.jpg"), "image/jpeg"));
 * }</pre>
 */
public final class MultipartBody {

    private final String boundary;
    private final List<Part> parts;

    public MultipartBody() {
        this.boundary = "mtoboundary" + UUID.randomUUID().toString().replace("-", "");
        this.parts = List.of();
    }

    private MultipartBody(String boundary, List<Part> parts) {
        this.boundary = boundary;
        this.parts = List.copyOf(parts);
    }

    /** Adds a text field. Returns a new {@code MultipartBody} for chaining. */
    public MultipartBody field(String name, String value) {
        List<Part> updated = new ArrayList<>(parts);
        updated.add(new FieldPart(name, value));
        return new MultipartBody(boundary, updated);
    }

    /** Adds a file part read from disk. Returns a new {@code MultipartBody} for chaining. */
    public MultipartBody file(String name, Path path, String contentType) throws IOException {
        return file(name, Files.readAllBytes(path), path.getFileName().toString(), contentType);
    }

    /** Adds a file part from a byte array. Returns a new {@code MultipartBody} for chaining. */
    public MultipartBody file(String name, byte[] data, String filename, String contentType) {
        List<Part> updated = new ArrayList<>(parts);
        updated.add(new FilePart(name, filename, contentType, data));
        return new MultipartBody(boundary, updated);
    }

    /** Returns the boundary string (without leading {@code --}). */
    public String boundary() {
        return boundary;
    }

    /** Builds the multipart body as a byte array. */
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] dashes = "--".getBytes(StandardCharsets.UTF_8);
        byte[] boundaryBytes = boundary.getBytes(StandardCharsets.UTF_8);

        for (Part part : parts) {
            out.write(dashes);
            out.write(boundaryBytes);
            out.write(crlf);
            for (String header : part.partHeaders()) {
                out.write(header.getBytes(StandardCharsets.UTF_8));
                out.write(crlf);
            }
            out.write(crlf);
            out.write(part.partBody());
            out.write(crlf);
        }

        out.write(dashes);
        out.write(boundaryBytes);
        out.write(dashes);
        out.write(crlf);

        return out.toByteArray();
    }

    /** Returns all text field parts (used for curl logging). */
    public List<FieldPart> fieldParts() {
        return parts.stream()
                .filter(p -> p instanceof FieldPart)
                .map(p -> (FieldPart) p)
                .toList();
    }

    /** Returns all file parts (used for curl logging). */
    public List<FilePart> fileParts() {
        return parts.stream()
                .filter(p -> p instanceof FilePart)
                .map(p -> (FilePart) p)
                .toList();
    }

    sealed interface Part permits FieldPart, FilePart {
        List<String> partHeaders();
        byte[] partBody();
    }

    public record FieldPart(String name, String value) implements Part {
        public List<String> partHeaders() {
            return List.of("Content-Disposition: form-data; name=\"" + name + "\"");
        }

        public byte[] partBody() {
            return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        }
    }

    public record FilePart(String name, String filename, String contentType, byte[] data) implements Part {
        public List<String> partHeaders() {
            return List.of(
                    "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"",
                    "Content-Type: " + contentType
            );
        }

        public byte[] partBody() {
            return data;
        }
    }
}
