package com.notshahrad.spring_boot_base.auth.token;

import com.notshahrad.spring_boot_base.auth.AccountSessionValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "base.auth.token", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AccessTokenProperties.class)
public class AccessTokenConfiguration {

    @Bean
    AccessTokenKeys accessTokenKeys(AccessTokenProperties properties) {
        return new AccessTokenKeys(properties, Clock.systemUTC().instant());
    }

    @Bean
    AccessTokenIssuer accessTokenIssuer(AccessTokenProperties properties, AccessTokenKeys keys) {
        return new AccessTokenIssuer(properties, keys, Clock.systemUTC());
    }

    @Bean
    RefreshSessionService refreshSessionService(JdbcTemplate jdbcTemplate, AccessTokenIssuer issuer,
                                               ObjectProvider<AccountSessionValidator> validators) {
        return new RefreshSessionService(new RefreshSessionStore(jdbcTemplate), issuer, Clock.systemUTC(), validators);
    }

    @Bean
    AccessTokenResourceServer accessTokenResourceServer(
            AccessTokenProperties properties, AccessTokenKeys keys, JsonMapper jsonMapper) {
        var decoder = AccessTokenDecoder.create(properties, keys, Clock.systemUTC());
        return new AccessTokenResourceServer(decoder, jsonMapper);
    }
}
