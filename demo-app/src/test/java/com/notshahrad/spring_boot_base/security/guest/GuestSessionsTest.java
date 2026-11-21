package com.notshahrad.spring_boot_base.security.guest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuestSessionsTest {

    @Test
    void expiryIsAbsoluteAndAReplacementGetsANewIdentity() {
        var request = new MockHttpServletRequest();
        var clock = mock(Clock.class);
        var now = Instant.parse("2026-01-01T10:00:00Z");
        var lifetime = Duration.ofMinutes(15);
        when(clock.instant()).thenReturn(now);
        var sessions = new GuestSessions(clock, lifetime);

        assertTrue(sessions.current(request).isEmpty());
        assertNull(request.getSession(false));
        var guest = sessions.start(request);

        when(clock.instant()).thenReturn(guest.expiresAt().minusNanos(1));
        assertEquals(guest, sessions.current(request).orElseThrow());
        assertEquals(guest, sessions.start(request));

        when(clock.instant()).thenReturn(guest.expiresAt());
        assertTrue(sessions.current(request).isEmpty());
        String previousSessionId = request.getSession().getId();
        var replacement = sessions.start(request);
        assertNotEquals(guest.guestId(), replacement.guestId());
        assertNotEquals(previousSessionId, request.getSession().getId());
        assertEquals(guest.expiresAt().plus(lifetime), replacement.expiresAt());
    }
}
