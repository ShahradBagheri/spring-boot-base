package com.notshahrad.spring_boot_base.auth.token;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.notshahrad.spring_boot_base.auth.token.AccessTokenIssuer.ACCESS;
import static com.notshahrad.spring_boot_base.auth.token.AccessTokenIssuer.SCOPE;
import static com.notshahrad.spring_boot_base.auth.token.AccessTokenIssuer.SESSION_ID;
import static com.notshahrad.spring_boot_base.auth.token.AccessTokenIssuer.TOKEN_TYPE;
import static com.notshahrad.spring_boot_base.auth.token.AccessTokenIssuer.TOKEN_USE;

final class AccessTokenDecoder {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error("invalid_token");

    private AccessTokenDecoder() {
    }

    static JwtDecoder create(AccessTokenProperties properties, AccessTokenKeys keys, Clock clock) {
        var processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType(TOKEN_TYPE)));
        processor.setJWSKeySelector((header, context) -> {
            if (!JWSAlgorithm.RS256.equals(header.getAlgorithm())
                    || header.getJWKURL() != null || header.getJWK() != null || header.getX509CertURL() != null) {
                return List.of();
            }
            var key = keys.verificationKey(header.getKeyID(), clock.instant());
            return key == null ? List.of() : List.of(key);
        });
        processor.setJWTClaimsSetVerifier((claims, context) -> {});
        var decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(jwt -> validate(jwt, properties, clock));
        return decoder;
    }

    private static OAuth2TokenValidatorResult validate(Jwt jwt, AccessTokenProperties properties, Clock clock) {
        var claims = jwt.getClaims();
        if (!properties.issuer().equals(claims.get("iss"))
                || !List.of(properties.audience()).equals(claims.get("aud"))
                || !ACCESS.equals(claims.get(TOKEN_USE))
                || !canonicalUuid(claims.get("sub"))
                || !canonicalUuid(claims.get("jti"))
                || (claims.containsKey(SESSION_ID) && !canonicalUuid(claims.get(SESSION_ID)))) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }

        var issuedAt = jwt.getIssuedAt();
        var notBefore = jwt.getNotBefore();
        var expiresAt = jwt.getExpiresAt();
        var now = clock.instant();
        if (issuedAt == null || !issuedAt.equals(notBefore) || expiresAt == null
                || !expiresAt.isAfter(issuedAt)
                || expiresAt.isAfter(issuedAt.plus(AccessTokenProperties.MAX_TTL))
                || issuedAt.isAfter(now.plus(AccessTokenProperties.CLOCK_SKEW))
                || !expiresAt.isAfter(now.minus(AccessTokenProperties.CLOCK_SKEW))) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }

        Object scopes = claims.get(SCOPE);
        if (claims.containsKey(SCOPE) && (!(scopes instanceof String scope)
                || !Arrays.stream(scope.split(" ", -1)).allMatch(properties.allowedScopes()::contains))) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static boolean canonicalUuid(Object value) {
        if (!(value instanceof String string) || string.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(string).toString().equals(string);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
