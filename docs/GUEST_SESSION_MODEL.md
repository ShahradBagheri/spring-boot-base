# Guest session lifecycle

Enable the guest browser API with `SPRING_PROFILES_ACTIVE=dev,auth-guest` or set `base.auth.guest.enabled=true`. It is off by default and uses the existing servlet session store. The API creates a temporary identity only when a client explicitly starts a guest session.

| Caller | Identity | Access |
|---|---|---|
| Anonymous visitor | No application identity | Explicitly public routes such as health. With the guest profile enabled, anonymous visitors can also start a guest session. |
| Guest | Server-issued UUID stored in the browser's servlet session | Explicitly public and guest-only routes. |
| Signed-in user | Registered account principal | Public routes and authenticated routes allowed by account and role policies. The guest session routes reject signed-in users. |

The guest UUID is an identifier for application use. Authorization comes from the active server-side session associated with the browser cookie. The API accepts no client-selected guest UUID and grants no Spring Security authenticated principal, roles, or bearer tokens to guests. A browser with a guest session continues to be anonymous to Spring Security. Endpoints with durable user data or account privileges continue to require sign-in.

## Browser flow

| Route | Purpose |
|---|---|
| `GET /api/guest/csrf` | Get the CSRF header name and token before starting or ending a session. |
| `POST /api/guest/session` | Create a guest or return the current one. Returns `200` with `guestId`, `issuedAt`, and `expiresAt`. Creation rotates the servlet session ID. |
| `GET /api/guest/session` | Read the current guest. Returns `401` when the browser has no active guest. |
| `DELETE /api/guest/session` | Remove the guest identity from the current browser session and return `204`. |

Preserve the session cookie from the CSRF request and any replacement cookie after starting a guest session. Include the CSRF header for POST and DELETE. Starting again before expiration returns the same identity with its original expiration. A new guest after expiration or explicit deletion receives a new UUID. Closing the browser, session-store eviction, or servlet session timeout can end the browser's access earlier.

Set `base.auth.guest.lifetime` to control the absolute lifetime. The default is `30m`, and accepted values range from `1s` to `24h`. Request activity does not extend this lifetime. With multiple replicas, use the same session-store or session-affinity policy as the rest of the app's browser sessions.

## Application routes and account lifecycle

Follow the [security policy](SECURITY_POLICY.md) when adding routes. Declare public routes with an explicit method and path using `permitAll()`. Use the guest authorization check on guest-only routes and `authenticated()` on routes that require a signed-in user. Keep a protected fallback. The scoped `GuestWebConfiguration` in the demo shows these rules in use alongside the default application filter chain.

Guest sessions can support temporary experiences such as carts or drafts. The consuming app supplies its domain model, ownership checks, and policies for transfer and cleanup. Signed-in users can access the guest draft claim endpoint when the optional example is enabled.

## Optional PostgreSQL draft example

Enable `SPRING_PROFILES_ACTIVE=dev,auth-guest,guest-drafts` and apply Flyway migration V9. The draft API also accepts `base.auth.guest.drafts.enabled=true` alongside an enabled guest session. The example stores one draft per guest or account.

| Route | Access and behavior |
|---|---|
| `GET /api/guest/drafts/current` | Active guest. Returns the current browser's draft or `404`. |
| `PUT /api/guest/drafts/current` | Active guest. Creates or replaces the draft with JSON `{"content":"Draft text"}`, up to 2,000 characters. |
| `GET /api/guest/drafts/csrf` | Signed-in browser. Fetch a fresh token after authentication. |
| `POST /api/guest/drafts/claim` | Signed-in local account using the same browser session. Transfers its unexpired guest draft and removes the temporary identity. Returns `204` when the transfer succeeds or there is no eligible draft. Returns `409` and preserves both drafts if the account already has one. |
| `GET /api/guest/drafts/account` | Signed-in local account. Returns its draft or `404`. |

PUT and POST require the CSRF header. Account lookup uses the signed-in local principal. Guest lookup uses the server-side identity from the browser's session. These routes take no owner ID from the client. Signing in alone leaves the draft guest-owned. To transfer it, authenticate in the same browser session, use any updated session cookie, and call the claim endpoint with a fresh CSRF token. A transfer changes ownership in one database statement. After transfer, retries return `204` and the draft remains available to the account.

Applications can use the guest-only route rules in `GuestWebConfiguration` and resolve guest ownership through `GuestSessions.withCurrent`. Scope data queries to the server-resolved owner and check expiry at the data boundary. Define a domain-specific conflict policy before adding a transfer for resources with merge or replacement semantics.

## Expiry and cleanup

Draft expiry uses the identity's original `expiresAt`. Expired drafts are unavailable for reads, writes, and claims even while waiting for physical deletion. Ending a guest session or losing access to its browser cookie leaves any persistent draft eligible for cleanup at its original expiry. Account drafts have their own lifecycle and are excluded from guest cleanup.

When enabled, the example cleans up a bounded batch of expired guest drafts on a fixed delay. Set `base.auth.guest.drafts.cleanup-interval` to a duration of at least `1s` (default `1m`) and `base.auth.guest.drafts.cleanup-batch-size` to a value from `1` to `10000` (default `500`). PostgreSQL skips locked rows so cleanup can run on multiple instances and avoid interfering with an in-progress transfer.
