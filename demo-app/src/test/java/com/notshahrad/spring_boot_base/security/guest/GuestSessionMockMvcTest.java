package com.notshahrad.spring_boot_base.security.guest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "base.auth.guest.lifetime=12m")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "auth-guest"})
class GuestSessionMockMvcTest {

    private static final String CSRF = "/api/guest/csrf";
    private static final String SESSION = "/api/guest/session";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper json;

    @Test
    void guestLifecycleUsesCsrfAndTheServerSession() throws Exception {
        mvc.perform(get(SESSION)).andExpect(status().isUnauthorized());
        mvc.perform(post(SESSION)).andExpect(status().isForbidden());

        var client = csrfSession();
        String before = client.session().getId();
        var created = start(client);
        String guestId = value(created, "guestId");
        assertNotEquals(before, client.session().getId());
        assertEquals(Duration.ofMinutes(12),
                Duration.between(Instant.parse(value(created, "issuedAt")), Instant.parse(value(created, "expiresAt"))));

        mvc.perform(get(SESSION).session(client.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestId").value(guestId));
        assertEquals(guestId, value(start(client), "guestId"));

        var other = csrfSession();
        mvc.perform(get(SESSION).session(other.session()).param("guestId", guestId))
                .andExpect(status().isUnauthorized());
        assertNotEquals(guestId, value(start(other), "guestId"));

        mvc.perform(delete(SESSION).session(client.session())).andExpect(status().isForbidden());
        mvc.perform(delete(SESSION).session(client.session()).header(client.header(), client.token()))
                .andExpect(status().isNoContent());
        mvc.perform(get(SESSION).session(client.session())).andExpect(status().isUnauthorized());
        assertNotEquals(guestId, value(start(client), "guestId"));
    }

    @Test
    void guestStatusCannotStandInForRegisteredAuthentication() throws Exception {
        var guest = csrfSession();
        start(guest);
        mvc.perform(get("/actuator/health").session(guest.session())).andExpect(status().isOk());
        mvc.perform(get("/api/example/me").session(guest.session())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/example/admin").session(guest.session())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/guest/unknown").session(guest.session())).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/example/me").session(guest.session()).with(user("member")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("member"));
        mvc.perform(get(SESSION).session(guest.session()).with(user("member"))).andExpect(status().isForbidden());
        mvc.perform(post(SESSION).session(guest.session()).with(user("member")).header(guest.header(), guest.token()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(SESSION).session(guest.session()).with(user("member")).header(guest.header(), guest.token()))
                .andExpect(status().isForbidden());
    }

    private CsrfSession csrfSession() throws Exception {
        var result = mvc.perform(get(CSRF)).andExpect(status().isOk()).andReturn();
        return new CsrfSession((MockHttpSession) result.getRequest().getSession(false),
                value(result, "headerName"), value(result, "token"));
    }

    private MvcResult start(CsrfSession client) throws Exception {
        return mvc.perform(post(SESSION).session(client.session()).header(client.header(), client.token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
    }

    private String value(MvcResult result, String field) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get(field).asString();
    }

    private record CsrfSession(MockHttpSession session, String header, String token) {
    }
}
