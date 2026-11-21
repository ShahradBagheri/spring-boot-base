# Maven modules and extension guide

This guide describes the modules that exist in the repository today and how to add or consume modules without turning optional features into mandatory dependencies.

## Existing modules

| Path | Maven artifact | Responsibilities |
| --- | --- | --- |
| `base-core/` | `com.notshahrad:base-core:0.0.1-SNAPSHOT` | Framework-independent exceptions and shared application contracts |
| `base-data-jpa/` | `com.notshahrad:base-data-jpa:0.0.1-SNAPSHOT` | JPA mapped superclass `BaseEntity`, UUID identifier, timestamps, optimistic-lock version and deletion metadata, and opt-in auditing configuration |
| `demo-app/` | `com.notshahrad:demo-app:0.0.1-SNAPSHOT` | Runnable Spring Boot application, REST concerns, external configuration, PostgreSQL connection and app-specific tests |
| `pom.xml` at repository root | `com.notshahrad:spring-boot-base:0.0.1-SNAPSHOT` | Parent POM, Maven reactor aggregator and dependency/plugin version management |

The root has `<packaging>pom</packaging>`. Only `demo-app` applies the Spring Boot Maven plugin to build an executable JAR. Library modules produce ordinary JAR files.

## Dependency rules

Current project-module dependencies:

```text
spring-boot-base (parent / aggregator)
  base-core                (no project-module dependency)
  base-data-jpa            (no project-module dependency)
  demo-app
    ├── base-core
    └── base-data-jpa
```

The parent/aggregator relationship is not a runtime dependency. Maven compiles modules in dependency order based on actual `<dependencies>` declarations, not on the order written in `<modules>`.

- `base-core` must not import Spring, Hibernate, Jakarta Persistence or a database driver. Its `maven-enforcer-plugin` rules and the [reactor verification script](../scripts/verify_reactor.py) guard this boundary.
- `base-data-jpa` owns persistence infrastructure and depends on Spring Data JPA. It may depend on `base-core` in the future **only if it starts using a core type**. It must not depend on `demo-app`.
- `demo-app` may depend on both library modules and holds application runtime configuration and application-level integrations.
- Circular project-module dependencies are prohibited. No library module should depend on an executable application.
- A module listed in the aggregator is built with the reactor but is not automatically added to the classpath of other modules. Runtime dependencies must be declared explicitly.
- The root `<dependencyManagement>` entries only manage coordinates and versions; they do **not** add dependencies by themselves.

## Build and test

All commands are executed from the repository root with Java 25 and Maven 3.9+.

| Goal | Command |
| --- | --- |
| Clean build and all tests | `mvn clean verify` |
| Build and install libraries into the local Maven repository | `mvn clean install` |
| Test only core | `mvn -pl base-core test` |
| Test JPA module and required reactor dependencies | `mvn -pl base-data-jpa -am test` |
| Test demo app and required reactor dependencies | `mvn -pl demo-app -am test` |
| Build executable app and required reactor dependencies | `mvn -pl demo-app -am package` |
| Inspect Maven dependencies | `mvn dependency:tree` |
| Check module graph and packaged JAR | `python3 scripts/verify_reactor.py` |

The verification script checks JAR packaging when a packaged demo JAR is present. Run it after `mvn clean package` for both structural and packaged-output checks. Automated verification is configured in [GitHub Actions](../.github/workflows/maven-reactor.yml).

The `base-data-jpa` module owns its persistence tests and includes H2 in test scope. `demo-app` also uses the `test` profile with H2 for Spring context tests; it does not require a local PostgreSQL server for the standard Maven test phase.

## Run demo-app

With a PostgreSQL database and environment variables configured, from the repository root:

```bash
mvn clean package
java -jar demo-app/target/demo-app-0.0.1-SNAPSHOT.jar
```

Environment variables read by the development application profile:

| Variable | Role | Default |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `dev` |
| `SERVER_PORT` | HTTP port | `8080` |
| `DB_URL` | JDBC PostgreSQL URL | Required for `dev` |
| `DB_USERNAME` | PostgreSQL username | Required for `dev` |
| `DB_PASSWORD` | PostgreSQL password | Required for `dev` |

