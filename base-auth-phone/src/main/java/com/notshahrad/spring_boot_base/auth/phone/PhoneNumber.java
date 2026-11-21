package com.notshahrad.spring_boot_base.auth.phone;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber.CountryCodeSource;

import java.util.Locale;
import java.util.regex.Pattern;

public record PhoneNumber(String e164) {

    private static final Pattern E164 = Pattern.compile("\\+[1-9][0-9]{1,14}");
    private static final Pattern DISPLAY_FORMAT = Pattern.compile("\\+?[0-9 ().-]+");
    private static final Pattern REGION = Pattern.compile("[A-Za-z]{2}");
    private static final PhoneNumberUtil NUMBERS = PhoneNumberUtil.getInstance();

    public PhoneNumber {
        if (e164 == null || !E164.matcher(e164).matches()) {
            throw invalidNumber();
        }
    }

    public static PhoneNumber parse(String value, String region) {
        if (value == null || value.length() > 64) {
            throw invalidNumber();
        }
        String input = value.strip();
        if (!DISPLAY_FORMAT.matcher(input).matches()) {
            throw invalidNumber();
        }

        boolean international = input.startsWith("+");
        String country = international ? "ZZ" : normalizeRegion(region);
        try {
            var parsed = NUMBERS.parseAndKeepRawInput(input, country);
            boolean valid = international ? NUMBERS.isValidNumber(parsed)
                    : parsed.getCountryCodeSource() == CountryCodeSource.FROM_DEFAULT_COUNTRY
                        && NUMBERS.isValidNumberForRegion(parsed, country);
            if (!valid) {
                throw invalidNumber();
            }
            return new PhoneNumber(NUMBERS.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164));
        } catch (NumberParseException ex) {
            throw invalidNumber();
        }
    }

    private static String normalizeRegion(String region) {
        if (region == null || !REGION.matcher(region).matches()) {
            throw new IllegalArgumentException("A supported country or territory is required for a national number");
        }
        String normalized = region.toUpperCase(Locale.ROOT);
        if (!NUMBERS.getSupportedRegions().contains(normalized)) {
            throw new IllegalArgumentException("A supported country or territory is required for a national number");
        }
        return normalized;
    }

    private static IllegalArgumentException invalidNumber() {
        return new IllegalArgumentException("Enter a valid phone number");
    }

    @Override
    public String toString() {
        return "PhoneNumber[redacted]";
    }
}
