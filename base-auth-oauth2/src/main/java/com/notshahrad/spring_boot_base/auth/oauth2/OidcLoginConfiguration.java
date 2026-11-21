package com.notshahrad.spring_boot_base.auth.oauth2;

import com.notshahrad.spring_boot_base.auth.OidcIdentityService;
import com.notshahrad.spring_boot_base.common.response.ApiError;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "base.auth.oauth2", name = "enabled", havingValue = "true")
public class OidcLoginConfiguration {

    @Bean
    public LocalOidcUserService localOidcUserService(OidcIdentityService identityService) {
        return new LocalOidcUserService(identityService);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain oauth2LoginFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository registrations,
            LocalOidcUserService localOidcUserService,
            JsonMapper jsonMapper) throws Exception {

        return http
                .securityMatcher("/oauth2/authorization/**", "/login/oauth2/code/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/login/oauth2/code/**"))
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
