package com.notshahrad.spring_boot_base.security.phone;

import com.notshahrad.spring_boot_base.auth.phone.PhoneNumber;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneRateLimitsTest {

    @Test
    void checksCanonicalPhoneIpAndOverallBudgetsIndependently() {
        var budget = new PhoneRateLimits.Budget(1, 2, 3);
        var limits = new PhoneRateLimits(new PhoneRateLimits.Properties(Duration.ofMinutes(1), budget, budget));
        var first = PhoneNumber.parse("+12025550150", null);
        var equivalent = PhoneNumber.parse("202-555-0150", "US");

        assertTrue(limits.request("192.0.2.1", first));
        assertFalse(limits.request("192.0.2.2", equivalent));
        assertTrue(limits.request("192.0.2.1", null));
        assertFalse(limits.request("192.0.2.1", null));
        assertTrue(limits.request("192.0.2.3", null));
        assertFalse(limits.request("192.0.2.4", null));

        assertTrue(limits.verify("192.0.2.1", first));
        assertFalse(limits.verify("192.0.2.2", equivalent));
    }
}
