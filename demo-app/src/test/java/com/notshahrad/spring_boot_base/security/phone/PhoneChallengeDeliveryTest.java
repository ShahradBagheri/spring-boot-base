package com.notshahrad.spring_boot_base.security.phone;

import com.notshahrad.spring_boot_base.auth.phone.PhoneAuthService;
import com.notshahrad.spring_boot_base.auth.phone.PhoneNumber;
import com.notshahrad.spring_boot_base.auth.phone.PhoneOtpService.Delivery;
import com.notshahrad.spring_boot_base.sms.SmsSender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.notshahrad.spring_boot_base.auth.phone.PhoneOtpService.Purpose.LOGIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhoneChallengeDeliveryTest {
    @Test
    void countsAcceptedAndRejectedTasksAndSuccessfulAndFailedSends() {
        var auth = mock(PhoneAuthService.class);
        var sender = mock(SmsSender.class);
        var number = PhoneNumber.parse("+12025550150", null);
        var id = UUID.randomUUID();
        var metrics = new SimpleMeterRegistry();
        when(auth.request(number, LOGIN, id)).thenReturn(Optional.of(new Delivery(id, "123456", Instant.MAX)));
        var worker = new PhoneChallengeDelivery(auth, sender, new SyncTaskExecutor(), metrics);
        worker.enqueue(number, LOGIN, id);
        doThrow(new IllegalStateException()).when(sender).send(any());
        worker.enqueue(number, LOGIN, id);
        new PhoneChallengeDelivery(auth, sender, task -> { throw new TaskRejectedException("full"); }, metrics)
                .enqueue(number, LOGIN, id);

        for (var outcome : java.util.List.of("rejected", "sent", "failed", "queued")) {
            assertThat(metrics.get("auth.challenge.delivery").tags("channel", "sms", "outcome", outcome)
                    .counter().count()).as(outcome).isEqualTo(outcome.equals("queued") ? 2 : 1);
        }
    }
}
