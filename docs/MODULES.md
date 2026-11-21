# Maven modules and extension guide

This guide describes the modules that exist in the repository today and how to add or consume modules without turning optional features into mandatory dependencies.

## Existing modules

| Path | Maven artifact | Responsibilities |
| --- | --- | --- |
| `base-core/` | `com.notshahrad:base-core:0.0.1-SNAPSHOT` | Framework-independent exceptions and shared application contracts |
| `base-data-jpa/` | `com.notshahrad:base-data-jpa:0.0.1-SNAPSHOT` | JPA mapped superclass `BaseEntity`, UUID identifier, timestamps, optimistic-lock version and deletion metadata, and opt-in auditing configuration |
| `base-web/` | `com.notshahrad:base-web:0.0.1-SNAPSHOT` | Reusable API response contracts and Spring MVC exception mapping |
| `base-security/` | `com.notshahrad:base-security:0.0.1-SNAPSHOT` | Optional Spring Security baseline, authentication/authorization error responses and method security |
| `base-auth/` | `com.notshahrad:base-auth:0.0.1-SNAPSHOT` | Optional local user identities, issuer/subject provider mapping, and transactional account provisioning |
| `base-auth-oauth2/` | `com.notshahrad:base-auth-oauth2:0.0.1-SNAPSHOT` | Optional OAuth2 Client / OIDC authorization-code integration with Google and Apple registrations |
| `base-auth-token/` | `com.notshahrad:base-auth-token:0.0.1-SNAPSHOT` | Optional first-party access JWT issuance, validation, and signing-key rotation |
| `base-auth-email/` | `com.notshahrad:base-auth-email:0.0.1-SNAPSHOT` | Optional email/password identities, authentication, and challenge templates |
| `base-mail/` | `com.notshahrad:base-mail:0.0.1-SNAPSHOT` | Framework-independent outbound email contracts, templates, and notification dispatch |
| `base-mail-smtp/` | `com.notshahrad:base-mail-smtp:0.0.1-SNAPSHOT` | Optional Spring Boot adapter for SMTP delivery |
| `base-auth-phone/` | `com.notshahrad:base-auth-phone:0.0.1-SNAPSHOT` | Optional verified E.164 phone login identities |
| `base-sms/` | `com.notshahrad:base-sms:0.0.1-SNAPSHOT` | Framework-independent SMS message and sender contracts |
| `base-mcp-server/` | `com.notshahrad:base-mcp-server:0.0.1-SNAPSHOT` | Optional MCP transport and bearer-token security |
| `demo-app/` | `com.notshahrad:demo-app:0.0.1-SNAPSHOT` | Runnable Spring Boot application consuming selected libraries, features, PostgreSQL migrations and integration tests |
| `pom.xml` at repository root | `com.notshahrad:spring-boot-base:0.0.1-SNAPSHOT` | Parent POM, Maven reactor aggregator and dependency/plugin version management |

The root has `<packaging>pom</packaging>`. Only `demo-app` applies the Spring Boot Maven plugin to build an executable JAR. Library modules produce ordinary JAR files.

## Dependency rules

Current project-module dependencies:

```text
spring-boot-base (parent / aggregator)
  base-core                (no project-module dependency)
  base-data-jpa            (no project-module dependency)
  base-web
    └── base-core
  base-security
    └── base-web
  base-auth
    └── base-data-jpa
  base-auth-oauth2
    ├── base-auth
    └── base-security
  base-auth-token
    ├── base-auth
    └── base-security
  base-auth-email
    └── base-auth
  base-mail
  base-mail-smtp
    └── base-mail
  base-auth-phone
    └── base-auth
  base-sms
  base-mcp-server         (no project-module dependency)
  demo-app
    ├── base-core
    ├── base-data-jpa
    ├── base-web
    ├── base-security
    ├── base-auth
    ├── base-auth-oauth2
    ├── base-auth-token
    ├── base-auth-email
    ├── base-mail-smtp
    ├── base-mail
    ├── base-auth-phone
    ├── base-sms
    └── base-mcp-server
```

The parent/aggregator relationship is not a runtime dependency. Maven compiles modules in dependency order based on actual `<dependencies>` declarations, not on the order written in `<modules>`.