The profile files are at `demo-app/src/main/resources/application*.yaml`, and test datasource configuration is in `demo-app/src/test/resources/application-test.yaml`. Database connection details and any future Flyway migrations belong in the application, not `base-core` or `base-data-jpa`.

The current `dev` profile uses Hibernate `ddl-auto: validate`. Flyway is a planned feature, not yet present. See [demo-app README](../demo-app/README.md) for a PowerShell startup example.

## How demo-app consumes the libraries

Its [POM](../demo-app/pom.xml) has explicit project dependencies managed by the root POM:

```xml
<dependency>
    <groupId>com.notshahrad</groupId>
    <artifactId>base-core</artifactId>
</dependency>
<dependency>
    <groupId>com.notshahrad</groupId>
    <artifactId>base-data-jpa</artifactId>
</dependency>
```

Its application entry point is `com.notshahrad.spring_boot_base.SpringBootBaseApplication`. The application supplies its own `AuditorAware<String>` bean (currently returning `system`), and imports `JpaAuditingConfiguration` from `base-data-jpa` via `demo-app/src/main/java/com/notshahrad/spring_boot_base/config/JpaConfig.java`. This keeps the auditing policy application-specific.

The application module also owns its HTTP exception handler and response DTOs. Those web-layer types must not be moved into the framework-independent `base-core` module.

## Consume the libraries from another Maven project

First publish the library artifacts to an accessible Maven repository, or install them locally from this checkout:

```bash
mvn clean install
```

In a separate project's `pom.xml`, declare the artifacts that the project actually needs. When that project does not inherit from this repository's parent POM, specify the version explicitly:

```xml
<dependency>
    <groupId>com.notshahrad</groupId>
    <artifactId>base-core</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.notshahrad</groupId>
    <artifactId>base-data-jpa</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

These artifacts are currently a snapshot example, not an assertion that they have been published to Maven Central or GitHub Packages. Applications that do not need persistence can depend on `base-core` alone.

For auditing in a consuming Spring Boot application, supply an `AuditorAware<String>` bean with the name `auditorAware` and explicitly import `com.notshahrad.spring_boot_base.common.config.JpaAuditingConfiguration`. The actual user identity must come from the consuming application's authentication model. For persistence, create your own `@Entity` classes extending `BaseEntity` and configure entity scanning and the datasource according to your application's packages and database.

## Add a new optional module

Do not create a placeholder module until its functionality is ready. When adding one:

1. Decide whether the capability is a framework-independent library, a framework integration, or application-only code.
2. Create a sibling directory (for example `base-security/`) with its own `pom.xml` and source/test directories.
3. Use `com.notshahrad:spring-boot-base:0.0.1-SNAPSHOT` as its Maven parent and `../pom.xml` as `relativePath`.
4. Add the new directory to the root `<modules>` list so Maven includes it in the reactor.
5. Declare only the dependencies that module needs. Add a root `<dependencyManagement>` entry if shared version management is useful; this does not make the module mandatory.
6. Add an explicit dependency only in the consuming application(s) that need that feature; keep `base-core` and unrelated modules independent.
7. Add tests and module-boundary enforcement, and update the [reactor verification script](../scripts/verify_reactor.py) if its expected module set or dependency rules change.
8. Run `mvn clean verify`, inspect `mvn dependency:tree` and verify the consuming application.

For example, a planned `base-security` module may depend on `base-core` and Spring Security, while `base-core` must never acquire a dependency on `base-security`. An application can choose to add `base-security` separately.

## Planned capabilities (not existing modules)

| Planned concern | Likely responsibility |
| --- | --- |
| Spring Security foundation | Optional security configuration and authorization policy |
| Google / Apple OIDC sign-in | Optional OAuth2/OIDC identity integration and account linking |
| Email/password login and outbound email | Account credentials, verification, recovery and configurable email delivery |
| Phone-number login | Phone normalization, OTP lifecycle and SMS integration |
| Guest / anonymous users | Optional guest identity lifecycle and account conversion |
| MCP server | Optional Spring AI / MCP tools, resources and authorization integration |

The final module names and split are not yet implemented or locked. Keep optional authentication and integration dependencies out of the default core, and keep each module's public API as small as practical.
