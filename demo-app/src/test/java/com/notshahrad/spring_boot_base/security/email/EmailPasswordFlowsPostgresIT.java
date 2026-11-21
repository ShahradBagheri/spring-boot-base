package com.notshahrad.spring_boot_base.security.email;

import com.notshahrad.spring_boot_base.mail.EmailMessage;
import com.notshahrad.spring_boot_base.mail.EmailSender;
import com.notshahrad.spring_boot_base.support.AbstractPostgresIntegrationTest;
import com.notshahrad.spring_boot_base.support.TokenTestKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "base.auth.email.enabled=true",
        "base.auth.email.password-enabled=true",
        "base.auth.email.client-origin=https://client.example.test"
})
@AutoConfigureMockMvc
@ActiveProfiles("auth-token")
class EmailPasswordFlowsPostgresIT extends AbstractPostgresIntegrationTest {

    private static final String PREFIX = "/api/auth/email";
    private static final String ORIGINAL_PASSWORD = "Original example passphrase!";
    private static final String NEW_PASSWORD = "A different test passphrase!";

    @TempDir
    static Path keyDirectory;

    @Autowired
    private MockMvc http;
    @Autowired
    private JsonMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BlockingQueue<EmailMessage> mail;

    @DynamicPropertySource
    static void tokenKeys(DynamicPropertyRegistry registry) throws Exception {
        TokenTestKeys.register(registry, keyDirectory);
    }

