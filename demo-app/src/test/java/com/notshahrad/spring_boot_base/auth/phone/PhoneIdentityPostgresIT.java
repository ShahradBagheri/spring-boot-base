package com.notshahrad.spring_boot_base.auth.phone;

import com.notshahrad.spring_boot_base.auth.AppUser;
import com.notshahrad.spring_boot_base.auth.AppUserRepository;
import com.notshahrad.spring_boot_base.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "base.auth.phone.enabled=true")
class PhoneIdentityPostgresIT extends AbstractPostgresIntegrationTest {

    private static final Instant VERIFIED_AT = Instant.parse("2026-11-21T00:00:00Z");

    @Autowired
    private PhoneIdentityRepository phones;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsCanonicalVerifiedOwnershipAndVerificationTime() {
        var owner = newUser();
        var national = PhoneNumber.parse("202-555-0123", "US");
        var international = PhoneNumber.parse("+1 (202) 555-0123", null);

        assertTrue(phones.findVerified(international).isEmpty());
        assertTrue(phones.bindVerified(owner, national, VERIFIED_AT));

        var identity = new PhoneIdentity(owner, international, VERIFIED_AT);
        assertEquals(identity, phones.findVerified(international).orElseThrow());
        assertEquals(identity, phones.findByUserId(owner).orElseThrow());
    }

    @Test
    void enforcesGlobalOwnershipAndOnePhonePerAccount() {
        var owner = newUser();
        var other = newUser();
        var original = PhoneNumber.parse("+1 202 555 0124", null);
        var otherNumber = PhoneNumber.parse("+1 202 555 0125", null);
        assertTrue(phones.bindVerified(owner, original, VERIFIED_AT));

        assertThrows(DuplicateKeyException.class, () ->
                phones.bindVerified(other, PhoneNumber.parse("202-555-0124", "US"), VERIFIED_AT));
        assertThrows(DuplicateKeyException.class, () -> phones.bindVerified(owner, otherNumber, VERIFIED_AT));
        assertTrue(phones.findByUserId(other).isEmpty());
        assertEquals(owner, phones.findVerified(original).orElseThrow().userId());
    }

    @Test
    void preventsInactiveAccountsFromAuthenticatingAndPreservesTheirOwnership() {
        var owner = newUser();
        var other = newUser();
        var number = PhoneNumber.parse("+1 202 555 0126", null);
        assertTrue(phones.bindVerified(owner, number, VERIFIED_AT));
        jdbc.update("UPDATE app_users SET deleted = TRUE WHERE id = ?", owner);

        assertTrue(phones.findVerified(number).isEmpty());
        assertEquals(owner, phones.findByUserId(owner).orElseThrow().userId());
        assertFalse(phones.bindVerified(owner, PhoneNumber.parse("+1 202 555 0127", null), VERIFIED_AT));
        assertThrows(DuplicateKeyException.class, () -> phones.bindVerified(other, number, VERIFIED_AT));
    }

    @Test
    void rejectsMalformedNumbersAndRowsWithoutProof() {
        var owner = newUser();
        assertThrows(IllegalArgumentException.class, () ->
                phones.bindVerified(owner, new PhoneNumber("+99912345"), VERIFIED_AT));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO phone_login_identities (user_id, phone_number, verified_at)
                VALUES (?, '2025550128', CURRENT_TIMESTAMP)
                """, owner));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO phone_login_identities (user_id, phone_number, verified_at)
                VALUES (?, '+12025550128', NULL)
                """, owner));
        assertTrue(phones.findByUserId(owner).isEmpty());
    }

    private UUID newUser() {
        return users.saveAndFlush(new AppUser(null, false, null)).getId();
    }
}
