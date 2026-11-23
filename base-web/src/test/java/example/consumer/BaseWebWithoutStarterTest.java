package example.consumer;

import com.notshahrad.spring_boot_base.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class BaseWebWithoutStarterTest {

    @Test
    void libraryAloneDoesNotRegisterAdviceOutsideBasePackages() {
        new WebApplicationContextRunner().withUserConfiguration(TestApplication.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(GlobalExceptionHandler.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
