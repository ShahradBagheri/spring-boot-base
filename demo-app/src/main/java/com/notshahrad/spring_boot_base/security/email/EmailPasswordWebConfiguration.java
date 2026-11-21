package com.notshahrad.spring_boot_base.security.email;

import com.notshahrad.spring_boot_base.auth.email.EmailPasswordPrincipal;
import com.notshahrad.spring_boot_base.auth.email.EmailPasswordService;
import com.notshahrad.spring_boot_base.common.response.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "base.auth.email", name = {"enabled", "password-enabled"}, havingValue = "true")
class EmailPasswordWebConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain emailPasswordFilterChain(HttpSecurity http, JsonMapper json,
            @Value("${server.servlet.session.cookie.name:JSESSIONID}") String sessionCookieName) throws Exception {
        return http.securityMatcher("/api/auth/email/**")
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(HttpMethod.GET, "/api/auth/email/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/email/signup", "/api/auth/email/verify",
                                "/api/auth/email/login", "/api/auth/email/reset/request",
                                "/api/auth/email/reset/complete").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, json, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, json, HttpStatus.FORBIDDEN, "Access denied")))
                .logout(logout -> logout.logoutUrl("/api/auth/email/logout").deleteCookies(sessionCookieName)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .build();
    }

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> emailPasswordSessionGuard(
            EmailPasswordService passwords, SecurityFilterProperties security, JsonMapper json) {
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(new OncePerRequestFilter() {
            private final SecurityContextLogoutHandler logout = new SecurityContextLogoutHandler();

            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws IOException, ServletException {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getPrincipal() instanceof EmailPasswordPrincipal principal
                        && !passwords.isCurrent(principal)) {
                    logout.logout(request, response, authentication);
                    writeError(response, json, HttpStatus.UNAUTHORIZED, "Authentication required");
                    return;
                }
                chain.doFilter(request, response);
            }
        });
        registration.setOrder(security.getOrder() + 1);
        return registration;
    }

    @Bean
    ThreadPoolTaskExecutor emailChallengeExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-challenge-");
        return executor;
    }

    private static void writeError(HttpServletResponse response, JsonMapper json, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(json.writeValueAsBytes(ApiError.of(status.value(), message)));
    }
}
