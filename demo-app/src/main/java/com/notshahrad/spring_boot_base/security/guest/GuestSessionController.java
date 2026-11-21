package com.notshahrad.spring_boot_base.security.guest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guest")
@ConditionalOnProperty(prefix = "base.auth.guest", name = "enabled", havingValue = "true")
class GuestSessionController {

    private final GuestSessions sessions;

    GuestSessionController(GuestSessions sessions) {
        this.sessions = sessions;
    }

    @GetMapping("/csrf")
    ResponseEntity<CsrfToken> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(token);
    }

    @PostMapping("/session")
    ResponseEntity<GuestSessions.Identity> start(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.start(request));
    }

    @GetMapping("/session")
    ResponseEntity<GuestSessions.Identity> current(HttpServletRequest request) {
        var guest = sessions.current(request)
                .orElseThrow(() -> new InsufficientAuthenticationException("Guest session required"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(guest);
    }

    @DeleteMapping("/session")
    ResponseEntity<Void> end(HttpServletRequest request) {
        sessions.end(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
