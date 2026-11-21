package com.notshahrad.spring_boot_base.auth.phone;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(prefix = "base.auth.phone", name = {"enabled", "otp.enabled"}, havingValue = "true")
@EnableConfigurationProperties(PhoneOtpProperties.class)
public class PhoneOtpService {

    private static final String HMAC = "HmacSHA256";
    private static final Pattern CODE = Pattern.compile("[0-9]{6}");

    private final PhoneOtpStore store;
    private final PhoneOtpProperties settings;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public PhoneOtpService(JdbcTemplate jdbc, PhoneOtpProperties settings) {
        this.store = new PhoneOtpStore(jdbc);
        this.settings = settings;
        this.key = signingKey(settings.hmacKey());
    }

    public Optional<Delivery> issue(PhoneNumber number, Purpose purpose) {
        return issue(number, purpose, UUID.randomUUID());
    }

    Optional<Delivery> issue(PhoneNumber number, Purpose purpose, UUID challengeId) {
        var valid = PhoneNumber.parse(number.e164(), null);
        Instant now = Instant.now();
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        byte[] verifier = verifier(valid, purpose, challengeId, code);

        return store.issue(valid, purpose, challengeId, verifier, now, settings)
                .map(expiresAt -> new Delivery(challengeId, code, expiresAt));
    }

    @Transactional
    public boolean verify(PhoneNumber number, Purpose purpose, UUID challengeId, String code) {
        if (challengeId == null) {
            return false;
        }

        var challenge = store.lock(number, purpose, challengeId).orElse(null);
        if (challenge == null || challenge.consumed() || challenge.attempts() >= settings.maxAttempts()
                || !Instant.now().isBefore(challenge.expiresAt())) {
            return false;
        }

        boolean valid = code != null && CODE.matcher(code).matches()
                && MessageDigest.isEqual(challenge.verifier(), verifier(number, purpose, challengeId, code));
        store.recordAttempt(number, purpose, valid);
        return valid;
    }

    public int purgeExpired() {
        return store.purgeExpired(Instant.now());
    }

    private byte[] verifier(PhoneNumber number, Purpose purpose, UUID challengeId, String code) {
        String message = "phone-otp:v1:" + purpose + ":" + number.e164() + ":" + challengeId + ":" + code;
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(message.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Phone OTP verifier is unavailable", ex);
        }
    }

    private static SecretKeySpec signingKey(String value) {
        try {
            if (value != null) {
                byte[] bytes = Base64.getDecoder().decode(value);
                if (bytes.length >= 32) {
                    return new SecretKeySpec(bytes, HMAC);
                }
            }
        } catch (IllegalArgumentException ex) {
            throw invalidKey();
        }
        throw invalidKey();
    }

    private static IllegalArgumentException invalidKey() {
        return new IllegalArgumentException("Configure a base64 phone OTP HMAC key containing at least 32 bytes");
    }

    public enum Purpose {
        SIGNUP, LOGIN
    }

    public record Delivery(UUID challengeId, String code, Instant expiresAt) {
        @Override
        public String toString() {
            return "Delivery[redacted]";
        }
    }
}
