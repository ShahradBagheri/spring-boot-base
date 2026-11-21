package com.notshahrad.spring_boot_base.auth;

public record VerifiedProviderIdentity(
        String issuer,
        String subject,
        String email,
        boolean emailVerified,
        String displayName
) {
    public VerifiedProviderIdentity {
        if (issuer == null || issuer.isBlank() || issuer.length() > 255) {
            throw new IllegalArgumentException("A nonblank issuer of at most 255 characters is required");
        }
        if (subject == null || subject.isBlank() || subject.length() > 255) {
            throw new IllegalArgumentException("A nonblank subject of at most 255 characters is required");
        }
    }
}
