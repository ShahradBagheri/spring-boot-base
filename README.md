# Spring Boot Base

A reusable Java 25 / Spring Boot 4.1.2 foundation built as a single Maven multi-module repository. The library modules are reusable JARs; `demo-app` is the only executable Spring Boot application.

## Requirements

- JDK 25
- Maven 3.9+
- PostgreSQL for running the `dev` profile (not needed for the standard test suite)

## Current modules

| Module | Packaging | Purpose |
| --- | --- | --- |
| `base-core` | JAR | Framework-independent application exceptions and contracts |
| `base-data-jpa` | JAR | Reusable JPA `BaseEntity`, mapping and opt-in auditing configuration |
| `demo-app` | Executable Spring Boot JAR | Application entry point, HTTP and runtime configuration, datasource credentials supplied via environment variables |

The root `pom.xml` has `pom` packaging and coordinates the reactor. It is not an application.

See [module architecture and extension guide](docs/MODULES.md) for dependency rules, module-specific builds, adding optional modules, and consuming the libraries from another project.

## Build and test from a fresh checkout

From the repository root:

```bash
mvn clean verify
```

This compiles the three modules, runs tests in the owning modules, and packages the executable `demo-app`. Tests use an embedded H2 datasource where applicable, so a running PostgreSQL instance is not required.

To build only a selected module and any reactor dependencies:

```bash
mvn -pl base-core test
mvn -pl base-data-jpa -am test
mvn -pl demo-app -am package
```

The [Maven reactor CI workflow](.github/workflows/maven-reactor.yml) also checks module boundaries, package contents and startup against PostgreSQL.

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

The application normally starts on port 8080 when `SERVER_PORT` is unset. Its development profile uses PostgreSQL and `spring.jpa.hibernate.ddl-auto=validate`; it does **not** create your application schema. Flyway integration is planned, not yet included. Supply any schema your application adds before starting with `validate`.

A `.env` file is not loaded automatically by Spring Boot: configure your IDE or shell to pass environment variables. Do not commit passwords.

See [demo-app instructions](demo-app/README.md) for further runtime details.

## Project status

The three modules above exist today. Optional security, sign-in, email, phone, guest-user and MCP integrations are planned features, **not installed modules**. Each should be introduced only when it has an implementation and tests; the base libraries must not depend on optional integrations.

Track remaining work in [GitHub Issues](https://github.com/ShahradBagheri/spring-boot-base/issues).
