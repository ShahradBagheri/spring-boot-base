package com.notshahrad.spring_boot_base.security;

import com.notshahrad.spring_boot_base.auth.LocalUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import java.util.List;

public final class BrowserSessionLogin {

    private final HttpSessionSecurityContextRepository sessions = new HttpSessionSecurityContextRepository();
    private final ChangeSessionIdAuthenticationStrategy fixation = new ChangeSessionIdAuthenticationStrategy();
    private final CsrfAuthenticationStrategy csrf = new CsrfAuthenticationStrategy(new HttpSessionCsrfTokenRepository());

    public void authenticate(LocalUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        fixation.onAuthentication(authentication, request, response);
        csrf.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        sessions.saveContext(context, request, response);
    }
}
