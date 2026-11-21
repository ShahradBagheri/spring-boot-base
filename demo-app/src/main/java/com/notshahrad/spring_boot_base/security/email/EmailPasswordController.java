package com.notshahrad.spring_boot_base.security.email;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.notshahrad.spring_boot_base.auth.email.EmailPasswordService;
import com.notshahrad.spring_boot_base.auth.email.LoginEmail;
import com.notshahrad.spring_boot_base.common.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/auth/email")
@ConditionalOnProperty(prefix = "base.auth.email", name = {"enabled", "password-enabled"}, havingValue = "true")
class EmailPasswordController {

    private final EmailPasswordService passwords;
    private final EmailChallengeDelivery delivery;
    private final HttpSessionSecurityContextRepository sessions = new HttpSessionSecurityContextRepository();
    private final ChangeSessionIdAuthenticationStrategy fixation = new ChangeSessionIdAuthenticationStrategy();
    private final CsrfAuthenticationStrategy csrf = new CsrfAuthenticationStrategy(new HttpSessionCsrfTokenRepository());
    private final Cache<String, AtomicInteger> attempts = Caffeine.newBuilder()
            .maximumSize(50_000).expireAfterWrite(Duration.ofMinutes(1)).build();

    EmailPasswordController(EmailPasswordService passwords, EmailChallengeDelivery delivery) {
        this.passwords = passwords;
        this.delivery = delivery;
    }

    @GetMapping("/csrf")
    ResponseEntity<CsrfToken> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(token);
    }

    @PostMapping(path = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> signup(@RequestBody EmailRequest body, HttpServletRequest request) {
        return requestChallenge(body, request, false);
    }

    @PostMapping(path = "/reset/request", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> resetRequest(@RequestBody EmailRequest body, HttpServletRequest request) {
        return requestChallenge(body, request, true);
    }

    @PostMapping(path = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> verify(@RequestBody Redemption body, HttpServletRequest request) {
        return complete(body, request, false);
    }

    @PostMapping(path = "/reset/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> reset(@RequestBody Redemption body, HttpServletRequest request) {
        return complete(body, request, true);
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> login(@RequestBody Login body, HttpServletRequest request, HttpServletResponse response) {
        if (!allow("request:" + request.getRemoteAddr(), 30)) {
            return tooManyRequests();
        }
        var address = parseAddress(body.email());
        if (address == null) {
            return error(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (!allow("login:" + address.value(), 8)) {
            return tooManyRequests();
        }
        var principal = passwords.authenticate(address, body.password());
        if (principal.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal.get(), null, List.of());
        fixation.onAuthentication(authentication, request, response);
        csrf.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        sessions.saveContext(context, request, response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<?> requestChallenge(EmailRequest body, HttpServletRequest request, boolean reset) {
        if (!allow("request:" + request.getRemoteAddr(), 30)) {
            return tooManyRequests();
        }
        var address = parseAddress(body.email());
        if (address != null && allow("delivery:" + address.value(), 3)) {
            delivery.enqueue(address, reset);
        }
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<?> complete(Redemption body, HttpServletRequest request, boolean reset) {
        if (!allow("request:" + request.getRemoteAddr(), 30)) {
            return tooManyRequests();
        }
        boolean success;
        try {
            success = reset ? passwords.completeReset(body.token(), body.password())
                    : passwords.completeSignup(body.token(), body.password());
        } catch (DuplicateKeyException ex) {
            success = false;
        }
        return success ? ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
                : error(HttpStatus.BAD_REQUEST, "Invalid challenge or password");
    }

    private boolean allow(String key, int limit) {
        return attempts.get(key, ignored -> new AtomicInteger()).incrementAndGet() <= limit;
    }

    private static LoginEmail parseAddress(String value) {
        try {
            return new LoginEmail(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static ResponseEntity<ApiError> tooManyRequests() {
        return error(HttpStatus.TOO_MANY_REQUESTS, "Try again later");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(ApiError.of(status.value(), message));
    }

    record EmailRequest(String email) {
        @Override
        public String toString() {
            return "EmailRequest[redacted]";
        }
    }

    record Login(String email, String password) {
        @Override
        public String toString() {
            return "Login[redacted]";
        }
    }

    record Redemption(String token, String password) {
        @Override
        public String toString() {
            return "Redemption[redacted]";
        }
    }
}
