package com.notshahrad.base.mcp;

import com.notshahrad.spring_boot_base.SpringBootBaseApplication;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Date;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SpringBootBaseApplication.class, properties = {
        "MCP_ISSUER=https://issuer.example",
        "MCP_RESOURCE=https://api.example/mcp",
        "base.mcp.server.requests-per-subject=2",
        "spring.ai.mcp.server.streamable-http.mcp-endpoint=/misconfigured"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "mcp"})
class McpServerMockMvcTest {

    private static final String ISSUER = "https://issuer.example";
    private static final String RESOURCE = "https://api.example/mcp";
    private static final String SCOPE = "mcp:access";
    private static final RSAKey KEY = signingKey();

    @TestBean(name = "mcpJwtDecoder", enforceOverride = true)
    private JwtDecoder decoder;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Environment environment;

    @Test
    void protectsTransportAndPublishesDiscoveryMetadata() throws Exception {
        mvc.perform(initialize()).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        containsString("https://api.example/.well-known/oauth-protected-resource/mcp")));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(new TestingAuthenticationToken("browser-user", null, "ROLE_ADMIN")));
        mvc.perform(initialize().session(session)).andExpect(status().isUnauthorized());
        mvc.perform(initialize().header(HttpHeaders.ORIGIN, "https://untrusted.example"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/.well-known/oauth-protected-resource/mcp")).andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value(RESOURCE))
                .andExpect(jsonPath("$.authorization_servers[0]").value(ISSUER))
                .andExpect(jsonPath("$.scopes_supported[0]").value(SCOPE));
        assertEquals("/mcp", environment.getRequiredProperty("spring.ai.mcp.server.streamable-http.mcp-endpoint"));
    }

    @Test
    void validatesClaimsAndLimitsAuthenticatedRequests() throws Exception {
        Instant expiry = Instant.now().plusSeconds(300);
        mvc.perform(bearer("client", "https://wrong.example", RESOURCE, SCOPE, expiry))
                .andExpect(status().isUnauthorized());
        mvc.perform(bearer("client", ISSUER, "https://other.example/api", SCOPE, expiry))
                .andExpect(status().isUnauthorized());
        mvc.perform(bearer("client", ISSUER, RESOURCE, SCOPE, Instant.now().minusSeconds(300)))
                .andExpect(status().isUnauthorized());
        mvc.perform(bearer("client", ISSUER, RESOURCE, SCOPE, null)).andExpect(status().isUnauthorized());
        mvc.perform(bearer("client", ISSUER, RESOURCE, "read", expiry)).andExpect(status().isForbidden());

        mvc.perform(bearer("client", ISSUER, RESOURCE, SCOPE, expiry))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.serverInfo.name").value("spring-boot-base"));
        mvc.perform(bearer("client", ISSUER, RESOURCE, SCOPE, expiry)).andExpect(status().isOk());
        mvc.perform(bearer("client", ISSUER, RESOURCE, SCOPE, expiry)).andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
        mvc.perform(bearer("another-client", ISSUER, RESOURCE, SCOPE, expiry)).andExpect(status().isOk());
    }

    static JwtDecoder decoder() throws JOSEException {
        var settings = new McpSecurityAutoConfiguration.Properties(URI.create(ISSUER), URI.create(RESOURCE), 2, 1200);
        return McpSecurityAutoConfiguration.withValidation(
                NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build(), settings);
    }

    private MockHttpServletRequestBuilder bearer(String subject, String issuer, String audience, String scope, Instant expiry)
            throws JOSEException {
        var claims = new JWTClaimsSet.Builder().subject(subject).issuer(issuer).audience(audience).claim("scope", scope);
        if (expiry != null) {
            claims.expirationTime(Date.from(expiry));
        }
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(KEY));
        return initialize().header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.serialize());
    }

    private MockHttpServletRequestBuilder initialize() {
        return post("/mcp").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content("""
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                          "protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1"}
                        }}
                        """);
    }

    private static RSAKey signingKey() {
        try {
            return new RSAKeyGenerator(2048).generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to create an MCP test key", ex);
        }
    }
}
