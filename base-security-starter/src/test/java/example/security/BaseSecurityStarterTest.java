package example.security;

import com.notshahrad.base.security.web.SecurityExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BaseSecurityStarterTest {

    private final WebApplicationContextRunner application =
            new WebApplicationContextRunner().withUserConfiguration(TestApplication.class);

    @Test
    void protectsApplicationRoutesAndKeepsCsrfEnabled() {
        application.run(context -> {
            assertThat(context).hasSingleBean(SecurityFilterChain.class).hasBean("baseSecurityFilterChain");
            var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                    .apply(springSecurity()).build();
            mvc.perform(get("/private"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
            mvc.perform(post("/private").with(user("member")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
            mvc.perform(post("/private").with(user("member")).with(csrf()))
                    .andExpect(status().isOk());
        });
    }

    @Test
    void customChainAndHandlerReplaceStarterDefaults() {
        application.withUserConfiguration(CustomSecurity.class).run(context -> {
            assertThat(context).hasSingleBean(SecurityFilterChain.class).hasBean("customChain")
                    .hasSingleBean(SecurityExceptionHandler.class).hasBean("customHandler")
                    .doesNotHaveBean("baseSecurityFilterChain").doesNotHaveBean("baseSecurityExceptionHandler");
            var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                    .apply(springSecurity()).build();
            mvc.perform(get("/private")).andExpect(status().isOk());
        });
    }

    @Test
    void nonServletApplicationAddsNoSecurityChainOrAdvice() {
        new ApplicationContextRunner().withUserConfiguration(TestApplication.class)
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(SecurityFilterChain.class)
                        .doesNotHaveBean(SecurityExceptionHandler.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(ExampleController.class)
    static class TestApplication {
    }

    @RestController
    static class ExampleController {
        @RequestMapping(path = "/private", method = {RequestMethod.GET, RequestMethod.POST})
        String secured() {
            return "ok";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomSecurity {
        @Bean
        SecurityFilterChain customChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll()).build();
        }

        @Bean
        SecurityExceptionHandler customHandler() {
            return new SecurityExceptionHandler();
        }
    }
}
