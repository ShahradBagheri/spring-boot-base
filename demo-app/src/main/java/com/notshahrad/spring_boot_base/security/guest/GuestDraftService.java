package com.notshahrad.spring_boot_base.security.guest;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

final class GuestDraftService {

    private final JdbcTemplate jdbc;

    GuestDraftService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<String> guestDraft(UUID guestId) {
        return jdbc.query("""
                SELECT content FROM guest_drafts WHERE id = ? AND account_id IS NULL AND expires_at > clock_timestamp()
                """, (row, index) -> row.getString("content"), guestId).stream().findFirst();
    }

    boolean save(GuestSessions.Identity guest, String content) {
        var expiration = guest.expiresAt().atOffset(ZoneOffset.UTC);
        return jdbc.update("""
                INSERT INTO guest_drafts (id, expires_at, content)
                SELECT ?, ?, ? WHERE CAST(? AS TIMESTAMP WITH TIME ZONE) > clock_timestamp()
                ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content
                WHERE guest_drafts.account_id IS NULL AND guest_drafts.expires_at > clock_timestamp()
                """, guest.guestId(), expiration, content, expiration) == 1;
    }

    Optional<String> accountDraft(UUID accountId) {
        return jdbc.query("""
                SELECT d.content FROM guest_drafts d
                JOIN app_users u ON u.id = d.account_id AND NOT u.deleted
                WHERE d.account_id = ?
                """, (row, index) -> row.getString("content"), accountId).stream().findFirst();
    }

    Claim claim(UUID guestId, UUID accountId) {
        try {
            int moved = jdbc.update("""
                    UPDATE guest_drafts SET account_id = ?, expires_at = NULL
                    WHERE id = ? AND account_id IS NULL AND expires_at > clock_timestamp()
                      AND EXISTS (SELECT 1 FROM app_users WHERE id = ? AND NOT deleted)
                    """, accountId, guestId, accountId);
            return moved == 1 ? Claim.MOVED : Claim.ABSENT;
        } catch (DuplicateKeyException ex) {
            return Claim.CONFLICT;
        }
    }

    int purgeExpired(int batchSize) {
        return jdbc.update("""
                WITH expired AS (
                    SELECT id FROM guest_drafts
                    WHERE account_id IS NULL AND expires_at <= statement_timestamp()
                    ORDER BY expires_at, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                DELETE FROM guest_drafts d USING expired e WHERE d.id = e.id AND d.account_id IS NULL
                """, batchSize);
    }

    enum Claim {
        MOVED, ABSENT, CONFLICT
    }
}
