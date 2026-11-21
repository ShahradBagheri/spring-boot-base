package com.notshahrad.spring_boot_base.security.token;

import com.notshahrad.spring_boot_base.auth.LocalUserPrincipal;
import com.notshahrad.spring_boot_base.auth.OidcIdentityService;
import com.notshahrad.spring_boot_base.auth.VerifiedProviderIdentity;
import com.notshahrad.spring_boot_base.support.TokenTestKeys;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:refresh-http;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "auth-token"})
@Sql(scripts = "/db/migration/V3__create_refresh_sessions.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class RefreshSessionMockMvcTest {

    @TempDir
    static Path keyDirectory;

    @Autowired
    private MockMvc http;
    @Autowired
    private JsonMapper json;
    @Autowired
    private OidcIdentityService identities;

    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry registry) throws Exception {
        TokenTestKeys.register(registry, keyDirectory);
    }

    @Test
    void issuanceRequiresCsrfAndARealAuthenticatedLocalUser() throws Exception {
        var local = account();
        http.perform(post("/api/token/issue").session(browserSession(local)))
                .andExpect(status().isForbidden());
        http.perform(post("/api/token/issue").with(csrf()))
                .andExpect(status().isUnauthorized());
        http.perform(post("/api/token/issue").session(browserSession("username-only")).with(csrf()))
                .andExpect(status().isForbidden());
        http.perform(post("/api/token/issue").session(browserSession(new LocalAccount(UUID.randomUUID()))).with(csrf()))
                .andExpect(status().isForbidden());

        JsonNode pair = issue(local);
        assertSubject(pair, local);
        http.perform(post("/api/token/issue").with(csrf()).header(HttpHeaders.AUTHORIZATION, bearer(pair)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browserCanFetchACsrfTokenAndUseItToIssueAPair() throws Exception {
        var local = account();
        var browser = browserSession(local);
        http.perform(get("/api/token/csrf")).andExpect(status().isUnauthorized());
        JsonNode csrf = json.readTree(http.perform(get("/api/token/csrf").session(browser))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn().getResponse().getContentAsString());
        JsonNode pair = json.readTree(http.perform(post("/api/token/issue").session(browser)
                        .header(csrf.get("headerName").asString(), csrf.get("token").asString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertSubject(pair, local);
    }

    @Test
    void rotatesOnceAndReuseRevokesOnlyThatSession() throws Exception {
        var local = account();
        JsonNode first = issue(local);
        JsonNode other = issue(local);

        JsonNode rotated = successfulRefresh(first);
        assertSubject(rotated, local);
        assertEquals(first.get("refreshExpiresAt"), rotated.get("refreshExpiresAt"));
        assertNotEquals(first.get("refreshToken"), rotated.get("refreshToken"));
        assertRejectedRefresh(first);
        assertRejectedRefresh(rotated);
        assertSubject(successfulRefresh(other), local);
    }

    @Test
    void logoutRevokesTheBearerSessionAndAllSessionsLogoutIsOwnerScoped() throws Exception {
        var local = account();
        var other = account();
        JsonNode first = issue(local);
        JsonNode second = issue(local);
        JsonNode outsider = issue(other);

        http.perform(post("/api/token/logout").session(browserSession(local))).andExpect(status().isUnauthorized());
        logout("/api/token/logout", first);
        logout("/api/token/logout", first);
        assertRejectedRefresh(first);
        assertSubject(first, local);

        JsonNode renewed = successfulRefresh(second);
        logout("/api/token/logout-all", renewed);
        assertRejectedRefresh(renewed);
        assertSubject(successfulRefresh(outsider), other);
    }

    @Test
    void rejectsMissingOrUrlSuppliedRefreshCredential() throws Exception {
        var local = account();
        JsonNode issued = issue(local);
        http.perform(post("/api/token/refresh").contentType(MediaType.APPLICATION_JSON)
                        .param("refreshToken", issued.get("refreshToken").asString()).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh authentication failed"));
        assertSubject(successfulRefresh(issued), local);
    }

    private LocalAccount account() {
        return new LocalAccount(identities.resolveOrCreate(new VerifiedProviderIdentity(
                "https://test.issuer.example", UUID.randomUUID().toString(), null, false, null)));
    }

    private MockHttpSession browserSession(Object principal) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private JsonNode issue(LocalAccount local) throws Exception {
        return json.readTree(http.perform(post("/api/token/issue").session(browserSession(local)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.accessExpiresAt").exists())
                .andExpect(jsonPath("$.refreshExpiresAt").exists())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions refresh(JsonNode pair) throws Exception {
        return http.perform(post("/api/token/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", pair.get("refreshToken").asString()))));
    }

    private JsonNode successfulRefresh(JsonNode pair) throws Exception {
        return json.readTree(refresh(pair).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn().getResponse().getContentAsString());
    }

    private void assertRejectedRefresh(JsonNode pair) throws Exception {
        refresh(pair).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh authentication failed"));
    }

    private void assertSubject(JsonNode pair, LocalAccount account) throws Exception {
        http.perform(get("/api/token/me").header(HttpHeaders.AUTHORIZATION, bearer(pair)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(account.getName()));
    }

    private void logout(String path, JsonNode pair) throws Exception {
        http.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer(pair))).andExpect(status().isNoContent());
    }

    private static String bearer(JsonNode pair) {
        return "Bearer " + pair.get("accessToken").asString();
    }

    private record LocalAccount(UUID getLocalUserId) implements LocalUserPrincipal {
        @Override
        public String getName() {
            return getLocalUserId.toString();
        }
    }
}
