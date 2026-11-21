# Spring Boot Base

A reusable Java 25 / Spring Boot 4.1.2 foundation in one Maven multi-module repository. `base-core`, `base-data-jpa`, `base-web` and `base-security` are separate library JARs; `demo-app` is the only executable application.

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
| `demo-app` | Executable Spring Boot JAR | Application entry point, sample endpoints, migrations, and runtime configuration |

The root `pom.xml` has `pom` packaging and coordinates the reactor. It is not an application.

Start with the [developer setup guide](docs/DEVELOPMENT_SETUP.md) for Java 25, Maven, PostgreSQL, Docker, and troubleshooting. See [module architecture](docs/MODULES.md) for dependency boundaries and [adding a feature](docs/EXTENDING.md) for entities, repositories, services, controllers, validation, and tests.

See the [API error response contract](docs/API_ERRORS.md) for the public JSON error shape, and the [security policy](docs/SECURITY_POLICY.md) for endpoint access rules and authentication limitations.

## Build and test from a fresh checkout

From the repository root:

```bash
mvn clean verify
```

This compiles the three modules, runs fast tests with H2 where applicable, runs PostgreSQL integration tests through Testcontainers and Maven Failsafe, and packages the executable `demo-app`. Docker must be available for the integration phase, but no local PostgreSQL installation or credentials are required. Use `mvn test` to run fast tests without Docker.

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

See [demo-app instructions](demo-app/README.md) for further runtime details.

## Project status

The four reusable library modules and `demo-app` above exist today. Core exceptions, JPA mapping, Flyway migrations, Testcontainers integration tests, reusable web contracts and optional Spring Security are implemented. `demo-app` opts in to `base-web` and `base-security` using Maven dependencies. Check the [security policy](docs/SECURITY_POLICY.md) for the current security baseline and limitations; sign-in, Google/Apple OIDC, outbound email, phone-based login, guest identities, and MCP integrations remain separate planned features. Do not treat test authentication as a working production login mechanism.

Track remaining work in [GitHub Issues](https://github.com/ShahradBagheri/spring-boot-base/issues).
