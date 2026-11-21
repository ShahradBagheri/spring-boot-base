package com.notshahrad.spring_boot_base.auth.email;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

final class EmailPasswordStore {

    private final JdbcTemplate jdbc;

    EmailPasswordStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    boolean isAddressReserved(LoginEmail address) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM email_login_identities WHERE address = ? AND verified_at IS NOT NULL)
                """, Boolean.class, address.value()));
    }

    Optional<Credential> findCredential(LoginEmail address) {
        return jdbc.query("""
                SELECT p.user_id, p.password_hash, p.credential_version
                FROM email_passwords p
                JOIN email_login_identities e ON e.user_id = p.user_id
                JOIN app_users u ON u.id = p.user_id
                WHERE e.address = ? AND e.verified_at IS NOT NULL AND NOT u.deleted
                """, (row, index) -> new Credential(row.getObject("user_id", UUID.class),
                row.getString("password_hash"), row.getLong("credential_version")), address.value()).stream().findFirst();
    }

    boolean isCurrent(EmailPasswordPrincipal principal) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM email_passwords p
                    JOIN app_users u ON u.id = p.user_id
                    WHERE p.user_id = ? AND p.credential_version = ? AND NOT u.deleted
                )
                """, Boolean.class, principal.userId(), principal.credentialVersion()));
    }

    boolean lockActiveAccount(UUID userId) {
        return !jdbc.queryForList("SELECT id FROM app_users WHERE id = ? AND NOT deleted FOR UPDATE",
                UUID.class, userId).isEmpty();
    }

    void insertPassword(UUID userId, String hash) {
        jdbc.update("""
                INSERT INTO email_passwords (user_id, password_hash, credential_version) VALUES (?, ?, 1)
                """, userId, hash);
    }

    void replacePassword(UUID userId, String hash) {
        jdbc.update("""
                UPDATE email_passwords SET password_hash = ?, credential_version = credential_version + 1
                WHERE user_id = ?
                """, hash, userId);
    }

    boolean issueChallenge(Purpose purpose, LoginEmail address, String digest, Credential owner,
                           Instant now, Instant resendAfter, Instant expiresAt) {
        return jdbc.update("""
                INSERT INTO email_auth_challenges
                    (purpose, address, token_hash, owner_id, credential_version, resend_after, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (purpose, address) DO UPDATE SET
                    token_hash = EXCLUDED.token_hash, owner_id = EXCLUDED.owner_id,
                    credential_version = EXCLUDED.credential_version, resend_after = EXCLUDED.resend_after,
                    expires_at = EXCLUDED.expires_at
                WHERE email_auth_challenges.resend_after <= ?
                """, purpose.name(), address.value(), digest, owner == null ? null : owner.userId(),
                owner == null ? null : owner.version(), resendAfter.atOffset(ZoneOffset.UTC),
                expiresAt.atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC)) == 1;
    }

    Optional<Challenge> lockChallenge(Purpose purpose, String digest, Instant now) {
        return jdbc.query("""
                SELECT address, owner_id, credential_version, expires_at FROM email_auth_challenges
                WHERE purpose = ? AND token_hash = ? AND expires_at > ? FOR UPDATE
                """, (row, index) -> new Challenge(new LoginEmail(row.getString("address")),
                row.getObject("owner_id", UUID.class), row.getLong("credential_version"),
                row.getObject("expires_at", OffsetDateTime.class).toInstant()),
                purpose.name(), digest, now.atOffset(ZoneOffset.UTC)).stream()
                .filter(challenge -> Instant.now().isBefore(challenge.expiresAt()))
                .findFirst();
    }

    void consumeChallenge(String digest) {
        jdbc.update("DELETE FROM email_auth_challenges WHERE token_hash = ?", digest);
    }

    int purgeExpired(Instant now, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Cleanup batch size must be positive");
        }
        return jdbc.update("""
                WITH expired AS (
                    SELECT token_hash FROM email_auth_challenges
                    WHERE expires_at <= ?
                    ORDER BY expires_at, token_hash LIMIT ? FOR UPDATE SKIP LOCKED
                )
                DELETE FROM email_auth_challenges t USING expired e WHERE t.token_hash = e.token_hash
                """, now.atOffset(ZoneOffset.UTC), batchSize);
    }

    enum Purpose { SIGNUP, RESET }

    record Credential(UUID userId, String passwordHash, long version) {
        @Override
        public String toString() {
            return "Credential[redacted]";
        }
    }

    record Challenge(LoginEmail address, UUID ownerId, long version, Instant expiresAt) {
    }
}
