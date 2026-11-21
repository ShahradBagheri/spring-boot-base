package com.notshahrad.spring_boot_base.config;

import com.notshahrad.spring_boot_base.common.config.JpaAuditingConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;

import com.notshahrad.spring_boot_base.auth.LocalUserPrincipal;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration(proxyBeanMethods = false)
@Import(JpaAuditingConfiguration.class)
public class JpaConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                return Optional.of("system");
            }
            if (!authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
                return Optional.of("anonymous");
            }
            if (authentication.getPrincipal() instanceof LocalUserPrincipal user) {
                return Optional.of(user.getLocalUserId().toString());
            }
            if (authentication instanceof LocalUserPrincipal user) {
                return Optional.of(user.getLocalUserId().toString());
            }
            return Optional.of("unattributed");
        };
    }
}
