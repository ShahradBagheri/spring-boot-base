package com.notshahrad.spring_boot_base.auth.phone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneNumberTest {

    @Test
    void normalizesInternationalAndNationalFormsToOneIdentity() {
        var international = PhoneNumber.parse("+44 20 7946 0018", null);
        assertEquals("+442079460018", international.e164());
        assertEquals(international, PhoneNumber.parse(" (020) 7946-0018 ", "gb"));
        assertEquals(international, PhoneNumber.parse("+44 (20) 7946.0018", "US"));
        assertFalse(international.toString().contains(international.e164()));
    }

    @Test
    void requiresAnExplicitSupportedRegionForNationalInput() {
        assertThrows(IllegalArgumentException.class, () -> PhoneNumber.parse("020 7946 0018", null));
        assertThrows(IllegalArgumentException.class, () -> PhoneNumber.parse("020 7946 0018", "ZZ"));
        assertThrows(IllegalArgumentException.class, () -> PhoneNumber.parse("020 7946 0018", "US"));
        assertThrows(IllegalArgumentException.class, () -> PhoneNumber.parse("2025550123", "CA"));
        assertThrows(IllegalArgumentException.class, () -> PhoneNumber.parse("011 44 20 7946 0018", "US"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"+999123456", "+44 123", "02079460018 ext 123", "+1-800-FLOWERS",
            "00442079460018", "011442079460018", "02079460018\r\n+442079460018", "+٤٤٢٠٧٩٤٦٠٠١٨"})
    void rejectsInvalidOrAmbiguousInput(String input) {
        assertThrows(IllegalArgumentException.class, () -> PhoneNumber.parse(input, "GB"));
    }

    @Test
    void canonicalValueDoesNotAcceptFormattedOrOverlongInput() {
        assertThrows(IllegalArgumentException.class, () -> new PhoneNumber("+1 202 555 0123"));
        assertThrows(IllegalArgumentException.class, () -> new PhoneNumber("+1234567890123456"));
    }
}
