package com.notshahrad.spring_boot_base.auth.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "base.auth.email", name = "enabled", havingValue = "true")
public class EmailIdentityRepository {

    private static final RowMapper<EmailIdentity> IDENTITY = (row, index) -> {
        var verifiedAt = row.getObject("verified_at", OffsetDateTime.class);
        return new EmailIdentity(row.getObject("user_id", UUID.class),
                new LoginEmail(row.getString("address")), verifiedAt == null ? null : verifiedAt.toInstant());
    };

    private final JdbcTemplate jdbc;

    public EmailIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<EmailIdentity> findByUserId(UUID userId) {
        return jdbc.query("""
                SELECT user_id, address, verified_at FROM email_login_identities WHERE user_id = ?
                """, IDENTITY, userId).stream().findFirst();
    }

    public Optional<EmailIdentity> findVerified(LoginEmail address) {
        return jdbc.query("""
                SELECT e.user_id, e.address, e.verified_at
                FROM email_login_identities e
                JOIN app_users u ON u.id = e.user_id
                WHERE e.address = ? AND e.verified_at IS NOT NULL AND NOT u.deleted
                """, IDENTITY, address.value()).stream().findFirst();
    }

    public boolean stagePending(UUID userId, LoginEmail address) {
        return jdbc.update("""
                INSERT INTO email_login_identities (user_id, address)
                SELECT id, ? FROM app_users WHERE id = ? AND NOT deleted
                ON CONFLICT (user_id) DO UPDATE SET address = EXCLUDED.address
                WHERE email_login_identities.verified_at IS NULL
                """, address.value(), userId) == 1;
    }

    boolean markVerified(UUID userId, LoginEmail verifiedAddress, Instant verifiedAt) {
        return jdbc.update("""
                UPDATE email_login_identities e SET verified_at = ?
                FROM app_users u
                WHERE e.user_id = u.id AND NOT u.deleted
                  AND e.user_id = ? AND e.address = ? AND e.verified_at IS NULL
                """, verifiedAt.atOffset(ZoneOffset.UTC), userId, verifiedAddress.value()) == 1;
    }
}
