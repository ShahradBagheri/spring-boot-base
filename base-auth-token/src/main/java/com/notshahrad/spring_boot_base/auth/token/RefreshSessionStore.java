package com.notshahrad.spring_boot_base.auth.token;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

final class RefreshSessionStore {

    private final JdbcTemplate jdbc;

    RefreshSessionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    boolean lockActiveUser(UUID userId) {
        return !jdbc.queryForList(
                "SELECT id FROM app_users WHERE id = ? AND deleted = FALSE FOR UPDATE", UUID.class, userId).isEmpty();
    }

    Optional<SessionOwner> findOwner(String hash) {
        return jdbc.query("""
                SELECT s.user_id, s.id FROM refresh_sessions s
                JOIN refresh_credentials c ON c.session_id = s.id
                WHERE c.token_hash = ?
                """, (row, index) -> new SessionOwner(row.getObject("user_id", UUID.class),
                row.getObject("id", UUID.class)), hash).stream().findFirst();
    }

    Optional<SessionState> lockSession(UUID sessionId) {
        return jdbc.query("""
                SELECT expires_at, revoked_at FROM refresh_sessions WHERE id = ? FOR UPDATE
                """, (row, index) -> new SessionState(row.getObject("expires_at", OffsetDateTime.class).toInstant(),
                row.getObject("revoked_at") != null), sessionId).stream().findFirst();
    }

    void createSession(UUID sessionId, UUID userId, Instant now, Instant expiresAt) {
        jdbc.update("INSERT INTO refresh_sessions (id, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
                sessionId, userId, utc(now), utc(expiresAt));
    }

    void addCredential(UUID sessionId, String hash, Instant now) {
        jdbc.update("INSERT INTO refresh_credentials (token_hash, session_id, created_at) VALUES (?, ?, ?)",
                hash, sessionId, utc(now));
    }

    boolean consume(UUID sessionId, String hash, Instant now) {
        return jdbc.update("""
                UPDATE refresh_credentials SET consumed_at = ?
                WHERE session_id = ? AND token_hash = ? AND consumed_at IS NULL
                """, utc(now), sessionId, hash) == 1;
    }

    void recordRotation(UUID sessionId, Instant now) {
        jdbc.update("UPDATE refresh_sessions SET rotated_at = ? WHERE id = ?", utc(now), sessionId);
    }

    void revoke(UUID userId, UUID sessionId, Instant now) {
        jdbc.update("""
                UPDATE refresh_sessions SET revoked_at = ?
                WHERE user_id = ? AND id = ? AND revoked_at IS NULL AND expires_at > ?
                """, utc(now), userId, sessionId, utc(now));
    }

    void revokeAll(UUID userId, Instant now) {
        jdbc.update("""
                UPDATE refresh_sessions SET revoked_at = ?
                WHERE user_id = ? AND revoked_at IS NULL AND expires_at > ?
                """, utc(now), userId, utc(now));
    }

    int purgeExpired(Instant now, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Cleanup batch size must be positive");
        }
        return jdbc.update("""
                WITH expired AS (
                    SELECT id FROM refresh_sessions
                    WHERE expires_at <= ?
                    ORDER BY expires_at, id LIMIT ? FOR UPDATE SKIP LOCKED
                )
                DELETE FROM refresh_sessions t USING expired e WHERE t.id = e.id
                """, utc(now), batchSize);
    }

    private static OffsetDateTime utc(Instant time) {
        return time.atOffset(ZoneOffset.UTC);
    }

    record SessionOwner(UUID userId, UUID sessionId) {
    }

    record SessionState(Instant expiresAt, boolean revoked) {
    }
}
