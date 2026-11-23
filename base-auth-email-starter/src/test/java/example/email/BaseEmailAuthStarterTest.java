package example.email;

import com.notshahrad.spring_boot_base.auth.email.EmailIdentityRepository;
import com.notshahrad.spring_boot_base.auth.email.EmailPasswordService;
import com.notshahrad.spring_boot_base.mail.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEmailAuthStarterTest {

    private final ApplicationContextRunner consumer = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class)
            .withPropertyValues("spring.datasource.url=jdbc:h2:mem:" + UUID.randomUUID());

    @Test
    void disabledByDefault() {
        consumer.run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(EmailIdentityRepository.class)
                .doesNotHaveBean(EmailPasswordService.class));
    }

    @Test
    void enablesPasswordServiceWithoutRequiringAMailTransport() {
        consumer.withPropertyValues(
                        "base.auth.local.enabled=true",
                        "base.auth.email.enabled=true",
                        "base.auth.email.password-enabled=true")
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(EmailPasswordService.class)
                        .doesNotHaveBean(EmailSender.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}
