package com.notshahrad.spring_boot_base.auth.token;

import com.notshahrad.spring_boot_base.auth.LocalUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AccessTokenIssuer {

    static final String TOKEN_TYPE = "at+jwt";
    static final String TOKEN_USE = "token_use";
    static final String ACCESS = "access";
    static final String SCOPE = "scope";
    static final String SESSION_ID = "sid";
    private static final String APPLICATION_SCOPE_PREFIX = "APP_SCOPE_";

    private final AccessTokenProperties properties;
    private final AccessTokenKeys keys;
    private final Clock clock;
    private final AuthenticationTrustResolver trust = new AuthenticationTrustResolverImpl();

    AccessTokenIssuer(AccessTokenProperties properties, AccessTokenKeys keys, Clock clock) {
        this.properties = properties;
        this.keys = keys;
        this.clock = clock;
    }

    public Jwt issue() {
        var local = requireLocalAuthentication();
        return sign(local.userId(), null, local.authorities());
    }

    LocalAuthentication requireLocalAuthentication() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!trust.isFullyAuthenticated(authentication)
                || authentication instanceof AbstractOAuth2TokenAuthenticationToken<?>
                || !(authentication.getPrincipal() instanceof LocalUserPrincipal localUser)
                || localUser.getLocalUserId() == null) {
            throw new AccessDeniedException("Access token issuance requires an authenticated local account");
        }

        return new LocalAuthentication(localUser, authentication.getAuthorities());
    }

    Jwt sign(UUID userId, UUID sessionId, Collection<? extends GrantedAuthority> authorities) {
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var headers = JwsHeader.with(SignatureAlgorithm.RS256)
                .type(TOKEN_TYPE)
                .keyId(properties.signingKeyId())
                .build();
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(userId.toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(properties.ttl()))
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_USE, ACCESS);

        if (sessionId != null) {
            claims.claim(SESSION_ID, sessionId.toString());
        }
        var scopes = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .filter(authority -> authority.startsWith(APPLICATION_SCOPE_PREFIX))
                .map(authority -> authority.substring(APPLICATION_SCOPE_PREFIX.length()))
                .filter(properties.allowedScopes()::contains)
                .distinct()
                .sorted()
                .toList();
        if (!scopes.isEmpty()) {
            claims.claim(SCOPE, String.join(" ", scopes));
        }
        return keys.encoder().encode(JwtEncoderParameters.from(headers, claims.build()));
    }

    record LocalAuthentication(LocalUserPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        UUID userId() {
            return principal.getLocalUserId();
        }
    }
}
