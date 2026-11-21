package com.notshahrad.spring_boot_base.auth.oauth2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "base.auth.oauth2.enabled=true",
        "spring.security.oauth2.client.registration.test.client-id=dummy-client",
        "spring.security.oauth2.client.registration.test.client-secret=dummy-secret",
        "spring.security.oauth2.client.registration.test.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.test.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.registration.test.scope=openid,profile,email",
        "spring.security.oauth2.client.registration.test.provider=test",
        "spring.security.oauth2.client.provider.test.authorization-uri=https://issuer.example/authorize",
        "spring.security.oauth2.client.provider.test.token-uri=https://issuer.example/token",
        "spring.security.oauth2.client.provider.test.jwk-set-uri=https://issuer.example/keys",
        "spring.security.oauth2.client.provider.test.user-name-attribute=sub"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OidcLoginConfigurationMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void configuredClientRedirectsToProviderWithoutCallingRealProvider() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/test"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("https://issuer.example/authorize")));
    }

    @Test
    void ordinaryAnonymousApplicationRoutesStayProtectedWhenOauthIsEnabled() throws Exception {
        mockMvc.perform(get("/api/example/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
