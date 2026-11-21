package com.notshahrad.spring_boot_base.mail;

import java.util.Objects;

public final class EmailNotifications {

    private final EmailSender sender;

    public EmailNotifications(EmailSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    public <T> void send(EmailTemplate<T> template, T data) {
        sender.send(template.render(data));
    }
}
