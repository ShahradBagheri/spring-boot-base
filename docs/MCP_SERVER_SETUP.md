# MCP server setup

The demo exposes an optional stateless Streamable HTTP endpoint at `/mcp`. It uses the MCP `2025-11-25` protocol in the examples below. You need a trusted OAuth authorization server that issues signed JWT access tokens and publishes its metadata and signing keys over HTTPS. The demo runs without a model provider or an AI API key.

## Enable the demo locally

Set up Java 25, Maven, and the PostgreSQL `dev` profile using the [demo application instructions](../demo-app/README.md). Use a development issuer and register the MCP resource there. The issuer must grant the `mcp:access` scope and put the exact MCP resource URL in the token's audience.

| Environment variable | Example | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev,mcp` | Enables the server and its synchronous examples alongside local database configuration. |
| `MCP_ISSUER` | `https://issuer.example.com` | Identifies the trusted issuer. Use the exact issuer advertised by your authorization server. |
| `MCP_RESOURCE` | `https://mcp.example.com/mcp` | Sets the published resource URL and required token audience. The path is `/mcp`. |

Build and start the app from the repository root.

```sh
mvn clean package
java -jar demo-app/target/demo-app-0.1.0-M1.jar
```

To disable the server, remove `mcp` from the active profiles and remove any override that enables `base.mcp.server.enabled`. To keep the server running without the demo tool, resource, and prompt, set `DEMO_MCP_EXAMPLES_ENABLED=false`.

## Connect a client

In a native or server-side MCP client, select Streamable HTTP and enter the full resource URL, such as `https://mcp.example.com/mcp`. For OAuth discovery, the client follows the `WWW-Authenticate` challenge to `/.well-known/oauth-protected-resource/mcp`, which identifies the issuer and required scope. Configure the client registration according to your authorization server. When the client requests a token, use the exact resource URL and `mcp:access`. The client sends the access token in the `Authorization: Bearer` header on each request. [MCP authorization specification](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization)

For a manual local check with a short-lived development token, set `MCP_ACCESS_TOKEN` in your shell and send an initialize request directly to the loopback listener. The token audience still needs to match `MCP_RESOURCE`. Use a trusted HTTPS endpoint matching `MCP_RESOURCE` when testing automatic discovery.

```sh
curl --fail-with-body http://127.0.0.1:8080/mcp \
  --header "Authorization: Bearer ${MCP_ACCESS_TOKEN}" \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json, text/event-stream' \
  --data '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"manual-check","version":"1"}}}'
```

The response identifies `spring-boot-base` and advertises the server capabilities. An MCP client can then list and use the [demo capabilities](MCP_SERVER_ARCHITECTURE.md#demo-capabilities).

| HTTP status | What to check |
|---|---|
| `401` | A bearer access token is present, signed by the configured issuer, unexpired, and targeted at the exact resource URL. |
| `403` | The token has `mcp:access` and the native client is sending requests without an `Origin` header. |
| `429` | A request budget was reached. Wait for the `Retry-After` interval. |

## Deploy and extend

In a consuming app, import `spring-boot-base-bom` and add `io.github.shahradbagheri:base-mcp-server-starter`. To enable the endpoint, configure `base.mcp.server.enabled`, `base.mcp.server.issuer`, and `base.mcp.server.resource`, and keep a protected fallback filter chain for other application routes. Give the dedicated MCP chain precedence for `/mcp`.

In production, use HTTPS for the issuer and application endpoint. If you terminate TLS at a reverse proxy, route both `/mcp` and its protected-resource metadata path to the app. Ensure the public resource URL and issued audience match. Configure gateway request and body-size limits, especially for traffic before authentication and across replicas. Keep bearer tokens out of logs and use short-lived tokens from your production issuer. [Transport and authorization design](MCP_SERVER_ARCHITECTURE.md#authorization-and-operating-limits)

Register only the tool, resource, and prompt specifications you intend to expose. Use the [demo capability registration](MCP_SERVER_ARCHITECTURE.md#demo-capabilities) as a pattern. In consuming applications, each data-bearing operation must apply its own authorization and tenant or owner checks after the transport scope check.

## Run the MCP tests

```sh
mvn -pl demo-app -am test -Dtest=McpServerHttpTest,McpServerMockMvcTest,SecurityAccessMockMvcTest -Dsurefire.failIfNoSpecifiedTests=false
```

The HTTP tests bind to an ephemeral loopback port and use the MCP Java SDK with locally signed test tokens. The MVC tests cover disabled-module behavior, discovery metadata, and authorization failures. These tests run with H2 and require no Docker, external OAuth server, or AI provider.
