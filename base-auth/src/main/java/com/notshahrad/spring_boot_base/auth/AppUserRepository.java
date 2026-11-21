package com.notshahrad.spring_boot_base.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
}