    @Test
    void verifiesOwnershipBeforeActivatingPasswordAndCreatesALocalSession() throws Exception {
        var browser = browser("192.0.2.1");
        http.perform(post(PREFIX + "/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", browser.email()))))
                .andExpect(status().isForbidden());

        String challenge = requestChallenge(browser, "/signup", "/verify-email");
        String storedHash = jdbc.queryForObject("""
                SELECT token_hash FROM email_auth_challenges WHERE purpose = 'SIGNUP' AND address = ?
                """, String.class, browser.email());
        assertEquals(digest(challenge), storedHash);
        assertTrue(jdbc.queryForList("SELECT user_id FROM email_login_identities WHERE address = ?",
                UUID.class, browser.email()).isEmpty());
        expectLoginRejected(browser, ORIGINAL_PASSWORD);

        redeem(browser, "/reset/complete", challenge, ORIGINAL_PASSWORD).andExpect(status().isBadRequest());
        redeem(browser, "/verify", challenge, "passwordpassword").andExpect(status().isBadRequest());
        redeem(browser, "/verify", challenge, ORIGINAL_PASSWORD).andExpect(status().isNoContent());
        redeem(browser, "/verify", challenge, ORIGINAL_PASSWORD).andExpect(status().isBadRequest());

        String hash = jdbc.queryForObject("""
                SELECT p.password_hash FROM email_passwords p
                JOIN email_login_identities e ON e.user_id = p.user_id WHERE e.address = ? AND e.verified_at IS NOT NULL
                """, String.class, browser.email());
        assertTrue(hash != null && hash.startsWith("$argon2id$") && !hash.contains(ORIGINAL_PASSWORD));

        var browserSession = new MockHttpSession();
        String oldId = browserSession.getId();
        var loggedIn = login(browser, ORIGINAL_PASSWORD, browserSession);
        assertNotEquals(oldId, loggedIn.getId());
        http.perform(get("/api/example/me").session(loggedIn)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").isNotEmpty());
    }

    @Test
    void passwordResetInvalidatesExistingPasswordSessionsAndRefreshCredentials() throws Exception {
        var browser = browser("192.0.2.2");
        redeem(browser, "/verify", requestChallenge(browser, "/signup", "/verify-email"), ORIGINAL_PASSWORD)
                .andExpect(status().isNoContent());
        MockHttpSession first = login(browser, ORIGINAL_PASSWORD, new MockHttpSession());
        MockHttpSession second = login(browser, ORIGINAL_PASSWORD, new MockHttpSession());
        JsonNode pair = json.readTree(http.perform(post("/api/token/issue").with(csrf()).session(first))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        var unknown = browser("192.0.2.3");
        String unknownResponse = send(unknown, "/reset/request", Map.of("email", unknown.email()))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals("", unknownResponse);
        String reset = requestChallenge(browser, "/reset/request", "/reset-password");
        redeem(browser, "/reset/complete", reset, NEW_PASSWORD).andExpect(status().isNoContent());
        redeem(browser, "/reset/complete", reset, ORIGINAL_PASSWORD).andExpect(status().isBadRequest());
        http.perform(get("/api/example/me").session(first)).andExpect(status().isUnauthorized());
        http.perform(get("/api/example/me").session(second)).andExpect(status().isUnauthorized());

        http.perform(post("/api/token/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", pair.get("refreshToken").asString()))))
                .andExpect(status().isUnauthorized());
        expectLoginRejected(browser, ORIGINAL_PASSWORD);
        http.perform(get("/api/example/me").session(login(browser, NEW_PASSWORD, new MockHttpSession())))
                .andExpect(status().isOk());
    }

    @Test
    void expiredAndSupersededVerificationChallengesCannotBeRedeemed() throws Exception {
        var browser = browser("192.0.2.4");
        String expired = requestChallenge(browser, "/signup", "/verify-email");
        jdbc.update("""
                UPDATE email_auth_challenges SET expires_at = CURRENT_TIMESTAMP - interval '1 minute',
                    resend_after = CURRENT_TIMESTAMP - interval '2 minutes'
                WHERE purpose = 'SIGNUP' AND address = ?
                """, browser.email());
        redeem(browser, "/verify", expired, ORIGINAL_PASSWORD).andExpect(status().isBadRequest());

        String replacement = requestChallenge(browser, "/signup", "/verify-email");
        assertNotEquals(expired, replacement);
        redeem(browser, "/verify", expired, ORIGINAL_PASSWORD).andExpect(status().isBadRequest());
        redeem(browser, "/verify", replacement, ORIGINAL_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void loginAttemptsReceiveGenericResponsesAndAreRateLimited() throws Exception {
        var browser = browser("192.0.2.5");
        for (int i = 0; i < 8; i++) {
            expectLoginRejected(browser, ORIGINAL_PASSWORD);
        }
        send(browser, "/login", Map.of("email", browser.email(), "password", ORIGINAL_PASSWORD))
                .andExpect(status().isTooManyRequests());
    }

    private String requestChallenge(Browser browser, String route, String clientPath) throws Exception {
        send(browser, route, Map.of("email", browser.email())).andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
        EmailMessage message = mail.poll(8, TimeUnit.SECONDS);
        assertNotNull(message, "Expected a message from the asynchronous delivery worker");
        assertEquals(browser.email(), message.recipient());
        String marker = "https://client.example.test" + clientPath + "#token=";
        int start = message.textBody().indexOf(marker);
        assertTrue(start >= 0);
        return message.textBody().substring(start + marker.length());
    }

    private ResultActions redeem(Browser browser, String route, String proof, String password) throws Exception {
        return send(browser, route, Map.of("token", proof, "password", password));
    }

    private void expectLoginRejected(Browser browser, String password) throws Exception {
        send(browser, "/login", Map.of("email", browser.email(), "password", password))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    private MockHttpSession login(Browser browser, String password, MockHttpSession session) throws Exception {
        JsonNode token = json.readTree(http.perform(get(PREFIX + "/csrf").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var result = http.perform(post(PREFIX + "/login").session(session)
                        .header(token.get("headerName").asString(), token.get("token").asString())
                        .with(request -> {
                            request.setRemoteAddr(browser.ip());
                            request.setRequestedSessionId(session.getId());
                            return request;
                        }).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", browser.email(), "password", password))))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ResultActions send(Browser browser, String route, Map<String, String> body) throws Exception {
        return http.perform(post(PREFIX + route).with(csrf()).with(request -> {
            request.setRemoteAddr(browser.ip());
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private static String digest(String raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.US_ASCII)));
    }

    private static Browser browser(String ip) {
        return new Browser(UUID.randomUUID() + "@example.test", ip);
    }

    private record Browser(String email, String ip) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestMail {

        @Bean
        BlockingQueue<EmailMessage> passwordMail() {
            return new LinkedBlockingQueue<>();
        }

        @Bean
        EmailSender testSender(BlockingQueue<EmailMessage> passwordMail) {
            return passwordMail::add;
        }
    }
}
