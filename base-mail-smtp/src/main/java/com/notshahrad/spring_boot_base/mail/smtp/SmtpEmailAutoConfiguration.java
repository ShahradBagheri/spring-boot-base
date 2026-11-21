package com.notshahrad.spring_boot_base.mail.smtp;

import com.notshahrad.spring_boot_base.mail.EmailSender;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@AutoConfiguration
@ConditionalOnProperty(prefix = "base.mail.smtp", name = "enabled", havingValue = "true")
@ConditionalOnMissingBean(EmailSender.class)
@EnableConfigurationProperties(SmtpProperties.class)
public class SmtpEmailAutoConfiguration {

    @Bean
    EmailSender smtpEmailSender(SmtpProperties properties) {
        var transport = new JavaMailSenderImpl();
        transport.setHost(properties.host());
        transport.setPort(properties.port());
        transport.setUsername(properties.username());
        transport.setPassword(properties.password());
        transport.setJavaMailProperties(properties.transportProperties());
        return new SmtpEmailSender(transport, properties.from());
    }
}
