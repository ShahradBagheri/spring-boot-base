package com.notshahrad.base.mcp;

import com.notshahrad.spring_boot_base.SpringBootBaseApplication;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.notshahrad.base.mcp.McpTestIssuer.ISSUER;
import static com.notshahrad.base.mcp.McpTestIssuer.RESOURCE;
import static com.notshahrad.base.mcp.McpTestIssuer.SCOPE;
import static com.notshahrad.base.mcp.McpTestIssuer.issue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = SpringBootBaseApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "MCP_ISSUER=" + ISSUER, "MCP_RESOURCE=" + RESOURCE})
@ActiveProfiles({"test", "mcp"})
class McpServerHttpTest {

    private static final String TOOL = "lookup_feature_guide";
    private static final String URI_INDEX = "spring-boot-base://guides/index";
    private static final String PROMPT = "plan_feature_integration";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper json;

    @TestBean(name = "mcpJwtDecoder", enforceOverride = true,
            methodName = "com.notshahrad.base.mcp.McpTestIssuer#decoder")
    private JwtDecoder decoder;

    @Test
    void realMcpClientDiscoversAndUsesTheExamplesOverHttp() throws Exception {
        try (var client = client("sdk-client")) {
            var initialized = client.initialize();
            assertEquals("spring-boot-base", initialized.serverInfo().name());
            assertEquals("2025-11-25", initialized.protocolVersion());

            var tool = client.listTools().tools().getFirst();
            assertEquals(TOOL, tool.name());
            var result = client.callTool(CallToolRequest.builder(TOOL).arguments(Map.of("feature", "oidc")).build());
            assertEquals(Map.of("feature", "oidc", "title", "Google and Apple sign-in",
                    "guidePath", "docs/OIDC_PROVIDER_SETUP.md"), result.structuredContent());
            var invalid = CallToolRequest.builder(TOOL).arguments(Map.of("feature", "unsupported")).build();
            assertTrue(client.callTool(invalid).isError());

            var resource = client.listResources().resources().getFirst();
            assertEquals(URI_INDEX, resource.uri());
            var contents = assertInstanceOf(TextResourceContents.class,
                    client.readResource(ReadResourceRequest.builder(URI_INDEX).build()).contents().getFirst());
            assertEquals("text/plain", contents.mimeType());
            assertTrue(contents.text().contains("docs/GUEST_SESSION_MODEL.md"));

            assertEquals(PROMPT, client.listPrompts().prompts().getFirst().name());
            var message = client.getPrompt(GetPromptRequest.builder(PROMPT).build()).messages().getFirst();
            assertTrue(assertInstanceOf(TextContent.class, message.content()).text().contains("Use placeholders for secrets"));
        }
    }

    @Test
    void initializationDoesNotAuthorizeLaterRequestsOrOtherRoutes() throws Exception {
        try (var client = client("isolated-client"); var http = HttpClient.newHttpClient()) {
            client.initialize();
            var rpc = HttpRequest.newBuilder(uri("/mcp")).timeout(TIMEOUT)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                    .header("MCP-Protocol-Version", "2025-11-25")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"jsonrpc":"2.0","id":1,"method":"tools/list"}
                            """));
            var anonymous = http.send(rpc.build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, anonymous.statusCode());
            assertTrue(anonymous.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE).orElseThrow()
                    .contains("/.well-known/oauth-protected-resource/mcp"));
            assertTrue(anonymous.headers().allValues(HttpHeaders.SET_COOKIE).isEmpty());

            String wrongResource = issue("isolated-client", ISSUER, "https://api.example/other", SCOPE,
                    Instant.now().plusSeconds(60));
            assertEquals(401, http.send(rpc.header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongResource).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());

            String mcpToken = issue("isolated-client", ISSUER, RESOURCE, SCOPE, Instant.now().plusSeconds(60));
            var accountRoute = HttpRequest.newBuilder(uri("/api/example/me")).timeout(TIMEOUT)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + mcpToken).GET().build();
            assertEquals(401, http.send(accountRoute, HttpResponse.BodyHandlers.discarding()).statusCode());
        }
    }

    private McpSyncClient client(String subject) throws Exception {
        String token = issue(subject, ISSUER, RESOURCE, SCOPE, Instant.now().plusSeconds(60));
        var transport = HttpClientStreamableHttpTransport.builder(uri("").toString())
                .endpoint("/mcp")
                .jsonMapper(new JacksonMcpJsonMapper(json))
                .supportedProtocolVersions(List.of("2025-11-25"))
                .openConnectionOnStartup(false)
                .connectTimeout(TIMEOUT)
                .requestBuilder(HttpRequest.newBuilder().header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .build();
        return McpClient.sync(transport).requestTimeout(TIMEOUT).build();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
