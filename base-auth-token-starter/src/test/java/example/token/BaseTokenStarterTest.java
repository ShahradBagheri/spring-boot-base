package example.token;

import com.notshahrad.spring_boot_base.auth.token.AccessTokenIssuer;
import com.notshahrad.spring_boot_base.auth.token.AccessTokenResourceServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class BaseTokenStarterTest {
    private final WebApplicationContextRunner consumer = new WebApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class)
            .withPropertyValues("spring.datasource.url=jdbc:h2:mem:token_starter");

    @Test
    void disabledTokensKeepOnlyTheDefaultSecurityChain() {
        consumer.run(context -> assertThat(context).hasNotFailed().hasSingleBean(SecurityFilterChain.class)
                .hasBean("baseSecurityFilterChain")
                .doesNotHaveBean(AccessTokenIssuer.class)
                .doesNotHaveBean(AccessTokenResourceServer.class));
    }

    @Test
    void enabledTokensFailStartupWithoutSigningKeys() {
        consumer.withPropertyValues("base.auth.token.enabled=true", "base.auth.token.issuer=https://issuer.example.test",
                        "base.auth.token.audience=my-api")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("base.auth.token."));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}
