package com.notshahrad.spring_boot_base.security.email;

import com.notshahrad.spring_boot_base.auth.email.EmailPasswordService;
import com.notshahrad.spring_boot_base.auth.email.LoginEmail;
import com.notshahrad.spring_boot_base.mail.EmailDeliveryException;
import com.notshahrad.spring_boot_base.mail.EmailMessage;
import com.notshahrad.spring_boot_base.mail.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final EmailSender sender;
    private final TaskExecutor executor;
    private final URI clientOrigin;

    EmailChallengeDelivery(EmailPasswordService passwords, EmailSender sender,
                           @Qualifier("emailChallengeExecutor") TaskExecutor executor,
                           @Value("${base.auth.email.client-origin}") String configuredOrigin) {
        this.passwords = passwords;
        this.sender = sender;
        this.executor = executor;
        this.clientOrigin = requireOrigin(configuredOrigin);
    }

    void enqueue(LoginEmail address, boolean reset) {
        try {
            executor.execute(() -> deliver(address, reset));
        } catch (TaskRejectedException ex) {
            log.warn("Email challenge delivery queue is full");
        }
    }

    private void deliver(LoginEmail address, boolean reset) {
        try {
            var result = reset ? passwords.requestReset(address) : passwords.requestSignup(address);
            result.ifPresent(delivery -> {
                String subject = reset ? "Reset your password" : "Verify your email address";
                String path = reset ? "/reset-password" : "/verify-email";
                URI link = clientOrigin.resolve(path + "#token=" + delivery.token());
                sender.send(new EmailMessage(address.value(), subject,
                        subject + " using this single-use link:\n\n" + link, null));
            });
        } catch (EmailDeliveryException ex) {
            log.warn("Email challenge could not be delivered ({})", ex.getReason());
        } catch (RuntimeException ex) {
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
}
