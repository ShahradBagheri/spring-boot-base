package com.notshahrad.spring_boot_base.config;

import com.notshahrad.spring_boot_base.auth.email.EmailPasswordService;
import com.notshahrad.spring_boot_base.auth.phone.PhoneOtpService;
import com.notshahrad.spring_boot_base.auth.token.RefreshSessionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "base.auth.cleanup.enabled", havingValue = "true")
@EnableConfigurationProperties(AuthCleanupConfiguration.Settings.class)
class AuthCleanupConfiguration {

    @ConfigurationProperties("base.auth.cleanup")
    record Settings(@DefaultValue("5m") Duration interval, @DefaultValue("500") int batchSize) {
        Settings {
            if (interval.compareTo(Duration.ofSeconds(1)) < 0 || batchSize < 1 || batchSize > 10_000) {
                throw new IllegalArgumentException("Use an interval of at least 1s and a batch size between 1 and 10000");
            }
        }
    }

    @Bean
    ThreadPoolTaskScheduler authCleanupScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("auth-cleanup-");
        return scheduler;
    }

    @Bean
    ApplicationRunner scheduleAuthCleanup(@Qualifier("authCleanupScheduler") TaskScheduler scheduler, Settings settings,
            ObjectProvider<EmailPasswordService> email, ObjectProvider<PhoneOtpService> phone,
            ObjectProvider<RefreshSessionService> tokens) {
        return args -> {
            var firstRun = scheduler.getClock().instant().plus(settings.interval());
            email.ifAvailable(service -> scheduler.scheduleWithFixedDelay(
                    () -> service.purgeExpiredChallenges(settings.batchSize()), firstRun, settings.interval()));
            phone.ifAvailable(service -> scheduler.scheduleWithFixedDelay(
                    () -> service.purgeExpired(settings.batchSize()), firstRun, settings.interval()));
            tokens.ifAvailable(service -> scheduler.scheduleWithFixedDelay(
                    () -> service.purgeExpired(settings.batchSize()), firstRun, settings.interval()));
        };
    }
}
