package com.notshahrad.spring_boot_base.security.token;

import com.notshahrad.spring_boot_base.auth.token.AccessTokenResourceServer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "base.auth.token", name = "enabled", havingValue = "true")
public class TokenApiConfiguration {

    @Bean
    @Order(0)
    SecurityFilterChain tokenApiFilterChain(HttpSecurity http, AccessTokenResourceServer resourceServer) throws Exception {
        http.securityMatcher("/api/token/**")
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(HttpMethod.GET, "/api/token/me").authenticated()
                        .anyRequest().denyAll());
        return resourceServer.configure(http).build();
    }

    @RestController
    @ConditionalOnProperty(prefix = "base.auth.token", name = "enabled", havingValue = "true")
    static class TokenUserController {

        @GetMapping("/api/token/me")
        Map<String, String> currentUser(Principal principal) {
            return Map.of("userId", principal.getName());
        }
    }
}
