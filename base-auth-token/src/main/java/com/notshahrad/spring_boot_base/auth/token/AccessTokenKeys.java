package com.notshahrad.spring_boot_base.auth.token;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

final class AccessTokenKeys {

    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Duration MAX_ROTATION_WINDOW = Duration.ofHours(24);

    private final JwtEncoder encoder;
    private final Map<String, VerificationKey> verificationKeys;

    AccessTokenKeys(AccessTokenProperties properties, Instant now) {
        String signingId = requireKeyId(properties.signingKeyId());
        RSAPublicKey publicKey = readPublicKey(properties.publicKey());
        RSAPrivateKey privateKey = readPrivateKey(properties.privateKey());
        requireMatchingPair(publicKey, privateKey);

        var signingKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(signingId)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));

        Map<String, VerificationKey> configured = new HashMap<>();
        configured.put(signingId, new VerificationKey(publicKey, null));
        for (var previous : properties.previousKeys()) {
            String keyId = requireKeyId(previous.keyId());
            Instant deadline = previous.verifyUntil();
            if (deadline == null || deadline.isAfter(now.plus(MAX_ROTATION_WINDOW))) {
                throw new IllegalArgumentException("Previous access token keys require a verification cutoff within 24 hours");
            }
            var key = new VerificationKey(readPublicKey(previous.publicKey()), deadline);
            if (configured.putIfAbsent(keyId, key) != null) {
                throw new IllegalArgumentException("Duplicate access token key ID");
            }
        }
        verificationKeys = Map.copyOf(configured);
    }

    JwtEncoder encoder() {
        return encoder;
    }

    RSAPublicKey verificationKey(String keyId, Instant now) {
        if (keyId == null) {
            return null;
        }
        VerificationKey key = verificationKeys.get(keyId);
        if (key == null || (key.verifyUntil() != null && !now.isBefore(key.verifyUntil()))) {
            return null;
        }
        return key.publicKey();
    }

    private static String requireKeyId(String keyId) {
        if (keyId == null || !KEY_ID.matcher(keyId).matches()) {
            throw new IllegalArgumentException("Access token key IDs must be 1–64 ASCII letters, digits, dots, underscores or hyphens");
        }
        return keyId;
    }

    private static RSAPublicKey readPublicKey(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Configure the access token RSA public key file");
        }
        try (var input = Files.newInputStream(path)) {
            RSAPublicKey key = RsaKeyConverters.x509().convert(input);
            if (key == null || key.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("RSA public key must be at least 2048 bits");
            }
            return key;
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Cannot load an RSA public key of at least 2048 bits from the configured file");
        }
    }

    private static RSAPrivateKey readPrivateKey(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Configure the access token RSA signing key file");
        }
        try (var input = Files.newInputStream(path)) {
            return RsaKeyConverters.pkcs8().convert(input);
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Cannot load an unencrypted PKCS#8 RSA signing key from the configured file");
        }
    }

    private static void requireMatchingPair(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        if (privateKey == null) {
            throw new IllegalArgumentException("The configured RSA signing key is empty");
        }
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            byte[] signed = signature.sign();
            signature.initVerify(publicKey);
            if (!signature.verify(signed)) {
                throw new IllegalArgumentException("Access token public and private keys do not match");
            }
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("Cannot use the configured RSA signing key pair");
        }
    }

    private record VerificationKey(RSAPublicKey publicKey, Instant verifyUntil) {
    }
}
