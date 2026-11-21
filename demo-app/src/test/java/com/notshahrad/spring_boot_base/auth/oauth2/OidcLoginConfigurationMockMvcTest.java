package com.notshahrad.spring_boot_base.auth.oauth2;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.notshahrad.spring_boot_base.auth.AppUserRepository;
import com.notshahrad.spring_boot_base.auth.ExternalIdentityRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "base.auth.oauth2.enabled=true")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class OidcLoginConfigurationMockMvcTest {

    private static final String GOOGLE_ISSUER = "https://google.issuer.example";
    private static final String APPLE_ISSUER = "https://apple.issuer.example";
    private static final String CLIENT_ID = "test-client";
    private static final String CALLBACK = "/login/oauth2/code/";
    private static final String CURRENT_USER = "/api/example/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private ExternalIdentityRepository identities;

    @Autowired
    private RSAKey signingKey;

    @MockitoBean
    private OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokenClient;

    @Test
    void ordinaryAnonymousApplicationRoutesStayProtectedWhenOidcIsEnabled() throws Exception {
        mockMvc.perform(get(CURRENT_USER))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void loginRenewsSessionAndReturningIdentityUsesSameLocalUserWithoutProfileClaims() throws Exception {
        Authorization google = beginLogin("google", GOOGLE_ISSUER);
        String beforeLogin = google.session().getId();
        MockHttpSession signedIn = completeLogin(google, GOOGLE_ISSUER, "returning-user", "user@example.com");
        String localId = localUserId(GOOGLE_ISSUER, "returning-user");

        assertNotEquals(beforeLogin, signedIn.getId());
        assertCurrentUser(signedIn, localId);

        MockHttpSession returningSession = completeLogin(
                beginLogin("google", GOOGLE_ISSUER), GOOGLE_ISSUER, "returning-user", null);
        assertCurrentUser(returningSession, localId);
        assertEquals(1L, identities.count());
        assertEquals(1L, users.count());
    }

    @Test
    void appleFormPostWithMatchingEmailAndSubjectUsesADifferentLocalAccount() throws Exception {
        String email = "shared@example.com";
        String subject = "shared-subject";
        MockHttpSession googleSession = completeLogin(beginLogin("google", GOOGLE_ISSUER),
                GOOGLE_ISSUER, subject, email);
        Authorization apple = beginLogin("apple", APPLE_ISSUER);
        assertEquals("form_post", apple.parameter("response_mode"));

        MockHttpSession appleSession = completeLogin(apple, APPLE_ISSUER, subject, email);
        String googleUser = localUserId(GOOGLE_ISSUER, subject);
        String appleUser = localUserId(APPLE_ISSUER, subject);

        assertNotEquals(googleUser, appleUser);
        assertCurrentUser(googleSession, googleUser);
        assertCurrentUser(appleSession, appleUser);
    }

    @Test
    void invalidStateAndDeniedConsentFailWithoutExchangingACode() throws Exception {
        Authorization invalid = beginLogin("google", GOOGLE_ISSUER);
        assertFailedLogin(mockMvc.perform(get(CALLBACK + "google").session(invalid.session())
                .param("state", "wrong-state").param("code", "test-code")));

        Authorization denied = beginLogin("apple", APPLE_ISSUER);
        assertFailedLogin(mockMvc.perform(post(CALLBACK + "apple").session(denied.session())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("state", denied.parameter("state")).param("error", "access_denied")));
        verifyNoInteractions(tokenClient);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void missingOrEmptySubjectDoesNotProvisionAnAccount(String subject) throws Exception {
        Authorization google = beginLogin("google", GOOGLE_ISSUER);
        stubTokenResponse(google, GOOGLE_ISSUER, subject, "user@example.com");

        assertFailedLogin(mockMvc.perform(google.callback()));
    }

    @Test
    void rejectsIdTokenFromWrongIssuer() throws Exception {
        Authorization google = beginLogin("google", GOOGLE_ISSUER);
        stubTokenResponse(google, APPLE_ISSUER, "wrong-issuer", null);

        assertFailedLogin(mockMvc.perform(google.callback()));
    }

    @Test
    void logoutRequiresCsrfAndInvalidatesTheLocalSession() throws Exception {
        MockHttpSession session = completeLogin(beginLogin("google", GOOGLE_ISSUER),
                GOOGLE_ISSUER, "logout-user", null);
        String localId = localUserId(GOOGLE_ISSUER, "logout-user");

        mockMvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        assertCurrentUser(session, localId);

        String sessionId = session.getId();
        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(unauthenticated());
        assertTrue(session.isInvalid());
        mockMvc.perform(get(CURRENT_USER).cookie(new Cookie("JSESSIONID", sessionId)))
                .andExpect(status().isUnauthorized());
    }

    private Authorization beginLogin(String registration, String issuer) throws Exception {
        var request = mockMvc.perform(get("/oauth2/authorization/" + registration))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", startsWith(issuer + "/authorize?")))
                .andReturn();
        var parameters = UriComponentsBuilder.fromUriString(request.getResponse().getRedirectedUrl())
                .build().getQueryParams();
        return new Authorization(registration, (MockHttpSession) request.getRequest().getSession(false), parameters);
    }

    private MockHttpSession completeLogin(Authorization authorization, String issuer, String subject, String email)
            throws Exception {
        stubTokenResponse(authorization, issuer, subject, email);
        var result = mockMvc.perform(authorization.callback()).andExpect(redirectedUrl("/")).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void stubTokenResponse(Authorization authorization, String issuer, String subject, String email)
            throws JOSEException {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(subject).audience(CLIENT_ID)
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
                .claim("nonce", authorization.parameter("nonce"));
        if (email != null) {
            claims.claim("email", email).claim("email_verified", true);
        }
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        token.sign(new RSASSASigner(signingKey));

        when(tokenClient.getTokenResponse(any())).thenReturn(OAuth2AccessTokenResponse.withToken("test-access-token")
                .tokenType(OAuth2AccessToken.TokenType.BEARER).scopes(Set.of("openid")).expiresIn(300)
                .additionalParameters(Map.of("id_token", token.serialize())).build());
    }

    private String localUserId(String issuer, String subject) {
        return identities.findByIssuerAndSubject(issuer, subject).orElseThrow().getUser().getId().toString();
    }

    private void assertCurrentUser(MockHttpSession session, String localId) throws Exception {
        mockMvc.perform(get(CURRENT_USER).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(localId));
    }

    private void assertFailedLogin(ResultActions response) throws Exception {
        response.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("External authentication failed"))
                .andExpect(unauthenticated());
        assertEquals(0L, identities.count());
        assertEquals(0L, users.count());
    }

    private record Authorization(String registration, MockHttpSession session, MultiValueMap<String, String> parameters) {
        String parameter(String name) {
            return UriUtils.decode(parameters.getFirst(name), StandardCharsets.UTF_8);
        }

        MockHttpServletRequestBuilder callback() {
            String path = CALLBACK + registration;
            var request = "apple".equals(registration)
                    ? post(path).contentType(MediaType.APPLICATION_FORM_URLENCODED) : get(path);
            return request.session(session)
                    .with(callback -> {
                        callback.setRequestedSessionId(session.getId());
                        return callback;
                    })
                    .param("state", parameter("state"))
                    .param("code", "test-code");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderFixtures {

        @Bean
        ClientRegistrationRepository testRegistrations() {
            return new InMemoryClientRegistrationRepository(
                    registration("google", GOOGLE_ISSUER), registration("apple", APPLE_ISSUER));
        }

        @Bean
        RSAKey signingKey() throws JOSEException {
            return new RSAKeyGenerator(2048).generate();
        }

        @Bean
        JwtDecoderFactory<ClientRegistration> testIdTokenDecoderFactory(RSAKey key) throws JOSEException {
            var publicKey = key.toRSAPublicKey();
            return registration -> {
                var decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
                decoder.setJwtValidator(new OidcIdTokenValidator(registration));
                return decoder;
            };
        }

        private ClientRegistration registration(String id, String issuer) {
            return ClientRegistration.withRegistrationId(id).clientId(CLIENT_ID).clientSecret("dummy-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}" + CALLBACK + "{registrationId}").scope("openid")
                    .issuerUri(issuer).authorizationUri(issuer + "/authorize").tokenUri(issuer + "/token")
                    .jwkSetUri(issuer + "/keys").userNameAttributeName("sub").build();
        }
    }
}
