package com.notshahrad.spring_boot_base.auth.email;

@FunctionalInterface
public interface PasswordBlocklist {

    boolean isBlocked(String password);
}
