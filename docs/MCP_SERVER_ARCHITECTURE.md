# Optional MCP server

The `base-mcp-server` Maven module integrates Spring AI 2.0.3 with the project's Spring Boot 4.1.2 and Java 25 build. The starter is `spring-ai-starter-mcp-server-webmvc`. The module fixes the transport to stateless HTTP at `/mcp` so the transport and its filter chain share one endpoint. Spring AI 2.0.x supports Spring Boot 4.1.x. [Spring AI compatibility](https://docs.spring.io/spring-ai/reference/getting-started.html) and [Spring AI 2.0.3 release](https://spring.io/blog/2026/11/20/spring-ai-2-1-0-GA-available-now/)

Use the module for applications that deliberately let MCP clients access a bounded set of application capabilities. The default demo application has no MCP transport or discovery endpoint. The implementation uses no model provider or AI API key. The demo app registers a small set of documentation examples when the MCP profile is active.

## Enable it in the demo

1. Add `mcp` to the active profiles, for example `SPRING_PROFILES_ACTIVE=dev,mcp`.
2. Set `MCP_ISSUER` to the HTTPS issuer of a trusted OAuth authorization server, such as `https://issuer.example.com`. The authorization server must publish discoverable signing keys and issue signed JWT access tokens.
3. Set `MCP_RESOURCE` to the public HTTPS endpoint, such as `https://app.example.com/mcp`. Configure the authorization server to issue access tokens for that exact audience with the `mcp:access` scope.
4. Connect a stateless Streamable HTTP-compatible MCP client to `/mcp`. It sends the access token in the Authorization header. The endpoint's bearer challenge points to `/.well-known/oauth-protected-resource/mcp` for discovery.

In a consuming application, include `base-mcp-server`, set `base.mcp.server.enabled=true`, and configure `base.mcp.server.issuer` and `base.mcp.server.resource`. Provide a protected fallback filter chain for the rest of the application, as the demo does with `base-security`. Keep the MCP chain ahead of any filter chain that matches `/mcp`. The library uses the base module's enable flag to set the Spring AI server switch, fixed path, and protocol.

## Demo capabilities

The `mcp` profile enables the synchronous examples through `demo.mcp.examples.enabled`. Set it to `false` to run the server without them. Each example requires the same authenticated `mcp:access` scope as the transport.

| Type | Name or URI | Behavior |
|---|---|---|
| Tool | `lookup_feature_guide` | Accepts one `feature` key from `oidc`, `email`, `phone`, `guest`, or `token`. Returns the key, title, and repository-relative guide path as structured data. Unknown or missing keys produce a tool error. |
| Resource | `spring-boot-base://guides/index` | Returns the fixed guide index as plain text. Clients can read this URI only. |
| Prompt | `plan_feature_integration` | Returns a reusable user message for drafting a setup and verification checklist. It takes no arguments. |

The guide catalog contains repository documentation references. It never opens a caller-supplied file or reads runtime settings, account records, or tenant data. Guide paths are relative to the repository. The separate prompt class supplies message text without calling a service or authorizing an operation.

To add a capability, annotate only its intended methods with `@McpTool`, `@McpResource`, or `@McpPrompt` and register the corresponding stateless specifications as Spring beans. The demo uses `SyncMcpAnnotationProviders` on an explicit list of handlers so Spring AI can build the specifications while broad tool callback conversion stays disabled. Keep tool adapters thin, validate inputs at the application boundary, and return a small typed result. Keep resource URIs allowlisted and their reads bounded. Treat prompts as templates. Any tool the client calls from a prompt must apply its own access rules. [Spring AI server annotations](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html)

## Authorization and operating limits

Spring Security verifies the token signature and issuer, requires an expiry and subject, and checks the audience and scope. The MCP chain uses bearer tokens exclusively and does not load browser sessions. Requests with an `Origin` header receive `403`; the initial endpoint is intended for native and server-side clients. Keep HTTPS enabled at the public endpoint. [MCP authorization specification](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization) and [Spring AI MCP server documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)

The application applies one-minute budgets to authorized requests. `base.mcp.server.requests-per-subject` defaults to 120 and `base.mcp.server.requests-overall` defaults to 1200. Both are positive integers and apply per application instance. An exhausted budget returns `429` with `Retry-After`. Set gateway request and body-size limits for traffic that arrives before authentication and for deployments with multiple replicas.

Register MCP capabilities explicitly. The module disables automatic conversion of general tool callbacks and exposure of tools from MCP clients. Each domain handler must validate and bound its arguments and results, authorize the operation against the authenticated principal, and resolve tenant and owner IDs on the server. For mutations, record the trusted actor, tenant, operation and outcome in the application's audit trail. Keep credentials and arbitrary request payloads out of those logs.
