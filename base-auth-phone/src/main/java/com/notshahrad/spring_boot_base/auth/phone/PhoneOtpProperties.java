package com.notshahrad.spring_boot_base.auth.phone;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("base.auth.phone.otp")
record PhoneOtpProperties(
        String hmacKey,
        @DefaultValue("5m") Duration lifetime,
        @DefaultValue("1m") Duration resendCooldown,
        @DefaultValue("5") int maxAttempts) {

    PhoneOtpProperties {
        if (lifetime.compareTo(Duration.ofSeconds(1)) < 0 || lifetime.compareTo(Duration.ofMinutes(10)) > 0
                || resendCooldown.compareTo(Duration.ofSeconds(1)) < 0 || resendCooldown.compareTo(lifetime) >= 0
                || maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("Invalid phone OTP lifetime, cooldown, or attempt limit");
        }
    }

    @Override
    public String toString() {
        return "PhoneOtpProperties[redacted]";
    }
}
