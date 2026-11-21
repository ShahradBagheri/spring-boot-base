package com.notshahrad.spring_boot_base.security.guest;

import com.notshahrad.spring_boot_base.auth.LocalUserPrincipal;
import com.notshahrad.spring_boot_base.common.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/guest/drafts")
@ConditionalOnProperty(prefix = "base.auth.guest", name = {"enabled", "drafts.enabled"}, havingValue = "true")
class GuestDraftController {

    private final GuestSessions sessions;
    private final GuestDraftService drafts;

    GuestDraftController(GuestSessions sessions, GuestDraftService drafts) {
        this.sessions = sessions;
        this.drafts = drafts;
    }

    @GetMapping("/current")
    ResponseEntity<Draft> current(HttpServletRequest request) {
        return read(sessions.withCurrent(request, guest -> drafts.guestDraft(guest.guestId()))
                .orElseThrow(GuestDraftController::missingGuest));
    }

    @PutMapping("/current")
    ResponseEntity<Void> save(HttpServletRequest request, @Valid @RequestBody Draft draft) {
        if (!sessions.withCurrent(request, guest -> drafts.save(guest, draft.content())).orElse(false)) {
            throw missingGuest();
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/csrf")
    ResponseEntity<CsrfToken> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(token);
    }

    @GetMapping("/account")
    ResponseEntity<Draft> account(@AuthenticationPrincipal LocalUserPrincipal principal) {
        return read(drafts.accountDraft(accountId(principal)));
    }

    @PostMapping("/claim")
    ResponseEntity<?> claim(HttpServletRequest request, @AuthenticationPrincipal LocalUserPrincipal principal) {
        UUID accountId = accountId(principal);
        var result = sessions.withCurrent(request, guest -> {
            var claim = drafts.claim(guest.guestId(), accountId);
            if (claim == GuestDraftService.Claim.MOVED) {
                sessions.end(request);
            }
            return claim;
        }).orElse(GuestDraftService.Claim.ABSENT);
        if (result == GuestDraftService.Claim.CONFLICT) {
            return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore())
                    .body(ApiError.of(409, "An account draft already exists"));
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static ResponseEntity<Draft> read(Optional<String> content) {
        if (content.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Draft(content.get()));
    }

    private static UUID accountId(LocalUserPrincipal principal) {
        if (principal == null || principal.getLocalUserId() == null) {
            throw new AccessDeniedException("Local account required");
        }
        return principal.getLocalUserId();
    }

    private static InsufficientAuthenticationException missingGuest() {
        return new InsufficientAuthenticationException("Guest session required");
    }

    record Draft(@NotNull @Size(max = 2000) String content) {
    }
}
