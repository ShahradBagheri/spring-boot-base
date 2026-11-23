package com.notshahrad.base.auth.phone;

import com.notshahrad.spring_boot_base.auth.phone.PhoneNumber;
import com.notshahrad.spring_boot_base.auth.phone.PhoneOtpService;
import com.notshahrad.spring_boot_base.sms.SmsMessage;
import com.notshahrad.spring_boot_base.sms.SmsSender;

public class PhoneOtpDelivery {
    private final SmsSender sender;

    public PhoneOtpDelivery(SmsSender sender) {
        this.sender = sender;
    }

    public void send(PhoneNumber number, PhoneOtpService.Delivery delivery) {
        sender.send(new SmsMessage(number.e164(), "Your verification code is " + delivery.code()));
    }
}
