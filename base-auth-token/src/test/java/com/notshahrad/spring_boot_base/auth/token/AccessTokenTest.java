package com.notshahrad.spring_boot_base.auth.token;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.notshahrad.spring_boot_base.auth.LocalUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccessTokenTest {

    private static final String ISSUER = "https://issuer.example";
    private static final String AUDIENCE = "example-api";
    private static final Instant NOW = Instant.parse("2026-04-10T10:15:30Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ACCOUNT_ID = UUID.fromString("2909dcfc-37d3-4ef7-a795-e87284897f8f");

    @TempDir
    static Path keyDirectory;

    private static RSAKey active;
    private static RSAKey old;
    private static AccessTokenProperties properties;
    private static AccessTokenKeys keys;

    @BeforeAll
    static void configureKeys() throws Exception {
        active = new RSAKeyGenerator(2048).generate();
        old = new RSAKeyGenerator(2048).generate();
        Path privateKey = writePem("active-private.pem", "PRIVATE KEY", active.toRSAPrivateKey().getEncoded());
        Path publicKey = writePem("active-public.pem", "PUBLIC KEY", active.toRSAPublicKey().getEncoded());
        Path oldPublicKey = writePem("old-public.pem", "PUBLIC KEY", old.toRSAPublicKey().getEncoded());
        properties = properties(privateKey, publicKey,
                List.of(new AccessTokenProperties.PreviousKey("old", oldPublicKey, NOW.plusSeconds(120))));
        keys = new AccessTokenKeys(properties, NOW);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void issuesPredictableClaimsAndOnlyAllowlistedApplicationScopes() {
        authenticate(new LocalAccount(ACCOUNT_ID), "APP_SCOPE_orders.read", "APP_SCOPE_admin",
                "SCOPE_email", "SCOPE_profile", "ROLE_ADMIN");
        var issuer = new AccessTokenIssuer(properties, keys, CLOCK);
        var decoder = AccessTokenDecoder.create(properties, keys, CLOCK);

        Jwt jwt = decoder.decode(issuer.issue().getTokenValue());

        assertEquals("active", jwt.getHeaders().get("kid"));
        assertEquals("RS256", jwt.getHeaders().get("alg"));
        assertEquals("at+jwt", jwt.getHeaders().get("typ"));
        assertEquals(ACCOUNT_ID.toString(), jwt.getSubject());
        assertEquals(ISSUER, jwt.getIssuer().toExternalForm());
        assertEquals(List.of(AUDIENCE), jwt.getAudience());
        assertEquals(NOW, jwt.getIssuedAt());
        assertEquals(NOW, jwt.getNotBefore());
        assertEquals(NOW.plusSeconds(300), jwt.getExpiresAt());
        assertEquals("access", jwt.getClaimAsString("token_use"));
        assertEquals("orders.read", jwt.getClaimAsString("scope"));
        assertEquals(Set.of("iss", "aud", "sub", "iat", "nbf", "exp", "jti", "token_use", "scope"),
                jwt.getClaims().keySet());
        assertNotEquals(jwt.getId(), issuer.issue().getId());
    }

    @Test
    void refusesAnonymousGenericRememberedOrBearerAuthentication() {
        var issuer = new AccessTokenIssuer(properties, keys, CLOCK);
        assertThrows(AccessDeniedException.class, issuer::issue);

        authenticate(ACCOUNT_ID.toString(), "ROLE_USER");
        assertThrows(AccessDeniedException.class, issuer::issue);

        var remembered = new RememberMeAuthenticationToken("test-key", new LocalAccount(ACCOUNT_ID), List.of());
        SecurityContextHolder.getContext().setAuthentication(remembered);
        assertThrows(AccessDeniedException.class, issuer::issue);

        authenticate(new LocalAccount(ACCOUNT_ID));
        var bearer = new JwtAuthenticationToken(issuer.issue(), List.of());
        SecurityContextHolder.getContext().setAuthentication(bearer);
        assertThrows(AccessDeniedException.class, issuer::issue);
    }

    @Test
    void acceptsOldKeyOnlyBeforeItsVerificationCutoff() throws Exception {
        String token = sign(old, header(JWSAlgorithm.RS256, "old", "at+jwt"), claims -> {});
        var overlappingDecoder = AccessTokenDecoder.create(properties, keys, CLOCK);
        assertEquals(ACCOUNT_ID.toString(), overlappingDecoder.decode(token).getSubject());

        var retiredDecoder = AccessTokenDecoder.create(properties, keys,
                Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC));
        assertThrows(JwtException.class, () -> retiredDecoder.decode(token));
    }

    @Test
    void rejectsUnknownMissingOrMismatchedKidAndUnsupportedAlgorithm() throws Exception {
        var decoder = AccessTokenDecoder.create(properties, keys, CLOCK);
        String unknownId = sign(active, header(JWSAlgorithm.RS256, "unknown", "at+jwt"), claims -> {});
        String noId = sign(active, header(JWSAlgorithm.RS256, null, "at+jwt"), claims -> {});
        String wrongSignature = sign(old, header(JWSAlgorithm.RS256, "active", "at+jwt"), claims -> {});
        String wrongAlgorithm = sign(active, header(JWSAlgorithm.PS256, "active", "at+jwt"), claims -> {});
        String embeddedKey = sign(active, new JWSHeader.Builder(header(JWSAlgorithm.RS256, "active", "at+jwt"))
                .jwk(old.toPublicJWK()).build(), claims -> {});
        for (String token : List.of(unknownId, noId, wrongSignature, wrongAlgorithm, embeddedKey)) {
            assertThrows(JwtException.class, () -> decoder.decode(token));
        }
    }

    @Test
    void rejectsProviderTypeAndOpaqueCredentials() throws Exception {
        var decoder = AccessTokenDecoder.create(properties, keys, CLOCK);
        String providerType = sign(active, header(JWSAlgorithm.RS256, "active", "JWT"), claims -> {});
        assertThrows(JwtException.class, () -> decoder.decode(providerType));
        assertThrows(JwtException.class, () -> decoder.decode("opaque-credential"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"iss", "aud", "sub", "jti", "token_use", "scope"})
    void rejectsInvalidClaims(String claim) throws Exception {
        var decoder = AccessTokenDecoder.create(properties, keys, CLOCK);
        String token = sign(active, header(JWSAlgorithm.RS256, "active", "at+jwt"),
                claims -> claims.claim(claim, claim.equals("aud") ? List.of("foreign-api") : "invalid"));
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void rejectsExpiredFutureOrOverlongTokens() throws Exception {
        var decoder = AccessTokenDecoder.create(properties, keys, CLOCK);
        String expired = sign(active, header(JWSAlgorithm.RS256, "active", "at+jwt"), claims -> claims
                .issueTime(Date.from(NOW.minusSeconds(300))).notBeforeTime(Date.from(NOW.minusSeconds(300)))
                .expirationTime(Date.from(NOW.minusSeconds(30))));
        String future = sign(active, header(JWSAlgorithm.RS256, "active", "at+jwt"), claims -> claims
                .issueTime(Date.from(NOW.plusSeconds(31))).notBeforeTime(Date.from(NOW.plusSeconds(31))));
        String overlong = sign(active, header(JWSAlgorithm.RS256, "active", "at+jwt"), claims -> claims
                .expirationTime(Date.from(NOW.plusSeconds(901))));
        for (String token : List.of(expired, future, overlong)) {
            assertThrows(JwtException.class, () -> decoder.decode(token));
        }
    }

    @Test
    void startupRejectsMissingMalformedMismatchedOrLongLivedKeys() throws Exception {
        Path oldPublicKey = properties.previousKeys().getFirst().publicKey();
        Path malformed = Files.writeString(keyDirectory.resolve("malformed.pem"), "invalid");
        assertThrows(IllegalArgumentException.class, () -> new AccessTokenKeys(
                properties(null, properties.publicKey(), List.of()), NOW));
        assertThrows(IllegalArgumentException.class, () -> new AccessTokenKeys(
                properties(malformed, properties.publicKey(), List.of()), NOW));
        assertThrows(IllegalArgumentException.class, () -> new AccessTokenKeys(
                properties(properties.privateKey(), oldPublicKey, List.of()), NOW));
        var longLived = new AccessTokenProperties.PreviousKey("old", oldPublicKey, NOW.plus(Duration.ofHours(25)));
        var duplicate = new AccessTokenProperties.PreviousKey("active", oldPublicKey, NOW.plusSeconds(60));
        assertThrows(IllegalArgumentException.class, () -> new AccessTokenKeys(
                properties(properties.privateKey(), properties.publicKey(), List.of(longLived)), NOW));
        assertThrows(IllegalArgumentException.class, () -> new AccessTokenKeys(
                properties(properties.privateKey(), properties.publicKey(), List.of(duplicate)), NOW));
    }

    @Test
    void startupRejectsUnresolvedIssuerAudienceAndExcessiveTtl() {
        assertThrows(IllegalArgumentException.class, () -> new AccessTokenProperties(
                "${APP_TOKEN_ISSUER}", AUDIENCE, Duration.ofMinutes(5), "active",
                properties.privateKey(), properties.publicKey(), List.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new AccessTokenProperties(
                ISSUER, "${APP_TOKEN_AUDIENCE}", Duration.ofMinutes(5), "active",
                properties.privateKey(), properties.publicKey(), List.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new AccessTokenProperties(
                ISSUER, AUDIENCE, Duration.ofMinutes(16), "active",
                properties.privateKey(), properties.publicKey(), List.of(), Set.of()));
    }

    private static void authenticate(Object principal, String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, granted));
    }

    private static AccessTokenProperties properties(Path privateKey, Path publicKey,
                                                    List<AccessTokenProperties.PreviousKey> previousKeys) {
        return new AccessTokenProperties(ISSUER, AUDIENCE, Duration.ofMinutes(5), "active", privateKey, publicKey,
                previousKeys, Set.of("orders.read", "email"));
    }

    private static JWSHeader header(JWSAlgorithm algorithm, String kid, String type) {
        return new JWSHeader.Builder(algorithm).keyID(kid).type(new JOSEObjectType(type)).build();
    }

    private static String sign(RSAKey key, JWSHeader header, Consumer<JWTClaimsSet.Builder> change) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCE).subject(ACCOUNT_ID.toString())
                .jwtID(UUID.randomUUID().toString()).issueTime(Date.from(NOW)).notBeforeTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(300))).claim("token_use", "access");
        change.accept(claims);
        var token = new SignedJWT(header, claims.build());
        token.sign(new RSASSASigner(key));
        return token.serialize();
    }

    private static Path writePem(String filename, String kind, byte[] encoded) throws Exception {
        String pem = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        return Files.writeString(keyDirectory.resolve(filename),
                "-----BEGIN " + kind + "-----\n" + pem + "\n-----END " + kind + "-----\n");
    }

    private record LocalAccount(UUID getLocalUserId) implements LocalUserPrincipal {
        @Override
        public String getName() {
            return getLocalUserId.toString();
        }
    }
}
