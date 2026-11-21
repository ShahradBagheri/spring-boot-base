package com.notshahrad.spring_boot_base.security.phone;

import com.notshahrad.spring_boot_base.common.response.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "base.auth.phone", name = {"enabled", "otp.enabled", "web.enabled"},
        havingValue = "true")
@EnableConfigurationProperties(PhoneRateLimits.Properties.class)
class PhoneAuthWebConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain phoneAuthFilterChain(HttpSecurity http, JsonMapper json,
            @Value("${server.servlet.session.cookie.name:JSESSIONID}") String sessionCookieName) throws Exception {
        return http.securityMatcher("/api/auth/phone/**")
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(HttpMethod.GET, "/api/auth/phone/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/phone/signup/request", "/api/auth/phone/signup/verify",
                                "/api/auth/phone/login/request", "/api/auth/phone/login/verify").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, json, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, json, HttpStatus.FORBIDDEN, "Access denied")))
                .logout(logout -> logout.logoutUrl("/api/auth/phone/logout").deleteCookies(sessionCookieName)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .build();
    }

    @Bean
    PhoneRateLimits phoneRateLimits(PhoneRateLimits.Properties properties) {
        return new PhoneRateLimits(properties);
    }

    @Bean
    ThreadPoolTaskExecutor phoneChallengeExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("phone-challenge-");
        return executor;
    }

    private static void writeError(HttpServletResponse response, JsonMapper json, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(json.writeValueAsBytes(ApiError.of(status.value(), message)));
    }
}
