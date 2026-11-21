package com.notshahrad.spring_boot_base.mail.smtp;

import com.notshahrad.spring_boot_base.mail.EmailDeliveryException;
import com.notshahrad.spring_boot_base.mail.EmailDeliveryException.Reason;
import com.notshahrad.spring_boot_base.mail.EmailMessage;
import com.notshahrad.spring_boot_base.mail.EmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class SmtpEmailSender implements EmailSender {

    private final JavaMailSender transport;
    private final String from;

    SmtpEmailSender(JavaMailSender transport, String from) {
        this.transport = transport;
        this.from = from;
    }

    @Override
    public void send(EmailMessage message) {
        Objects.requireNonNull(message, "message");
        try {
            transport.send(prepare(message));
        } catch (MessagingException ex) {
            throw new EmailDeliveryException(Reason.INVALID_MESSAGE);
        } catch (MailAuthenticationException ex) {
            throw new EmailDeliveryException(Reason.AUTHENTICATION_FAILED);
        } catch (MailException ex) {
            throw new EmailDeliveryException(Reason.TRANSPORT_FAILED);
        }
    }

    private MimeMessage prepare(EmailMessage message) throws MessagingException {
        var mime = transport.createMimeMessage();
        boolean text = message.textBody() != null && !message.textBody().isBlank();
        boolean html = message.htmlBody() != null && !message.htmlBody().isBlank();
        var helper = new MimeMessageHelper(mime, text && html, StandardCharsets.UTF_8.name());
        helper.setFrom(mailbox(from));
        helper.setTo(mailbox(message.recipient()));
        helper.setSubject(message.subject());
        if (text && html) {
            helper.setText(message.textBody(), message.htmlBody());
        } else {
            helper.setText(html ? message.htmlBody() : message.textBody(), html);
        }
        return mime;
    }

    static InternetAddress mailbox(String value) throws AddressException {
        if (value == null || value.indexOf('@') <= 0 || value.endsWith("@")
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new AddressException("Expected a single mailbox");
        }
        var address = new InternetAddress(value, true);
        address.validate();
        if (address.isGroup() || !value.equals(address.getAddress())) {
            throw new AddressException("Expected a single mailbox");
        }
        return address;
    }
}
