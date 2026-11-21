package com.notshahrad.spring_boot_base.mail.smtp;

import com.notshahrad.spring_boot_base.mail.EmailDeliveryException;
import com.notshahrad.spring_boot_base.mail.EmailDeliveryException.Reason;
import com.notshahrad.spring_boot_base.mail.EmailMessage;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmtpEmailSenderTest {

    @Test
    void requiresCertificateVerificationAndConfiguresFiniteNetworkTimeouts() {
        var startTls = props(SmtpProperties.Security.STARTTLS).transportProperties();
        assertThat(startTls).containsEntry("mail.smtp.starttls.enable", "true")
                .containsEntry("mail.smtp.starttls.required", "true")
                .containsEntry("mail.smtp.ssl.checkserveridentity", "true")
                .containsEntry("mail.smtp.connectiontimeout", "3000")
                .containsEntry("mail.smtp.timeout", "5000")
                .containsEntry("mail.smtp.writetimeout", "5000")
                .containsEntry("mail.smtp.auth", "true");

        var implicit = props(SmtpProperties.Security.IMPLICIT_TLS);
        assertThat(implicit.port()).isEqualTo(465);
        assertThat(implicit.transportProperties()).containsEntry("mail.smtp.ssl.enable", "true")
                .containsEntry("mail.smtp.ssl.checkserveridentity", "true")
                .containsEntry("mail.smtp.starttls.enable", "false");
        assertThat(implicit.toString()).doesNotContain("test-passphrase");
    }

    @Test
    void rejectsTimeoutsThatWouldBecomeInfinite() {
        assertThrows(IllegalArgumentException.class, () -> new SmtpProperties(
                "smtp.example.test", null, "sender@example.test", null, null, SmtpProperties.Security.STARTTLS,
                Duration.ZERO, Duration.ofSeconds(5), Duration.ofSeconds(5)));
    }

    @Test
    void sanitizesAuthenticationAndDeliveryFailures() {
        assertSafeFailure(new MailAuthenticationException("test-passphrase rejected for receiver@example.test"),
                Reason.AUTHENTICATION_FAILED);
        assertSafeFailure(new MailSendException("Socket or SMTP diagnostic with receiver@example.test"),
                Reason.TRANSPORT_FAILED);
    }

    @Test
    void rejectsInvalidRecipientBeforeCallingTheTransport() {
        var transport = new JavaMailSenderImpl() {
            @Override
            public void send(MimeMessage... messages) {
                throw new AssertionError("An invalid message reached SMTP");
            }
        };
        var sender = new SmtpEmailSender(transport, "sender@example.test");
        var failure = assertThrows(EmailDeliveryException.class,
                () -> sender.send(new EmailMessage("receiver@example.test>", "Subject", "Sensitive body", null)));
        assertThat(failure.getReason()).isEqualTo(Reason.INVALID_MESSAGE);
    }

    private static void assertSafeFailure(MailException underlying, Reason expected) {
        var transport = new JavaMailSenderImpl() {
            @Override
            public void send(MimeMessage... messages) {
                throw underlying;
            }
        };
        var sender = new SmtpEmailSender(transport, "sender@example.test");
        var failure = assertThrows(EmailDeliveryException.class,
                () -> sender.send(new EmailMessage("receiver@example.test", "Subject", "Sensitive body", null)));
        assertThat(failure.getReason()).isEqualTo(expected);
        assertThat(failure.getMessage()).doesNotContain("receiver@example.test", "Sensitive body", "test-passphrase");
        assertThat(failure.getCause()).isNull();
    }

    private static SmtpProperties props(SmtpProperties.Security security) {
        return new SmtpProperties("smtp.example.test", null, "sender@example.test",
                "smtp-user", "test-passphrase", security,
                Duration.ofSeconds(3), Duration.ofSeconds(5), Duration.ofSeconds(5));
    }
}
