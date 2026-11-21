# Demo Application

The `demo-app` module is the executable Spring Boot application in this Maven reactor. It consumes `base-core` and `base-data-jpa` as regular Maven dependencies.

## Requirements

- Java 25
- Maven 3.9 or newer
- PostgreSQL for the `dev` profile

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

Spring Boot does not automatically load a `.env` file. If you use one locally, arrange for your IDE or environment to expose its values as process environment variables.

## Run tests

```powershell
mvn clean verify
```

The `test` profile uses an in-memory H2 datasource and does not require a running PostgreSQL instance.

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
| `demo-app` | Application entry point, environment configuration, and executable Spring Boot artifact |

The repository root is a Maven aggregator with `pom` packaging. Application-specific databases, migrations, and runtime configuration belong in `demo-app`.
