package com.notshahrad.spring_boot_base.mail.smtp;

import jakarta.mail.internet.AddressException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Properties;

@ConfigurationProperties("base.mail.smtp")
record SmtpProperties(
        String host,
        Integer port,
        String from,
        String username,
        String password,
        @DefaultValue("STARTTLS") Security security,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        @DefaultValue("5s") Duration writeTimeout) {

    SmtpProperties {
        if (host == null || host.isBlank() || host.chars().anyMatch(Character::isWhitespace)
                || host.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Configure base.mail.smtp.host with an SMTP hostname or address");
        }
        if (port == null) {
            port = security.defaultPort;
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("The SMTP port must be between 1 and 65535");
        }
        try {
            SmtpEmailSender.mailbox(from);
        } catch (AddressException ex) {
            throw new IllegalArgumentException("Configure base.mail.smtp.from with a sender mailbox");
        }

        username = emptyAsNull(username);
        password = emptyAsNull(password);
        if ((username == null) != (password == null)) {
            throw new IllegalArgumentException("Configure both SMTP username and password, or leave both unset");
        }
        if (username != null && security == Security.PLAINTEXT) {
            throw new IllegalArgumentException("SMTP credentials require STARTTLS or implicit TLS");
        }

        validateTimeout(connectTimeout);
        validateTimeout(readTimeout);
        validateTimeout(writeTimeout);
    }

    Properties transportProperties() {
        var properties = new Properties();
        properties.setProperty("mail.smtp.auth", Boolean.toString(username != null));
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(security == Security.STARTTLS));
        properties.setProperty("mail.smtp.starttls.required", Boolean.toString(security == Security.STARTTLS));
        properties.setProperty("mail.smtp.ssl.enable", Boolean.toString(security == Security.IMPLICIT_TLS));
        properties.setProperty("mail.smtp.ssl.checkserveridentity", Boolean.toString(security != Security.PLAINTEXT));
        properties.setProperty("mail.smtp.connectiontimeout", Long.toString(connectTimeout.toMillis()));
        properties.setProperty("mail.smtp.timeout", Long.toString(readTimeout.toMillis()));
        properties.setProperty("mail.smtp.writetimeout", Long.toString(writeTimeout.toMillis()));
        properties.setProperty("mail.debug", "false");
        return properties;
    }

    private static String emptyAsNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void validateTimeout(Duration value) {
        if (value.compareTo(Duration.ofMillis(1)) < 0 || value.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("Each SMTP timeout must be between 1 millisecond and 60 seconds");
        }
    }

    @Override
    public String toString() {
        return "SmtpProperties[redacted]";
    }

    enum Security {
        STARTTLS(587),
        IMPLICIT_TLS(465),
        PLAINTEXT(1025);

        private final int defaultPort;

        Security(int defaultPort) {
            this.defaultPort = defaultPort;
        }
    }
}
