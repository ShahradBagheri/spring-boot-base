package com.notshahrad.spring_boot_base.auth.oauth2;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppleAuthorizationRequestResolverTest {

    @Test
    void appleUsesFormPostAndOtherProvidersKeepDefaultAuthorizationResponseMode() {
        var repository = new InMemoryClientRegistrationRepository(
                registration("apple", "https://appleid.apple.com/auth/authorize"),
                registration("google", "https://accounts.google.com/o/oauth2/v2/auth"));

        var resolver = new AppleAuthorizationRequestResolver(repository);

        var appleRequest = new MockHttpServletRequest("GET", "/oauth2/authorization/apple");
        appleRequest.setServletPath("/oauth2/authorization/apple");
        var apple = resolver.resolve(appleRequest);

        assertNotNull(apple);
        assertEquals("form_post", apple.getAdditionalParameters().get("response_mode"));
        assertTrue(apple.getAuthorizationRequestUri().contains("response_mode=form_post"));

        var googleRequest = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        googleRequest.setServletPath("/oauth2/authorization/google");
        var google = resolver.resolve(googleRequest);

        assertNotNull(google);
        assertNull(google.getAdditionalParameters().get("response_mode"));
    }

    private static ClientRegistration registration(String name, String authorizationUri) {
        return ClientRegistration.withRegistrationId(name)
                .clientId(name + "-client-id")
                .clientSecret("test-only-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "email")
                .authorizationUri(authorizationUri)
                .tokenUri("https://issuer.example/token")
                .jwkSetUri("https://issuer.example/keys")
                .clientName(name)
                .build();
    }
}
