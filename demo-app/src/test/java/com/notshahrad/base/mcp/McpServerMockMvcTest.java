package com.notshahrad.base.mcp;

import com.notshahrad.spring_boot_base.SpringBootBaseApplication;
import com.nimbusds.jose.JOSEException;
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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

import static com.notshahrad.base.mcp.McpTestIssuer.ISSUER;
import static com.notshahrad.base.mcp.McpTestIssuer.RESOURCE;
import static com.notshahrad.base.mcp.McpTestIssuer.SCOPE;
import static com.notshahrad.base.mcp.McpTestIssuer.issue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SpringBootBaseApplication.class, properties = {
        "MCP_ISSUER=" + ISSUER,
        "MCP_RESOURCE=" + RESOURCE,
        "base.mcp.server.requests-per-subject=2",
        "spring.ai.mcp.server.streamable-http.mcp-endpoint=/misconfigured"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "mcp"})
class McpServerMockMvcTest {

    private static final String TOOL = "lookup_feature_guide";
    private static final String RESOURCE_URI = "spring-boot-base://guides/index";
    private static final String PROMPT = "plan_feature_integration";

    @TestBean(name = "mcpJwtDecoder", enforceOverride = true,
            methodName = "com.notshahrad.base.mcp.McpTestIssuer#decoder")
    private JwtDecoder decoder;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Environment environment;

    @Autowired
    private JsonMapper json;

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

    @Test
    void discoversAndInvokesTheFeatureGuideTool() throws Exception {
        mvc.perform(authorized("tool-client", "tools/list", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools", hasSize(1)))
                .andExpect(jsonPath("$.result.tools[0].name").value(TOOL))
                .andExpect(jsonPath("$.result.tools[0].annotations.readOnlyHint").value(true))
                .andExpect(jsonPath("$.result.tools[0].outputSchema.properties.guidePath").exists());
        mvc.perform(authorized("tool-client", "tools/call", Map.of("name", TOOL, "arguments", Map.of("feature", "phone"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.structuredContent.feature").value("phone"))
                .andExpect(jsonPath("$.result.structuredContent.guidePath").value("docs/PHONE_AUTHENTICATION_SETUP.md"));
    }

    @Test
    void rejectsUnknownOrMissingFeatureKeys() throws Exception {
        mvc.perform(authorized("invalid-tool-client", "tools/call",
                        Map.of("name", TOOL, "arguments", Map.of("feature", "../application.yaml"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true));
        mvc.perform(authorized("invalid-tool-client", "tools/call", Map.of("name", TOOL, "arguments", Map.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true));
    }

    @Test
    void publishesOnlyTheFixedGuideResource() throws Exception {
        mvc.perform(authorized("resource-client", "resources/list", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.resources", hasSize(1)))
                .andExpect(jsonPath("$.result.resources[0].uri").value(RESOURCE_URI));
        mvc.perform(authorized("resource-client", "resources/read", Map.of("uri", RESOURCE_URI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.contents", hasSize(1)))
                .andExpect(jsonPath("$.result.contents[0].mimeType").value("text/plain"))
                .andExpect(jsonPath("$.result.contents[0].text").value(containsString("docs/GUEST_SESSION_MODEL.md")));
        mvc.perform(authorized("unknown-resource-client", "resources/read", Map.of("uri", "file:///application.yaml")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").exists());
    }

    @Test
    void discoversAndFetchesTheIntegrationPrompt() throws Exception {
        mvc.perform(authorized("prompt-client", "prompts/list", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.prompts", hasSize(1)))
                .andExpect(jsonPath("$.result.prompts[0].name").value(PROMPT));
        mvc.perform(authorized("prompt-client", "prompts/get", Map.of("name", PROMPT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messages", hasSize(1)))
                .andExpect(jsonPath("$.result.messages[0].role").value("user"))
                .andExpect(jsonPath("$.result.messages[0].content.text").value(containsString("Use placeholders for secrets")));
    }

    @Test
    void requiresTheMcpScopeForAllThreeCapabilities() throws Exception {
        assertMcpScope("tools/call", Map.of("name", TOOL, "arguments", Map.of("feature", "oidc")));
        assertMcpScope("resources/read", Map.of("uri", RESOURCE_URI));
        assertMcpScope("prompts/get", Map.of("name", PROMPT));
    }

    private void assertMcpScope(String method, Map<String, ?> params) throws Exception {
        mvc.perform(rpc(method, params)).andExpect(status().isUnauthorized());
        String token = issue("limited-client", ISSUER, RESOURCE, "read", Instant.now().plusSeconds(300));
        mvc.perform(rpc(method, params).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder authorized(String subject, String method, Map<String, ?> params)
            throws JOSEException {
        String token = issue(subject, ISSUER, RESOURCE, SCOPE, Instant.now().plusSeconds(300));
        return rpc(method, params).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder rpc(String method, Map<String, ?> params) {
        return post("/mcp").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .header("MCP-Protocol-Version", "2025-11-25")
                .content(json.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params)));
    }

    private MockHttpServletRequestBuilder bearer(String subject, String issuer, String audience, String scope, Instant expiry)
            throws JOSEException {
        return initialize().header(HttpHeaders.AUTHORIZATION, "Bearer " + issue(subject, issuer, audience, scope, expiry));
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
}
