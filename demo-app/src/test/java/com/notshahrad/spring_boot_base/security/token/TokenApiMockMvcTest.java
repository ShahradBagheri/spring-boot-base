package com.notshahrad.spring_boot_base.security.token;

import com.notshahrad.spring_boot_base.auth.LocalUserPrincipal;
import com.notshahrad.spring_boot_base.auth.token.AccessTokenIssuer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
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

    @TempDir
    static Path keyDirectory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenIssuer issuer;

    @DynamicPropertySource
    static void tokenEnvironment(DynamicPropertyRegistry registry) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        Path privateKey = writeKey("private.pem", "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        Path publicKey = writeKey("public.pem", "PUBLIC KEY", keyPair.getPublic().getEncoded());
        registry.add("APP_TOKEN_ISSUER", () -> "https://app.issuer.example");
        registry.add("APP_TOKEN_AUDIENCE", () -> "demo-api");
        registry.add("APP_TOKEN_SIGNING_KEY_ID", () -> "test-key");
        registry.add("APP_TOKEN_PRIVATE_KEY_FILE", privateKey::toString);
        registry.add("APP_TOKEN_PUBLIC_KEY_FILE", publicKey::toString);
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

    private static Path writeKey(String filename, String kind, byte[] encoded) throws Exception {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        return Files.writeString(keyDirectory.resolve(filename),
                "-----BEGIN " + kind + "-----\n" + body + "\n-----END " + kind + "-----\n");
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
