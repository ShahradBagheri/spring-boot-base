package com.notshahrad.spring_boot_base.security.guest;

import com.notshahrad.spring_boot_base.auth.AppUser;
import com.notshahrad.spring_boot_base.auth.AppUserRepository;
import com.notshahrad.spring_boot_base.auth.phone.PhoneAuthService.PhonePrincipal;
import com.notshahrad.spring_boot_base.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "base.auth.guest.drafts.cleanup-interval=1h")
@AutoConfigureMockMvc
@ActiveProfiles({"auth-guest", "guest-drafts"})
class GuestDraftPostgresIT extends AbstractPostgresIntegrationTest {

    private static final String ROOT = "/api/guest/drafts";
    private static final String CURRENT = ROOT + "/current";
    private static final String ACCOUNT = ROOT + "/account";
    private static final String CLAIM = ROOT + "/claim";

    @Autowired
    private MockMvc http;
    @Autowired
    private JsonMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private GuestDraftService drafts;
    @Autowired
    private AppUserRepository users;

    @Test
    void scopesDataToTheSessionAndPreservesBothDraftsOnConflict() throws Exception {
        var first = guest();
        var second = guest();
        write(first, "First guest");
        http.perform(get(CURRENT).session(second.session()).param("guestId", first.id().toString()))
                .andExpect(status().isNotFound());
        write(second, "Second guest");
        readGuest(first, "First guest");
        readGuest(second, "Second guest");
        http.perform(post(CLAIM).session(first.session()).with(csrf())).andExpect(status().isUnauthorized());

        UUID account = account();
        UUID otherAccount = account();
        claim(first, account).andExpect(status().isNoContent());
        claim(first, account).andExpect(status().isNoContent());
        readAccount(account, "First guest");
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM guest_drafts WHERE id = ? AND account_id IS NULL", Integer.class, first.id()));
        http.perform(get(ACCOUNT).with(signedIn(otherAccount)).param("accountId", account.toString()))
                .andExpect(status().isNotFound());
        http.perform(get(CURRENT).session(first.session())).andExpect(status().isUnauthorized());

        claim(second, account).andExpect(status().isConflict());
        readAccount(account, "First guest");
        readGuest(second, "Second guest");
        claim(second, otherAccount).andExpect(status().isNoContent());
        readAccount(otherAccount, "Second guest");
    }

    @Test
    void removesExpiredDraftsInBatchesAndPreservesAccountAndActiveData() throws Exception {
        var older = guest();
        var expired = guest();
        var active = guest();
        var transferred = guest();
        write(older, "Oldest");
        write(expired, "Expired");
        write(active, "Active");
        write(transferred, "Transferred");

        UUID account = account();
        claim(transferred, account).andExpect(status().isNoContent());
        jdbc.update("UPDATE guest_drafts SET expires_at = now() - interval '2 days' WHERE id = ?", older.id());
        jdbc.update("UPDATE guest_drafts SET expires_at = now() - interval '1 day' WHERE id = ?", expired.id());
        http.perform(get(CURRENT).session(expired.session())).andExpect(status().isNotFound());
        claim(expired, account).andExpect(status().isNoContent());

        assertEquals(1, drafts.purgeExpired(1));
        assertEquals(0, count(older.id()));
        assertEquals(1, count(expired.id()));
        assertEquals(1, drafts.purgeExpired(1));
        assertEquals(0, count(expired.id()));
        assertEquals(0, drafts.purgeExpired(1));
        readGuest(active, "Active");
        readAccount(account, "Transferred");
    }

    private UUID account() {
        return users.saveAndFlush(new AppUser(null, false, null)).getId();
    }

    private Guest guest() throws Exception {
        var response = http.perform(post("/api/guest/session").with(csrf()))
                .andExpect(status().isOk()).andReturn();
        String id = json.readTree(response.getResponse().getContentAsString()).get("guestId").asString();
        return new Guest((MockHttpSession) response.getRequest().getSession(false), UUID.fromString(id));
    }

    private void write(Guest guest, String content) throws Exception {
        http.perform(put(CURRENT).session(guest.session()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("content", content))))
                .andExpect(status().isNoContent());
    }

    private ResultActions claim(Guest guest, UUID account) throws Exception {
        try {
            return http.perform(post(CLAIM).session(guest.session()).with(signedIn(account)).with(csrf()));
        } finally {
            guest.session().removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        }
    }

    private void readGuest(Guest guest, String content) throws Exception {
        http.perform(get(CURRENT).session(guest.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value(content));
    }

    private void readAccount(UUID account, String content) throws Exception {
        http.perform(get(ACCOUNT).with(signedIn(account)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value(content));
    }

    private int count(UUID guestId) {
        return jdbc.queryForObject("SELECT count(*) FROM guest_drafts WHERE id = ?", Integer.class, guestId);
    }

    private static RequestPostProcessor signedIn(UUID account) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(new PhonePrincipal(account), null, List.of()));
    }

    private record Guest(MockHttpSession session, UUID id) {
    }
}
