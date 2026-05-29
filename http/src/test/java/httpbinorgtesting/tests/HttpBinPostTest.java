package httpbinorgtesting.tests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import httpbinorgtesting.infrastructure.BaseTest;
import org.modulartestorchestrator.base.Pipeline;
import org.modulartestorchestrator.http.model.HttpCallRequest;
import org.modulartestorchestrator.http.model.HttpCallResponse;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class HttpBinPostTest extends BaseTest {

    @Test
    public void postEchoesRequestBody() throws Exception {
        var request = new HttpCallRequest<>(
                "https://httpbin.org/post",
                "POST",
                List.of(),
                new PostRequest("123")
        );

        var expected = new HttpCallResponse<>(
                "",
                200,
                Map.of("content-type", List.of("application/json")),
                new PostResponse(Map.of("userId", "123"))
        );

        Pipeline.given(request)
                .then(client.makeCall(expected))
                .execute();
    }

    record PostRequest(String userId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PostResponse(Object json) {}
}
