# Spring Boot Base

A reusable Java 25 / Spring Boot 4.1.2 foundation in one Maven multi-module repository. The reusable modules build as library JARs. `demo-app` is the executable application.

## Start a new application

Spring Boot Base supports two ways to start an independent application. Both use
Java 25, a Spring Boot 4.1.2 parent, and a pinned Base BOM.

### Add the starters to an existing Maven app

Create an app at [Spring Initializr](https://start.spring.io/) or use an existing
Spring Boot Maven project. Set `base.version` to a version already available
from Maven Central, then import the BOM and select the starters you need.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.shahradbagheri</groupId>
            <artifactId>spring-boot-base-bom</artifactId>
            <version>${base.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.shahradbagheri</groupId>
        <artifactId>base-web-starter</artifactId>
    </dependency>
</dependencies>
```

The first public release is pending. The repository's staged-artifact workflow
can verify starter usage before the public coordinates are available. Public
Maven Central downloads use Maven's default repository without credentials.
See the [distribution guide](docs/MAVEN_DISTRIBUTION.md) for supported versions.

### Download a generated application

Run `python3 generator/server.py --base-version VERSION`, using an existing
public Base release for `VERSION`. Open `http://127.0.0.1:8080`, choose the
features you need and download the ZIP. The generated project has its own
Boot parent, wrapper, configuration, and tests. See the
[generator documentation](generator/README.md) for CLI use.

### Configure your application

Use application-owned credentials, security policies, datasource settings and
schema migrations. The [starter defaults](docs/STARTER_CONVENTIONS.md) describe
feature flags and bean overrides. For a project that copied the Base source,
follow the [migration guide](docs/MIGRATE_COPIED_PROJECT.md) before substituting
starter dependencies.

## Develop the Base libraries

Clone this repository to develop the Base implementation, starters, demo, or
generator.

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
| `base-websocket` | JAR | Opt-in STOMP messaging over WebSocket. See the [setup guide](docs/WEBSOCKET.md). |
| `base-security` | JAR | Optional security filter chain, CSRF baseline, method-level authorization and JSON 401/403 responses |
| `base-auth` | JAR | Optional local users and issuer/subject identity mapping, with fail-closed account linking |
| `base-auth-oauth2` | JAR | Optional Google/Apple OAuth2 Client and local OIDC principal adaptation; disabled by default |
| `base-auth-token` | JAR | Optional first-party access JWT issuance and bearer validation |
| `base-auth-email` | JAR | Optional storage and normalization for verified email login identities |
| `base-mail` | JAR | Provider-independent email contracts, templates, and notification dispatch |
| `base-mail-smtp` | JAR | Opt-in SMTP delivery for outbound email |
| `base-auth-phone` | JAR | Optional verified phone login identities |
| `base-sms` | JAR | Provider-independent SMS contracts |
| `base-mcp-server` | JAR | Optional MCP HTTP transport and bearer-token security |
| `demo-app` | Executable Spring Boot JAR | Application entry point, sample endpoints, migrations, and runtime configuration |

The root `pom.xml` has `pom` packaging and coordinates the reactor. It is not an application.

Start with the [developer setup guide](docs/DEVELOPMENT_SETUP.md) for Java 25, Maven, PostgreSQL, Docker, and troubleshooting. See [module architecture](docs/MODULES.md) for dependency boundaries and [adding a feature](docs/EXTENDING.md) for entities, repositories, services, controllers, validation, and tests.

See the [API error response contract](docs/API_ERRORS.md) for the public JSON error shape, and the [security policy](docs/SECURITY_POLICY.md) for endpoint access rules and authentication limitations.

To adopt an individual auth module, see [using auth modules in another service](docs/AUTH_MODULE_ADOPTION.md) for the required PostgreSQL migrations and a standalone email/password example.

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
java -jar demo-app/target/demo-app-0.1.0-M1.jar
```

In another terminal:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

The application normally starts on port 8080 when `SERVER_PORT` is unset. Its development profile uses PostgreSQL, Flyway migrations, and `spring.jpa.hibernate.ddl-auto=validate`. Flyway creates the versioned schema before Hibernate validates it, starting with the `application_settings` table. Use an empty database on first startup; Flyway does not automatically baseline existing unversioned schemas. See the [database migration guide](docs/DB_MIGRATIONS.md).

A `.env` file is not loaded automatically by Spring Boot: configure your IDE or shell to pass environment variables. Do not commit passwords.

See the [demo app instructions](demo-app/README.md) for runtime details and the [OIDC provider setup guide](docs/OIDC_PROVIDER_SETUP.md) to enable Google or Apple sign-in. See the [token operating guide](docs/TOKEN_OPERATIONS.md) for setup and recovery, and the [access token contract](docs/ACCESS_TOKEN_CONTRACT.md) for JWT and refresh behavior.

## Project status

Core exceptions, JPA mapping, Flyway migrations, Testcontainers integration tests, reusable web contracts, optional Spring Security, and Google/Apple OIDC sign-in are implemented. The [email authentication model](docs/EMAIL_AUTHENTICATION_MODEL.md) describes email identities, signup, verification, and password reset. See the [email/password setup guide](docs/EMAIL_PASSWORD_SETUP.md) for the opt-in flow. See the [email configuration guide](docs/EMAIL_CONFIGURATION.md) for independent enablement, template customization, SMTP delivery, and sender-domain setup. See the [phone authentication setup guide](docs/PHONE_AUTHENTICATION_SETUP.md) to enable SMS signup and login. See the [guest session guide](docs/GUEST_SESSION_MODEL.md) for opt-in browser guest identities. The optional guest draft example includes account transfer and expiry cleanup. See the [MCP server setup guide](docs/MCP_SERVER_SETUP.md) to enable the server and connect a client.

Track remaining work in [GitHub Issues](https://github.com/ShahradBagheri/spring-boot-base/issues).
