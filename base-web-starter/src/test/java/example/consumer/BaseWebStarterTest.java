package example.consumer;

import com.notshahrad.spring_boot_base.common.exception.GlobalExceptionHandler;
import com.notshahrad.spring_boot_base.common.exception.ResourceNotFoundException;
import com.notshahrad.spring_boot_base.common.response.ApiError;
import com.notshahrad.spring_boot_base.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BaseWebStarterTest {

    private final WebApplicationContextRunner application = new WebApplicationContextRunner()
            .withUserConfiguration(TestApplication.class);

    @Test
    void discoversAdviceAndServesResponseContractsOutsideBasePackages() {
        application.run(context -> {
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).build();
            mvc.perform(get("/example"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value("ready"));
            mvc.perform(get("/missing"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("Item not found with id: 42"));
        });
    }

    @Test
    void applicationAdviceReplacesDefault() {
        application.withBean(CustomAdvice.class).run(context -> {
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).build();
            mvc.perform(get("/missing"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Application message"));
        });
    }

    @Test
    void nonWebApplicationDoesNotRegisterAdvice() {
        new ApplicationContextRunner().withUserConfiguration(TestApplication.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(GlobalExceptionHandler.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(ExampleController.class)
    static class TestApplication {
    }

    @RestController
    static class ExampleController {
        @GetMapping("/example")
        ApiResponse<String> example() {
            return ApiResponse.success("ready");
        }

        @GetMapping("/missing")
        void missing() {
            throw new ResourceNotFoundException("Item", 42);
        }
    }

    @RestControllerAdvice
    static class CustomAdvice extends GlobalExceptionHandler {
        @Override
        public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException exception) {
            return ResponseEntity.status(404).body(ApiError.of(404, "Application message"));
        }
    }
}
