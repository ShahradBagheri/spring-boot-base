package example.startermatrix;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StarterMatrixTest {
    private static final WebApplicationContextRunner APP =
            new WebApplicationContextRunner().withUserConfiguration(Consumer.class, PrivateController.class);

    @Test
    void withoutBaseTheApplicationUsesBootDefaults() {
        APP.run(context -> {
            assertThat(context).hasNotFailed()
                    .doesNotHaveBean("baseSecurityFilterChain")
                    .doesNotHaveBean("baseGlobalExceptionHandler")
                    .doesNotHaveBean("entityManagerFactory");
            MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).build()
                    .perform(get("/private")).andExpect(status().isOk());
        });
    }

    @Test
    void optionalFeaturesRemainInactiveWithAllStartersPresent() {
        allStarters().run(context -> assertThat(context).hasNotFailed()
                .hasBean("baseSecurityFilterChain")
                .hasBean("baseGlobalExceptionHandler")
                .doesNotHaveBean("jpaAuditingHandler")
                .doesNotHaveBean("appUserRepository")
                .doesNotHaveBean("accessTokenIssuer")
                .doesNotHaveBean("oauth2LoginFilterChain")
                .doesNotHaveBean("smtpEmailSender")
                .doesNotHaveBean("basePhoneOtpDelivery")
                .doesNotHaveBean("stompWebSocketHandlerMapping")
                .doesNotHaveBean("mcpSecurityFilterChain"));
    }

    @Test
    void scopedMcpChainAndWebSocketKeepTheApplicationFallback() {
        allStarters().withPropertyValues(
                "base.mcp.server.enabled=true",
                "base.mcp.server.issuer=https://issuer.example.test",
                "base.mcp.server.resource=https://app.example.test/mcp",
                "base.websocket.enabled=true",
                "base.data.jpa.auditing.enabled=true",
                "base.auth.local.enabled=true"
        ).run(context -> {
            assertThat(context).hasNotFailed()
                    .hasBean("mcpSecurityFilterChain")
                    .hasBean("baseSecurityFilterChain")
                    .hasBean("stompWebSocketHandlerMapping")
                    .hasBean("appUserRepository")
                    .hasBean("jpaAuditingHandler");
            var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                    .addFilters(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class))
                    .build();
            mvc.perform(get("/private")).andExpect(status().isUnauthorized());
            mvc.perform(get("/mcp")).andExpect(status().isUnauthorized());
        });
    }

    private WebApplicationContextRunner allStarters() {
        return APP.withPropertyValues(
                "spring.datasource.url=jdbc:h2:mem:starter_matrix",
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.ai.mcp.server.enabled=false"
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Consumer {
    }

    @RestController
    static class PrivateController {
        @GetMapping("/private")
        String privateRoute() {
            return "ok";
        }
    }
}
