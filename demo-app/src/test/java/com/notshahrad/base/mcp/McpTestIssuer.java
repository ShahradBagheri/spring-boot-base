package com.notshahrad.base.mcp;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.net.URI;
import java.time.Instant;
import java.util.Date;

final class McpTestIssuer {

    static final String ISSUER = "https://issuer.example";
    static final String RESOURCE = "https://api.example/mcp";
    static final String SCOPE = "mcp:access";

    private static final RSAKey KEY = signingKey();

    private McpTestIssuer() {
    }

    static JwtDecoder decoder() throws JOSEException {
        var settings = new McpSecurityAutoConfiguration.Properties(URI.create(ISSUER), URI.create(RESOURCE), 120, 1200);
        return McpSecurityAutoConfiguration.withValidation(
                NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build(), settings);
    }

    static String issue(String subject, String issuer, String audience, String scope, Instant expiry) throws JOSEException {
        var claims = new JWTClaimsSet.Builder().subject(subject).issuer(issuer).audience(audience).claim("scope", scope);
        if (expiry != null) {
            claims.expirationTime(Date.from(expiry));
        }
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(KEY));
        return jwt.serialize();
    }

    private static RSAKey signingKey() {
        try {
            return new RSAKeyGenerator(2048).generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to create an MCP test key", ex);
        }
    }
}
