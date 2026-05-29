package httpbinorgtesting.infrastructure;

import org.modulartestorchestrator.base.retry.RetryConfig;
import org.modulartestorchestrator.http.HttpTestClient;
import org.testng.annotations.BeforeMethod;

import java.time.Duration;

public abstract class BaseTest {

    protected HttpTestClient client;

    @BeforeMethod
    public void setUp() {
        client = new HttpTestClient(RetryConfig.of(3, Duration.ofSeconds(2)));
    }
}
