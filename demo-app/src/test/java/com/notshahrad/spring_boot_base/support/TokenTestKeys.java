package com.notshahrad.spring_boot_base.support;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;

public final class TokenTestKeys {

    private TokenTestKeys() {
    }

    public static RSAPrivateKey register(DynamicPropertyRegistry registry, Path directory) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Path privateKey = write(directory.resolve("private.pem"), "PRIVATE KEY", pair.getPrivate().getEncoded());
        Path publicKey = write(directory.resolve("public.pem"), "PUBLIC KEY", pair.getPublic().getEncoded());
        registry.add("APP_TOKEN_ISSUER", () -> "https://app.issuer.example");
        registry.add("APP_TOKEN_AUDIENCE", () -> "demo-api");
        registry.add("APP_TOKEN_SIGNING_KEY_ID", () -> "test-key");
        registry.add("APP_TOKEN_PRIVATE_KEY_FILE", privateKey::toString);
        registry.add("APP_TOKEN_PUBLIC_KEY_FILE", publicKey::toString);
        return (RSAPrivateKey) pair.getPrivate();
    }

    private static Path write(Path path, String kind, byte[] encoded) throws Exception {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        return Files.writeString(path,
                "-----BEGIN " + kind + "-----\n" + body + "\n-----END " + kind + "-----\n");
    }
}
