package example.oauth;

import com.notshahrad.spring_boot_base.auth.oauth2.LocalOidcUserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class BaseOidcStarterTest {
    private final WebApplicationContextRunner consumer = new WebApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class)
            .withPropertyValues("spring.datasource.url=jdbc:h2:mem:oauth_starter");

    @Test
    void providerRegistrationAloneDoesNotInstallLoginRoutes() {
        consumer.withPropertyValues(
                        "spring.security.oauth2.client.registration.google.client-id=test-client",
                        "spring.security.oauth2.client.registration.google.client-secret=test-only")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(SecurityFilterChain.class)
                        .hasBean("baseSecurityFilterChain")
                        .doesNotHaveBean("oauth2LoginFilterChain")
                        .doesNotHaveBean(LocalOidcUserService.class));
    }

    @Test
    void enabledProviderHasScopedLoginChainAndProtectedFallback() {
        consumer.withPropertyValues(
                        "base.auth.local.enabled=true", "base.auth.oauth2.enabled=true",
                        "spring.security.oauth2.client.registration.google.client-id=test-client",
                        "spring.security.oauth2.client.registration.google.client-secret=test-only")
                .run(context -> assertThat(context).hasNotFailed().hasBean("baseSecurityFilterChain")
                        .hasBean("oauth2LoginFilterChain").hasSingleBean(LocalOidcUserService.class));
    }

    @Test
    void enabledLoginWithNoRegistrationFailsStartup() {
        consumer.withPropertyValues("base.auth.local.enabled=true", "base.auth.oauth2.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("ClientRegistrationRepository"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}
