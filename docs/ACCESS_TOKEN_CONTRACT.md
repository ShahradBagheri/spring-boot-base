# First-party access and refresh tokens

`base-auth-token` is an optional Maven library for applications that need bearer authentication on selected API routes. Browser sign-in through Google or Apple uses the application's session cookie. Google and Apple ID tokens are consumed by OAuth2 Client during sign-in. Resource Server validates the application's own signed access JWTs on the selected routes.

## Access JWT

| Header or claim | Contract |
| --- | --- |
| `alg` | RS256, using a configured RSA key of at least 2048 bits |
| `typ` | `at+jwt` |
| `kid` | Active signing key identifier. Verification uses the active key or a configured public key before its rotation cutoff. |
| `iss` | Exact application issuer configured in `base.auth.token.issuer` |
| `aud` | A single exact API audience configured in `base.auth.token.audience` |
| `sub` | The existing local user's canonical lowercase UUID |
| `iat`, `nbf` | Issue time in UTC epoch seconds. The token is valid from that time. |
| `exp` | Issue time plus the configured TTL. Default 5 minutes, allowed range 1–900 whole seconds. |
| `jti` | Random UUID unique to the issued JWT |
| `token_use` | `access` |
| `scope` | Optional space-delimited application scopes drawn from `allowed-scopes` and granted on the trusted authentication as `APP_SCOPE_<name>` |

Verification allows 30 seconds of clock skew and caps the signed lifetime at 15 minutes. Selected endpoint policies can require `SCOPE_<name>` in Spring Security. The default allowlist is empty. Role claims and external provider scopes confer no API permissions. The JWT contains no email, display name, credentials, or provider tokens. Treat its contents as readable by anyone who obtains it.

A consuming application calls `AccessTokenIssuer.issue()` after its authentication flow installs a fully authenticated `LocalUserPrincipal` in Spring Security's current context. The result is a Spring `Jwt` with the signed value and expiry. The caller supplies neither a user ID nor arbitrary JWT claims. The adapter that creates the local principal owns the authentication and local account lookup. The bearer principal resolves to the validated local UUID through `Authentication.getName()`. Its JWT principal cannot be used to issue another token.

```mermaid
flowchart TD
    login["Trusted application sign-in"] --> principal["Authenticated local principal"]
    principal --> issuer["Access token issuer"]
    issuer --> client["API client"]
    client --> api["Bearer-only API route"]
    api --> valid{"Valid JWT and authorized route?"}
    valid -->|Yes| resource["API response"]
    valid -->|No| error["JSON 401 or 403"]
```

## Refresh contract for the next batch

Refresh credentials will be opaque cryptographically random secrets with at least 256 bits of entropy, an absolute session lifetime, and separate revocable sessions per client or device. They will be stored as hashes and will carry no JWT claims. The planned default refresh-session lifetime is 30 days from session creation. A refresh cannot extend that absolute deadline. The implementation will return the interval and session expiry to clients explicitly.

```mermaid
flowchart TD
    present["Client submits refresh credential"] --> validate{"Session active and credential unused?"}
    validate -->|Yes| rotate["Consume and replace credential atomically"]
    rotate --> pair["Return new access and refresh pair"]
    validate -->|Replay| revoke["Revoke affected refresh session"]
    validate -->|Invalid or expired| reject["Generic JSON 401"]
    revoke --> reject
```

