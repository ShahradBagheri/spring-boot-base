package com.notshahrad.spring_boot_base.mail;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailMessageTest {

    @Test
    void recordsPlainTextAndHtmlWithoutSendingEither() {
        var first = new EmailMessage("user@example.com", "Hello", "First line\nSecond line", null);
        var second = new EmailMessage("user@example.com", "Welcome", null, "<p>Example</p>");
        var sender = new RecordingEmailSender();

        sender.send(first);
        var snapshot = sender.messages();
        sender.send(second);

        assertEquals(List.of(first), snapshot);
        assertEquals(List.of(first, second), sender.messages());
        assertFalse(second.toString().contains(second.recipient()));
        assertFalse(second.toString().contains(second.htmlBody()));
    }

    @Test
    void rejectsHeaderControlsMultipleRecipientsAndEmptyBodies() {
        assertThrows(IllegalArgumentException.class, () ->
                new EmailMessage("user@example.com", "Hello\r\nBcc: other@example.com", "Body", null));
        assertThrows(IllegalArgumentException.class, () ->
                new EmailMessage("user@example.com,other@example.com", "Hello", "Body", null));
        assertThrows(IllegalArgumentException.class, () ->
                new EmailMessage("user@example.com", "Hello", null, " "));
    }
}
