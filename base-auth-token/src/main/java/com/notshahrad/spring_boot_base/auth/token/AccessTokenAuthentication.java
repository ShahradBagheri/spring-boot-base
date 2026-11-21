package com.notshahrad.spring_boot_base.auth.token;

import com.notshahrad.spring_boot_base.auth.LocalUserPrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

final class AccessTokenAuthentication extends JwtAuthenticationToken implements LocalUserPrincipal {

    AccessTokenAuthentication(Jwt jwt, AbstractAuthenticationToken authentication) {
        super(jwt, authentication.getPrincipal(), authentication.getAuthorities());
    }

    @Override
    public UUID getLocalUserId() {
        return userId();
    }

    UUID userId() {
        return UUID.fromString(getToken().getSubject());
    }

    UUID sessionId() {
        String claim = getToken().getClaimAsString(AccessTokenIssuer.SESSION_ID);
        return claim == null ? null : UUID.fromString(claim);
    }
}