Issuance, refresh, and revocation HTTP endpoints are tracked in [#129](https://github.com/ShahradBagheri/spring-boot-base/issues/129), [#130](https://github.com/ShahradBagheri/spring-boot-base/issues/130), and [#131](https://github.com/ShahradBagheri/spring-boot-base/issues/131). Logout for token clients will revoke the selected refresh session or all of the user's refresh sessions, as requested. A previously issued access token remains valid until expiry or signing-key retirement. Browser logout ends the existing local session under the [OIDC session contract](OIDC_AUTHENTICATION_MODEL.md#logout-contract).

## Transport and failures

Send the access JWT in the HTTPS `Authorization: Bearer <access-token>` header. Use platform-protected storage on native clients, keep access tokens in memory where practical, and keep credentials out of URLs, telemetry, and logs. Browser clients use the session model by default. If a consuming application exposes token issuance to an authenticated browser session, that state-changing action requires CSRF protection. Use explicit trusted origins for any cross-origin API access.

The bearer chain ignores session cookies. A missing, malformed, invalid, or expired token produces a generic JSON `ApiError` 401 with a `WWW-Authenticate: Bearer` challenge. A verified token that lacks endpoint permission receives JSON 403. Missing or invalid required configuration prevents startup when opt-in is active.

## Enable bearer validation

Add the `base-auth-token` dependency and ensure the application imports `AccessTokenConfiguration` if its package is outside component scanning. Let the application bind the reusable `AccessTokenResourceServer` configurer to a dedicated ordered `SecurityFilterChain`. Set a narrow `securityMatcher` and explicit endpoint authorization before calling `resourceServer.configure(http).build()`. The configurer sets a stateless bearer-only chain with CSRF disabled for that chain. The application should give it priority over the catch-all browser/session chain.

The demo already wires the chain. Append `auth-token` to the active profiles, such as `dev,auth-token`, and supply the following values through the process environment. For an environment with OIDC login, append the desired provider profile separately.

| Demo environment variable | Purpose |
| --- | --- |
| `APP_TOKEN_ISSUER` | A distinct deployment issuer, for example `https://issuer.example` |
| `APP_TOKEN_AUDIENCE` | Identifier for the API, for example `demo-api` |
| `APP_TOKEN_SIGNING_KEY_ID` | A unique key ID, for example `access-2026-01`. Allows 1–64 letters, digits, dots, underscores, or hyphens. |
| `APP_TOKEN_PRIVATE_KEY_FILE` | Mounted unencrypted PKCS#8 RSA PEM file with `BEGIN PRIVATE KEY` |
| `APP_TOKEN_PUBLIC_KEY_FILE` | Matching X.509 SubjectPublicKeyInfo PEM file with `BEGIN PUBLIC KEY` |

Configure `base.auth.token.ttl` and `base.auth.token.allowed-scopes` when the defaults are insufficient. The profile sets `base.auth.token.enabled=true`. The demo route is `GET /api/token/me`. It returns the local UUID when supplied a valid bearer JWT. The current batch exposes an issuance service for trusted application code, so enabling the profile by itself gives no HTTP route for obtaining a token.

For local development, generate a disposable key pair outside the checkout and configure the resulting absolute paths. For deployments, mount the signing key from secret storage with process-only read access.

```sh
umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /your/secure/path/signer.pem
openssl pkey -in /your/secure/path/signer.pem -pubout -out /your/secure/path/verifier.pem
```

## Rotate signing keys

1. Generate a replacement pair with a new `kid`. Distribute the replacement **public** key and ID to intended verifiers through trusted deployment configuration before switching signers. Enable a bounded overlap during rolling deployments.
2. Switch the active `signing-key-id`, `private-key`, and `public-key` to the new pair. Add the outgoing keys to `base.auth.token.previous-keys` with `key-id`, `public-key` (PEM path), and `verify-until` (UTC timestamp). Set the cutoff to at least the final issuance time plus the old TTL and 30 seconds of clock skew. A configured cutoff can be at most 24 hours ahead at startup. The decoder stops accepting the key at the cutoff without a restart.
3. Remove expired keys from configuration and retire the old files. For independent verifiers, distribute the same active and outgoing public keys, IDs, issuer, audience, allowed scopes, cutoffs, and validation rules. Verification uses a pinned local `kid` map and ignores token-supplied key locations by rejecting those headers. No public JWKS route is needed for this configuration.

The JOSE token type follows the `at+jwt` convention described in [RFC 9068](https://www.rfc-editor.org/rfc/rfc9068.html). This application's claim contract is specified above.
