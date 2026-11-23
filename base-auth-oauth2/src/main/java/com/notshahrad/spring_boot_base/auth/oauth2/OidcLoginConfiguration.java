package com.notshahrad.spring_boot_base.auth.oauth2;

import com.notshahrad.spring_boot_base.auth.OidcIdentityService;
import com.notshahrad.spring_boot_base.common.response.ApiError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "base.auth.oauth2", name = "enabled", havingValue = "true")
public class OidcLoginConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LocalOidcUserService localOidcUserService(OidcIdentityService identityService) {
        return new LocalOidcUserService(identityService);
    }

    @Bean
    @Order(1)
    @ConditionalOnMissingBean(name = "oauth2LoginFilterChain")
    public SecurityFilterChain oauth2LoginFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository registrations,
            LocalOidcUserService localOidcUserService,
            JsonMapper jsonMapper,
            @Value("${server.servlet.session.cookie.name:JSESSIONID}") String sessionCookieName) throws Exception {

        var paths = PathPatternRequestMatcher.withDefaults();
        var logoutRequest = paths.matcher(HttpMethod.POST, "/logout");

        return http
                .securityMatcher(new OrRequestMatcher(
                        paths.matcher("/oauth2/authorization/**"),
                        paths.matcher("/login/oauth2/code/**"),
                        logoutRequest))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/login/oauth2/code/**"))
                .logout(logout -> logout
                        .logoutRequestMatcher(logoutRequest)
                        .deleteCookies(sessionCookieName)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .oauth2Login(login -> login
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(new AppleAuthorizationRequestResolver(registrations)))
                        .userInfoEndpoint(endpoint -> endpoint.oidcUserService(localOidcUserService))
                        .defaultSuccessUrl("/", true)
                        .failureHandler((request, response, exception) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getOutputStream().write(jsonMapper.writeValueAsBytes(
                                    ApiError.of(401, "External authentication failed")));
                        }))
                .build();
    }
}
