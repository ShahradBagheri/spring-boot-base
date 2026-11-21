package com.notshahrad.spring_boot_base.security.phone;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.notshahrad.spring_boot_base.auth.phone.PhoneNumber;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

final class PhoneRateLimits {

    private final Cache<String, AtomicInteger> requests;
    private final Cache<String, AtomicInteger> verifications;
    private final Cache<String, AtomicInteger> overall;
    private final Properties settings;

    PhoneRateLimits(Properties settings) {
        this.settings = settings;
        requests = cache(settings.window());
        verifications = cache(settings.window());
        overall = Caffeine.newBuilder().expireAfterWrite(settings.window()).build();
    }

    boolean request(String ip, PhoneNumber phone) {
        return allow(requests, ip, phone, settings.requests(), "requests");
    }

    boolean verify(String ip, PhoneNumber phone) {
        return allow(verifications, ip, phone, settings.verifications(), "verifications");
    }

    private boolean allow(Cache<String, AtomicInteger> counters, String ip, PhoneNumber phone, Budget budget,
                          String operation) {
        return take(counters, "ip:" + ip, budget.perIp())
                && (phone == null || take(counters, "phone:" + phone.e164(), budget.perPhone()))
                && take(overall, operation, budget.overall());
    }

    private static boolean take(Cache<String, AtomicInteger> counters, String key, int limit) {
        return counters.get(key, ignored -> new AtomicInteger())
                .getAndUpdate(count -> count < limit ? count + 1 : count) < limit;
    }

    private static Cache<String, AtomicInteger> cache(Duration window) {
        return Caffeine.newBuilder().expireAfterWrite(window).maximumSize(50_000).build();
    }

    @ConfigurationProperties("base.auth.phone.rate")
    record Properties(@DefaultValue("1m") Duration window, @DefaultValue Budget requests,
                      @DefaultValue Budget verifications) {
        Properties {
            if (window.compareTo(Duration.ofSeconds(1)) < 0 || window.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalArgumentException("Phone rate window must be between one second and one hour");
            }
        }
    }

    record Budget(@DefaultValue("3") int perPhone, @DefaultValue("20") int perIp,
                  @DefaultValue("200") int overall) {
        Budget {
            if (perPhone < 1 || perIp < 1 || overall < 1) {
                throw new IllegalArgumentException("Phone rate limits must be positive");
            }
        }
    }
}
