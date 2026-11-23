package example.phone;

import com.notshahrad.base.auth.phone.PhoneOtpDelivery;
import com.notshahrad.spring_boot_base.auth.phone.PhoneAuthService;
import com.notshahrad.spring_boot_base.auth.phone.PhoneIdentityRepository;
import com.notshahrad.spring_boot_base.auth.phone.PhoneNumber;
import com.notshahrad.spring_boot_base.auth.phone.PhoneOtpService;
import com.notshahrad.spring_boot_base.sms.SmsMessage;
import com.notshahrad.spring_boot_base.sms.SmsSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BasePhoneAuthStarterTest {

    private final ApplicationContextRunner consumer = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class)
            .withPropertyValues("spring.datasource.url=jdbc:h2:mem:" + UUID.randomUUID());

    private final ApplicationContextRunner otpConsumer = consumer.withPropertyValues(
            "base.auth.local.enabled=true",
            "base.auth.phone.enabled=true",
            "base.auth.phone.otp.enabled=true",
            "base.auth.phone.otp.hmac-key=" + Base64.getEncoder().encodeToString(new byte[32])
    );

    @Test
    void disabledByDefault() {
        consumer.run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(PhoneIdentityRepository.class)
                .doesNotHaveBean(PhoneOtpDelivery.class)
                .doesNotHaveBean(SmsSender.class));
    }

    @Test
    void missingSmsSenderPreventsOtpStartup() {
        otpConsumer.run(context -> assertThat(context).hasFailed()
                .getFailure().hasMessageContaining(SmsSender.class.getName()));
    }

    @Test
    void sendsThroughTheApplicationProvider() {
        var captured = new AtomicReference<SmsMessage>();
        otpConsumer.withBean(SmsSender.class, () -> captured::set).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(PhoneAuthService.class);
            var phone = new PhoneNumber("+12025550123");
            var delivery = new PhoneOtpService.Delivery(UUID.randomUUID(), "123456", Instant.now().plusSeconds(300));
            context.getBean(PhoneOtpDelivery.class).send(phone, delivery);
            assertThat(captured.get().recipient()).isEqualTo(phone.e164());
            assertThat(captured.get().text()).contains("123456");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}
