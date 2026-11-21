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

Guest sessions can support temporary experiences such as a cart or draft when the consuming application supplies the data model and ownership checks. Sign-in grants access according to the registered account's permissions and disables access to guest-only routes. Signing in or registering does not transfer data in this implementation. Explicit transfer, domain ownership rules, and cleanup of persisted guest data are covered by [#63](https://github.com/ShahradBagheri/spring-boot-base/issues/63), [#64](https://github.com/ShahradBagheri/spring-boot-base/issues/64), and [#65](https://github.com/ShahradBagheri/spring-boot-base/issues/65).
