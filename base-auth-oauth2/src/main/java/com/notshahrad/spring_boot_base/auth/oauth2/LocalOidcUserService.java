package com.notshahrad.spring_boot_base.auth.oauth2;

import com.notshahrad.spring_boot_base.auth.OidcIdentityService;
import com.notshahrad.spring_boot_base.auth.VerifiedProviderIdentity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Objects;
import java.util.UUID;

public class LocalOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OAuth2UserService<OidcUserRequest, OidcUser> verifiedUserService;
    private final OidcIdentityService identityService;

    public LocalOidcUserService(OidcIdentityService identityService) {
        this(new OidcUserService(), identityService);
    }

    LocalOidcUserService(OAuth2UserService<OidcUserRequest, OidcUser> verifiedUserService,
                         OidcIdentityService identityService) {
        this.verifiedUserService = Objects.requireNonNull(verifiedUserService);
        this.identityService = Objects.requireNonNull(identityService);
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = verifiedUserService.loadUser(userRequest);

        var token = oidcUser.getIdToken();
        var issuer = token.getIssuer();
        VerifiedProviderIdentity identity;
        try {
            identity = new VerifiedProviderIdentity(
                    issuer == null ? null : issuer.toExternalForm(),
                    token.getSubject(),
                    oidcUser.getEmail(),
                    Boolean.TRUE.equals(oidcUser.getEmailVerified()),
                    oidcUser.getFullName()
            );
        } catch (IllegalArgumentException ex) {
            throw new OAuth2AuthenticationException("invalid_id_token");
        }

        UUID localUserId = identityService.resolveOrCreate(identity);
        return new LocalOidcUser(oidcUser, localUserId);
    }
}
