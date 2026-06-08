package dev.garlandframework.http;

import dev.garlandframework.base.PipelineContext;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpTestClientAuthTest {

    @Test
    public void storeBearerStoresTokenInContextAndPassesThrough() throws Exception {
        PipelineContext ctx = new PipelineContext();

        String result = HttpTestClient.storeBearer().apply("my-token", ctx);

        assertThat(result).isEqualTo("my-token");
        assertThat(ctx.<String>get(HttpTestClient.BEARER_CTX_KEY)).isEqualTo("my-token");
    }

    @Test
    public void storeBearerWithExtractorStoresTokenAndReturnsInputUnchanged() throws Exception {
        record TokenDto(String accessToken) {}
        PipelineContext ctx = new PipelineContext();
        TokenDto dto = new TokenDto("extracted-token");

        TokenDto result = HttpTestClient.storeBearer(TokenDto::accessToken).apply(dto, ctx);

        assertThat(result).isSameAs(dto);
        assertThat(ctx.<String>get(HttpTestClient.BEARER_CTX_KEY)).isEqualTo("extracted-token");
    }

    @Test
    public void storeBearerOverwritesPreviousTokenInContext() throws Exception {
        PipelineContext ctx = new PipelineContext();
        HttpTestClient.storeBearer().apply("old-token", ctx);

        HttpTestClient.storeBearer().apply("new-token", ctx);

        assertThat(ctx.<String>get(HttpTestClient.BEARER_CTX_KEY)).isEqualTo("new-token");
    }
}
