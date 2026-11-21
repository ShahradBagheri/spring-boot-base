# Phone authentication and account lifecycle

The phone authentication module shares the `AppUser` account model with email and OIDC login. Enable its storage using `base.auth.phone.enabled=true`. The default application leaves phone authentication disabled. SMS delivery is a separate, provider-independent capability.

## Phone number identity

Store one verified E.164 number per local account. An E.164 number can belong to only one account, including accounts that are currently inactive. Releasing an inactive account's number requires an explicit account lifecycle decision.

- International input starts with `+` and its calling code. The supplied country field does not change the explicit calling code.
- National input requires an explicit two-letter country or territory, such as `GB` or `US`. Ask the user to choose it. A number entered with an international dialing prefix such as `00` or `011` must be entered with `+` instead.
- The parser accepts conventional spaces, parentheses, dots, and hyphens. It rejects extensions, vanity letters, and numbers that fail the region's numbering plan. For example, `020 7946 0018` with `GB` and `+44 20 7946 0018` both normalize to `+442079460018`.
- Number normalization checks format and numbering-plan validity. Only successful proof of possession permits binding an identity. Sending an SMS and checking possession are separate steps.

The application uses [Google libphonenumber](https://github.com/google/libphonenumber) for parsing, validation, and E.164 formatting.

## Signup, login, and changes

| Flow | Policy |
| --- | --- |
| Signup | Start a distinct signup challenge. After the user redeems a valid challenge for an unbound number, create the local user and bind the number in the same transaction. Recheck uniqueness before committing. |
| Login | Redeem a login challenge for a number already verified on an active account. A login challenge cannot create an account. Login and signup challenges are bound to their original purpose and number. The public request response stays the same for eligible, inactive, and unknown numbers. |
| Link a number | Require an authenticated session with recent reauthentication and an explicit linking challenge for the new number. A matching phone claim from Google, Apple, or another account profile cannot merge accounts. A number already bound to another account remains reserved. |
| Change a number | Keep the current number active until a replacement is proven and committed. Require recent authentication with an existing authenticator or an approved account recovery flow. Reaching the new number alone cannot authorize the change. |
| Successful authentication | Establish a local browser session, rotate the session identifier, and apply the usual CSRF rules. A consuming application can separately enable first-party bearer tokens. |

SMS possession can change after a SIM swap, number port, or reassignment. Provide an alternate account recovery method and apply the consuming application's risk policy to authenticator changes and other sensitive operations. [NIST guidance on PSTN authentication](https://pages.nist.gov/800-63-4/sp800-63b.html)

## Storage and delivery setup

`base-auth-phone` contains the normalized value, immutable identity snapshot, and PostgreSQL repository. Consuming applications can scan the module package or import `PhoneIdentityRepository`. Apply the [phone identity migration](DB_MIGRATIONS.md#phone-login-identities) before enabling it. The repository writes an identity only from the package-level verified binding operation. All persisted rows carry a verification time.

`base-sms` supplies a framework-independent `SmsSender` contract and a single-recipient `SmsMessage`. Supply an adapter through application configuration when integrating delivery. Keep provider URLs, API credentials, and sender IDs in external configuration or secret storage. Avoid logging full destination numbers, raw verification codes, or message text.

For tests, use the matching `com.notshahrad:base-sms` version with Maven type `test-jar`, classifier `tests`, and test scope. Inject `RecordingSmsSender` to capture message snapshots in memory. It opens no provider connection.

## One-time-code lifecycle

Enable the reusable OTP service with both `base.auth.phone.enabled=true` and `base.auth.phone.otp.enabled=true`. Apply migration V8. Configure `base.auth.phone.otp.hmac-key` using a randomly generated Base64 key of at least 32 bytes from secret storage. Every application instance verifying the same challenges must use the same key. Changing it invalidates outstanding codes.

| Property under `base.auth.phone.otp` | Default | Behavior |
| --- | --- | --- |
| `lifetime` | `5m` | Challenge window. Accepted range is one second through ten minutes. |
| `resend-cooldown` | `1m` | Minimum interval before replacement. At least one second and shorter than the lifetime. |
| `max-attempts` | `5` | Failed attempt budget per number and purpose in one challenge window. Accepted range is one through ten. |

The service generates a six-digit code and returns it to the trusted delivery caller. Store the returned challenge ID for verification and give the code only to the SMS sender. PostgreSQL holds a keyed verifier bound to the phone number, purpose, and challenge ID. A resend replaces the challenge ID and verifier while preserving the expiry and failed-attempt count. A consumed or exhausted challenge cannot be replaced until its window expires.

Verification locks the challenge row until the transaction ends. A successful verification consumes the challenge. The caller may join that transaction with local account creation or authentication decisions. Return rejected proof normally so the failed-attempt update can commit. Applications can periodically call `purgeExpired()` to remove expired rows after their resend cooldown has passed.

The HTTP signup/login routes, SMS dispatch, and request-level abuse controls are tracked separately. [Phone authentication epic](https://github.com/ShahradBagheri/spring-boot-base/issues/41)
