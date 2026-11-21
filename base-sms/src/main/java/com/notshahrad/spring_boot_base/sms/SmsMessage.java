package com.notshahrad.spring_boot_base.sms;

import java.util.regex.Pattern;

public record SmsMessage(String recipient, String text) {

    private static final Pattern E164 = Pattern.compile("\\+[1-9][0-9]{1,14}");

    public SmsMessage {
        if (recipient == null || !E164.matcher(recipient).matches()) {
            throw new IllegalArgumentException("Provide one E.164 SMS recipient");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Provide a nonblank SMS body");
        }
    }

    @Override
    public String toString() {
        return "SmsMessage[redacted]";
    }
}
