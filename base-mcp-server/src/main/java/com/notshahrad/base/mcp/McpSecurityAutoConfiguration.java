package com.notshahrad.base.mcp;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = McpServerEnvironment.ENABLED, havingValue = "true")
@EnableConfigurationProperties(McpSecurityAutoConfiguration.Properties.class)
public class McpSecurityAutoConfiguration {

    static final String SCOPE = "mcp:access";
    static final String METADATA = "/.well-known/oauth-protected-resource" + McpServerEnvironment.ENDPOINT;

    @Bean
    @Order(0)
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http, Properties settings,
            @Qualifier("mcpJwtDecoder") JwtDecoder decoder) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        var endpoint = paths.matcher(McpServerEnvironment.ENDPOINT);
        var metadata = paths.matcher(HttpMethod.GET, METADATA);
        var unauthorized = new BearerTokenAuthenticationEntryPoint();
        unauthorized.setResourceMetadataParameterResolver(
                request -> settings.resource().resolve(METADATA).toString());

        return http.securityMatcher(new OrRequestMatcher(endpoint, metadata))
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(metadata).permitAll()
                        .anyRequest().hasAuthority("SCOPE_" + SCOPE))
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(unauthorized)
                        .jwt(jwt -> jwt.decoder(decoder))
                        .protectedResourceMetadata(config -> config.protectedResourceMetadataCustomizer(builder -> builder
                                .resource(settings.resource().toString())
                                .authorizationServer(settings.issuer().toString())
                                .bearerMethod("header")
                                .scope(SCOPE))))
                .addFilterBefore(new McpOriginFilter(endpoint), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new McpRateLimitFilter(endpoint, settings.requestsPerSubject(), settings.requestsOverall()),
                        AuthorizationFilter.class)
                .build();
    }

    @Bean(defaultCandidate = false)
    JwtDecoder mcpJwtDecoder(Properties settings) {
        return new SupplierJwtDecoder(() -> withValidation(
                NimbusJwtDecoder.withIssuerLocation(settings.issuer().toString()).build(), settings));
    }

    static JwtDecoder withValidation(NimbusJwtDecoder decoder, Properties settings) {
        String resource = settings.resource().toString();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(settings.issuer().toString()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audiences -> audiences != null && audiences.contains(resource)),
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, subject -> subject != null && !subject.isBlank())));
        return decoder;
    }

    @ConfigurationProperties("base.mcp.server")
    record Properties(URI issuer, URI resource, @DefaultValue("120") int requestsPerSubject,
                      @DefaultValue("1200") int requestsOverall) {

        Properties {
            requireHttps(issuer, "issuer");
            requireHttps(resource, "resource");
            if (!McpServerEnvironment.ENDPOINT.equals(resource.getRawPath())) {
                throw new IllegalArgumentException("The MCP resource URL must use the /mcp path");
            }
            if (requestsPerSubject < 1 || requestsOverall < 1) {
                throw new IllegalArgumentException("MCP request limits must be positive");
            }
        }

        private static void requireHttps(URI uri, String property) {
            if (uri == null || !"https".equals(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("base.mcp.server." + property + " requires an HTTPS URL");
            }
        }
    }
}
