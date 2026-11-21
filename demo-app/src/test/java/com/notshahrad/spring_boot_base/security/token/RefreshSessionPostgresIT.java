package com.notshahrad.spring_boot_base.security.token;

import com.nimbusds.jwt.SignedJWT;
import com.notshahrad.spring_boot_base.auth.LocalUserPrincipal;
import com.notshahrad.spring_boot_base.auth.OidcIdentityService;
import com.notshahrad.spring_boot_base.auth.VerifiedProviderIdentity;
import com.notshahrad.spring_boot_base.auth.token.RefreshSessionService;
import com.notshahrad.spring_boot_base.auth.token.RefreshSessionService.TokenPair;
import com.notshahrad.spring_boot_base.support.AbstractPostgresIntegrationTest;
import com.notshahrad.spring_boot_base.support.TokenTestKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("auth-token")
class RefreshSessionPostgresIT extends AbstractPostgresIntegrationTest {

    @TempDir
    static Path keyDirectory;

    @Autowired
    private RefreshSessionService sessions;
    @Autowired
    private OidcIdentityService identities;
    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry registry) throws Exception {
        TokenTestKeys.register(registry, keyDirectory);
    }

    @Test
    void storesOnlyDigestsAndCascadesExpiredSessionCleanup() throws Exception {
        TokenPair pair = issueForNewAccount();
        UUID sessionId = sessionId(pair);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(pair.refreshToken().getBytes(StandardCharsets.US_ASCII)));
        assertEquals(digest, jdbc.queryForObject(
                "SELECT token_hash FROM refresh_credentials WHERE session_id = ?", String.class, sessionId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO refresh_credentials (token_hash, session_id, created_at)
                VALUES ('short', ?, CURRENT_TIMESTAMP)
                """, sessionId));

        jdbc.update("""
                UPDATE refresh_sessions
                SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 days',
                    expires_at = CURRENT_TIMESTAMP - INTERVAL '1 day'
                WHERE id = ?
                """, sessionId);
        assertTrue(sessions.refresh(pair.refreshToken()).isEmpty());
        assertTrue(sessions.purgeExpired() >= 1);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_credentials WHERE session_id = ?", Integer.class, sessionId));
    }

    @Test
    void concurrentExchangeSucceedsAtMostOnceAndCommitsReplayRevocation() throws Exception {
        TokenPair original = issueForNewAccount();
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        Callable<Optional<TokenPair>> exchange = () -> {
            ready.countDown();
            assertTrue(go.await(10, TimeUnit.SECONDS));
            return sessions.refresh(original.refreshToken());
        };
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(exchange);
            var second = workers.submit(exchange);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            var successful = results.stream().flatMap(Optional::stream).toList();
            assertEquals(1, successful.size());
            assertTrue(sessions.refresh(successful.getFirst().refreshToken()).isEmpty());
        }
        assertFalse(jdbc.queryForObject(
                "SELECT revoked_at IS NULL FROM refresh_sessions WHERE id = ?", Boolean.class, sessionId(original)));
    }

    private TokenPair issueForNewAccount() {
        UUID id = identities.resolveOrCreate(new VerifiedProviderIdentity(
                "https://postgres.issuer.example", UUID.randomUUID().toString(), null, false, null));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new LocalAccount(id), null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            return sessions.issue();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static UUID sessionId(TokenPair pair) throws Exception {
        return UUID.fromString(SignedJWT.parse(pair.accessToken()).getJWTClaimsSet().getStringClaim("sid"));
    }

    private record LocalAccount(UUID getLocalUserId) implements LocalUserPrincipal {
        @Override
        public String getName() {
            return getLocalUserId.toString();
        }
    }
}
