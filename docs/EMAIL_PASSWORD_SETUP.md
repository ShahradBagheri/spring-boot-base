# Email and password authentication

The demo uses the same local user IDs for email/password and other authentication methods. Email signup, verification, login, and recovery are opt-in. SMTP delivery can also be used independently for ordinary notifications. See the [account model](EMAIL_AUTHENTICATION_MODEL.md) and the [email configuration guide](EMAIL_CONFIGURATION.md) for sender options and template customization.

## Enable the demo

1. Configure the demo's PostgreSQL database. Flyway V5 creates the email identity table, and V6 creates the password and challenge tables.
2. Configure an `EmailSender`. For local development use the SMTP adapter with a localhost mail catcher. In an automated test, inject a recording sender.
3. Append `auth-email` to the active profiles, for example `SPRING_PROFILES_ACTIVE=dev,auth-email`. Set `EMAIL_AUTH_CLIENT_ORIGIN` to the public HTTPS origin of the frontend. An HTTP loopback origin is accepted for local development.
4. The frontend owns the `/verify-email` and `/reset-password` pages. Read the challenge from the URL fragment in the browser, clear the fragment from the address bar, and send the challenge with the chosen password in the appropriate POST body. The API demo sends these links and provides the JSON endpoints. They can also be exercised manually by using the token from the local mail catcher.

The profile enables both `base.auth.email.enabled` and `base.auth.email.password-enabled`. Consuming apps can choose their own endpoints, delivery implementation, and frontend while using the same services and PostgreSQL migrations.

## Browser API

The common prefix is `/api/auth/email`. Use the same browser session for `GET /csrf` and subsequent POST requests. Send the returned token under the returned header name. Login rotates the browser session ID and the CSRF token. Request a fresh CSRF token before the next POST. The demo serves session-protected endpoints and expects a same-origin browser client or an application-specific proxy.

| Method and path | Request JSON | Result |
| --- | --- | --- |
| `GET /csrf` | | CSRF header name and token for the current browser session |
| `POST /signup` | `{"email":"person@example.test"}` | Generic 202, with verification delivery when eligible |
| `POST /verify` | `{"token":"<signup-challenge>","password":"<chosen-password>"}` | 204 after activating the verified email and initial password |
| `POST /login` | `{"email":"person@example.test","password":"<password>"}` | 204 with an authenticated browser session or generic 401 |
| `POST /reset/request` | `{"email":"person@example.test"}` | Generic 202, with reset delivery when eligible |
| `POST /reset/complete` | `{"token":"<reset-challenge>","password":"<new-password>"}` | 204 after changing the password and invalidating existing password sessions |
| `POST /logout` | | 204 after ending the browser session |

Signup challenges last 30 minutes and reset challenges last 15 minutes. Each challenge can be redeemed once. Issuing a replacement after the one-minute resend cooldown invalidates the previous link for that address and purpose. Invalid, expired, and used challenges receive a generic 400 on redemption. Verification and password reset require a POST, so fetching an email link cannot activate an account or change a password by itself.

Login uses Argon2id, a random salt per hash, and a generic response for unknown, pending, or invalid credentials. Passwords require 15–128 Unicode code points. A built-in baseline rejects common passwords. Supply additional `PasswordBlocklist` beans for the application's common and compromised password corpus. The demo rate limits requests per remote address and login attempts per canonical email in a bounded per-process cache. Apply shared limits at the ingress when deploying several replicas.

Password reset increments the account's password credential version. Existing password sessions are rejected on their next request. When the first-party token module is enabled, reset revokes the account's refresh families in the same transaction. Existing access JWTs retain their normal expiry. Sign in again with the new password.

Challenge requests are queued for asynchronous delivery in the demo. The bounded in-process queue can lose work on restart. Production applications can use a durable delivery worker. Keep raw challenge links, email credentials, and password bodies out of logs. Use `EmailPasswordService.purgeExpiredChallenges()` from the consuming application's maintenance job to remove expired rows.

[OWASP password recovery guidance](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html)
