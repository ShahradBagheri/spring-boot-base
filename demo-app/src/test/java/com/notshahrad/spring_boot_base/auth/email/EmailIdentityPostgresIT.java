package com.notshahrad.spring_boot_base.auth.email;

import com.notshahrad.spring_boot_base.auth.OidcIdentityService;
import com.notshahrad.spring_boot_base.auth.VerifiedProviderIdentity;
import com.notshahrad.spring_boot_base.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "base.auth.email.enabled=true")
class EmailIdentityPostgresIT extends AbstractPostgresIntegrationTest {

    private static final Instant VERIFIED_AT = Instant.parse("2026-11-21T01:00:00Z");

    @Autowired
    private EmailIdentityRepository emailIdentities;
    @Autowired
    private OidcIdentityService localUsers;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void providerProfileEmailDoesNotCreateALoginIdentity() {
        var address = randomAddress();
        var user = newLocalUser(address);

        assertTrue(emailIdentities.findByUserId(user).isEmpty());
        assertTrue(emailIdentities.findVerified(address).isEmpty());
        assertTrue(emailIdentities.stagePending(user, address));
        assertFalse(emailIdentities.findByUserId(user).orElseThrow().isVerified());
        assertTrue(emailIdentities.findVerified(address).isEmpty());

        assertTrue(emailIdentities.markVerified(user, address, VERIFIED_AT));
        assertEquals(new EmailIdentity(user, address, VERIFIED_AT), emailIdentities.findVerified(address).orElseThrow());
    }

    @Test
    void staleVerificationCannotVerifyAReplacementAndVerifiedAddressCannotBeOverwritten() {
        var original = randomAddress();
        var replacement = randomAddress();
        var user = newLocalUser(original);

        assertTrue(emailIdentities.stagePending(user, original));
        assertTrue(emailIdentities.stagePending(user, replacement));
        assertFalse(emailIdentities.markVerified(user, original, VERIFIED_AT));
        assertTrue(emailIdentities.markVerified(user, replacement, VERIFIED_AT));

        assertFalse(emailIdentities.stagePending(user, original));
        assertFalse(emailIdentities.markVerified(user, replacement, VERIFIED_AT.plusSeconds(60)));
        assertEquals(VERIFIED_AT, emailIdentities.findByUserId(user).orElseThrow().verifiedAt());
    }

    @Test
    void duplicatePendingCandidatesCanStageButOnlyOneUserCanOwnTheVerifiedAddress() {
        var canonical = randomAddress();
        var first = newLocalUser(canonical);
        var second = newLocalUser(canonical);
        var equivalent = new LoginEmail(canonical.value().replace("@example.com", "@EXAMPLE.COM"));

        assertTrue(emailIdentities.stagePending(first, canonical));
        assertTrue(emailIdentities.stagePending(second, equivalent));
        assertTrue(emailIdentities.markVerified(first, canonical, VERIFIED_AT));
        assertThrows(DuplicateKeyException.class, () -> emailIdentities.markVerified(second, equivalent, VERIFIED_AT));
        assertFalse(emailIdentities.findByUserId(second).orElseThrow().isVerified());
        assertEquals(first, emailIdentities.findVerified(equivalent).orElseThrow().userId());
    }

    @Test
    void anInactiveOwnerCannotSignInOrStageAnotherIdentifier() {
        var address = randomAddress();
        var user = newLocalUser(address);
        assertTrue(emailIdentities.stagePending(user, address));
        assertTrue(emailIdentities.markVerified(user, address, VERIFIED_AT));

        jdbc.update("UPDATE app_users SET deleted = TRUE WHERE id = ?", user);
        assertTrue(emailIdentities.findVerified(address).isEmpty());
        assertFalse(emailIdentities.stagePending(user, randomAddress()));
        assertTrue(emailIdentities.findByUserId(user).orElseThrow().isVerified());
    }

    private static LoginEmail randomAddress() {
        return new LoginEmail("Ada+" + UUID.randomUUID() + "@example.com");
    }

    private UUID newLocalUser(LoginEmail contactAddress) {
        return localUsers.resolveOrCreate(new VerifiedProviderIdentity(
                "https://email-test.issuer.example", UUID.randomUUID().toString(), contactAddress.value(), true, null));
    }
}