- `base-core` must not import Spring, Hibernate, Jakarta Persistence or a database driver. Its `maven-enforcer-plugin` rules and the [reactor verification script](../scripts/verify_reactor.py) guard this boundary.
- `base-data-jpa` owns persistence infrastructure and depends on Spring Data JPA. It may depend on `base-core` in the future **only if it starts using a core type**. It must not depend on `demo-app`.
- `base-web` depends on `base-core` for application exceptions and provides web response contracts and the MVC exception handler. It does not depend on JPA, `base-security` or `demo-app`.
- `base-security` depends on `base-web` for the API error response contract and adds Spring Security only for consumers that explicitly use this module. It does not depend on `demo-app`.
- `base-auth` depends on `base-data-jpa` for persistent users and external login identity mappings; it does not depend on Spring Security, an OAuth2 Client or `demo-app`. It must only receive identities that have already been validated by a trusted authentication adapter.
- `base-auth-oauth2` depends on `base-auth` and `base-security`; it adds Spring Security OAuth2 Client only to applications that explicitly depend on this module. The OAuth2 login filter chain is disabled unless `base.auth.oauth2.enabled=true` and suitable client registrations are configured.
- `base-auth-token` depends on `base-auth` and `base-security`. Its stateless bearer configuration is opt-in and runs on routes selected by the consuming app.
- `base-auth-email` uses `base-auth` local users and the consuming application's PostgreSQL datasource. The demo renders challenge content and dispatches it through `base-mail`. `base-mail` can be used independently for outbound notifications.
- `base-mail-smtp` implements the `base-mail` sending contract. The demo includes the adapter, and delivery is disabled until explicitly enabled.
- `base-auth-phone` uses `base-auth` local users and the application's PostgreSQL datasource. The provider-independent SMS abstraction is in `base-sms`. See the [phone authentication model](PHONE_AUTHENTICATION_MODEL.md).
- `base-mcp-server` contains the stateless MCP transport and scoped Spring Security configuration. The demo app registers its example tool, resource, and prompt. See the [MCP server architecture](MCP_SERVER_ARCHITECTURE.md).
- `demo-app` may depend on the library modules it explicitly opts into and holds runtime configuration and application-level integrations.
- Circular project-module dependencies are prohibited. No library module should depend on an executable application.
- A module listed in the aggregator is built with the reactor but is not automatically added to the classpath of other modules. Runtime dependencies must be declared explicitly.
- The root `<dependencyManagement>` entries only manage coordinates and versions; they do **not** add dependencies by themselves.

## BaseEntity equality

`BaseEntity` compares persisted entities by UUID only when they represent the same effective entity class. Two different transient instances are never equal because their identifiers are null; an entity is always equal to itself. Hibernate proxies are compared using their underlying persistent class, so a proxy and a loaded entity with the same ID compare equally.

`hashCode()` is based on the persistent entity class, not the generated identifier or mutable fields. This value stays stable before and after `persist()`, so entities already stored in a `HashSet` or used as `HashMap` keys remain findable after UUID generation, auditing changes, or soft deletion.

Both methods are final in `BaseEntity` to preserve the equality contract for subclasses. Do not add Lombok `@EqualsAndHashCode` or override either method in entity subclasses. A constant hash per entity class is a deliberate tradeoff: it avoids mutable hash codes, though many entities of one type will share a hash bucket.

## BaseEntity timestamps

Every concrete JPA entity extending `BaseEntity` inherits two `Instant` fields managed by Spring Data JPA auditing:

- `createdAt` uses `@CreatedDate` and `@Column(nullable = false, updatable = false)`. It is populated during initial persistence and is not updated by subsequent SQL updates.
- `updatedAt` uses `@LastModifiedDate` and `@Column(nullable = false)`. It is populated during initial persistence and refreshed when Hibernate flushes a change to a managed entity.

Both fields are null on a new Java object until the auditing listener runs. The application must enable `JpaAuditingConfiguration`, as `demo-app` does through its `JpaConfig`. These values are persisted by the database; they do not update merely because a detached object is modified.

The fields use `Instant` to represent instants on the UTC timeline. Database timestamp precision may differ from Java nanosecond precision, so persistence tests should compare values reloaded from the database rather than raw in-memory values before and after a round trip.

## BaseEntity soft deletion

Soft deletion is an **opt-in state transition**, not automatic row filtering or a replacement for JPA repository deletion. Every entity extending `BaseEntity` inherits the `deleted`, `deletedAt`, and `deletedBy` fields.

