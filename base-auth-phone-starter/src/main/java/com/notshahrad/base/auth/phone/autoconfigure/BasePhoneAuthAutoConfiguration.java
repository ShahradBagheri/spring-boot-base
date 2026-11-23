package com.notshahrad.base.auth.phone.autoconfigure;

import com.notshahrad.base.auth.phone.PhoneOtpDelivery;
import com.notshahrad.spring_boot_base.auth.phone.PhoneAuthService;
import com.notshahrad.spring_boot_base.auth.phone.PhoneIdentityRepository;
import com.notshahrad.spring_boot_base.auth.phone.PhoneOtpService;
import com.notshahrad.spring_boot_base.sms.SmsSender;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(prefix = "base.auth.phone", name = "enabled", havingValue = "true")
@Import({PhoneIdentityRepository.class, PhoneOtpService.class, PhoneAuthService.class})
public class BasePhoneAuthAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "base.auth.phone.otp", name = "enabled", havingValue = "true")
    PhoneOtpDelivery basePhoneOtpDelivery(SmsSender sender) {
        return new PhoneOtpDelivery(sender);
    }
}
