package com.notshahrad.spring_boot_base.security.email;

import com.notshahrad.spring_boot_base.auth.email.DefaultEmailChallengeTemplates;
import com.notshahrad.spring_boot_base.auth.email.EmailChallengeTemplates;
import com.notshahrad.spring_boot_base.auth.email.EmailChallengeTemplates.Challenge;
import com.notshahrad.spring_boot_base.auth.email.EmailPasswordService;
import com.notshahrad.spring_boot_base.auth.email.LoginEmail;
import com.notshahrad.spring_boot_base.mail.EmailDeliveryException;
import com.notshahrad.spring_boot_base.mail.EmailMessage;
import com.notshahrad.spring_boot_base.mail.EmailNotifications;
import com.notshahrad.spring_boot_base.mail.EmailSender;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "base.auth.email", name = {"enabled", "password-enabled"}, havingValue = "true")
class EmailChallengeDelivery {

    private static final Logger log = LoggerFactory.getLogger(EmailChallengeDelivery.class);

    private final EmailPasswordService passwords;
    private final EmailNotifications notifications;
    private final EmailChallengeTemplates templates;
    private final TaskExecutor executor;
    private final MeterRegistry metrics;
    private final URI clientOrigin;

    EmailChallengeDelivery(EmailPasswordService passwords, EmailSender sender,
                           ObjectProvider<EmailChallengeTemplates> templates,
                           @Qualifier("emailChallengeExecutor") TaskExecutor executor,
                           @Value("${base.auth.email.client-origin}") String configuredOrigin, MeterRegistry metrics) {
        this.passwords = passwords;
        this.notifications = new EmailNotifications(sender);
        this.templates = templates.getIfAvailable(DefaultEmailChallengeTemplates::new);
        this.executor = executor;
        this.metrics = metrics;
        this.clientOrigin = requireOrigin(configuredOrigin);
    }

    void enqueue(LoginEmail address, boolean reset) {
        try {
            executor.execute(() -> deliver(address, reset));
            count("queued");
        } catch (TaskRejectedException ex) {
            count("rejected");
            log.warn("Email challenge delivery queue is full");
        }
    }

    private void deliver(LoginEmail address, boolean reset) {
        try {
            var result = reset ? passwords.requestReset(address) : passwords.requestSignup(address);
            result.ifPresent(delivery -> {
                String path = reset ? "/reset-password" : "/verify-email";
                URI link = clientOrigin.resolve(path + "#token=" + delivery.token());
                notifications.send(challenge -> {
                    var content = reset ? templates.passwordReset(challenge) : templates.signup(challenge);
                    return new EmailMessage(address.value(), content.subject(), content.textBody(), content.htmlBody());
                }, new Challenge(address, link));
                count("sent");
            });
        } catch (EmailDeliveryException ex) {
            count("failed");
            log.warn("Email challenge could not be delivered ({})", ex.getReason());
        } catch (RuntimeException ex) {
            count("failed");
            log.error("Email challenge processing failed ({})", ex.getClass().getSimpleName());
        }
    }

    private static URI requireOrigin(String value) {
        URI origin = URI.create(value);
        boolean secure = "https".equalsIgnoreCase(origin.getScheme());
        boolean local = "http".equalsIgnoreCase(origin.getScheme()) && origin.getHost() != null
                && Set.of("localhost", "127.0.0.1", "[::1]").contains(origin.getHost());
        if ((!secure && !local) || origin.getHost() == null || origin.getUserInfo() != null
                || origin.getQuery() != null || origin.getFragment() != null
                || (!origin.getPath().isEmpty() && !"/".equals(origin.getPath()))) {
            throw new IllegalArgumentException("Configure an HTTPS email client origin, or an HTTP loopback origin");
        }
        return origin;
    }

    private void count(String outcome) {
        metrics.counter("auth.challenge.delivery", "channel", "email", "outcome", outcome).increment();
    }
}
