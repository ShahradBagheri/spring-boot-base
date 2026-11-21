package com.notshahrad.spring_boot_base.auth.email;

import java.util.Locale;
import java.util.regex.Pattern;

public record LoginEmail(String value) {

    private static final Pattern DOT_ATOM = Pattern.compile(
            "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*");
    private static final Pattern DNS_LABEL = Pattern.compile("[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?");

    public LoginEmail {
        if (value == null || value.chars().anyMatch(Character::isISOControl)) {
            throw invalidAddress();
        }

        String address = value.strip();
        int at = address.indexOf('@');
        if (address.length() > 254 || at < 1 || at > 64 || at != address.lastIndexOf('@')) {
            throw invalidAddress();
        }

        String localPart = address.substring(0, at);
        String domain = address.substring(at + 1);
        String[] labels = domain.split("\\.", -1);
        if (!DOT_ATOM.matcher(localPart).matches() || labels.length < 2) {
            throw invalidAddress();
        }
        for (String label : labels) {
            if (!DNS_LABEL.matcher(label).matches()) {
                throw invalidAddress();
            }
        }
        value = localPart + "@" + domain.toLowerCase(Locale.ROOT);
    }

    private static IllegalArgumentException invalidAddress() {
        return new IllegalArgumentException("Enter a valid email address");
    }

    @Override
    public String toString() {
        return "LoginEmail[redacted]";
    }
}
