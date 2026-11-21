package com.notshahrad.spring_boot_base.auth;

import com.notshahrad.spring_boot_base.support.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class LocalOidcIdentityPostgresIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private OidcIdentityService identities;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private ExternalIdentityRepository external;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void resolvesSameIssuerAndSubjectWithoutDuplicatingUser() {
        var incoming = new VerifiedProviderIdentity(
                "https://issuer.example", "same-subject", null, false, null);

        var firstId = identities.resolveOrCreate(incoming);
        entityManager.flush();
        entityManager.clear();

        var secondId = identities.resolveOrCreate(new VerifiedProviderIdentity(
                "https://issuer.example", "same-subject", "updated@example.com", true, "Name"));
        entityManager.flush();

        assertEquals(firstId, secondId);
        assertEquals("updated@example.com", users.findById(secondId).orElseThrow().getEmail());
        assertTrue(users.findById(secondId).orElseThrow().isEmailVerified());
        assertNotNull(external.findByIssuerAndSubject("https://issuer.example", "same-subject").orElseThrow());
    }

    @Test
    @Transactional
    void differentIssuersWithMatchingEmailsDoNotMergeAccounts() {
        var google = identities.resolveOrCreate(new VerifiedProviderIdentity(
                "https://accounts.google.com", "first", "same@example.com", true, null));
        var apple = identities.resolveOrCreate(new VerifiedProviderIdentity(
                "https://appleid.apple.com", "second", "same@example.com", true, null));

        entityManager.flush();

        assertNotEquals(google, apple);
        assertEquals("same@example.com", users.findById(google).orElseThrow().getEmail());
        assertEquals("same@example.com", users.findById(apple).orElseThrow().getEmail());
    }

    @Test
    @Transactional
    void databaseRejectsDuplicateIssuerAndSubject() {
        AppUser firstUser = users.save(new AppUser(null, false, null));
        AppUser secondUser = users.save(new AppUser(null, false, null));

        external.saveAndFlush(new ExternalIdentity(firstUser, "https://issuer.unique", "subject"));
        external.save(new ExternalIdentity(secondUser, "https://issuer.unique", "subject"));

        assertThrows(PersistenceException.class, () -> entityManager.flush());
    }
}
