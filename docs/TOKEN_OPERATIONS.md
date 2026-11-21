# Operating first-party access and refresh tokens

Use a server-side session for a same-origin browser application. Enable `base-auth-token` for clients that need a first-party bearer credential, such as a mobile client calling the API. Google and Apple sign-in still establishes the local browser session. Their provider tokens stay in the sign-in flow. The [access token contract](ACCESS_TOKEN_CONTRACT.md) defines the application's JWT claims, expiry, and HTTP responses.

## Start the demo from a fresh checkout

1. Configure Java 25, Maven, and an empty PostgreSQL database using the [demo application instructions](../demo-app/README.md). Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the same shell or IDE run configuration.
2. For a local Google sign-in, register the matching callback and set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` using the [OIDC provider guide](OIDC_PROVIDER_SETUP.md). To use Apple instead, configure that provider and substitute `oauth2-apple` in the active profiles.
3. Create disposable RSA keys outside the checkout. The example uses a Bash shell and OpenSSL.

   ```sh
   umask 077
   key_dir="$(mktemp -d)"
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$key_dir/signer.pem"
   openssl pkey -in "$key_dir/signer.pem" -pubout -out "$key_dir/verifier.pem"

   export APP_TOKEN_ISSUER=https://issuer.example
   export APP_TOKEN_AUDIENCE=demo-api
   export APP_TOKEN_SIGNING_KEY_ID=local-1
   export APP_TOKEN_PRIVATE_KEY_FILE="$key_dir/signer.pem"
   export APP_TOKEN_PUBLIC_KEY_FILE="$key_dir/verifier.pem"
   export SPRING_PROFILES_ACTIVE=dev,auth-token,oauth2-google

   mvn -pl demo-app -am package
   java -jar demo-app/target/demo-app-0.0.1-SNAPSHOT.jar
   ```

4. Open `/oauth2/authorization/google` on the registered origin and sign in. Confirm the session is active by opening `/api/example/me` on the same origin. Use that browser session for the issue request below. Run `mvn clean verify` with Docker to exercise the flow in CI-style tests using generated keys and test identities.

To return to session-only mode, remove `auth-token` from the active profiles and restart. Keep any OIDC provider profiles you use. The token endpoints and signer are disabled, and the migrated tables remain available if you re-enable the module.

## Configure a consuming application

Add `com.notshahrad:base-auth-token` to the application dependencies. Use the reactor's managed version or the published version matching the other base modules. Enable `base.auth.token.enabled` and import `AccessTokenConfiguration` if component scanning does not find it. Configure the Spring JDBC datasource and transaction manager against the same PostgreSQL database that stores local users. Apply the token migrations through the consuming application's Flyway history. Expose token issuance only after trusted application authentication establishes a `LocalUserPrincipal`.

Give the bearer chain an explicit route matcher and priority over the application's browser/session chain. For example, this configuration secures `GET /api/mobile/me` if the consuming app provides that endpoint. Session-authenticated CSRF and issuance handlers belong in the browser chain.

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "base.auth.token", name = "enabled", havingValue = "true")
@Import(AccessTokenConfiguration.class)
class MobileApiSecurity {
    @Bean
    @Order(0)
    SecurityFilterChain mobileApi(HttpSecurity http, AccessTokenResourceServer tokens) throws Exception {
        http.securityMatcher("/api/mobile/**")
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(HttpMethod.GET, "/api/mobile/me").authenticated()
                        .anyRequest().denyAll());
        return tokens.configure(http).build();
    }
}
```

| Demo environment setting | Library property | Value |
| --- | --- | --- |
| `APP_TOKEN_ISSUER` | `base.auth.token.issuer` | Exact issuer string shared with verifiers |
| `APP_TOKEN_AUDIENCE` | `base.auth.token.audience` | Exact audience for this API |
| `APP_TOKEN_SIGNING_KEY_ID` | `base.auth.token.signing-key-id` | Unique ID with 1–64 ASCII letters, digits, dots, underscores, or hyphens |
| `APP_TOKEN_PRIVATE_KEY_FILE` | `base.auth.token.private-key` | Secret-mounted unencrypted PKCS#8 RSA PEM, at least 2048 bits |
| `APP_TOKEN_PUBLIC_KEY_FILE` | `base.auth.token.public-key` | Matching X.509 SubjectPublicKeyInfo PEM |
| `BASE_AUTH_TOKEN_TTL` | `base.auth.token.ttl` | Defaults to `5m`. Allowed range is 1–900 whole seconds, with 30 seconds of verifier clock skew. |

Refresh sessions have a 30-day absolute lifetime. Refreshing preserves the original expiry. Supply key paths through environment configuration. Mount the private key from secret storage with read permission limited to the signer process, and distribute only the public keys to independent verifiers. This implementation uses trusted public-key configuration by `kid`. It exposes no public JWKS endpoint.

