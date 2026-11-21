package com.notshahrad.spring_boot_base.auth.phone;

import com.notshahrad.spring_boot_base.auth.phone.PhoneOtpService.Purpose;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

final class PhoneOtpStore {

    private final JdbcTemplate jdbc;

    PhoneOtpStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Instant> issue(PhoneNumber phone, Purpose purpose, UUID id, byte[] verifier, Instant now,
                            PhoneOtpProperties settings) {
        return jdbc.query("""
                INSERT INTO phone_otp_challenges
                    (phone_number, purpose, challenge_id, verifier, expires_at, resend_after, issued_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (phone_number, purpose) DO UPDATE SET
                    challenge_id = EXCLUDED.challenge_id,
                    verifier = EXCLUDED.verifier,
                    issued_at = EXCLUDED.issued_at,
                    resend_after = EXCLUDED.resend_after,
                    consumed = FALSE,
                    expires_at = CASE WHEN phone_otp_challenges.expires_at <= EXCLUDED.issued_at
                        THEN EXCLUDED.expires_at ELSE phone_otp_challenges.expires_at END,
                    failed_attempts = CASE WHEN phone_otp_challenges.expires_at <= EXCLUDED.issued_at
                        THEN 0 ELSE phone_otp_challenges.failed_attempts END
                WHERE phone_otp_challenges.resend_after <= EXCLUDED.issued_at
                  AND (phone_otp_challenges.expires_at <= EXCLUDED.issued_at
                       OR (NOT phone_otp_challenges.consumed AND phone_otp_challenges.failed_attempts < ?))
                RETURNING expires_at
                """, (row, index) -> row.getObject("expires_at", OffsetDateTime.class).toInstant(),
                phone.e164(), purpose.name(), id, verifier, utc(now.plus(settings.lifetime())),
                utc(now.plus(settings.resendCooldown())), utc(now), settings.maxAttempts()).stream().findFirst();
    }

    Optional<Challenge> lock(PhoneNumber phone, Purpose purpose, UUID id) {
        return jdbc.query("""
                SELECT verifier, expires_at, failed_attempts, consumed FROM phone_otp_challenges
                WHERE phone_number = ? AND purpose = ? AND challenge_id = ? FOR UPDATE
                """, (row, index) -> new Challenge(row.getBytes("verifier"),
                row.getObject("expires_at", OffsetDateTime.class).toInstant(), row.getInt("failed_attempts"),
                row.getBoolean("consumed")), phone.e164(), purpose.name(), id).stream().findFirst();
    }

    void recordAttempt(PhoneNumber phone, Purpose purpose, boolean successful) {
        if (successful) {
            jdbc.update("UPDATE phone_otp_challenges SET consumed = TRUE WHERE phone_number = ? AND purpose = ?",
                    phone.e164(), purpose.name());
        } else {
            jdbc.update("""
                    UPDATE phone_otp_challenges SET failed_attempts = failed_attempts + 1
                    WHERE phone_number = ? AND purpose = ?
                    """, phone.e164(), purpose.name());
        }
    }

    int purgeExpired(Instant now, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Cleanup batch size must be positive");
        }
        return jdbc.update("""
                WITH expired AS (
                    SELECT challenge_id FROM phone_otp_challenges
                    WHERE expires_at <= ? AND resend_after <= ?
                    ORDER BY expires_at, challenge_id LIMIT ? FOR UPDATE SKIP LOCKED
                )
                DELETE FROM phone_otp_challenges t USING expired e WHERE t.challenge_id = e.challenge_id
                """, utc(now), utc(now), batchSize);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    record Challenge(byte[] verifier, Instant expiresAt, int attempts, boolean consumed) {
    }
}
