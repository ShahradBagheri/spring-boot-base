package com.notshahrad.spring_boot_base.auth;

@FunctionalInterface
public interface AccountSessionValidator {

    boolean isCurrent(LocalUserPrincipal principal);
}
