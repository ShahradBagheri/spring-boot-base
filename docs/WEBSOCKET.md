# STOMP over WebSocket

Add the Maven dependency `com.notshahrad:base-websocket` to your servlet app and set `base.websocket.enabled=true`. The module registers `/ws`, dispatches `/app` messages to application controllers, and uses an in-memory broker for `/topic` subscriptions on a single server. Browser clients use the same origin by default. Configure individual trusted origins through the list property `base.websocket.allowed-origins`.

## Try the demo

Start the demo with WebSockets enabled and the access-token profile configured using the [token guide](TOKEN_OPERATIONS.md). Obtain an access token for a registered account.

1. Connect two STOMP 1.2 clients to `ws://localhost:8080/ws` using `Authorization: Bearer <access-token>` on each HTTP upgrade request. Use `wss://` for a deployed service.
2. Subscribe each client to `/topic/broadcast`.
3. Send a text message to `/app/broadcast` with content type `text/plain`. Both subscribers receive the text.

The demo also accepts an existing signed-in HTTP session. Spring carries its principal into the WebSocket connection. The demo permits publishing and subscribing only on their respective example destinations. Every signed-in subscriber receives the example broadcast.

## Consuming applications

Install an HTTP security policy for the handshake and a message authorization policy for inbound STOMP frames before exposing the endpoint. The reusable transport registers broker destinations. The consuming app owns permissions, membership checks, and connection lifetime. The demo's access-token validation occurs at the handshake.

Guest identities in the demo use server-side servlet sessions. The broadcast example requires a signed-in account. An application offering guest messaging needs to resolve an active guest session and enforce its lifetime and allowed destinations.
