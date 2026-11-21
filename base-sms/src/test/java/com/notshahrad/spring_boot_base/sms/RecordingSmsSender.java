package com.notshahrad.spring_boot_base.sms;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RecordingSmsSender implements SmsSender {

    private final List<SmsMessage> messages = new ArrayList<>();

    @Override
    public synchronized void send(SmsMessage message) {
        messages.add(Objects.requireNonNull(message, "message"));
    }

    public synchronized List<SmsMessage> messages() {
        return List.copyOf(messages);
    }
}
