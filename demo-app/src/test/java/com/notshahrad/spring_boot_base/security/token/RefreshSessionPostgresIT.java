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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "logging.level.org.springframework.jdbc.core.JdbcTemplate=DEBUG",
        "logging.level.org.springframework.jdbc.core.StatementCreatorUtils=TRACE"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
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
    @Autowired
    private MockMvc http;

    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry registry) throws Exception {
        TokenTestKeys.register(registry, keyDirectory);
    }

    @Test
    void storesOnlyDigestsAndCascadesExpiredSessionCleanup() throws Exception {
        TokenPair pair = issueForNewAccount();
        UUID sessionId = sessionId(pair);
        String digest = digest(pair.refreshToken());
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
    void flywayEnforcesOneUnusedCredentialAndIndexesSessionLookups() throws Exception {
        TokenPair initial = issueForNewAccount();
        UUID sessionId = sessionId(initial);
        String secondHash = digest(UUID.randomUUID().toString());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO refresh_credentials (token_hash, session_id, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                """, secondHash, sessionId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO refresh_credentials (token_hash, session_id, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                """, secondHash, UUID.randomUUID()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO refresh_credentials (token_hash, session_id, created_at, consumed_at)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "g".repeat(64), sessionId));

        var indexNames = jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = current_schema() AND tablename IN ('refresh_sessions', 'refresh_credentials')
                """, String.class);
        assertTrue(indexNames.containsAll(Set.of("ix_refresh_sessions_user", "ix_refresh_sessions_expiry",
                "ix_refresh_credentials_session", "uq_refresh_credentials_unused_session")));
    }

    @Test
    void rotationPersistsAndLogsOnlyCredentialHashes(CapturedOutput output) throws Exception {
        var original = issueForNewAccount();
        var replacement = sessions.refresh(original.refreshToken()).orElseThrow();
        String originalHash = digest(original.refreshToken());
        String replacementHash = digest(replacement.refreshToken());
        assertEquals(Set.of(originalHash, replacementHash), Set.copyOf(jdbc.queryForList("""
                SELECT token_hash FROM refresh_credentials WHERE session_id = ?
                """, String.class, sessionId(original))));
        assertEquals(List.of(replacementHash), jdbc.queryForList("""
                SELECT token_hash FROM refresh_credentials WHERE session_id = ? AND consumed_at IS NULL
                """, String.class, sessionId(original)));

        assertTrue(output.getAll().contains(originalHash) && output.getAll().contains(replacementHash),
                "Test should exercise JDBC bind-parameter logging");
        assertFalse(output.getAll().contains(original.refreshToken())
                || output.getAll().contains(replacement.refreshToken()), "A plaintext refresh credential reached the logs");
        assertFalse(replacement.toString().contains(replacement.refreshToken()), "TokenPair must redact credentials");
    }

    @Test
    void revocationCommitsInPostgresAndIsLimitedToTheAuthenticatedOwner() throws Exception {
        var owner = newAccount();
        var first = issueFor(owner);
        var second = issueFor(owner);
        var third = issueFor(owner);
        var unrelated = issueForNewAccount();

        logout("/api/token/logout", first);
        assertTrue(sessions.refresh(first.refreshToken()).isEmpty());
        var rotated = sessions.refresh(second.refreshToken()).orElseThrow();
        logout("/api/token/logout-all", rotated);
        assertTrue(sessions.refresh(rotated.refreshToken()).isEmpty());
        assertTrue(sessions.refresh(third.refreshToken()).isEmpty());
        assertTrue(sessions.refresh(unrelated.refreshToken()).isPresent());

        assertEquals(3, jdbc.queryForObject("""
                SELECT COUNT(*) FROM refresh_sessions WHERE user_id = ? AND revoked_at IS NOT NULL
                """, Integer.class, owner.getLocalUserId()));
    }

    private void logout(String route, TokenPair auth) throws Exception {
        http.perform(post(route).header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.accessToken()))
                .andExpect(status().isNoContent());
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
        return issueFor(newAccount());
    }

    private LocalAccount newAccount() {
        return new LocalAccount(identities.resolveOrCreate(new VerifiedProviderIdentity(
                "https://postgres.issuer.example", UUID.randomUUID().toString(), null, false, null)));
    }

    private TokenPair issueFor(LocalAccount account) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(account, null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            return sessions.issue();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static String digest(String credential) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(credential.getBytes(StandardCharsets.US_ASCII)));
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
