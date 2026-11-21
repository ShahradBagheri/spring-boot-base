package com.notshahrad.spring_boot_base.security.email;

import com.notshahrad.spring_boot_base.auth.email.EmailChallengeTemplates;
import com.notshahrad.spring_boot_base.auth.email.EmailChallengeTemplates.Challenge;
import com.notshahrad.spring_boot_base.auth.email.EmailPasswordService;
import com.notshahrad.spring_boot_base.auth.email.LoginEmail;
import com.notshahrad.spring_boot_base.mail.EmailDeliveryException;
import com.notshahrad.spring_boot_base.mail.EmailMessage;
import com.notshahrad.spring_boot_base.mail.EmailSender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class EmailChallengeDeliveryTest {

    private static final LoginEmail ADDRESS = new LoginEmail("recipient@example.test");
    private static final String PROOF = "sensitive-proof-for-test";
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final EmailPasswordService passwords = mock(EmailPasswordService.class);

    @Test
    void dispatchUsesTheApplicationTemplateForEachPurpose() {
        var outgoing = new ArrayList<EmailMessage>();
        var delivery = worker(outgoing::add, new EmailChallengeTemplates() {
            @Override
            public Content signup(Challenge challenge) {
                return message("Custom signup", challenge);
            }

            @Override
            public Content passwordReset(Challenge challenge) {
                return message("Custom reset", challenge);
            }

            private Content message(String subject, Challenge challenge) {
                return new Content(subject, challenge.link().toString(), null);
            }
        });
        when(passwords.requestSignup(ADDRESS)).thenReturn(Optional.of(new EmailPasswordService.Delivery(PROOF)));
        when(passwords.requestReset(ADDRESS)).thenReturn(Optional.of(new EmailPasswordService.Delivery(PROOF)));

        delivery.enqueue(ADDRESS, false);
        delivery.enqueue(ADDRESS, true);

        assertThat(metrics.get("auth.challenge.delivery").tags("channel", "email", "outcome", "sent").counter().count()).isEqualTo(2);
        assertThat(metrics.get("auth.challenge.delivery").tags("channel", "email", "outcome", "queued").counter().count()).isEqualTo(2);
        assertThat(outgoing).extracting(EmailMessage::recipient).containsExactly(ADDRESS.value(), ADDRESS.value());
        assertThat(outgoing).extracting(EmailMessage::subject).containsExactly("Custom signup", "Custom reset");
        assertThat(outgoing).extracting(EmailMessage::textBody).containsExactly(
                "https://client.example.test/verify-email#token=" + PROOF,
                "https://client.example.test/reset-password#token=" + PROOF);
    }

    @Test
    void skipsDeliveryWhenThereIsNoEligibleChallenge() {
        var outgoing = new ArrayList<EmailMessage>();
        var delivery = worker(outgoing::add, null);
        when(passwords.requestSignup(ADDRESS)).thenReturn(Optional.empty());
        when(passwords.requestReset(ADDRESS)).thenReturn(Optional.empty());

        delivery.enqueue(ADDRESS, false);
        delivery.enqueue(ADDRESS, true);

        assertThat(outgoing).isEmpty();
    }

    @Test
    void reportsFailuresWithoutLoggingTheRecipientOrProof(CapturedOutput output) {
        when(passwords.requestReset(ADDRESS)).thenReturn(Optional.of(new EmailPasswordService.Delivery(PROOF)));
        List<EmailSender> failingSenders = List.of(
                message -> { throw new EmailDeliveryException(EmailDeliveryException.Reason.TRANSPORT_FAILED); },
                message -> { throw new IllegalStateException(message.recipient() + " " + message.textBody()); });

        for (var sender : failingSenders) {
            worker(sender, null).enqueue(ADDRESS, true);
        }

        assertThat(metrics.get("auth.challenge.delivery").tags("channel", "email", "outcome", "failed").counter().count()).isEqualTo(2);
        assertThat(output.getAll()).contains("Email challenge could not be delivered (TRANSPORT_FAILED)")
                .contains("Email challenge processing failed (IllegalStateException)")
                .doesNotContain(ADDRESS.value(), PROOF);
    }

    @Test
    void countsRejectedEmailSubmissions() {
        var factory = new StaticListableBeanFactory();
        var worker = new EmailChallengeDelivery(passwords, mock(EmailSender.class),
                factory.getBeanProvider(EmailChallengeTemplates.class),
                task -> { throw new TaskRejectedException("full"); }, "https://client.example.test", metrics);
        worker.enqueue(ADDRESS, false);
        assertThat(metrics.get("auth.challenge.delivery").tags("channel", "email", "outcome", "rejected")
                .counter().count()).isEqualTo(1);
    }

    private EmailChallengeDelivery worker(EmailSender sender, EmailChallengeTemplates templates) {
        var factory = new StaticListableBeanFactory();
        if (templates != null) {
            factory.addBean("emailChallengeTemplates", templates);
        }
        return new EmailChallengeDelivery(passwords, sender, factory.getBeanProvider(EmailChallengeTemplates.class),
                new SyncTaskExecutor(), "https://client.example.test", metrics);
    }
}
