# Enable phone signup and login

Phone signup and login are opt-in browser session flows in `demo-app`. They use PostgreSQL for phone identities and OTP state. Apply Flyway migrations V7 and V8 and read the [phone authentication model](PHONE_AUTHENTICATION_MODEL.md) for identity and OTP behavior.

## Configure the application

1. Add `auth-phone` to the active profiles, for example `SPRING_PROFILES_ACTIVE=dev,auth-phone`. The profile enables both `base.auth.phone.enabled` and `base.auth.phone.otp.enabled`.
2. Set `PHONE_AUTH_OTP_HMAC_KEY` to a Base64-encoded random key of at least 32 bytes. Generate a development key with `openssl rand -base64 32`. Store the key in your environment or secrets manager and use the same value across application replicas. Changing the key invalidates outstanding codes.
3. Register an application-owned `SmsSender` bean from `base-sms`. Its `send(SmsMessage message)` method receives an E.164 recipient and the SMS body. Supply provider credentials and sender identity through your environment or secrets manager and configure delivery timeouts in the provider client. For automated tests, use an in-memory sender and assert on captured messages without contacting a provider. See `PhoneAuthPostgresIT` in `demo-app` for an example.

## Make a request

Fetch `GET /api/auth/phone/csrf` and preserve its session cookie. For each POST, send the same cookie and include the CSRF token using the returned `headerName`. Send JSON for the request and verify endpoints.

| Route | Request | Success |
|---|---|---|
| `POST /api/auth/phone/signup/request` | `phoneNumber`, optional `region` | `202` with `challengeId` |
| `POST /api/auth/phone/login/request` | `phoneNumber`, optional `region` | `202` with `challengeId` |
| `POST /api/auth/phone/signup/verify` | `phoneNumber`, optional `region`, `challengeId`, `code` | `204` and an authenticated session for the new account |
| `POST /api/auth/phone/login/verify` | `phoneNumber`, optional `region`, `challengeId`, `code` | `204` and an authenticated session for the existing account |
| `POST /api/auth/phone/logout` | CSRF header and session cookie | `204` after ending the session |

Use an international number beginning with `+`, or submit a national number alongside its two-letter region. Submit the six-digit code as a JSON string to preserve leading zeroes. The request endpoint acknowledges asynchronously with the same response shape for eligible and ineligible numbers. Keep the newest challenge ID for verification. Unusable proofs return a generic `401`. Rate-limited submissions return `429`.

After successful verification, use the updated session cookie and fetch a fresh CSRF token before the next POST. These routes issue browser sessions. JWT access and refresh tokens have their own [operating guide](TOKEN_OPERATIONS.md).

## Configure throttling

`base.auth.phone.rate.window` defaults to `1m` and accepts durations between one second and one hour. Each budget below is a positive integer applied per window.

| Operation | Per normalized number | Per client IP | Overall |
|---|---|---|---|
| SMS requests | `base.auth.phone.rate.requests.per-phone` (3) | `base.auth.phone.rate.requests.per-ip` (20) | `base.auth.phone.rate.requests.overall` (200) |
| Verification | `base.auth.phone.rate.verifications.per-phone` (3) | `base.auth.phone.rate.verifications.per-ip` (20) | `base.auth.phone.rate.verifications.overall` (200) |

The HTTP counters and bounded SMS queue are in memory and apply per application instance. The OTP cooldown, expiry and failed-attempt count are stored in PostgreSQL. Use shared gateway limits and provider spending caps when running multiple instances. Behind a proxy, configure the server to derive the remote address from trusted proxies only. Queued SMS deliveries can be lost during an application restart.

Use SMS possession checks according to your application's account recovery and sensitive-operation requirements. [NIST guidance on PSTN authentication](https://pages.nist.gov/800-63-4/sp800-63b.html)
