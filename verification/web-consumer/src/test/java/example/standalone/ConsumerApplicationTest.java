package example.standalone;

import com.notshahrad.spring_boot_base.common.exception.GlobalExceptionHandler;
import com.notshahrad.spring_boot_base.common.exception.ResourceNotFoundException;
import com.notshahrad.spring_boot_base.common.response.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {ConsumerApplication.class, ConsumerApplicationTest.CustomAdvice.class})
class ConsumerApplicationTest {

    @LocalServerPort
    int port;

    @Test
    void startsAndServesTheResponseContractWithoutAuthOrADatabase() throws Exception {
        var response = request("/example");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"success\":true", "\"data\":\"standalone\"");
    }

    @Test
    void applicationOverrideHandlesMvcErrors() throws Exception {
        var response = request("/missing");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"status\":404", "\"message\":\"Application message\"");
    }

    private HttpResponse<String> request(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CustomAdvice {
        @Bean
        GlobalExceptionHandler applicationAdvice() {
            return new ApplicationAdvice();
        }
    }

    @TestComponent
    @org.springframework.web.bind.annotation.RestControllerAdvice
    static class ApplicationAdvice extends GlobalExceptionHandler {
        @Override
        public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException exception) {
            return ResponseEntity.status(404).body(ApiError.of(404, "Application message"));
        }
    }
}
