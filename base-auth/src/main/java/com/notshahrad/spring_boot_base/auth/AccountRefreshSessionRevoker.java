package com.notshahrad.spring_boot_base.auth;

import java.util.UUID;

@FunctionalInterface
public interface AccountRefreshSessionRevoker {

    void revokeForAccount(UUID userId);
}
