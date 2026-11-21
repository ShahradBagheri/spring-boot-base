package com.notshahrad.spring_boot_base.security.token;

import com.notshahrad.spring_boot_base.auth.token.AccessTokenResourceServer;
import com.notshahrad.spring_boot_base.auth.token.RefreshSessionService;
import com.notshahrad.spring_boot_base.auth.token.RefreshSessionService.TokenPair;
import com.notshahrad.spring_boot_base.common.response.ApiError;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "base.auth.token", name = "enabled", havingValue = "true")
public class TokenApiConfiguration {

    @Bean
    @Order(0)
    SecurityFilterChain tokenApiFilterChain(HttpSecurity http, AccessTokenResourceServer resourceServer) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        var tokenRoutes = paths.matcher("/api/token/**");
        var issueRoute = paths.matcher(HttpMethod.POST, "/api/token/issue");
        http.securityMatcher(request -> tokenRoutes.matches(request) && !issueRoute.matches(request))
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(HttpMethod.GET, "/api/token/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/token/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/token/logout", "/api/token/logout-all").authenticated()
                        .anyRequest().denyAll());
        return resourceServer.configure(http).build();
    }

    @RestController
    @ConditionalOnProperty(prefix = "base.auth.token", name = "enabled", havingValue = "true")
    static class TokenUserController {

        private final RefreshSessionService sessions;

        TokenUserController(RefreshSessionService sessions) {
            this.sessions = sessions;
        }

        @GetMapping("/api/token/me")
        Map<String, String> currentUser(Principal principal) {
            return Map.of("userId", principal.getName());
        }

        @PostMapping("/api/token/issue")
        ResponseEntity<TokenPair> issue() {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.issue());
        }

        @PostMapping(path = "/api/token/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
        ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
            var refreshed = sessions.refresh(request.refreshToken());
            if (refreshed.isPresent()) {
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(refreshed.get());
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore())
                    .body(ApiError.of(401, "Refresh authentication failed"));
        }

        @PostMapping("/api/token/logout")
        ResponseEntity<Void> logout() {
            sessions.revokeCurrent();
            return ResponseEntity.noContent().build();
        }

        @PostMapping("/api/token/logout-all")
        ResponseEntity<Void> logoutAll() {
            sessions.revokeAll();
            return ResponseEntity.noContent().build();
        }

        record RefreshRequest(String refreshToken) {
            @Override
            public String toString() {
                return "RefreshRequest[redacted]";
            }
        }
    }
}
