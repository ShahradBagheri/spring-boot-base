# Authentication model after Google or Apple OIDC login

## Decision

The optional `base-auth-oauth2` module will support **server-side sessions for browser-based OIDC login by default**, with a well-defined extension point for consumers that need a different application authentication mechanism.

This is an architecture decision for issue #33, not an implemented authentication flow. The existing `base-security` baseline only protects endpoints and does not create user accounts or perform OAuth2/OIDC sign-in. Provider integration starts in later issues.

External Google and Apple ID tokens prove identity **to the relying party during the OIDC login exchange**. They must **not** be reused as first-party API access tokens. The application creates its own authenticated context after verifying the external identity and resolving it to a local application user.

## Module responsibilities

| Module | Responsibility |
| --- | --- |
| `base-core` | Framework-neutral contracts and application exceptions |
| `base-web` | API response types and client-safe error mapping |
| `base-security` | Optional fail-closed HTTP authorization, CSRF baseline, 401/403 errors, method security |
| Planned `base-auth` | Local user identity, principal representation, identity resolution and account lifecycle |
| Planned `base-auth-oauth2` | Optional Spring Security OAuth2 Client OIDC authorization-code login, provider mapping and success/failure/logout adapters |
| `demo-app` | Explicitly chooses authentication modules, registrations, redirect destinations and domain access rules |

A service that only needs `base-core` or `base-data-jpa` does not depend on any OAuth2 provider. A consuming application can opt in to `base-security` without adding OAuth2 Client.

## Browser login success

1. The browser starts sign-in through the enabled provider registration, using the OAuth2 authorization-code flow with OIDC `openid` scope.
2. Spring Security validates the authorization response: state, provider tokens, issuer, audience and nonce where applicable. Configure PKCE and secure authorization request storage according to the chosen client.
3. Resolve the validated external identity using the composite key **issuer + subject (`iss` + `sub`)**. Email is profile data, not a unique external-account identifier.
4. Provision or resolve the local application user using the planned `base-auth` contracts. Automatic account linking based solely on a matching email address is forbidden.
5. Establish a first-party Spring Security authentication for the **local user** in the server-side HTTP session and protect against session fixation by changing the session identifier at login.
6. Respond with a redirect to an explicitly allowed relative application destination, with no bearer token or ID token in the URL.

The default session cookie must be `HttpOnly`, `Secure` in HTTPS deployments, and use an appropriate `SameSite` policy (normally `Lax` for a same-site web application). Production deployments must enforce HTTPS. If a cross-site identity flow requires a different cookie policy, evaluate that browser topology explicitly and retain CSRF safeguards.

Provider profile claims can be missing or change over time. In particular, do not assume Apple supplies a user's name after the first login, or that email is present or verified for every identity.

## Browser requests and authorization

The browser authenticates subsequent same-origin requests with its session cookie; it does not send the Google or Apple ID token to business APIs. State-changing requests remain protected by Spring Security CSRF rules, using an appropriate CSRF token transport for the web client.

`base-security` continues to deny application requests unless authenticated. Higher-level role, tenant, and resource ownership rules remain the consuming application's responsibility. Only the enabled login initiation and callback routes may be opened as narrowly scoped exceptions.

An unauthenticated **API** request returns HTTP 401 in `ApiError` format; a successfully authenticated user lacking the required authority receives HTTP 403. Browser navigation to a login screen may use a separate configured redirect entry point. Never redirect arbitrary JSON API calls to a provider login page.

## Authentication failures

- Invalid state, rejected provider tokens, provider unavailability, denied consent, missing required local identity, or failed user provisioning **must not establish a logged-in session**.
- Provide an application-controlled failure destination or generic client-safe JSON error response. Log technical details server-side without secrets, authorization codes, private keys or raw tokens.
- Do not automatically create a local account if the provider identity fails validation.
- Support an explicit customization point so consuming apps decide their post-login success and failure destinations and how to display errors, without replacing identity verification.

## Logout contract

With OIDC enabled, a CSRF-protected `POST /logout` clears local authentication, invalidates the server session, and expires the app session cookie. The demo returns HTTP 204. See the [provider setup guide](OIDC_PROVIDER_SETUP.md#verify-sign-in-and-logout) for verification steps.

Logging out of the application **does not necessarily revoke the external Google/Apple session**. Provider-wide logout or remote token revocation is a separate optional integration that must not be implied by successful local logout.

## Native and third-party API clients

Do not issue first-party JWTs by default as a side effect of social login. A mobile or third-party API consumer that cannot use the application session must explicitly opt into a **separate application-token strategy**. Its access tokens, expiry/refresh rules, audience, scope, storage, revocation, and signing keys must be designed and tested independently.

Validating bearer tokens received by an API uses an OAuth2 Resource Server or equivalent token-validation module. OAuth2 Client (external sign-in) and Resource Server (API token validation) must not be conflated.

A consumer may replace the session-oriented success handler with a deliberate application-owned token/session adapter, but must keep the local identity resolution and OIDC verification boundaries intact.

## Authentication test coverage

- Successful sign-in resolves a validated `iss + sub` identity to the correct local user.
- Returning sign-in uses the existing mapping; identity linking requires explicit verification, not email matching.
- Missing email or Apple one-time profile data does not break a valid login.
- Invalid callback, state or provider tokens produce no authenticated session.
- Browser session is renewed on login and invalidated on CSRF-protected logout.
- Public login/callback routes exist **only** with the OAuth2 module and configured provider.
- API clients receive client-safe 401/403 rather than provider redirects.
- The base security tests continue to pass when OAuth2 is disabled.
- Tests use local provider registrations, signed test ID tokens, and a stubbed code exchange.

## Implementation order

1. Build reusable `base-auth` user/identity contracts and persistence mapping.
2. Add an optional `base-auth-oauth2` dependency on Spring Security OAuth2 Client with no built-in provider credentials.
3. Integrate Google and Apple as independently enabled OIDC registrations with externally supplied secrets.
4. Add local-user provisioning, explicit account linking and automated login/logout tests.

Provider-specific implementation must follow this contract rather than silently deciding to return Google or Apple tokens as application credentials.
