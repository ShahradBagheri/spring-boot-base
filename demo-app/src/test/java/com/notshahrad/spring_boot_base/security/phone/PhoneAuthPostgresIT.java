package com.notshahrad.spring_boot_base.security.phone;

import com.notshahrad.spring_boot_base.sms.SmsMessage;
import com.notshahrad.spring_boot_base.sms.SmsSender;
import com.notshahrad.spring_boot_base.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
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
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "base.auth.phone.rate.verifications.per-phone=12")
@AutoConfigureMockMvc
@ActiveProfiles({"auth-phone", "auth-guest", "guest-drafts"})
class PhoneAuthPostgresIT extends AbstractPostgresIntegrationTest {

    private static final String PREFIX = "/api/auth/phone";

    @Autowired
    private MockMvc http;
    @Autowired
    private JsonMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BlockingQueue<SmsMessage> sms;

    @DynamicPropertySource
    static void key(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String encoded = Base64.getEncoder().encodeToString(key);
        registry.add("PHONE_AUTH_OTP_HMAC_KEY", () -> encoded);
    }

    @Test
    void signupAndLoginResolveTheSameLocalUserWithFreshSessions() throws Exception {
        String number = "+12025550151";
        var firstSession = (MockHttpSession) http.perform(post("/api/guest/session").with(csrf()))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        http.perform(put("/api/guest/drafts/current").session(firstSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Draft before signup\"}"))
                .andExpect(status().isNoContent());

        var signup = request("signup", number, "192.0.2.51");
        var signupCode = receiveCode(number);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM phone_login_identities WHERE phone_number = ?", Integer.class, number));

        verify("login", number, signup, signupCode, new MockHttpSession()).andExpect(status().isUnauthorized());
        String beforeLogin = firstSession.getId();
        var established = session(verify("signup", number, signup, signupCode, firstSession));
        assertNotEquals(beforeLogin, established.getId());
        String user = currentUser(established);
        http.perform(get("/api/guest/drafts/account").session(established)).andExpect(status().isNotFound());
        http.perform(post("/api/guest/drafts/claim").session(established)).andExpect(status().isForbidden());
        var csrfResponse = http.perform(get("/api/guest/drafts/csrf").session(established))
                .andExpect(status().isOk()).andReturn();
        var claimCsrf = json.readTree(csrfResponse.getResponse().getContentAsString());
        http.perform(post("/api/guest/drafts/claim").session(established)
                        .header(claimCsrf.get("headerName").asString(), claimCsrf.get("token").asString()))
                .andExpect(status().isNoContent());
        http.perform(get("/api/guest/drafts/account").session(established))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Draft before signup"));
        verify("signup", number, signup, signupCode, new MockHttpSession()).andExpect(status().isUnauthorized());

        var login = request("login", number, "192.0.2.51");
        assertEquals(user, currentUser(session(verify("login", number, login, receiveCode(number), new MockHttpSession()))));
    }

    @Test
    void acknowledgesUnknownLoginWithoutExposingAnOtpOrCreatingAUser() throws Exception {
        String number = "+12025550152";
        var challenge = request("login", number, "192.0.2.52");
        verify("login", number, challenge, "000000", new MockHttpSession())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired verification code"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM phone_login_identities WHERE phone_number = ?", Integer.class, number));
    }

    @Test
    void requiresCsrfAndLimitsARecipientAcrossDifferentClients() throws Exception {
        String number = "+12025550153";
        http.perform(post(PREFIX + "/login/request").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phoneNumber", number))))
                .andExpect(status().isForbidden());
        for (int i = 1; i <= 3; i++) {
            request("login", number, "192.0.2." + (60 + i));
        }
        postRequest("login", number, "192.0.2.64").andExpect(status().isTooManyRequests());
    }

    private UUID request(String flow, String number, String ip) throws Exception {
        var result = postRequest(flow, number, ip).andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("challengeId").asString());
    }

    private ResultActions postRequest(String flow, String number, String ip) throws Exception {
        return http.perform(post(PREFIX + "/" + flow + "/request").with(csrf()).with(request -> {
            request.setRemoteAddr(ip);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("phoneNumber", number))));
    }

    private ResultActions verify(String flow, String phone, UUID id, String code, MockHttpSession session)
            throws Exception {
        return http.perform(post(PREFIX + "/" + flow + "/verify").session(session).with(csrf()).with(request -> {
            request.setRequestedSessionId(session.getId());
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("phoneNumber", phone, "challengeId", id.toString(), "code", code))));
    }

    private MockHttpSession session(ResultActions result) throws Exception {
        return (MockHttpSession) result.andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }

    private String currentUser(MockHttpSession session) throws Exception {
        var result = http.perform(get("/api/example/me").session(session)).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("username").asString();
    }

    private String receiveCode(String number) throws Exception {
        SmsMessage message = sms.poll(8, TimeUnit.SECONDS);
        assertNotNull(message);
        assertEquals(number, message.recipient());
        return message.text().substring(message.text().length() - 6);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeTransport {

        @Bean
        BlockingQueue<SmsMessage> phoneMessages() {
            return new LinkedBlockingQueue<>();
        }

        @Bean
        SmsSender fakeSmsSender(BlockingQueue<SmsMessage> phoneMessages) {
            return phoneMessages::add;
        }
    }
}
