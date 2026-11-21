package com.notshahrad.spring_boot_base.auth.phone;

import com.notshahrad.spring_boot_base.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.notshahrad.spring_boot_base.auth.phone.PhoneOtpService.Purpose.LOGIN;
import static com.notshahrad.spring_boot_base.auth.phone.PhoneOtpService.Purpose.SIGNUP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "base.auth.phone.enabled=true",
        "base.auth.phone.otp.enabled=true",
        "base.auth.phone.otp.max-attempts=2",
        "base.auth.cleanup.enabled=true"
})
class PhoneOtpPostgresIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private PhoneOtpService otp;
    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void key(DynamicPropertyRegistry registry) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        registry.add("base.auth.phone.otp.hmac-key", () -> encoded);
    }

    @Test
    void storesAKeyedVerifierAndConsumesOnlyTheMatchingChallenge() {
        var number = phone("0130");
        var delivery = otp.issue(number, SIGNUP).orElseThrow();
        assertTrue(delivery.code().matches("[0-9]{6}"));
        assertFalse(delivery.toString().contains(delivery.code()));
        assertEquals(32, jdbc.queryForObject("""
                SELECT octet_length(verifier) FROM phone_otp_challenges WHERE challenge_id = ?
                """, Integer.class, delivery.challengeId()));
        assertTrue(otp.issue(number, SIGNUP).isEmpty());
        assertFalse(otp.verify(phone("0131"), SIGNUP, delivery.challengeId(), delivery.code()));
        assertFalse(otp.verify(number, LOGIN, delivery.challengeId(), delivery.code()));
        assertFalse(otp.verify(number, SIGNUP, UUID.randomUUID(), delivery.code()));
        assertTrue(otp.verify(number, SIGNUP, delivery.challengeId(), delivery.code()));
        assertFalse(otp.verify(number, SIGNUP, delivery.challengeId(), delivery.code()));
    }

    @Test
    void resendSupersedesTheOldCodeWithoutRenewingTimeOrAttempts() {
        var number = phone("0132");
        var first = otp.issue(number, LOGIN).orElseThrow();
        assertFalse(otp.verify(number, LOGIN, first.challengeId(), wrongCode(first.code())));
        allowResend(first.challengeId());

        var second = otp.issue(number, LOGIN).orElseThrow();
        assertEquals(first.expiresAt(), second.expiresAt());
        assertNotEquals(first.challengeId(), second.challengeId());
        assertFalse(otp.verify(number, LOGIN, first.challengeId(), first.code()));
        assertFalse(otp.verify(number, LOGIN, second.challengeId(), "invalid"));
        assertFalse(otp.verify(number, LOGIN, second.challengeId(), second.code()));

        allowResend(second.challengeId());
        assertTrue(otp.issue(number, LOGIN).isEmpty());
    }

    @Test
    void expiredChallengesAreRejectedAndCanBePurged() {
        var number = phone("0133");
        var delivery = otp.issue(number, LOGIN).orElseThrow();
        var second = otp.issue(phone("0135"), LOGIN).orElseThrow();
        var active = otp.issue(phone("0136"), LOGIN).orElseThrow();
        jdbc.update("""
                UPDATE phone_otp_challenges SET issued_at = CURRENT_TIMESTAMP - INTERVAL '10 minutes',
                    expires_at = CURRENT_TIMESTAMP - INTERVAL '5 minutes',
                    resend_after = CURRENT_TIMESTAMP - INTERVAL '9 minutes'
                WHERE challenge_id IN (?, ?)
                """, delivery.challengeId(), second.challengeId());
        assertFalse(otp.verify(number, LOGIN, delivery.challengeId(), delivery.code()));
        assertEquals(1, otp.purgeExpired(1));
        assertTrue(otp.purgeExpired() >= 1);
        assertTrue(otp.verify(phone("0136"), LOGIN, active.challengeId(), active.code()));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM phone_otp_challenges WHERE challenge_id = ?
                """, Integer.class, delivery.challengeId()));
    }

    @Test
    void simultaneousRedemptionsOnlySucceedOnce() throws Exception {
        var number = phone("0134");
        var delivery = otp.issue(number, SIGNUP).orElseThrow();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> verify = () -> {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));
            return otp.verify(number, SIGNUP, delivery.challengeId(), delivery.code());
        };

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(verify);
            var second = pool.submit(verify);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }

    private void allowResend(UUID id) {
        jdbc.update("""
                UPDATE phone_otp_challenges SET issued_at = CURRENT_TIMESTAMP - INTERVAL '2 minutes',
                    resend_after = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE challenge_id = ?
                """, id);
    }

    private static PhoneNumber phone(String suffix) {
        return PhoneNumber.parse("+1202555" + suffix, null);
    }

    private static String wrongCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }
}
