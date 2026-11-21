package com.notshahrad.spring_boot_base.auth.email;

import com.notshahrad.spring_boot_base.auth.LocalUserPrincipal;

import java.io.Serializable;
import java.util.UUID;

public record EmailPasswordPrincipal(UUID userId, long credentialVersion) implements LocalUserPrincipal, Serializable {

    @Override
    public UUID getLocalUserId() {
        return userId;
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
