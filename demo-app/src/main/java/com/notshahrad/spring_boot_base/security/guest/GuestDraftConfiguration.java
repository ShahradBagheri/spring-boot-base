package com.notshahrad.spring_boot_base.security.guest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "base.auth.guest", name = {"enabled", "drafts.enabled"}, havingValue = "true")
@EnableConfigurationProperties(GuestDraftConfiguration.Properties.class)
class GuestDraftConfiguration {

    @Bean
    GuestDraftService guestDraftService(JdbcTemplate jdbc) {
        return new GuestDraftService(jdbc);
    }

    @Bean
    ThreadPoolTaskScheduler guestDraftCleanupScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("guest-draft-cleanup-");
        return scheduler;
    }

    @Bean
    ApplicationRunner guestDraftCleanup(GuestDraftService drafts, Properties properties,
            @Qualifier("guestDraftCleanupScheduler") TaskScheduler scheduler) {
        return args -> scheduler.scheduleWithFixedDelay(() -> drafts.purgeExpired(properties.cleanupBatchSize()),
                scheduler.getClock().instant().plus(properties.cleanupInterval()), properties.cleanupInterval());
    }

    @ConfigurationProperties("base.auth.guest.drafts")
    record Properties(@DefaultValue("1m") Duration cleanupInterval, @DefaultValue("500") int cleanupBatchSize) {
        Properties {
            if (cleanupInterval.compareTo(Duration.ofSeconds(1)) < 0 || cleanupBatchSize < 1 || cleanupBatchSize > 10_000) {
                throw new IllegalArgumentException("Guest draft cleanup needs an interval of at least 1s and a batch of 1–10000");
            }
        }
    }
}
