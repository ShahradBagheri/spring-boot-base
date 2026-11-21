package com.notshahrad.spring_boot_base.auth.phone;

import com.notshahrad.spring_boot_base.auth.AppUser;
import com.notshahrad.spring_boot_base.auth.AppUserRepository;
import com.notshahrad.spring_boot_base.auth.LocalUserPrincipal;
import com.notshahrad.spring_boot_base.auth.phone.PhoneOtpService.Delivery;
import com.notshahrad.spring_boot_base.auth.phone.PhoneOtpService.Purpose;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "base.auth.phone", name = {"enabled", "otp.enabled"}, havingValue = "true")
public class PhoneAuthService {

    private final PhoneOtpService otp;
    private final PhoneIdentityRepository phones;
    private final AppUserRepository users;

    public PhoneAuthService(PhoneOtpService otp, PhoneIdentityRepository phones, AppUserRepository users) {
        this.otp = otp;
        this.phones = phones;
        this.users = users;
    }

    public Optional<Delivery> request(PhoneNumber number, Purpose purpose, UUID challengeId) {
        boolean eligible = purpose == Purpose.SIGNUP ? !phones.isReserved(number) : phones.findVerified(number).isPresent();
        return eligible ? otp.issue(number, purpose, challengeId) : Optional.empty();
    }

    @Transactional
    public Optional<PhonePrincipal> authenticate(PhoneNumber number, Purpose purpose, UUID challengeId, String code) {
        if (!otp.verify(number, purpose, challengeId, code)) {
            return Optional.empty();
        }
        if (purpose == Purpose.LOGIN) {
            return phones.findVerified(number).map(identity -> new PhonePrincipal(identity.userId()));
        }
        if (phones.isReserved(number)) {
            return Optional.empty();
        }
        var user = users.saveAndFlush(new AppUser(null, false, null));
        if (!phones.bindVerified(user.getId(), number, Instant.now())) {
            throw new IllegalStateException("Unable to activate the new phone identity");
        }
        return Optional.of(new PhonePrincipal(user.getId()));
    }

    public record PhonePrincipal(UUID userId) implements LocalUserPrincipal, Serializable {
        @Override
        public UUID getLocalUserId() {
            return userId;
        }

        @Override
        public String getName() {
            return userId.toString();
        }
    }
}
