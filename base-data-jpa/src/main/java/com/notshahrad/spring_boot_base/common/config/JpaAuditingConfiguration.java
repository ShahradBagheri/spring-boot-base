package com.notshahrad.spring_boot_base.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Opt-in JPA auditing support for applications consuming base-data-jpa.
 *
 * The consuming application provides its own AuditorAware<String> bean named
 * "auditorAware", so this library does not impose an authentication model.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfiguration {
}
