package com.notshahrad.spring_boot_base.security.phone;

import com.notshahrad.spring_boot_base.auth.phone.PhoneAuthService;
import com.notshahrad.spring_boot_base.auth.phone.PhoneAuthService.PhonePrincipal;
import com.notshahrad.spring_boot_base.auth.phone.PhoneNumber;
import com.notshahrad.spring_boot_base.auth.phone.PhoneOtpService.Purpose;
import com.notshahrad.spring_boot_base.common.response.ApiError;
import com.notshahrad.spring_boot_base.security.BrowserSessionLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/phone")
@ConditionalOnProperty(prefix = "base.auth.phone", name = {"enabled", "otp.enabled"}, havingValue = "true")
class PhoneAuthController {

    private final PhoneAuthService auth;
    private final PhoneChallengeDelivery delivery;
    private final PhoneRateLimits limits;
    private final BrowserSessionLogin browserLogin = new BrowserSessionLogin();

    PhoneAuthController(PhoneAuthService auth, PhoneChallengeDelivery delivery, PhoneRateLimits limits) {
        this.auth = auth;
        this.delivery = delivery;
        this.limits = limits;
    }

    @GetMapping("/csrf")
    ResponseEntity<CsrfToken> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(token);
    }

    @PostMapping(path = "/{flow:signup|login}/request", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> request(@PathVariable String flow, @RequestBody OtpRequest body, HttpServletRequest request) {
        PhoneNumber phone = parse(body.phoneNumber(), body.region());
        if (!limits.request(request.getRemoteAddr(), phone)) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "Try again later");
        }
        UUID challengeId = UUID.randomUUID();
        if (phone != null) {
            delivery.enqueue(phone, purpose(flow), challengeId);
        }
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new OtpAcknowledgement(challengeId));
    }

    @PostMapping(path = "/{flow:signup|login}/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> verify(@PathVariable String flow, @RequestBody OtpProof body,
                             HttpServletRequest request, HttpServletResponse response) {
        PhoneNumber phone = parse(body.phoneNumber(), body.region());
        if (!limits.verify(request.getRemoteAddr(), phone)) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "Try again later");
        }
        if (phone == null) {
            return invalidProof();
        }
        Optional<PhonePrincipal> principal;
        try {
            principal = auth.authenticate(phone, purpose(flow), body.challengeId(), body.code());
        } catch (DuplicateKeyException ex) {
            return invalidProof();
        }
        if (principal.isEmpty()) {
            return invalidProof();
        }
        browserLogin.authenticate(principal.get(), request, response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static Purpose purpose(String flow) {
        return "signup".equals(flow) ? Purpose.SIGNUP : Purpose.LOGIN;
    }

    private static PhoneNumber parse(String number, String region) {
        try {
            return PhoneNumber.parse(number, region);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static ResponseEntity<ApiError> invalidProof() {
        return error(HttpStatus.UNAUTHORIZED, "Invalid or expired verification code");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(ApiError.of(status.value(), message));
    }

    record OtpAcknowledgement(UUID challengeId) {
    }

    record OtpRequest(String phoneNumber, String region) {
        @Override
        public String toString() {
            return "OtpRequest[redacted]";
        }
    }

    record OtpProof(String phoneNumber, String region, UUID challengeId, String code) {
        @Override
        public String toString() {
            return "OtpProof[redacted]";
        }
    }
}
