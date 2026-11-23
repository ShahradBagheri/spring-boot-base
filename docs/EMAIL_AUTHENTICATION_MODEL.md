# Email authentication and account lifecycle

`base-auth-email` is an optional capability. It shares local users with Google, Apple, phone, and token authentication. `base-mail` is a separate outbound email contract and can be used for notifications without enabling email/password login.

## Login identity and contact addresses

A local user can have one primary email/password login address. Its verification state lives in `email_login_identities.verified_at`. There is at most one row per user and at most one verified user per canonical address. Pending rows may share an address. Starting a signup therefore cannot reserve another person's mailbox. The verified-identity index and `app_users` foreign key enforce the ownership rules in PostgreSQL.

The login address is separate from an account's display or contact email in `app_users`, and from the recipient on an outbound email. OIDC profile claims and caller-supplied notification addresses do not populate the login table. Soft-deleted accounts cannot sign in. Their verified address stays reserved until an explicit account recovery or removal process releases it.

### Normalization

Trim outer spaces and lowercase the DNS domain. Preserve the local part exactly, including ASCII case, dots, and plus tags. The first implementation accepts an unquoted ASCII dot-atom local part and at least two DNS labels, including already encoded Punycode domains. It rejects quoted local parts, Unicode addresses, display names, control characters, and address lists. Validation establishes supported syntax. Verification establishes mailbox control.

[SMTP mailbox case rules, RFC 5321 §2.4](https://datatracker.ietf.org/doc/html/rfc5321#section-2.4)

## Account flows

| Flow | Policy |
| --- | --- |
| Sign up | Stage a signup challenge for the requested address and send a short-lived verification link. Use the same public response for existing and unknown addresses. A pending identity cannot authenticate. The person who redeems the verification challenge chooses the initial password. |
| Verify | Bind the signup challenge to the exact canonical address and verification purpose. Consume it, create the local user, and activate the email identity and password in one transaction. Issuing a new challenge supersedes the previous one for that address and purpose. A passive link preview cannot complete verification. |
| Log in | Look up the verified canonical address for an active user and check their password. Return a generic authentication failure for unknown addresses, pending identities, and wrong passwords. Establish the standard local browser session on success. A consuming application may separately opt into [first-party token issuance](ACCESS_TOKEN_CONTRACT.md). |
| Link to an existing account | Require an authenticated session with recent reauthentication and proof of the new email address. Link the verified identity to that same local user. A Google, Apple, or phone account with matching profile information stays separate unless the user completes an explicit, verified linking flow. If the address is already verified for another local user, deny the link and require the account recovery process. |
| Change a verified address | Require recent authentication and verification of the replacement. Keep the current address active until a transactional change succeeds. The pending-storage API cannot overwrite a verified identity. |
| Reset a password | Provide a generic acknowledgement. Send a single-use reset link only for an active verified identity. A password reset replaces the credential and invalidates outstanding reset challenges and existing application sessions and refresh families through the consuming application's session integrations. The user signs in again. Previously issued stateless access JWTs keep their normal expiry. |

Apply rate limits to signup, login, verification, resend, and reset. Expiring challenges store only verifiers and never change account state merely because a client submits an email address. Verification and reset completion use state-changing requests. Keep passwords, complete link URLs, and raw challenge values out of logs and monitoring.

Use Argon2id for password storage with per-password salts and versioned parameters. Accept 15–128 Unicode code points for password-only authentication. Allow paste and password managers, preserve password characters, and reject common or compromised passwords using a blocklist. Avoid character-class rules and periodic changes without evidence of compromise.

[NIST SP 800-63B-4 password requirements](https://pages.nist.gov/800-63-4/sp800-63b.html#passwords) · [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)

## Module boundary and current status

- `base-auth-email` provides normalization, the PostgreSQL identity repository, and the password and challenge services. To load the repository, include the module, scan its package or import `EmailIdentityRepository`, and set `base.auth.email.enabled=true`. Apply [migration V5](DB_MIGRATIONS.md#email-login-identities) in the consuming application. Only trusted application code stages an identity for a local account. The package-level verification transition is reserved for the challenge handler.
- `base-mail` provides `EmailSender` and `EmailMessage` for single-recipient application email with text and/or HTML. The optional [SMTP adapter](SMTP_DELIVERY.md) supplies one delivery implementation. A consuming application can supply its own. Recipient addresses are separate from authentication state. The module has no Spring or provider runtime dependency. Callers should handle delivery failures and keep provider credentials outside source control.
- For tests against a locally installed reactor, depend on `io.github.shahradbagheri:base-mail` with the matching reactor version, Maven type `test-jar`, classifier `tests`, and test scope. `RecordingEmailSender` records immutable message snapshots in memory without connecting to a provider.

Use the [email/password setup guide](EMAIL_PASSWORD_SETUP.md) to enable the demo and exercise signup, login, verification, and recovery. Explicit account linking and changes to an already verified address are application-specific flows.
