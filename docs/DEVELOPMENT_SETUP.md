# Development setup and verification

This guide is for a fresh checkout of the Maven multi-module repository. Run all commands from the repository root unless a section explicitly says otherwise.

## Prerequisites

- Java **25** JDK (not just the runtime). Set `JAVA_HOME` to that JDK and ensure Maven uses it.
- Maven **3.9+**.
- PostgreSQL to run the application using the `dev` profile. A fresh, empty database is recommended.
- Docker Desktop or another compatible Docker engine to run the full PostgreSQL Testcontainers integration suite. Docker is not required for fast unit and H2 tests.

Check the selected executables:

```bash
java -version
mvn -version
docker info
```

Maven must report Java 25. On Windows, the JDK Maven uses may differ from the JDK configured in IntelliJ. If they disagree, adjust `JAVA_HOME` and `PATH` before building.

## Clone and run fast tests

```bash
git clone https://github.com/ShahradBagheri/spring-boot-base.git
cd spring-boot-base
mvn clean test
```

`mvn test` runs core unit tests, Spring MVC tests, and JPA tests using H2 where applicable. It does **not** execute the Failsafe `*IT` PostgreSQL tests. It needs neither Docker nor PostgreSQL.

## Full verification

Start Docker, then run:

```bash
mvn clean verify
```

Maven runs the entire reactor, Failsafe tests using disposable PostgreSQL Testcontainers, Flyway migrations, Hibernate schema validation, and builds `demo-app/target/demo-app-0.0.1-SNAPSHOT.jar`.

If Docker is unavailable, `mvn clean test` is the fast fallback, **not** a substitute for a passing full verification workflow. GitHub Actions performs the full verification and also launches the packaged app against its own PostgreSQL service.

## PostgreSQL application database

Create a new, empty PostgreSQL database using your own administrator account. Example SQL, run inside `psql`:

```sql
CREATE DATABASE base_project;
```

Use environment variables instead of credentials in versioned YAML. In PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:DB_URL = "jdbc:postgresql://localhost:5432/base_project"
$env:DB_USERNAME = "your_postgres_user"
$env:DB_PASSWORD = "your_local_password"
$env:SERVER_PORT = "8080"
```

On macOS/Linux or a Bash shell:

```bash
export SPRING_PROFILES_ACTIVE=dev
export DB_URL=jdbc:postgresql://localhost:5432/base_project
export DB_USERNAME=your_postgres_user
export DB_PASSWORD=your_local_password
export SERVER_PORT=8080
```

Use your real local PostgreSQL username and password only in your environment. Never commit credentials, `.env`, local IDE secrets, or database dumps containing private data. Spring Boot does not automatically import `.env`.

For a **local build with Docker available**:

```bash
mvn clean package
java -jar demo-app/target/demo-app-0.0.1-SNAPSHOT.jar
```

For a package-only build **without Docker**, Maven's `package` phase runs fast tests but not Failsafe's `integration-test`/`verify` phases. Perform `mvn clean verify` before merging code that changes persistence.

Flyway creates the `application_settings` table from `V1__create_application_settings.sql` and records the migration in `flyway_schema_history`. Hibernate validates the resulting PostgreSQL schema with `ddl-auto=validate`; it does not generate a production database schema. Flyway does not automatically baseline an existing, manually managed database.

## Confirm the application starts

With the app running, from another terminal:

```bash
curl --fail http://localhost:8080/actuator/health
```

A healthy application returns JSON containing `"status":"UP"`. The planned/implemented security behavior of other endpoints is documented in [the security policy](SECURITY_POLICY.md). Do not expect a production login mechanism until its dedicated authentication modules have been implemented.

## Project layout and profiles

| Location | Responsibility |
| --- | --- |
| `pom.xml` | Maven parent and aggregator |
| `base-core/` | Framework-independent exceptions and contracts |
| `base-data-jpa/` | JPA `BaseEntity`, audited entity mapping infrastructure |
| `base-web/` | Reusable HTTP response contract and MVC errors |
| `base-security/` | Optional Spring Security baseline and JSON authentication errors |
| `demo-app/` | Executable Spring Boot app, Flyway migrations, HTTP and runtime configuration |
| `demo-app/src/main/resources/application.yaml` | Shared profile and Flyway defaults |
| `demo-app/src/main/resources/application-dev.yaml` | PostgreSQL datasource and Hibernate schema validation |
| `demo-app/src/test/resources/application-test.yaml` | H2 test database with Flyway disabled |
| `demo-app/src/test/resources/application-integration.yaml` | PostgreSQL Testcontainers and Flyway integration profile |

Use [module architecture](MODULES.md) for boundaries, [extension guide](EXTENDING.md) for adding features, and [migration guide](DB_MIGRATIONS.md) for database evolution.

## Common build problems

- **Unsupported class file version:** Run `mvn -version` and ensure Maven uses Java 25, not a different JDK from your IDE.
- **Missing `DB_URL` or PostgreSQL authentication failure:** Set the `dev` environment variables before launching the jar. The default profile is `dev`.
- **Flyway migration failure:** Confirm the target database is empty on first run, then inspect the migration history; do not blindly turn on `baseline-on-migrate`.
- **Testcontainers cannot find Docker:** Start Docker and verify `docker info`. Local `mvn test` can still run without it.
- **Hibernate schema validation failure:** Ensure Flyway migrations completed successfully and the entity mapping matches the migrated table.
