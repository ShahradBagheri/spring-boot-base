# Optional Google and Apple sign-in

`demo-app` includes `base-auth-oauth2`. The OIDC login flow is disabled by default. Start with the [demo database and build instructions](../demo-app/README.md), then add the provider profiles and environment variables below before starting the JAR. Flyway creates the local user and external identity tables in PostgreSQL.

| Providers | `SPRING_PROFILES_ACTIVE` for local development | Required environment variables |
| --- | --- | --- |
| None | `dev` | Database settings only |
| Google | `dev,oauth2-google` | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` |
| Apple | `dev,oauth2-apple` | `APPLE_SERVICES_ID`, `APPLE_CLIENT_SECRET` |
| Both | `dev,oauth2-google,oauth2-apple` | All four provider variables |

In a deployment, append the provider profiles to the application's existing active profiles. The provider profiles enable `base.auth.oauth2.enabled` and register only the selected providers.

## Google

1. In Google Cloud, select your project and configure branding and audience in Google Auth Platform. For an external app in testing, add the Google accounts you will use under **Test users**.
2. Under **Clients**, create an OAuth client of type **Web application**. Under **Authorized redirect URIs**, add the exact callback URL. Use `http://localhost:8080/login/oauth2/code/google` for the demo on the default local port, or `https://app.example.com/login/oauth2/code/google` for an HTTPS deployment. Use the same scheme, host, port, and path when starting sign-in.
3. Supply the generated client ID as `GOOGLE_CLIENT_ID` and the client secret as `GOOGLE_CLIENT_SECRET`. In IntelliJ, add them to the app's Run configuration. For deployment, inject them from your secrets manager.
4. Start the application with `dev,oauth2-google` and open `http://localhost:8080/oauth2/authorization/google` in a browser. This registration requests `openid`, `profile`, and `email`.

[Google Auth Platform setup](https://support.google.com/cloud/answer/15544987) · [Google OpenID Connect documentation](https://developers.google.com/identity/openid-connect/openid-connect)

## Apple

1. Enable Sign in with Apple on the primary App ID in Apple Developer. Create or select a **Services ID**, enable Sign in with Apple for it, and associate it with that primary App ID.
2. Register the website domain in the Services ID configuration and add its exact **Return URL**, for example `https://app.example.com/login/oauth2/code/apple`. Use a registered HTTPS domain for development too. The Apple web callback requires a domain name.
3. Create a Sign in with Apple key associated with the primary App ID. Record its **Key ID**, your **Team ID**, and the **Services ID**, and keep the downloaded `.p8` file in secure storage.
4. Generate the client-secret JWT outside the application and sign it with the Apple private key using ES256.

   | JWT field | Value |
   | --- | --- |
   | Header `alg` | `ES256` |
   | Header `kid` | `<Apple Key ID>` |
   | `iss` | `<Apple Team ID>` |
   | `sub` | `<Apple Services ID>` |
   | `aud` | `https://appleid.apple.com` |
   | `iat` | Issue time in Unix seconds |
   | `exp` | Expiration in Unix seconds, at most six months after issuance |

5. Supply the Services ID as `APPLE_SERVICES_ID` and the signed JWT as `APPLE_CLIENT_SECRET`. Start with `dev,oauth2-apple`. Open `https://app.example.com/oauth2/authorization/apple` on your registered HTTPS host.

The Apple profile sets the session cookie to `Secure; SameSite=None` so the browser sends it on Apple's cross-site `form_post` callback. Use HTTPS for the whole session when the Apple profile is active. Spring Security checks the saved authorization state on the callback, and normal application POST requests still require CSRF protection. If TLS terminates at a reverse proxy, have the proxy strip untrusted forwarding headers and set the original HTTPS scheme and public host. Configure Spring Boot to honor the headers from that proxy, for example with `SERVER_FORWARD_HEADERS_STRATEGY=framework`.

Apple can send the user's name in the `user` form field on the first authorization. The application maps the account using the verified ID token's issuer and subject. A returning login works without optional profile fields.

[Apple web configuration](https://developer.apple.com/help/account/capabilities/configure-sign-in-with-apple-for-the-web) · [Apple client-secret creation](https://developer.apple.com/documentation/accountorganizationaldatasharing/creating-a-client-secret)

## Secret rotation

Keep the Google client secret, Apple signing key, and generated Apple client-secret JWT in a secrets manager or a protected local Run configuration. Keep them out of source control and request logs. The application reads the client secrets at startup. For Apple, schedule JWT creation before expiry, replace `APPLE_CLIENT_SECRET`, and restart or redeploy so the new secret is loaded. Keep the Apple signing key in the signing environment. For Google, provision a replacement in Google Cloud, deploy it, verify sign-in, then retire the old secret.

## Verify sign-in and logout

1. Start from the selected provider's sign-in URL and complete the flow in a browser. The demo redirects to `/`. Open `/api/example/me` on the same host in the same browser to see the local user UUID in the `username` response field.
2. Sign in again from a fresh browser session with the same provider account and confirm the UUID stays the same. Accounts authenticated by different issuers stay separate even when they share a verified email.
3. Deny consent in a fresh session and confirm the callback returns a generic HTTP 401 and the protected endpoint stays inaccessible.
4. From a signed-in client, send `POST /logout` with the session's Spring Security CSRF token in the `X-CSRF-TOKEN` header or `_csrf` form field. A successful logout returns HTTP 204, invalidates the local session, and expires the session cookie. The next request to `/api/example/me` returns HTTP 401. A browser frontend must render or expose the CSRF token to its own same-origin client. Google and Apple can retain their own provider sessions.

The automated flow uses test registrations, locally signed ID tokens, and a stubbed code exchange. Run it without provider credentials or external provider calls:

```sh
mvn -pl demo-app -am -Dtest=OidcLoginConfigurationMockMvcTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Use the browser steps above to verify the live registrations, callback URLs, and cookies in each deployment. See the [OIDC authentication model](OIDC_AUTHENTICATION_MODEL.md) and [local identity architecture](LOCAL_IDENTITIES.md) for the session and account-linking contracts.
