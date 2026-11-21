package com.notshahrad.base.mcp;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

final class McpRateLimitFilter extends OncePerRequestFilter {

    private final RequestMatcher endpoint;
    private final int perSubject;
    private final int overall;
    private final Cache<String, AtomicInteger> subjects = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1)).maximumSize(10_000).build();
    private final Cache<String, AtomicInteger> total = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1)).build();

    McpRateLimitFilter(RequestMatcher endpoint, int perSubject, int overall) {
        this.endpoint = endpoint;
        this.perSubject = perSubject;
        this.overall = overall;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !endpoint.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String subject = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!take(subjects.get(subject, ignored -> new AtomicInteger()), perSubject)
                || !take(total.get("mcp", ignored -> new AtomicInteger()), overall)) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "60");
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean take(AtomicInteger counter, int limit) {
        return counter.getAndUpdate(count -> count < limit ? count + 1 : count) < limit;
    }
}
