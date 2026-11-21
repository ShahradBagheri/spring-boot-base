# Optional Google and Apple OpenID Connect login

The reusable `base-auth-oauth2` module provides a Spring Security OAuth2 Client authorization-code login chain and a local-user adapter. It depends on `base-auth` for local users and `base-security` for the fail-closed baseline.

This module is **disabled by default** (`base.auth.oauth2.enabled` is absent/false). No provider credentials or sample real tokens are stored in the repository.

The two profile files are independently selectable:

- `demo-app/src/main/resources/application-oauth2-google.yaml`
- `demo-app/src/main/resources/application-oauth2-apple.yaml`

The profiles add the required Spring Security `ClientRegistration` properties and enable the OIDC login chain. The default app remains protected when neither profile is enabled. Do not activate an OIDC profile without its registration credentials; startup should fail instead of silently accepting anonymous requests.

## Default behavior

Login initiation: `GET /oauth2/authorization/{registrationId}` (for example, `/oauth2/authorization/google`).

Authorization callback: `/login/oauth2/code/{registrationId}`. The provider response must match the registered redirect exactly. Spring Security validates state and exchanges the authorization code over the back channel. OIDC token validation, issuer, audience and subject resolution are delegated to Spring Security's OIDC client.

`LocalOidcUserService` is called **after** Spring Security's verified `OidcUserService` and creates or loads a local `AppUser` using issuer + subject, never email alone. The authenticated principal's name becomes the local UUID. Provider ID tokens are **not** treated as application-issued API tokens.

The current successful login redirect is `/`. This is a minimal implementation; applications with a frontend should configure an allowed destination as part of their login UX. The demo app has no real account management UI or default user credentials.

## Enable Google login

1. In Google Cloud Console, configure the OAuth consent screen and create a **Web application** OAuth client.
2. Register your application's exact callback URL, e.g. `https://example.com/login/oauth2/code/google`. For local testing, a suitable development origin may be supported by Google's console.
3. Expose `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` as process environment variables. Do not commit them, and do not reuse production secrets in test source files.
4. Enable the additional Spring profile `oauth2-google` along with the database profile. For example `SPRING_PROFILES_ACTIVE=dev,oauth2-google`.
5. Open `https://example.com/oauth2/authorization/google` and complete Google sign-in. Verify that login returns to the configured callback and the local user is created once; a returning sign-in must reuse the same issuer/subject identity.

The Google registration requests only `openid`, `profile`, and `email` scopes. Email is not an account-link key, even if Google marks it verified.

## Enable Sign in with Apple

1. Configure Sign in with Apple for your web app in Apple Developer, including a **Services ID** tied to a primary App ID and authorized website domain/return URL.
2. Obtain your Apple **Team ID**, the Services ID used as `client_id`, an Apple private key (`.p8`) and its **Key ID**.
3. In a secure environment or secrets service, generate the client-secret JWT signed with your Apple private key using **ES256**. The JWT header must include `alg: ES256` and `kid: <Key ID>`; claims must include `iss: <Team ID>`, `sub: <Services ID>`, `aud: https://appleid.apple.com`, `iat`, and `exp` within Apple's validity limits. **Do not store the private key or JWT in Git.**
4. Supply `APPLE_SERVICES_ID` and the generated `APPLE_CLIENT_SECRET` as environment variables (or through a secured deployment secret injection mechanism), and rotate the secret before its expiration. Team ID, Key ID, and private key are needed only by your external JWT-signing process.
5. Enable `SPRING_PROFILES_ACTIVE=dev,oauth2-apple`. The exact callback URL should be `https://example.com/login/oauth2/code/apple` and must be registered in the Services ID configuration.
6. Use HTTPS with session cookies marked `Secure; SameSite=None` for Apple's cross-site `form_post` callback. Ensure reverse proxies forward the original secure scheme/host correctly.

The Apple authorization resolver requests `response_mode=form_post` for Apple's registration; the callback is exempted from CSRF *only for the OAuth callback path*, while Spring Security still verifies authorization state. Other application POST requests keep CSRF protection.

Apple may provide a display name only on the first authorization, in its optional `user` form field. The module does **not** require a name or email from that field to authenticate, and it does not treat unverified form data as identity proof. Missing optional profile claims are accepted. A product that wants to retain Apple's first-time name should add an explicit, validated profile-capture feature after successful OIDC verification.

Do not expect Apple to support insecure localhost callbacks for a web Services ID. Test on an HTTPS domain with an approved Apple return URL.

## Independent provider selection

- `dev,oauth2-google`: Google only.
- `dev,oauth2-apple`: Apple only.
- `dev,oauth2-google,oauth2-apple`: both, each with its own credentials.
- `dev`: no OAuth2 login endpoint enabled; the normal `base-security` policy remains active.

Google and Apple registrations are optional; no default security endpoint is made globally public. The baseline continues to return JSON `ApiError` 401/403 responses for unpermitted application access.

## Automated tests and real-provider verification

`LocalOidcUserServiceTest` verifies that a verified OIDC subject is mapped to a local UUID, including missing optional email and name. `OidcLoginConfigurationMockMvcTest` initiates an authorization redirect using a local dummy client registration and verifies that unrelated API paths remain protected. Tests do not need real provider credentials or network access.

A real Google/Apple sign-in cannot be confirmed without the respective developer registrations, valid callback domains, HTTPS/cookie configuration and live secrets. **Live provider authorization-code callbacks have not been verified by this automated suite.** Before releasing either provider, manually validate first login, returning login, denied consent, invalid/expired state, logout, and the account-linking policy. Issue #39 tracks fuller provider-neutral OIDC integration coverage.

## Operational limitations

The `APPLE_CLIENT_SECRET` value is supplied externally and is **not automatically regenerated or refreshed by the current implementation**. Deployments must rotate it securely before expiration. Full first-time Apple display-name capture, multi-instance session storage, production logout UX and comprehensive OIDC integration tests are separate follow-up concerns.

Read [OIDC authentication model](OIDC_AUTHENTICATION_MODEL.md) and [local identity architecture](LOCAL_IDENTITIES.md) before enabling login in an application.
