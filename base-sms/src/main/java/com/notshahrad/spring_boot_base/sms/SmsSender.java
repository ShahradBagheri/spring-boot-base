package com.notshahrad.spring_boot_base.sms;

@FunctionalInterface
public interface SmsSender {

    void send(SmsMessage message);
}
