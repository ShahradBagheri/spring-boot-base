package com.notshahrad.spring_boot_base.websocket;

import com.notshahrad.spring_boot_base.auth.token.AccessTokenResourceServer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"base.websocket.enabled", "base.auth.token.enabled"}, havingValue = "true")
class WebSocketBearerConfiguration {
    @Bean
    @Order(0)
    SecurityFilterChain webSocketBearerChain(HttpSecurity http, AccessTokenResourceServer tokens) {
        var upgrade = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/ws");
        http.securityMatcher(request -> upgrade.matches(request) && request.getHeader(HttpHeaders.AUTHORIZATION) != null)
                .authorizeHttpRequests(routes -> routes.anyRequest().authenticated());
        return tokens.configure(http).build();
    }
}