- New entities start with `deleted = false` and null deletion metadata.
- Calling `softDelete(actor)` changes `deleted` to `true`, records the current `Instant` and the supplied actor, and persists those changes when the managed entity is flushed. The actor must be non-null, non-blank, and at most 100 Unicode code points.
- Calling `softDelete(actor)` again on an already deleted entity preserves its original deletion timestamp and actor, after validating the new argument.
- Calling `restore()` reactivates the entity and clears `deletedAt` and `deletedBy`; calling it on an active entity is a no-op.
- These methods do not call `EntityManager.remove()` or a repository delete method. They do not automatically save a detached entity; callers are responsible for appropriate transaction boundaries and persistence.
- **No global Hibernate or Spring Data filter is configured.** `EntityManager.find()`, unfiltered JPQL and ordinary `findAll()` queries can return soft-deleted rows. Queries that need only active records must explicitly filter by `deleted = false`; administrative queries can filter by `deleted = true` or omit the predicate to include both.

For example, a consuming Spring Data repository may declare `List<MyEntity> findByDeletedFalse()` and `List<MyEntity> findByDeletedTrue()`. With JPQL, use `where e.deleted = false` for active records. This filter applies only to queries that explicitly include it; `findById()` still retrieves deleted rows when their identifier exists.

A standard `JpaRepository.delete()` or `deleteById()` is **not** intercepted by `BaseEntity` and can physically delete rows. Applications should expose dedicated soft-delete operations when that policy is required. Automatic filtering, interception of physical deletes, and deleted-row access control are deliberately deferred to a later implementation, not implied by this base class.

Persistence and query behavior are exercised by `BaseEntityPersistenceIntegrationTest` with H2, including the visibility of deleted rows, explicit active/deleted queries, idempotent deletion, and restoration.

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

The `base-data-jpa` module owns its persistence tests and includes H2 in test scope. `demo-app` uses the `test` profile with H2 for fast Spring context, MVC and JPA tests. Classes named `*IT` in `demo-app` run with Maven Failsafe during `verify`, starting PostgreSQL via Testcontainers. Full `verify` and `install` require Docker; `mvn clean test` does not.

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

The profile files are at `demo-app/src/main/resources/application*.yaml`, and test datasource configuration is in `demo-app/src/test/resources/application-test.yaml`. Database connection details and versioned Flyway migrations belong in the application, not `base-core` or `base-data-jpa`.

The current `dev` profile uses Flyway to apply versioned PostgreSQL migrations and Hibernate `ddl-auto: validate` to check the resulting schema. Flyway migrations live under `demo-app/src/main/resources/db/migration`. See the [database migration guide](DB_MIGRATIONS.md) and [demo-app README](../demo-app/README.md) for startup details.

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

Its application entry point is `com.notshahrad.spring_boot_base.SpringBootBaseApplication`. The application supplies its own `AuditorAware<String>` bean (see the [audit policy](BASE-ENTITY-CONTRACT.md)), and imports `JpaAuditingConfiguration` from `base-data-jpa` via `demo-app/src/main/java/com/notshahrad/spring_boot_base/config/JpaConfig.java`. This keeps the auditing policy application-specific.

HTTP exception handlers and shared API DTOs are in `base-web`, not in `demo-app`. Spring Security configuration and 401/403 error mapping are in `base-security`. Both are reusable JARs, never dependencies of `base-core`. The demo application owns controllers and feature-specific DTOs.

The [feature extension guide](EXTENDING.md) explains where to put new entities, repositories, services, request/response records, controllers, validation rules and tests. Shared web and security configuration stays in `demo-app`; the documented [security policy](SECURITY_POLICY.md) defines access expectations for new HTTP endpoints.

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

The implemented `base-security` module depends on `base-web` and Spring Security; it does not depend on `demo-app`, JPA or a specific login method. An application can omit `base-security` entirely or add it and import `SecurityConfig` where its component scan does not cover the configuration package. For a custom security policy, provide your own `SecurityFilterChain` before the default bean is registered.

## Planned optional modules and capabilities

| Planned concern | Likely responsibility |
| --- | --- |
| Additional authentication modules | Reusable authentication and identity interfaces, provider implementations and authorization policies |
| Google / Apple OIDC sign-in | Optional OAuth2/OIDC identity integration and account linking |
| Email/password login and outbound email | Account credentials, verification, recovery and configurable email delivery |
| Phone-number login | Phone normalization, OTP lifecycle and SMS integration |
| Guest / anonymous users | Optional guest identity lifecycle and account conversion |

The final module names and split are not yet implemented or locked. Keep optional authentication and integration dependencies out of the default core, and keep each module's public API as small as practical.
