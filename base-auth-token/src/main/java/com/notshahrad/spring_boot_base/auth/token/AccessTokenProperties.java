package com.notshahrad.spring_boot_base.auth.token;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@ConfigurationProperties("base.auth.token")
record AccessTokenProperties(
        String issuer,
        String audience,
        @DefaultValue("5m") Duration ttl,
        String signingKeyId,
        Path privateKey,
        Path publicKey,
        List<PreviousKey> previousKeys,
        Set<String> allowedScopes) {

    static final Duration MAX_TTL = Duration.ofMinutes(15);
    static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    private static final Pattern SCOPE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");

    AccessTokenProperties {
        if (issuer == null || issuer.isBlank() || !issuer.equals(issuer.strip()) || issuer.contains("${")) {
            throw new IllegalArgumentException("Configure base.auth.token.issuer with an application issuer");
        }
        if (audience == null || audience.isBlank() || !audience.equals(audience.strip()) || audience.contains("${")) {
            throw new IllegalArgumentException("Configure base.auth.token.audience with an API audience");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(MAX_TTL) > 0 || ttl.getNano() != 0) {
            throw new IllegalArgumentException("Access token TTL must be a whole number of seconds from 1 to 900");
        }
        previousKeys = previousKeys == null ? List.of() : List.copyOf(previousKeys);
        allowedScopes = allowedScopes == null ? Set.of() : Set.copyOf(allowedScopes);
        if (allowedScopes.stream().anyMatch(scope -> !SCOPE_NAME.matcher(scope).matches())) {
            throw new IllegalArgumentException("Configure valid application scope names");
        }
    }

    record PreviousKey(String keyId, Path publicKey, Instant verifyUntil) {
    }
}
