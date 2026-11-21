package com.notshahrad.spring_boot_base.auth.phone;

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
@ConditionalOnProperty(prefix = "base.auth.phone", name = "enabled", havingValue = "true")
public class PhoneIdentityRepository {

    private static final RowMapper<PhoneIdentity> IDENTITY = (row, index) -> new PhoneIdentity(
            row.getObject("user_id", UUID.class),
            new PhoneNumber(row.getString("phone_number")),
            row.getObject("verified_at", OffsetDateTime.class).toInstant());

    private final JdbcTemplate jdbc;

    public PhoneIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PhoneIdentity> findByUserId(UUID userId) {
        return jdbc.query("""
                SELECT user_id, phone_number, verified_at FROM phone_login_identities WHERE user_id = ?
                """, IDENTITY, userId).stream().findFirst();
    }

    public Optional<PhoneIdentity> findVerified(PhoneNumber number) {
        return jdbc.query("""
                SELECT p.user_id, p.phone_number, p.verified_at
                FROM phone_login_identities p
                JOIN app_users u ON u.id = p.user_id
                WHERE p.phone_number = ? AND NOT u.deleted
                """, IDENTITY, number.e164()).stream().findFirst();
    }

    boolean bindVerified(UUID userId, PhoneNumber number, Instant verifiedAt) {
        PhoneNumber valid = PhoneNumber.parse(number.e164(), null);
        return jdbc.update("""
                INSERT INTO phone_login_identities (user_id, phone_number, verified_at)
                SELECT id, ?, ? FROM app_users WHERE id = ? AND NOT deleted
                """, valid.e164(), verifiedAt.atOffset(ZoneOffset.UTC), userId) == 1;
    }
}
