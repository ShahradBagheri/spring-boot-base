package com.notshahrad.spring_boot_base.security.guest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.WebUtils;

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

final class GuestSessions {

    private static final String ATTRIBUTE = GuestSessions.class.getName() + ".identity";

    private final Clock clock;
    private final Duration lifetime;

    GuestSessions(Clock clock, Duration lifetime) {
        this.clock = clock;
        this.lifetime = lifetime;
    }

    Identity start(HttpServletRequest request) {
        var session = request.getSession();
        synchronized (WebUtils.getSessionMutex(session)) {
            var existing = current(request);
            if (existing.isPresent()) {
                return existing.get();
            }

            request.changeSessionId();
            Instant issuedAt = clock.instant();
            var guest = new Identity(UUID.randomUUID(), issuedAt, issuedAt.plus(lifetime));
            session.setAttribute(ATTRIBUTE, guest);
            return guest;
        }
    }

    Optional<Identity> current(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null && session.getAttribute(ATTRIBUTE) instanceof Identity guest
                && clock.instant().isBefore(guest.expiresAt())) {
            return Optional.of(guest);
        }
        return Optional.empty();
    }

    <T> Optional<T> withCurrent(HttpServletRequest request, Function<Identity, T> action) {
        var session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            return current(request).map(action);
        }
    }

    void end(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            synchronized (WebUtils.getSessionMutex(session)) {
                session.removeAttribute(ATTRIBUTE);
            }
        }
    }

    record Identity(UUID guestId, Instant issuedAt, Instant expiresAt) implements Serializable {
    }
}
