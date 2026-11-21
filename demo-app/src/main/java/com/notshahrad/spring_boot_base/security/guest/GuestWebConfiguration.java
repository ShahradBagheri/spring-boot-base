package com.notshahrad.spring_boot_base.security.guest;

import com.notshahrad.spring_boot_base.common.response.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "base.auth.guest", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GuestWebConfiguration.Properties.class)
class GuestWebConfiguration {

    private static final String SESSION = "/api/guest/session";
    private static final String CSRF = "/api/guest/csrf";

    @Bean
    GuestSessions guestSessions(Properties properties) {
        return new GuestSessions(Clock.systemUTC(), properties.lifetime());
    }

    @Bean
    AuthorizationManager<RequestAuthorizationContext> guestOnly(GuestSessions sessions) {
        var trust = new AuthenticationTrustResolverImpl();
        return (authentication, context) -> new AuthorizationDecision(
                trust.isAnonymous(authentication.get()) && sessions.current(context.getRequest()).isPresent());
    }

    @Bean
    @Order(1)
    SecurityFilterChain guestFilterChain(HttpSecurity http, JsonMapper json,
                                        AuthorizationManager<RequestAuthorizationContext> guestOnly) throws Exception {
        return http.securityMatcher("/api/guest/**")
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(HttpMethod.GET, CSRF).anonymous()
                        .requestMatchers(HttpMethod.POST, SESSION).anonymous()
                        .requestMatchers(HttpMethod.GET, SESSION).access(guestOnly)
                        .requestMatchers(HttpMethod.DELETE, SESSION).access(guestOnly)
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, json, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, json, HttpStatus.FORBIDDEN, "Access denied")))
                .requestCache(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }

    private static void writeError(HttpServletResponse response, JsonMapper json, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(json.writeValueAsBytes(ApiError.of(status.value(), message)));
    }

    @ConfigurationProperties("base.auth.guest")
    record Properties(@DefaultValue("30m") Duration lifetime) {

        Properties {
            if (lifetime.compareTo(Duration.ofSeconds(1)) < 0 || lifetime.compareTo(Duration.ofDays(1)) > 0) {
                throw new IllegalArgumentException("Guest lifetime must be between one second and one day");
            }
        }
    }
}
