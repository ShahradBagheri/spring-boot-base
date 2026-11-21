# Spring Boot Base

A reusable Java 25 / Spring Boot 4.1.2 foundation in one Maven multi-module repository. The reusable modules build as library JARs. `demo-app` is the executable application.

## Requirements

- JDK 25
- Maven 3.9+
- PostgreSQL for running the `dev` profile
- Docker-compatible runtime for PostgreSQL Testcontainers integration tests (`mvn verify`); not required for `mvn test`

## Current modules

| Module | Packaging | Purpose |
| --- | --- | --- |
| `base-core` | JAR | Framework-independent application exceptions and contracts |
| `base-data-jpa` | JAR | Reusable JPA `BaseEntity`, mapping and opt-in auditing configuration |
| `base-web` | JAR | Reusable API error/success DTOs and MVC exception handling |
| `base-security` | JAR | Optional security filter chain, CSRF baseline, method-level authorization and JSON 401/403 responses |
| `base-auth` | JAR | Optional local users and issuer/subject identity mapping, with fail-closed account linking |
| `base-auth-oauth2` | JAR | Optional Google/Apple OAuth2 Client and local OIDC principal adaptation; disabled by default |
| `base-auth-token` | JAR | Optional first-party access JWT issuance and bearer validation |
| `base-auth-email` | JAR | Optional storage and normalization for verified email login identities |
| `base-mail` | JAR | Provider-independent outbound email contracts |
| `base-mail-smtp` | JAR | Opt-in SMTP delivery for outbound email |
| `base-auth-phone` | JAR | Optional verified phone login identities |
| `base-sms` | JAR | Provider-independent SMS contracts |
| `demo-app` | Executable Spring Boot JAR | Application entry point, sample endpoints, migrations, and runtime configuration |

The root `pom.xml` has `pom` packaging and coordinates the reactor. It is not an application.

Start with the [developer setup guide](docs/DEVELOPMENT_SETUP.md) for Java 25, Maven, PostgreSQL, Docker, and troubleshooting. See [module architecture](docs/MODULES.md) for dependency boundaries and [adding a feature](docs/EXTENDING.md) for entities, repositories, services, controllers, validation, and tests.

See the [API error response contract](docs/API_ERRORS.md) for the public JSON error shape, and the [security policy](docs/SECURITY_POLICY.md) for endpoint access rules and authentication limitations.

## Build and test from a fresh checkout

From the repository root:

```bash
mvn clean verify
```

This builds the Maven reactor, runs fast tests with H2 where applicable, runs PostgreSQL integration tests through Testcontainers and Maven Failsafe, and packages the executable `demo-app`. Docker must be available for the integration phase, but no local PostgreSQL installation or credentials are required. Use `mvn test` to run fast tests without Docker.

To build only a selected module and any reactor dependencies:

```bash
mvn -pl base-core test
mvn -pl base-data-jpa -am test
mvn -pl demo-app -am package
```

The [Maven reactor CI workflow](.github/workflows/maven-reactor.yml) also checks module boundaries, runs Testcontainers migrations, verifies package contents, and smoke-tests startup against PostgreSQL. See the [database migration and integration-test guide](docs/DB_MIGRATIONS.md).

## Run the demo application

Create a PostgreSQL database (for example `base_project`) and provide these environment variables. This example uses PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:DB_URL = "jdbc:postgresql://localhost:5432/base_project"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "your-local-password"
$env:SERVER_PORT = "8080"
mvn clean package
java -jar demo-app/target/demo-app-0.0.1-SNAPSHOT.jar
```

In another terminal:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

The application normally starts on port 8080 when `SERVER_PORT` is unset. Its development profile uses PostgreSQL, Flyway migrations, and `spring.jpa.hibernate.ddl-auto=validate`. Flyway creates the versioned schema before Hibernate validates it, starting with the `application_settings` table. Use an empty database on first startup; Flyway does not automatically baseline existing unversioned schemas. See the [database migration guide](docs/DB_MIGRATIONS.md).

A `.env` file is not loaded automatically by Spring Boot: configure your IDE or shell to pass environment variables. Do not commit passwords.

See the [demo app instructions](demo-app/README.md) for runtime details and the [OIDC provider setup guide](docs/OIDC_PROVIDER_SETUP.md) to enable Google or Apple sign-in. See the [token operating guide](docs/TOKEN_OPERATIONS.md) for setup and recovery, and the [access token contract](docs/ACCESS_TOKEN_CONTRACT.md) for JWT and refresh behavior.

## Project status

Core exceptions, JPA mapping, Flyway migrations, Testcontainers integration tests, reusable web contracts, optional Spring Security, and Google/Apple OIDC sign-in are implemented. The [email authentication model](docs/EMAIL_AUTHENTICATION_MODEL.md) describes email identities, signup, verification, and password reset. See the [email/password setup guide](docs/EMAIL_PASSWORD_SETUP.md) for the opt-in flow. See the [SMTP delivery guide](docs/SMTP_DELIVERY.md) to enable outbound mail. Phone authentication, guest identities, and MCP integrations are planned.

Track remaining work in [GitHub Issues](https://github.com/ShahradBagheri/spring-boot-base/issues).
