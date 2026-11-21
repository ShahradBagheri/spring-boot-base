package com.notshahrad.spring_boot_base.mcp;

import java.util.List;
import java.util.stream.Collectors;

final class FeatureGuideCatalog {

    private static final List<FeatureGuide> GUIDES = List.of(
            new FeatureGuide("oidc", "Google and Apple sign-in", "docs/OIDC_PROVIDER_SETUP.md"),
            new FeatureGuide("email", "Email and password sign-in", "docs/EMAIL_PASSWORD_SETUP.md"),
            new FeatureGuide("phone", "Phone sign-in", "docs/PHONE_AUTHENTICATION_SETUP.md"),
            new FeatureGuide("guest", "Guest browser sessions", "docs/GUEST_SESSION_MODEL.md"),
            new FeatureGuide("token", "Access and refresh tokens", "docs/TOKEN_OPERATIONS.md"));

    FeatureGuide lookup(String feature) {
        return GUIDES.stream()
                .filter(guide -> guide.feature().equals(feature))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown feature guide"));
    }

    String index() {
        return GUIDES.stream()
                .map(guide -> "%s — %s (%s)".formatted(guide.feature(), guide.title(), guide.guidePath()))
                .collect(Collectors.joining("\n"));
    }

    public record FeatureGuide(String feature, String title, String guidePath) {
    }
}
