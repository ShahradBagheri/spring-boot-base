package com.notshahrad.spring_boot_base.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {

    Optional<ExternalIdentity> findByIssuerAndSubject(String issuer, String subject);
}
