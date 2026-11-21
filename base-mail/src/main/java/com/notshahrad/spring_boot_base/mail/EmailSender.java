package com.notshahrad.spring_boot_base.mail;

@FunctionalInterface
public interface EmailSender {

    void send(EmailMessage message);
}
