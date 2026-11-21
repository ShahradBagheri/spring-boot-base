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

## First startup

1. Create a **new, empty PostgreSQL database**, such as `base_project`.
2. Set `SPRING_PROFILES_ACTIVE=dev`, `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for the database.
3. Run `mvn clean package` and then `java -jar demo-app/target/demo-app-0.0.1-SNAPSHOT.jar`.
4. Flyway creates its schema history table, runs `V1`, and records the successful migration. Hibernate validates the resulting schema.
5. On subsequent startups, Flyway verifies the migration history but does not execute `V1` again.

For existing databases containing manually managed tables, do not blindly enable automatic baselining. First inventory and reconcile their schema and migration history, and plan an explicit baseline or new database migration.

## Testing

The regular `test` profile uses H2 with Hibernate `create-drop`, and disables Flyway to keep focused JPA tests independent of PostgreSQL-specific SQL.

`FlywayPostgresMigrationTest` runs only when `FLYWAY_TEST_JDBC_URL` points to PostgreSQL. It creates a uniquely named temporary schema, runs the real versioned migration, verifies the columns and schema history, calls `migrate()` again to confirm there are no pending migrations, and drops the temporary schema. GitHub Actions supplies a PostgreSQL service and runs this test during `mvn clean verify`.

The CI startup smoke test also starts the real application using the PostgreSQL service and `ddl-auto=validate`, exercising Flyway startup and matching Hibernate entity mappings against the migrated schema.

If running the PostgreSQL migration test yourself, provide `FLYWAY_TEST_JDBC_URL`, `FLYWAY_TEST_USERNAME`, and `FLYWAY_TEST_PASSWORD` pointing to an accessible PostgreSQL database where the user may create and drop test schemas.
