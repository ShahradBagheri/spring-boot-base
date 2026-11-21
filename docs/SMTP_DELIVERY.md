# Outbound SMTP delivery

Add `com.notshahrad:base-mail-smtp` using the version of the other base modules. The demo application already carries the adapter. It starts with email delivery disabled and needs no SMTP settings in that mode. Set `base.mail.smtp.enabled=true` to register the adapter as an `EmailSender`. Supplying your own `EmailSender` bean takes precedence, even when SMTP is enabled.

SMTP delivery is independent of email/password authentication. Inject `EmailSender` into application services that send notifications. The adapter uses the configured sender mailbox and supports plain text, HTML, or both as MIME alternatives.

## Configuration

| Environment variable | Purpose and default |
| --- | --- |
| `BASE_MAIL_SMTP_ENABLED` | Enable the adapter. Default `false`. |
| `BASE_MAIL_SMTP_HOST` | SMTP hostname or address. Required when enabled. |
| `BASE_MAIL_SMTP_PORT` | Optional port override. Default `587` for STARTTLS, `465` for implicit TLS, or `1025` for plaintext. |
| `BASE_MAIL_SMTP_FROM` | Provider-approved sender mailbox. Required when enabled. |
| `BASE_MAIL_SMTP_SECURITY` | `starttls` is the default and requires a successful TLS upgrade. Use `implicit-tls` for TLS from connection start or `plaintext` for a development catcher. |
| `BASE_MAIL_SMTP_USERNAME`, `BASE_MAIL_SMTP_PASSWORD` | Supply both for SMTP authentication, or leave both unset for an unauthenticated relay. Inject them from secret storage. |
| `BASE_MAIL_SMTP_CONNECTTIMEOUT` | Socket connect timeout, default `3s`. |
| `BASE_MAIL_SMTP_READTIMEOUT` | Socket read timeout, default `5s`. |
| `BASE_MAIL_SMTP_WRITETIMEOUT` | Socket write timeout, default `5s`. |

The timeout settings accept Spring durations from 1 millisecond through 60 seconds. They cover individual socket operations. Both encrypted modes validate the SMTP server hostname with the JVM's trusted certificate authorities. The adapter rejects credentials in plaintext mode. A process can start with syntactically valid settings while the relay is temporarily unavailable. The first send reports delivery errors.

[Spring Boot mail configuration](https://docs.spring.io/spring-boot/reference/io/email.html) · [Angus Mail TLS options](https://eclipse-ee4j.github.io/angus-mail/FAQ.html)

## Local development

Run a local Mailpit catcher with ports bound to localhost.

```sh
docker run -d --rm --name base-mailpit \
  -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 axllent/mailpit

export BASE_MAIL_SMTP_ENABLED=true
export BASE_MAIL_SMTP_HOST=127.0.0.1
export BASE_MAIL_SMTP_SECURITY=plaintext
export BASE_MAIL_SMTP_FROM=sender@example.test
```

Trigger an outbound email from consuming application code using a test recipient, then inspect the message in the catcher. The demo enables dependency injection for the adapter without adding a public send-mail endpoint. [Mailpit at localhost:8025](http://localhost:8025)

```java
emailSender.send(new EmailMessage(
        "receiver@example.test",
        "Delivery check",
        "This message goes to the local mail catcher.",
        null));
```

The embedded SMTP test exercises MIME delivery to a local catcher without outbound network calls. Consumers can also inject `RecordingEmailSender` from the `base-mail` test artifact for tests without sockets.

[Mailpit Docker instructions](https://mailpit.axllent.org/docs/install/docker/)

## Failure handling

Sending is synchronous. Keep SMTP calls out of database transactions and latency-sensitive request handlers. For critical notifications, dispatch through a consuming application's durable worker after the business change commits. A return from `send` indicates that the SMTP relay accepted the message. Mailbox delivery status belongs to the provider.

`EmailDeliveryException` exposes a reason such as invalid message, rejected authentication, or transport failure. Its public diagnostic omits credentials, recipients, and message content. The adapter makes one attempt per call. A failed transport can have an ambiguous outcome if the server accepted the message before the connection dropped. Let the caller choose bounded retries with deduplication where possible. Fix configuration or invalid input before retrying those failures.
