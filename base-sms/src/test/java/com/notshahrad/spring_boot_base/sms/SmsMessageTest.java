package com.notshahrad.spring_boot_base.sms;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmsMessageTest {

    @Test
    void capturesMessagesInAReplaceableSenderWithoutSendingThem() {
        var fake = new RecordingSmsSender();
        SmsSender sender = fake;
        var first = new SmsMessage("+12025550123", "Example verification code");
        var second = new SmsMessage("+12025550124", "Second example");

        sender.send(first);
        var snapshot = fake.messages();
        sender.send(second);

        assertEquals(List.of(first), snapshot);
        assertEquals(List.of(first, second), fake.messages());
        assertFalse(first.toString().contains(first.recipient()));
        assertFalse(first.toString().contains(first.text()));
    }

    @Test
    void requiresOneCanonicalRecipientAndANonblankBody() {
        assertThrows(IllegalArgumentException.class, () -> new SmsMessage("2025550123", "Message"));
        assertThrows(IllegalArgumentException.class, () -> new SmsMessage("+12025550123,+12025550124", "Message"));
        assertThrows(IllegalArgumentException.class, () -> new SmsMessage("+12025550123", " "));
    }
}
