package com.notshahrad.spring_boot_base.auth;

import java.util.UUID;

@FunctionalInterface
public interface AccountLinkApproval {

    boolean approve(UUID authenticatedLocalUserId, VerifiedProviderIdentity identity);
}
