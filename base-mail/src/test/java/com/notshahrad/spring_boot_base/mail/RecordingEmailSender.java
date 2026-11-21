package com.notshahrad.spring_boot_base.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RecordingEmailSender implements EmailSender {

    private final List<EmailMessage> messages = new ArrayList<>();

    @Override
    public synchronized void send(EmailMessage message) {
        messages.add(Objects.requireNonNull(message, "message"));
    }

    public synchronized List<EmailMessage> messages() {
        return List.copyOf(messages);
    }
}
