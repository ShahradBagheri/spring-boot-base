# PostgreSQL schema migrations

The executable `demo-app` uses Flyway to version its PostgreSQL database schema. Flyway runs during application startup **before** Hibernate validates entity mappings.

## Configuration

- Migration files: `demo-app/src/main/resources/db/migration/`
- Naming: `V<version>__<descriptive_name>.sql`, for example `V1__create_application_settings.sql`
- Versions increase monotonically. Never edit a versioned migration after it has run on a shared database; add a new migration instead.
- `spring.flyway.baseline-on-migrate=false` intentionally avoids silently adopting existing, unversioned schemas.
- `spring.flyway.validate-on-migrate=true` checks migration metadata.
- `spring.flyway.clean-disabled=true` prevents destructive Flyway clean operations.
- `spring.jpa.hibernate.ddl-auto=validate` is retained in the development profile. Hibernate validates the tables created by Flyway; it does not mutate them.

Flyway and Hibernate use the application's configured PostgreSQL datasource. Database credentials are read from environment variables and must not be committed.

## Initial schema

Migration `V1__create_application_settings.sql` creates `application_settings`, a concrete application-side key/value settings table. It corresponds to the `ApplicationSetting` JPA entity and inherits these columns from `BaseEntity`: UUID ID, creation/modification timestamps and actors, optimistic-lock version, and soft-delete state and metadata. A unique constraint on `setting_key` prevents duplicate names.

This table is intended for non-secret application settings, not passwords or other credentials. The entity is a minimal persistence example; it does not expose API endpoints or establish a global configuration service.

## Refresh-token schema

`V3` creates `refresh_sessions` and `refresh_credentials`. `V4` adds a PostgreSQL partial unique index to limit each family to one unused credential while preserving consumed digests for replay detection. It also checks that stored digests use lowercase SHA-256 hex encoding. The migrations run independently of the token profile. See the [token operating guide](TOKEN_OPERATIONS.md#roll-out-and-rotate-keys) for deployment and recovery.

## Email login identities

`V5` creates `email_login_identities` with one row per local user and a unique index on verified addresses. `V6` stores adaptive password hashes, credential versions used to invalidate browser sessions, and short-lived challenge hashes. Existing OIDC profile emails are left outside the login table. The migration runs regardless of whether the email authentication capability is enabled. See the [email authentication model](EMAIL_AUTHENTICATION_MODEL.md) for the verification policy.

## Phone login identities

`V7` creates `phone_login_identities`. Each row holds one canonical E.164 number and its verification time. The database enforces a unique number across all accounts and at most one row per local user. The migration creates the table regardless of the feature flag. See the [phone authentication model](PHONE_AUTHENTICATION_MODEL.md).

## First startup

1. Create a **new, empty PostgreSQL database**, such as `base_project`.
2. Set `SPRING_PROFILES_ACTIVE=dev`, `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for the database.
3. Run `mvn clean package` and then `java -jar demo-app/target/demo-app-0.0.1-SNAPSHOT.jar`.
4. Flyway creates its schema history table, applies all pending migrations in order, and records each successful migration. Hibernate validates the resulting schema.
5. On subsequent startups, Flyway verifies the migration history but skips migrations that have already run.

For existing databases containing manually managed tables, do not blindly enable automatic baselining. First inventory and reconcile their schema and migration history, and plan an explicit baseline or new database migration.

## Testing

The fast `test` profile uses H2 and Hibernate `create-drop`, and disables Flyway for unit, HTTP, and focused JPA tests. `mvn test` runs those tests without Docker.

PostgreSQL integration tests use Testcontainers 2.x, the real PostgreSQL 17 engine, and the `integration` profile. `AbstractPostgresIntegrationTest` starts a reusable container and supplies its JDBC URL, username, and password through `@DynamicPropertySource`. No developer database, credentials, or `FLYWAY_TEST_*` environment variables are required.

Run `mvn clean verify` with a Docker-compatible daemon available. Maven Failsafe runs classes ending in `IT` during the `integration-test` and `verify` phases; a broken migration fails the build.

- `PostgresContainerSmokeIT` checks container startup and JDBC connectivity.
- `FlywayMigrationIT` applies versioned migrations to a fresh temporary PostgreSQL schema, validates the migration history, verifies the schema columns, and confirms a second migration run executes nothing.
- `ApplicationSettingPostgresIT` starts the real Spring Boot application context with Flyway enabled and Hibernate `ddl-auto=validate`, then checks the migrated schema, inherited auditing and version fields, soft deletion, and the unique key constraint.
- `RefreshSessionPostgresIT` verifies constraints, hash-only persistence and JDBC logging, rotation, concurrent exchange, cleanup, and owner-scoped revocation with the token profile enabled.
- `EmailPasswordFlowsPostgresIT` verifies email signup, reset, challenge expiry and reuse, generic acknowledgements, and password and refresh session revocation with a fake email sender.

The integration profile is defined in `demo-app/src/test/resources/application-integration.yaml`. It does not use Hibernate schema generation; Flyway must create all tables.

The GitHub Actions workflow runs `mvn clean verify` with Docker available and separately smoke-tests the executable application against its PostgreSQL service. Docker is a prerequisite for the full verification build, but not for the fast `mvn test` phase.

After adding or modifying any Flyway migration, run the full `verify` workflow. Existing applied migrations should never be edited; create a new migration instead.
