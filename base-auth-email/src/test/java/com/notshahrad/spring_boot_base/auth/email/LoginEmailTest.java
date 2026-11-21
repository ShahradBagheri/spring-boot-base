package com.notshahrad.spring_boot_base.auth.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginEmailTest {

    @Test
    void normalizesOnlyTheDomainAndExteriorSpaces() {
        var email = new LoginEmail("  Ada.Dev+work@MAIL.Example  ");
        assertEquals("Ada.Dev+work@mail.example", email.value());
        assertEquals(email, new LoginEmail("Ada.Dev+work@mail.example"));
        assertNotEquals(email, new LoginEmail("ada.dev+work@mail.example"));
        assertNotEquals(email, new LoginEmail("AdaDev@mail.example"));
        assertFalse(email.toString().contains(email.value()));
    }

    @Test
    void acceptsPunycodeWithoutRewritingTheLocalPart() {
        assertEquals("User@xn--bcher-kva.example", new LoginEmail("User@XN--BCHER-KVA.Example").value());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "name@example", "name@@example.com", "name..tag@example.com",
            "name@-example.com", "name@example.com.", "name@bücher.example", "üser@example.com",
            "name@example.com\r\n", "Name <name@example.com>", "\"name\"@example.com"})
    void rejectsMalformedOrUnsupportedInput(String address) {
        assertThrows(IllegalArgumentException.class, () -> new LoginEmail(address));
    }

    @Test
    void rejectsAnOverlongLocalPart() {
        assertThrows(IllegalArgumentException.class, () -> new LoginEmail("a".repeat(65) + "@example.com"));
    }
}
