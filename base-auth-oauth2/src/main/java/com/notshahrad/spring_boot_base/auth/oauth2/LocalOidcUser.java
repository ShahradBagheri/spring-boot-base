package com.notshahrad.spring_boot_base.auth.oauth2;

import com.notshahrad.spring_boot_base.auth.LocalUserPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class LocalOidcUser implements OidcUser, LocalUserPrincipal, Serializable {

    private static final long serialVersionUID = 1L;

    private final OidcUser delegate;
    private final UUID localUserId;

    public LocalOidcUser(OidcUser delegate, UUID localUserId) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.localUserId = Objects.requireNonNull(localUserId, "localUserId must not be null");
    }

    @Override
    public UUID getLocalUserId() {
        return localUserId;
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return localUserId.toString();
    }
}
