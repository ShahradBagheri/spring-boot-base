package com.notshahrad.spring_boot_base.security.phone;

import com.notshahrad.spring_boot_base.auth.phone.PhoneAuthService;
import com.notshahrad.spring_boot_base.auth.phone.PhoneNumber;
import com.notshahrad.spring_boot_base.auth.phone.PhoneOtpService.Purpose;
import com.notshahrad.spring_boot_base.sms.SmsMessage;
import com.notshahrad.spring_boot_base.sms.SmsSender;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "base.auth.phone", name = {"enabled", "otp.enabled", "web.enabled"},
        havingValue = "true")
class PhoneChallengeDelivery {

    private static final Logger log = LoggerFactory.getLogger(PhoneChallengeDelivery.class);
    private final PhoneAuthService auth;
    private final SmsSender sender;
    private final TaskExecutor executor;
    private final MeterRegistry metrics;

    PhoneChallengeDelivery(PhoneAuthService auth, SmsSender sender,
                           @Qualifier("phoneChallengeExecutor") TaskExecutor executor, MeterRegistry metrics) {
        this.auth = auth;
        this.sender = sender;
        this.executor = executor;
        this.metrics = metrics;
    }

    void enqueue(PhoneNumber number, Purpose purpose, UUID challengeId) {
        try {
            executor.execute(() -> deliver(number, purpose, challengeId));
            count("queued");
        } catch (TaskRejectedException ex) {
            count("rejected");
            log.warn("Phone authentication delivery queue is full");
        }
    }

    private void deliver(PhoneNumber number, Purpose purpose, UUID challengeId) {
        try {
            auth.request(number, purpose, challengeId).ifPresent(otp -> {
                sender.send(new SmsMessage(number.e164(), "Your verification code is " + otp.code()));
                count("sent");
            });
        } catch (RuntimeException ex) {
            count("failed");
            log.warn("Phone authentication delivery failed ({})", ex.getClass().getSimpleName());
        }
    }

    private void count(String outcome) {
        metrics.counter("auth.challenge.delivery", "channel", "sms", "outcome", outcome).increment();
    }
}
