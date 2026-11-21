package com.notshahrad.spring_boot_base.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailNotificationsTest {

    @Test
    void rendersAnApplicationTemplateAndDeliversThroughTheConfiguredSender() {
        var sender = new RecordingEmailSender();
        var notifications = new EmailNotifications(sender);
        EmailTemplate<String> invoiceTemplate = reference -> new EmailMessage(
                "accounting@example.test", "Invoice ready", "Invoice " + reference + " is ready.", null);

        notifications.send(invoiceTemplate, "invoice-17");

        assertEquals(1, sender.messages().size());
        assertEquals("Invoice invoice-17 is ready.", sender.messages().getFirst().textBody());
    }
}
