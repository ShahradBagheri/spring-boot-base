package com.notshahrad.spring_boot_base.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OidcIdentityServiceTest {

    private AppUserRepository users;
    private ExternalIdentityRepository identities;
    private ObjectProvider<AccountLinkApproval> approvalProvider;
    private OidcIdentityService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        identities = mock(ExternalIdentityRepository.class);
        approvalProvider = mock(ObjectProvider.class);
        service = new OidcIdentityService(users, identities, approvalProvider);
    }

    @Test
    void firstLoginCreatesLocalUserAndExternalMapping() {
        UUID userId = UUID.randomUUID();
        AppUser saved = mock(AppUser.class);
        when(saved.getId()).thenReturn(userId);
        when(users.save(any(AppUser.class))).thenReturn(saved);

        VerifiedProviderIdentity provider = new VerifiedProviderIdentity(
                "https://issuer.example", "subject-1", "person@example.com", true, "Person");

        assertEquals(userId, service.resolveOrCreate(provider));
        verify(users).save(any(AppUser.class));
        verify(identities).save(any(ExternalIdentity.class));
    }

    @Test
    void returningSubjectReusesUserAndDoesNotCreateAnother() {
        UUID userId = UUID.randomUUID();
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        ExternalIdentity existing = mock(ExternalIdentity.class);
        when(existing.getUser()).thenReturn(user);
        when(identities.findByIssuerAndSubject("https://issuer.example", "sub"))
                .thenReturn(Optional.of(existing));

        VerifiedProviderIdentity provider = new VerifiedProviderIdentity(
                "https://issuer.example", "sub", null, false, null);

        assertEquals(userId, service.resolveOrCreate(provider));
        verify(users, never()).save(any());
        verify(identities, never()).save(any());
        verify(user).refreshProfile(null, false, null);
    }

    @Test
    void matchingEmailDoesNotCauseAutomaticLinking() {
        UUID userId = UUID.randomUUID();
        AppUser newlyCreated = mock(AppUser.class);
        when(newlyCreated.getId()).thenReturn(userId);
        when(users.save(any(AppUser.class))).thenReturn(newlyCreated);
        VerifiedProviderIdentity otherIssuer = new VerifiedProviderIdentity(
                "https://different-issuer.example", "different-sub", "same@example.com", false, null);

        assertEquals(userId, service.resolveOrCreate(otherIssuer));
        verify(identities).findByIssuerAndSubject("https://different-issuer.example", "different-sub");
        verify(users).save(any(AppUser.class));
    }

    @Test
    void linkingIsDeniedWithoutExplicitApproval() {
        UUID userId = UUID.randomUUID();
        VerifiedProviderIdentity external = new VerifiedProviderIdentity(
                "https://issuer.example", "sub", null, false, null);

        assertThrows(SecurityException.class, () -> service.linkToAuthenticatedUser(userId, external));
        verify(users, never()).findById(any());
        verify(identities, never()).save(any());
    }

    @Test
    void cannotAttachProviderIdentityToAnotherUser() {
        UUID userId = UUID.randomUUID();
        AppUser requestedUser = mock(AppUser.class);
        when(requestedUser.getId()).thenReturn(userId);
        when(users.findById(userId)).thenReturn(Optional.of(requestedUser));
        when(approvalProvider.getIfAvailable()).thenReturn((id, identity) -> true);

        AppUser owner = mock(AppUser.class);
        when(owner.getId()).thenReturn(UUID.randomUUID());
        ExternalIdentity previous = mock(ExternalIdentity.class);
        when(previous.getUser()).thenReturn(owner);
        when(identities.findByIssuerAndSubject("https://issuer.example", "sub"))
                .thenReturn(Optional.of(previous));

        assertThrows(SecurityException.class, () -> service.linkToAuthenticatedUser(userId,
                new VerifiedProviderIdentity("https://issuer.example", "sub", null, false, null)));
        verify(identities, never()).save(any());
    }

    @Test
    void approvedAuthenticatedLinkCanAttachASecondProvider() {
        UUID userId = UUID.randomUUID();
        AppUser target = mock(AppUser.class);
        when(target.getId()).thenReturn(userId);
        when(users.findById(userId)).thenReturn(Optional.of(target));
        when(approvalProvider.getIfAvailable()).thenReturn((id, identity) -> true);
        VerifiedProviderIdentity newProvider = new VerifiedProviderIdentity(
                "https://second-issuer.example", "sub-2", null, false, null);

        assertEquals(userId, service.linkToAuthenticatedUser(userId, newProvider));
        verify(identities).save(any(ExternalIdentity.class));
    }
}
