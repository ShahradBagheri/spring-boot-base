package com.notshahrad.spring_boot_base.auth.oauth2;

import com.notshahrad.spring_boot_base.auth.OidcIdentityService;
import com.notshahrad.spring_boot_base.auth.VerifiedProviderIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalOidcUserServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void verifiedIdentityMapsToLocalUserWithoutReusingExternalTokenAsApiCredential() {
        OAuth2UserService<OidcUserRequest, OidcUser> validator = mock(OAuth2UserService.class);
        OidcIdentityService identities = mock(OidcIdentityService.class);
        OidcUserRequest request = mock(OidcUserRequest.class);
        OidcUser verifiedUser = mock(OidcUser.class);

        var token = new OidcIdToken(
                "opaque-provider-token",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T01:00:00Z"),
                Map.of("iss", "https://accounts.google.com", "sub", "provider-subject")
        );
        UUID localUserId = UUID.randomUUID();

        when(validator.loadUser(request)).thenReturn(verifiedUser);
        when(verifiedUser.getIdToken()).thenReturn(token);
        when(verifiedUser.getEmail()).thenReturn(null);
        when(verifiedUser.getFullName()).thenReturn(null);
        when(identities.resolveOrCreate(any(VerifiedProviderIdentity.class))).thenReturn(localUserId);

        var resolved = new LocalOidcUserService(validator, identities).loadUser(request);

        assertInstanceOf(LocalOidcUser.class, resolved);
        assertEquals(localUserId.toString(), resolved.getName());
        assertEquals(localUserId, ((LocalOidcUser) resolved).getLocalUserId());
        verify(identities).resolveOrCreate(new VerifiedProviderIdentity(
                "https://accounts.google.com", "provider-subject", null, false, null));
    }
}
