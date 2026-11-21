# Demo Application

The `demo-app` module is the executable Spring Boot application in this Maven reactor. It consumes `base-core`, `base-data-jpa`, `base-web`, and `base-security` as regular Maven dependencies. Removing the optional `base-security` dependency also removes its reusable security baseline; consuming apps should opt in deliberately.

## Requirements

- Java 25
- Maven 3.9 or newer
- PostgreSQL for the `dev` profile
- Docker-compatible engine to execute the full Testcontainers integration suite

## Run locally with PostgreSQL

Create an empty PostgreSQL database, then set the following environment variables in PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:DB_URL = "jdbc:postgresql://localhost:5432/base_project"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "your-local-password"
$env:SERVER_PORT = "8080"
```

Run from the repository root:

```powershell
mvn clean package
java -jar demo-app/target/demo-app-0.0.1-SNAPSHOT.jar
```

The application-specific datasource configuration is in `src/main/resources/application-dev.yaml`. The shared server port and active profile defaults are in `application.yaml`. No database credentials are committed.

An empty PostgreSQL database is initialized by Flyway on startup. The first migration creates `application_settings`; Hibernate checks the resulting schema using `ddl-auto=validate`. Migrations are stored in `src/main/resources/db/migration` and are tracked in `flyway_schema_history`. See the [migration guide](../docs/DB_MIGRATIONS.md) before using an existing database.

Spring Boot does not automatically load a `.env` file. If you use one locally, arrange for your IDE or environment to expose its values as process environment variables.

## Optional MCP server

See the [MCP setup guide](../docs/MCP_SERVER_SETUP.md) to enable the server, configure its OAuth issuer, and connect a client.

## Authentication maintenance and delivery metrics

Set `base.auth.cleanup.enabled=true` to schedule cleanup for each enabled email-password, phone-OTP, and refresh-session service. Each service deletes up to `base.auth.cleanup.batch-size` expired rows per run (default 500, range 1–10000). The fixed delay is `base.auth.cleanup.interval` (default 5m, minimum 1s). PostgreSQL skips locked rows. Refresh-family removal also deletes its credential history. Size the batch and interval for the expected expiry rate.

The Micrometer counter `auth.challenge.delivery` uses `channel=email|sms` and `outcome=queued|rejected|sent|failed`. Queued counts accepted tasks, including ineligible challenges that produce no message. Sent means the sender returned successfully. Failed includes processing and sender exceptions. Each application instance publishes its own counters.

## Run tests

```powershell
mvn clean test
```

This runs fast unit, MVC and JPA tests with the H2 `test` profile. For the full verification suite, start Docker and run:

```powershell
mvn clean verify
```

Failsafe runs `*IT` tests against disposable PostgreSQL Testcontainers, applying the real Flyway migrations and checking persistence mappings. A locally installed PostgreSQL server is not required for those integration tests.

## Check startup

With the application running, request:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

A healthy application returns a status of `UP`.

## Module responsibilities

| Module | Responsibility |
| --- | --- |
| `base-core` | Shared framework-independent contracts and exceptions |
| `base-data-jpa` | Reusable JPA mapped superclass and auditing configuration |
| `base-web` | Shared API response DTOs and MVC exception mapping |
| `base-security` | Optional Spring Security configuration and authentication/authorization errors |
| `demo-app` | Application entry point, environment configuration, and executable Spring Boot artifact |

The repository root is a Maven aggregator with `pom` packaging. Application-specific databases, migrations, and runtime configuration belong in `demo-app`.

For a full checklist, consult [development setup](../docs/DEVELOPMENT_SETUP.md). For a new entity, repository, service, controller, validation and tests, consult [feature extension](../docs/EXTENDING.md). Check [the security policy](../docs/SECURITY_POLICY.md) before exposing application routes.
