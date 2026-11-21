package com.notshahrad.spring_boot_base.auth.token;

import com.notshahrad.spring_boot_base.auth.AccountRefreshSessionRevoker;
import com.notshahrad.spring_boot_base.auth.AccountSessionValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
public class RefreshSessionService implements AccountRefreshSessionRevoker {

    private static final Duration SESSION_LIFETIME = Duration.ofDays(30);
    private static final Pattern CREDENTIAL = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final RefreshSessionStore store;
    private final AccessTokenIssuer issuer;
    private final Clock clock;
    private final ObjectProvider<AccountSessionValidator> validators;
    private final SecureRandom random = new SecureRandom();

    RefreshSessionService(RefreshSessionStore store, AccessTokenIssuer issuer, Clock clock,
                          ObjectProvider<AccountSessionValidator> validators) {
        this.store = store;
        this.issuer = issuer;
        this.clock = clock;
        this.validators = validators;
    }

    public TokenPair issue() {
        var local = issuer.requireLocalAuthentication();
        if (!store.lockActiveUser(local.userId())
                || !validators.stream().allMatch(validator -> validator.isCurrent(local.principal()))) {
            throw new AccessDeniedException("Token issuance requires an active local account");
        }

        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var sessionId = UUID.randomUUID();
        var expiresAt = now.plus(SESSION_LIFETIME);
        store.createSession(sessionId, local.userId(), now, expiresAt);
        return mint(local.userId(), sessionId, now, expiresAt, local.authorities());
    }

    public Optional<TokenPair> refresh(String credential) {
        if (credential == null || !CREDENTIAL.matcher(credential).matches()) {
            return Optional.empty();
        }
        String hash = hash(credential);
        var match = store.findOwner(hash);
        if (match.isEmpty() || !store.lockActiveUser(match.get().userId())) {
            return Optional.empty();
        }

        var owner = match.get();
        var session = store.lockSession(owner.sessionId());
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (session.isEmpty() || session.get().revoked() || !now.isBefore(session.get().expiresAt())) {
            return Optional.empty();
        }
        if (!store.consume(owner.sessionId(), hash, now)) {
            store.revoke(owner.userId(), owner.sessionId(), now);
            return Optional.empty();
        }

        store.recordRotation(owner.sessionId(), now);
        return Optional.of(mint(owner.userId(), owner.sessionId(), now, session.get().expiresAt(), List.of()));
    }

    public void revokeCurrent() {
        var bearer = requireBearer();
        UUID sessionId = bearer.sessionId();
        if (sessionId != null && store.lockActiveUser(bearer.userId())) {
            store.revoke(bearer.userId(), sessionId, clock.instant());
        }
    }

    public void revokeAll() {
        var bearer = requireBearer();
        if (store.lockActiveUser(bearer.userId())) {
            store.revokeAll(bearer.userId(), clock.instant());
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeForAccount(UUID userId) {
        if (store.lockActiveUser(userId)) {
            store.revokeAll(userId, clock.instant());
        }
    }

    public int purgeExpired() {
        var cutoff = clock.instant();
        int total = 0;
        int removed;
        do {
            removed = store.purgeExpired(cutoff, 500);
            total += removed;
        } while (removed == 500);
        return total;
    }

    public int purgeExpired(int batchSize) {
        return store.purgeExpired(clock.instant(), batchSize);
    }

    private TokenPair mint(UUID userId, UUID sessionId, Instant now, Instant sessionExpiry,
                           Collection<? extends GrantedAuthority> authorities) {
        byte[] entropy = new byte[32];
        random.nextBytes(entropy);
        var refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        var accessToken = issuer.sign(userId, sessionId, authorities);
        store.addCredential(sessionId, hash(refreshToken), now);
        return new TokenPair(accessToken.getTokenValue(), accessToken.getExpiresAt(), refreshToken, sessionExpiry);
    }

    private static AccessTokenAuthentication requireBearer() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof AccessTokenAuthentication bearer
                && bearer.isAuthenticated()) {
            return bearer;
        }
        throw new AccessDeniedException("Revocation requires an authenticated access token");
    }

    private static String hash(String credential) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(credential.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record TokenPair(String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt) {
        @Override
        public String toString() {
            return "TokenPair[accessExpiresAt=" + accessExpiresAt + ", refreshExpiresAt=" + refreshExpiresAt + "]";
        }
    }
}
