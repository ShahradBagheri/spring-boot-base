package com.notshahrad.spring_boot_base.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class OidcIdentityService {

    private final AppUserRepository users;
    private final ExternalIdentityRepository identities;
    private final ObjectProvider<AccountLinkApproval> linkApproval;

    public OidcIdentityService(AppUserRepository users, ExternalIdentityRepository identities,
                               ObjectProvider<AccountLinkApproval> linkApproval) {
        this.users = users;
        this.identities = identities;
        this.linkApproval = linkApproval;
    }

    @Transactional
    public UUID resolveOrCreate(VerifiedProviderIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");

        return identities.findByIssuerAndSubject(identity.issuer(), identity.subject())
                .map(existing -> {
                    AppUser user = existing.getUser();
                    user.refreshProfile(identity.email(), identity.emailVerified(), identity.displayName());
                    return user.getId();
                })
                .orElseGet(() -> {
                    AppUser user = users.save(new AppUser(
                            identity.email(), identity.emailVerified(), identity.displayName()));
                    identities.save(new ExternalIdentity(user, identity.issuer(), identity.subject()));
                    return user.getId();
                });
    }

    @Transactional
    public UUID linkToAuthenticatedUser(UUID authenticatedLocalUserId, VerifiedProviderIdentity identity) {
        Objects.requireNonNull(authenticatedLocalUserId, "authenticatedLocalUserId must not be null");
        Objects.requireNonNull(identity, "identity must not be null");

        AccountLinkApproval approval = linkApproval.getIfAvailable();
        if (approval == null || !approval.approve(authenticatedLocalUserId, identity)) {
            throw new SecurityException("Verified account-link approval is required");
        }

        AppUser target = users.findById(authenticatedLocalUserId)
                .orElseThrow(() -> new IllegalArgumentException("Local user does not exist"));

        var mapping = identities.findByIssuerAndSubject(identity.issuer(), identity.subject());
        if (mapping.isPresent()) {
            if (!mapping.get().getUser().getId().equals(authenticatedLocalUserId)) {
                throw new SecurityException("Provider identity belongs to a different account");
            }
            return target.getId();
        }

        identities.save(new ExternalIdentity(target, identity.issuer(), identity.subject()));
        return target.getId();
    }
}
