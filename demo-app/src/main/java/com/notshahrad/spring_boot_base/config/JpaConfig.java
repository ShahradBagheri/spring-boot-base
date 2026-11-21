package com.notshahrad.spring_boot_base.config;

import com.notshahrad.spring_boot_base.common.config.JpaAuditingConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

@Configuration(proxyBeanMethods = false)
@Import(JpaAuditingConfiguration.class)
public class JpaConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of("system");
    }
}
