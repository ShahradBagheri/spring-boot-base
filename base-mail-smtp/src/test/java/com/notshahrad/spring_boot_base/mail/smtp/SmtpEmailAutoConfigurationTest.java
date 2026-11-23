package com.notshahrad.spring_boot_base.mail.smtp;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.notshahrad.spring_boot_base.mail.EmailDeliveryException;
import com.notshahrad.spring_boot_base.mail.EmailMessage;
import com.notshahrad.spring_boot_base.mail.EmailSender;
import jakarta.mail.Multipart;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmtpEmailAutoConfigurationTest {

    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withUserConfiguration(ExampleApplication.class);

    @Test
    void disabledConfigurationRequiresNoCredentialsOrTransport() {
        application.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(EmailSender.class));
    }

    @Test
    void enabledConfigurationBacksOffWithoutJakartaMail() {
        application.withClassLoader(new FilteredClassLoader("jakarta.mail"))
                .withPropertyValues("base.mail.smtp.enabled=true")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(EmailSender.class));
    }

    @Test
    void publishesBoundPropertyAndActivationMetadata() throws Exception {
        try (var metadata = getClass().getResourceAsStream("/META-INF/spring-configuration-metadata.json")) {
            assertThat(metadata).isNotNull();
            assertThat(new String(metadata.readAllBytes(), UTF_8))
                    .contains("base.mail.smtp.host", "base.mail.smtp.enabled");
        }
    }

    @Test
    void customSenderTakesPrecedenceWithoutSmtpSettings() {
        EmailSender custom = message -> {};
        application.withBean(EmailSender.class, () -> custom)
                .withPropertyValues("base.mail.smtp.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailSender.class);
                    assertThat(context.getBean(EmailSender.class)).isSameAs(custom);
                });
    }

    @Test
    void enabledConfigurationRejectsMissingSettingsAndPlaintextCredentials() {
        application.withPropertyValues("base.mail.smtp.enabled=true")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("Configure base.mail.smtp.host with an SMTP hostname or address"));

        application.withPropertyValues(
                        "base.mail.smtp.enabled=true",
                        "base.mail.smtp.host=127.0.0.1",
                        "base.mail.smtp.from=sender@example.test",
                        "base.mail.smtp.security=plaintext",
                        "base.mail.smtp.username=example",
                        "base.mail.smtp.password=test-only")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("SMTP credentials require STARTTLS or implicit TLS"));
    }

    @Test
    void sendsTextAndHtmlToAnEmbeddedSmtpCatcher() {
        var catcher = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        catcher.start();
        try {
            catcher.setUser("receiver@example.test", "unused-test-password");
            application.withPropertyValues(
                            "base.mail.smtp.enabled=true",
                            "base.mail.smtp.host=127.0.0.1",
                            "base.mail.smtp.port=" + catcher.getSmtp().getPort(),
                            "base.mail.smtp.security=plaintext",
                            "base.mail.smtp.from=sender@example.test")
                    .run(context -> {
                        assertThat(context).hasSingleBean(EmailSender.class);
                        context.getBean(EmailSender.class).send(new EmailMessage(
                                "receiver@example.test", "Résumé", "Plain body", "<p>HTML body</p>"));

                        assertThat(catcher.waitForIncomingEmail(5000, 1)).isTrue();
                        var received = catcher.getReceivedMessages()[0];
                        assertThat(received.getSubject()).isEqualTo("Résumé");
                        assertThat(received.getFrom()[0].toString()).isEqualTo("sender@example.test");
                        assertThat(received.getAllRecipients()[0].toString()).isEqualTo("receiver@example.test");
                        assertThat(received.getContent()).isInstanceOf(Multipart.class);
                        var mixed = (Multipart) received.getContent();
                        var related = (Multipart) mixed.getBodyPart(0).getContent();
                        var alternative = (Multipart) related.getBodyPart(0).getContent();
                        assertThat(alternative.getBodyPart(0).getContent().toString()).contains("Plain body");
                        assertThat(alternative.getBodyPart(1).getContent().toString()).contains("<p>HTML body</p>");
                    });
        } finally {
            catcher.stop();
        }
    }

    @Test
    void defaultStartTlsDoesNotFallBackToUnencryptedDelivery() {
        var catcher = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        catcher.start();
        try {
            application.withPropertyValues(
                            "base.mail.smtp.enabled=true",
                            "base.mail.smtp.host=127.0.0.1",
                            "base.mail.smtp.port=" + catcher.getSmtp().getPort(),
                            "base.mail.smtp.from=sender@example.test")
                    .run(context -> {
                        var sender = context.getBean(EmailSender.class);
                        var failure = assertThrows(EmailDeliveryException.class, () -> sender.send(
                                new EmailMessage("receiver@example.test", "Subject", "Body", null)));
                        assertThat(failure.getReason()).isEqualTo(EmailDeliveryException.Reason.TRANSPORT_FAILED);
                        assertThat(catcher.getReceivedMessages()).isEmpty();
                    });
        } finally {
            catcher.stop();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ExampleApplication {
    }
}
