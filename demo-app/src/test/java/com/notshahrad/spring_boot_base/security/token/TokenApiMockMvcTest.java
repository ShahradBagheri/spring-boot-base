package com.notshahrad.spring_boot_base.security.token;

import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.notshahrad.spring_boot_base.auth.LocalUserPrincipal;
import com.notshahrad.spring_boot_base.auth.token.AccessTokenIssuer;
import com.notshahrad.spring_boot_base.support.TokenTestKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "auth-token"})
class TokenApiMockMvcTest {

    private static final String TOKEN_ROUTE = "/api/token/me";
    private static final UUID ACCOUNT_ID = UUID.fromString("77fa30b3-fdfd-4e78-991d-06bc68e479e3");

    private static RSAPrivateKey signingKey;

    @TempDir
    static Path keyDirectory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenIssuer issuer;

    @DynamicPropertySource
    static void tokenEnvironment(DynamicPropertyRegistry registry) throws Exception {
        signingKey = TokenTestKeys.register(registry, keyDirectory);
    }

    @Test
    void validBearerReachesTheDedicatedRouteWithoutCreatingASession() throws Exception {
        String token = issueForTestAccount();

        mockMvc.perform(get(TOKEN_ROUTE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ACCOUNT_ID.toString()))
                .andExpect(result -> assertNull(result.getRequest().getSession(false)));

        mockMvc.perform(get("/api/example/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void rejectsMissingMalformedAndQueryParameterCredentials() throws Exception {
        mockMvc.perform(get(TOKEN_ROUTE).accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.message").value("Authentication required"));

        mockMvc.perform(get(TOKEN_ROUTE).header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));

        mockMvc.perform(get(TOKEN_ROUTE).param("access_token", issueForTestAccount()))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "tampered", "iss", "aud", "token_use"})
    void invalidSignedBearerGetsTheSameClientSafe401(String invalid) throws Exception {
        String valid = issueForTestAccount();
        String token;
        if (invalid.equals("tampered")) {
            int signature = valid.lastIndexOf('.') + 1;
            char replacement = valid.charAt(signature) == 'A' ? 'B' : 'A';
            token = valid.substring(0, signature) + replacement + valid.substring(signature + 1);
        } else {
            var parsed = SignedJWT.parse(valid);
            var claims = new JWTClaimsSet.Builder(parsed.getJWTClaimsSet());
            switch (invalid) {
                case "expired" -> {
                    Instant now = Instant.now();
                    claims.issueTime(Date.from(now.minusSeconds(300))).notBeforeTime(Date.from(now.minusSeconds(300)))
                            .expirationTime(Date.from(now.minusSeconds(60)));
                }
                case "aud" -> claims.audience("foreign-api");
                default -> claims.claim(invalid, "foreign");
            }
            var signed = new SignedJWT(parsed.getHeader(), claims.build());
            signed.sign(new RSASSASigner(signingKey));
            token = signed.serialize();
        }

        mockMvc.perform(get(TOKEN_ROUTE).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void validBearerDoesNotOverrideEndpointAuthorization() throws Exception {
        mockMvc.perform(post(TOKEN_ROUTE).header(HttpHeaders.AUTHORIZATION, "Bearer " + issueForTestAccount()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void browserSessionWorksOnlyOnTheSessionChain() throws Exception {
        var session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("browser-user", null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        mockMvc.perform(get(TOKEN_ROUTE).session(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/example/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("browser-user"));
    }

    private String issueForTestAccount() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new LocalAccount(), null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            return issuer.issue().getTokenValue();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private record LocalAccount() implements LocalUserPrincipal {
        @Override
        public UUID getLocalUserId() {
            return ACCOUNT_ID;
        }

        @Override
        public String getName() {
            return ACCOUNT_ID.toString();
        }
    }
}