## Example requests

These requests use placeholders. Use the configured HTTPS origin for deployments. The browser client can use same-origin requests so its session cookie is sent automatically.

```http
GET /api/token/csrf HTTP/1.1
Host: <app-host>
Cookie: JSESSIONID=<local-browser-session>
```

Copy the returned `headerName` and `token` into the issue request. The default header name is `X-CSRF-TOKEN`.

```http
POST /api/token/issue HTTP/1.1
Host: <app-host>
Cookie: JSESSIONID=<same-local-browser-session>
X-CSRF-TOKEN: <returned-csrf-token>
```

The response contains `accessToken`, `accessExpiresAt`, `refreshToken`, and `refreshExpiresAt`. Pass the access token in the Authorization header on API calls.

```http
GET /api/token/me HTTP/1.1
Host: <app-host>
Authorization: Bearer <access-token>
```

Rotate the refresh credential through the JSON body. Treat HTTP 401 as a signal to clear the local pair and reauthenticate. Serialize refresh requests per client session, and replace the pair as one operation. If the response is lost after the server commits rotation, reauthenticate. Retrying the old credential can revoke that family.

```http
POST /api/token/refresh HTTP/1.1
Host: <app-host>
Content-Type: application/json

{"refreshToken":"<current-refresh-token>"}
```

```http
POST /api/token/logout HTTP/1.1
Host: <app-host>
Authorization: Bearer <access-token-with-session-id>
```

Use `POST /api/token/logout-all` with an application access JWT to revoke all refresh sessions for the authenticated account. Either logout returns HTTP 204 on success. Clear the local credentials when logging out. Already-issued JWTs remain accepted until expiry, including permitted clock skew, or until their signing key is removed from verifiers. Use `POST /logout` with a session and CSRF token to end the browser session separately.

## Protect credentials in clients

- Keep same-origin browser requests on the HttpOnly session cookie with CSRF protection for state-changing requests. Fetch the CSRF token only from the application's origin, and keep browser bearer credentials out of persistent JavaScript-accessible storage.
- On native clients, keep the short-lived access token in memory where practical. Store the refresh credential using platform-protected storage, such as the iOS Keychain or encrypted app-private storage backed by the Android Keystore. Configure backups so they cannot export those credentials.
- Use TLS, and keep session cookies, authorization headers, and refresh bodies out of access logs, application traces, and crash reports. Keep credentials out of all URL paths and queries.
- The bearer chain reads the Authorization header and refresh accepts JSON. If an application adopts a refresh cookie, it must also configure CSRF protection for that cookie-backed route. Configure CORS for explicit trusted origins, methods, and headers. Allow credentials only on origins and endpoints that require the browser session.

## Roll out and rotate keys

Deploy the application migrations before routing traffic to token endpoints. The demo applies V3 for refresh tables and V4 for the single-unused-credential index whether or not the token profile is active. On an existing V3 database, check for duplicate unused rows and malformed digest values before applying V4. Building the index takes a write lock on the credential table, so schedule the migration with the expected table size in mind. Keep applied migration files unchanged.

For planned key rotation, generate a replacement with a new `kid` and stage its public key in every verifier before switching signers. Deploy these steps in order:

1. While the old key signs, add the replacement public key to the verifier sets. In deployments where the same application signs and verifies, use a temporary `base.auth.token.previous-keys` entry with the replacement key's ID, PEM file path, and `verify-until` timestamp. Complete the rollout before that timestamp.
2. Switch the active key ID and both key file paths to the replacement. Remove its temporary verification entry and add the old public key to `previous-keys`. Each entry uses `key-id`, `public-key`, and `verify-until`. Calculate the old key cutoff from the last possible old-key issuance time plus the prior access TTL and its 30 seconds of clock skew. The cutoff must be within 24 hours at startup.
3. After the cutoff, remove the old verification entries and retire the old key material. Confirm every verifier uses the new active key and the intended trust set.

## Respond to credential compromise

| Incident | Operator response |
| --- | --- |
| A refresh credential may have leaked | Revoke the affected family with a valid access JWT belonging to it, or revoke all families for the account through a newly authenticated session. Require reauthentication. Review the source of exposure and preserve redacted incident records. |
| A signing private key may have leaked | Stop signing with it. Replace the signer and remove the compromised `kid` from every verifier immediately, including any overlap entries. Expect active JWTs signed by that key to fail. Investigate whether any refresh sessions were also exposed and revoke those families. |
| A client repeatedly receives refresh HTTP 401 | Clear the local pair and require fresh sign-in. Check that the client persists replacements together and makes only one refresh request at a time. |

Use the [refresh-token rotation guidance in RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14) and the [PostgreSQL partial-index documentation](https://www.postgresql.org/docs/17/indexes-partial.html) for the underlying controls.
