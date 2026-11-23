package com.notshahrad.base.data.jpa.autoconfigure;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({EntityManagerFactory.class, EnableJpaAuditing.class})
@ConditionalOnBean(EntityManagerFactory.class)
@ConditionalOnProperty(prefix = "base.data.jpa.auditing", name = "enabled", havingValue = "true")
@ConditionalOnMissingBean(name = "jpaAuditingHandler")
@EnableJpaAuditing
public class BaseJpaAuditingAutoConfiguration {
}
