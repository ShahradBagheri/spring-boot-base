# Working vertical slice: application settings

This is a **real, compiled example** in `demo-app`, not a collection of unimplemented snippets. It shows how an application consumes `base-core`, `base-data-jpa`, `base-web` and `base-security` to expose a small administration-only API.

The settings in this example are **non-secret application values**. Do not use this feature to store database passwords, API credentials or signing keys; those belong in an external secrets manager or protected deployment configuration.

## Package structure

```text
demo-app/src/main/java/com/notshahrad/spring_boot_base/settings/
  ApplicationSetting.java
  ApplicationSettingRepository.java
  ApplicationSettingService.java
  ApplicationSettingController.java
  CreateApplicationSettingRequest.java
  UpdateApplicationSettingRequest.java
  ApplicationSettingResponse.java
```

`ApplicationSetting` is the real JPA entity introduced with Flyway migration `demo-app/src/main/resources/db/migration/V1__create_application_settings.sql`. It extends `BaseEntity` and therefore inherits UUID, timestamps, audit users, optimistic locking and soft-delete metadata. The migrated `application_settings` table also enforces a unique `setting_key`.

`ApplicationSettingRepository` extends `JpaRepository<ApplicationSetting, UUID>`. It provides an active-row lookup through `findByKeyAndDeletedFalse` and a uniqueness precheck using `existsByKey`. Soft-deleted rows still reserve their unique keys: the repository checks all rows before creating one. The database unique constraint remains the final integrity check during concurrent requests.

## HTTP endpoints

All three endpoints are guarded by `@PreAuthorize("hasRole('ADMIN')")` on `ApplicationSettingController`, and every route also passes through the reusable `base-security` filter chain. The baseline does not create real users: the tests simulate an administrator using `@WithMockUser`.

| HTTP | URL | Behavior |
| --- | --- | --- |
| `POST` | `/api/settings` | Create a new non-secret setting; returns 201 |
| `GET` | `/api/settings/{key}` | Read an active setting by key; returns 200 |
| `PUT` | `/api/settings/{key}` | Update an active setting's value; returns 200 |

Example request to create a setting (requires an authenticated administrator and a CSRF token when using cookie/session authentication):

```http
POST /api/settings
Content-Type: application/json

{"key":"feature.demo","value":"enabled"}
```

The success body uses `ApplicationSettingResponse`:

```json
{
  "id": "a81758a7-2937-4f5a-87e7-063e1308894f",
  "key": "feature.demo",
  "value": "enabled"
}
```

This UUID is illustrative. `ApplicationSettingResponse` is a DTO, not the JPA entity; it avoids exposing internal auditing, deletion, and locking fields.

Example request to update an existing value:

```http
PUT /api/settings/feature.demo
Content-Type: application/json

{"value":"disabled"}
```

## Responsibilities and validation

1. **Request records:** `CreateApplicationSettingRequest` requires a nonblank key (at most 100 characters) and a nonblank value (at most 2048 characters). `UpdateApplicationSettingRequest` validates its value with the same maximum length.
2. **Controller:** Declares routing, HTTP status and `@Valid @RequestBody`; it delegates operations to the service and restricts access to administrators.
3. **Service:** Uses `@Transactional` for create/update and `@Transactional(readOnly = true)` for read. It enforces uniqueness at application level, retrieves only nondeleted rows, and maps entities to response DTOs.
4. **Repository:** Encapsulates JPA queries and leaves database integrity to the unique constraint.
5. **Entity and migration:** Apply domain invariants, auditing, optimistic locking and the database schema. A JPA `BaseEntity` soft-delete does not automatically filter rows.
6. **Error handling:** The `base-web` `GlobalExceptionHandler` converts field-validation failures and application exceptions into client-safe `ApiError` JSON. The `base-security` filter chain and security advice handle 401 and 403.

Expected failures include 400 with `fieldErrors` for an invalid request, 400 with a deliberate business message for a duplicate key, 404 when an active key does not exist, 401 for unauthenticated requests and 403 for an authenticated non-admin principal. Unexpected internal failures return a safe generic 500.

## Tests

`demo-app/src/test/java/com/notshahrad/spring_boot_base/settings/ApplicationSettingVerticalSliceMockMvcTest.java` exercises the real controller/service/repository through MockMvc and H2. It verifies:

- Creating, reading, updating, and re-reading a setting
- `@Valid` request validation and `ApiError.fieldErrors`
- Duplicate key detection and deliberate error mapping
- Not-found error mapping for a nonexistent key
- Administrator authorization, non-admin 403 and anonymous 401

The existing `ApplicationSettingPostgresIT` exercises inherited mappings, Flyway, soft deletion and uniqueness against disposable PostgreSQL via Testcontainers.

Run the fast test suite with `mvn clean test`, then `mvn clean verify` with Docker available to include the PostgreSQL integration tests.

## Reusing this feature pattern

For a new domain, create a feature-first package, a concrete `BaseEntity` subclass, a matching **new Flyway migration**, a Spring Data repository, a transactional service, request/response DTOs, a controller with explicit authorization, and tests at both HTTP and PostgreSQL levels. Do not copy the `application_settings` table or its migration into unrelated domains. See [EXTENDING.md](EXTENDING.md) for illustrative class outlines and [MODULES.md](MODULES.md) for the reusable module boundaries.
