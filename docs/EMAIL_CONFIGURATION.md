# Email configuration and security

Applications can enable password authentication, outbound mail, or both. Email authentication owns account verification and recovery. Outbound delivery handles notifications through the `EmailSender` interface.

| Use case | Configuration |
| --- | --- |
| Application notifications | Include `base-mail` and supply an `EmailSender`. For SMTP, also include `base-mail-smtp` and set `BASE_MAIL_SMTP_ENABLED=true`. Leave the `auth-email` profile off. |
| Email/password authentication using SMTP | Enable `auth-email` alongside the database profile and configure the SMTP adapter. Set the public frontend origin in `EMAIL_AUTH_CLIENT_ORIGIN`. |
| Email/password authentication using another sender | Enable `auth-email`, leave the SMTP adapter disabled, and supply your own `EmailSender` bean. That sender handles challenge delivery. It can be a production provider or a recording implementation in tests. |

The demo includes the optional modules. Removing the `auth-email` profile disables its password routes unless the flags are enabled elsewhere. Removing the SMTP flag disables the default SMTP sender. If password authentication is enabled, the application needs a sender for verification and recovery. The [password setup guide](EMAIL_PASSWORD_SETUP.md) lists the endpoints and client requirements.

## Configure SMTP and the sender domain

Use your provider's approved relay, port, and sender mailbox. These values are illustrative. Inject `BASE_MAIL_SMTP_PASSWORD` from secret storage at runtime.

```dotenv
BASE_MAIL_SMTP_ENABLED=true
BASE_MAIL_SMTP_HOST=smtp.example.test
BASE_MAIL_SMTP_PORT=587
BASE_MAIL_SMTP_SECURITY=starttls
BASE_MAIL_SMTP_USERNAME=your-provider-username
BASE_MAIL_SMTP_FROM=notifications@example.test
```

For local development, use a loopback mail catcher without real credentials. In production, use verified TLS and restrict the credential to the application and permitted sender domains where your provider supports it. The [SMTP delivery guide](SMTP_DELIVERY.md) covers TLS modes, timeouts, local testing, and failure handling.

At your DNS provider, use the exact records supplied by the mail provider for the sender domain.

- **SPF** authorizes the sending infrastructure for the envelope sender domain.
- **DKIM** lets your provider sign messages with a domain key. Publish the provider's public key under the configured selector and keep the private signing key with the signer.
- **DMARC** checks alignment between the visible From domain and a domain authenticated by SPF or DKIM. Monitor reports and verify alignment from your legitimate senders before enforcing a stricter policy.

Check the received headers of a test message from your production domain and confirm the expected authentication results. SMTP acceptance only confirms handoff to the relay. Delivery can still fail later because of filtering, reputation, or a mailbox rejection. [DMARC standard](https://www.rfc-editor.org/rfc/rfc9989.html)

## Customize auth templates and send notifications

The demo uses `DefaultEmailChallengeTemplates` for signup verification and password reset. Register an `EmailChallengeTemplates` bean to customize the subject or body. The application supplies the challenge link and addresses the rendered message to the account's mailbox. The template controls the subject and body. Keep that link intact and preserve its fragment. If you add HTML, escape any interpolated text or attribute values appropriately.

```java
@Bean
EmailChallengeTemplates accountEmailTemplates() {
    var defaults = new DefaultEmailChallengeTemplates();
    return new EmailChallengeTemplates() {
        @Override
        public Content signup(Challenge challenge) {
            return new Content("Confirm your account",
                    "Open this link to confirm your email:\n" + challenge.link(), null);
        }

        @Override
        public Content passwordReset(Challenge challenge) {
            return defaults.passwordReset(challenge);
        }
    };
}
```

Use `EmailNotifications` in application code to pair any typed template with a configured sender. This example takes its invoice reference from application data and can run with an SMTP adapter or a provider-specific sender.

```java
var notifications = new EmailNotifications(emailSender);
EmailTemplate<String> invoiceReady = reference -> new EmailMessage(
        "billing@example.test", "Invoice available", "Invoice " + reference + " is ready.", null);
notifications.send(invoiceReady, "INV-17");
```

Rendering and sending are synchronous in `EmailNotifications`. The demo queues authentication messages on its bounded worker and keeps mail transport outside the credential transaction. A consuming application can pair the same templates with its own worker or outbox. Decide retry and deduplication policy in that worker. A send failure can be ambiguous if the provider accepted the email before the connection dropped.

## Account handling and credential rotation

Signup sends a mailbox challenge. Redeeming it creates the verified login identity and sets the initial password. Login requires a verified address and uses a browser session. Recovery uses a separate expiring challenge, changes the password, and invalidates existing password sessions and the account's refresh families when the token module is enabled. An existing access JWT keeps its normal expiry. Account linking to Google, Apple, or phone accounts requires explicit proof and application integration. A matching profile email does not by itself link accounts. See the [email account lifecycle](EMAIL_AUTHENTICATION_MODEL.md).

Keep SMTP/API credentials in deployment secret storage and keep mail bodies, raw challenges, passwords, and browser cookies out of logs and telemetry. For planned rotation, provision the replacement credential, deploy it, test delivery, then revoke the previous credential. If the old secret is exposed, revoke it promptly and investigate unexpected sends. Manage DKIM selector rotation separately with the signing provider so recipients can still verify messages sent during the change.

Run `mvn clean verify` with Docker to cover the PostgreSQL auth flows. The tests use a fake sender for account messages and a loopback SMTP catcher for MIME delivery. They exercise expiration, replay, generic account responses, sender errors, and credential redaction.
