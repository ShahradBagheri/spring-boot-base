# Adding a feature to the base project

This guide describes **where** a new feature belongs and the sequence for adding persistence, business logic, HTTP endpoints, validation, errors, and tests. It reflects the modules and packages currently in the repository. The illustrative `FeatureFlag` snippets below are **not implemented application classes**; a complete working example is tracked separately in issue #31.

## Existing package responsibilities

All Java packages start with `com.notshahrad.spring_boot_base`.

| Module and package | Responsibility | Existing example |
| --- | --- | --- |
| `base-core/common/exception` | Framework-independent application exceptions | `BaseException`, `BusinessException`, `ResourceNotFoundException` |
| `base-data-jpa/common/entity` | Reusable mapped superclass and inherited persistent metadata | `BaseEntity` |
| `base-data-jpa/common/config` | Opt-in Spring Data JPA auditing configuration | `JpaAuditingConfiguration` |
| `demo-app/config` | Runtime integration and application configuration | `JpaConfig` |
| `demo-app/settings` | First concrete JPA entity and persistence example | `ApplicationSetting` |
| `base-web/common/response` | Reusable HTTP response records | `ApiError`, `ApiResponse` |
| `base-web/common/exception` | Web-layer exception translation | `GlobalExceptionHandler` |
| `base-security/config` | Optional Spring Security filter chain and 401/403 mapping | `SecurityConfig` |

The application entry point is `demo-app/src/main/java/com/notshahrad/spring_boot_base/SpringBootBaseApplication.java`.

Keep the core module free of web, security, JPA and database dependencies. `base-web` depends only on `base-core`, and `base-security` depends on `base-web`, not on the executable app. Add the optional web/security modules explicitly to a consuming application's POM. Keep REST DTOs, controllers, Spring Security configuration and request validation in the application or a dedicated web integration module. Do not make a reusable library depend on `demo-app`.

## Feature-first organization

For a new feature, create a package under the executable application's source root, for example:

```text
demo-app/src/main/java/com/notshahrad/spring_boot_base/flags/
  FeatureFlag.java
  FeatureFlagRepository.java
  FeatureFlagService.java
  FeatureFlagController.java
  CreateFeatureFlagRequest.java
  FeatureFlagResponse.java
```

Feature-first organization keeps HTTP, service, and persistence code discoverable for that feature. Shared infrastructure remains in the existing `common` and `config` packages.

## 1. Model the entity

Define a concrete `@Entity` extending the existing `BaseEntity`, rather than duplicating UUID, audit timestamps, version and soft-delete fields.

```java
@Entity
@Table(name = "feature_flags")
public class FeatureFlag extends BaseEntity {
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    protected FeatureFlag() {
    }

    public FeatureFlag(String name, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
```

Add constructor invariants and any other domain validation needed; request DTO validation is not a substitute for enforcing the entity's own rules. `BaseEntity` supplies a UUID identifier, `Instant` auditing fields, optimistic-lock `version` and soft-deletion metadata.

New entities must be covered by a **new** Flyway migration. For this example, add `demo-app/src/main/resources/db/migration/V2__create_feature_flags.sql` with the new table, all inherited `BaseEntity` columns, and matching constraints. Do not edit `V1__create_application_settings.sql` after it has been applied. See [database migrations](DB_MIGRATIONS.md).

## 2. Add a repository

A consuming Spring Data JPA repository belongs with its feature:

```java
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {
    Optional<FeatureFlag> findByNameAndDeletedFalse(String name);
}
```

`BaseEntity` does **not** automatically exclude soft-deleted records. Use explicit predicates such as `DeletedFalse` or `where entity.deleted = false` for normal application queries. Ordinary `findById` can return deleted rows.

## 3. Write a transactional service

Keep persistence operations and business rules out of controllers.

```java
@Service
public class FeatureFlagService {
    private final FeatureFlagRepository repository;

    public FeatureFlagService(FeatureFlagRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public FeatureFlagResponse create(CreateFeatureFlagRequest request) {
        FeatureFlag entity = repository.save(new FeatureFlag(request.name(), request.enabled()));
        return new FeatureFlagResponse(entity.getId(), entity.getName(), entity.isEnabled());
    }

    @Transactional(readOnly = true)
    public FeatureFlagResponse find(UUID id) {
        FeatureFlag entity = repository.findById(id)
                .filter(flag -> !flag.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("FeatureFlag", id));
        return new FeatureFlagResponse(entity.getId(), entity.getName(), entity.isEnabled());
    }
}
```

The example assumes the existing core exception types and a generated UUID. Use the existing `GlobalExceptionHandler` rather than creating an HTTP mapping in the service. Never expose persistence entities directly as arbitrary public responses when a dedicated DTO is more appropriate.

## 4. Validate the HTTP boundary

Use request and response records in the feature package:

```java
public record CreateFeatureFlagRequest(
        @NotBlank @Size(max = 100) String name,
        boolean enabled
) {
}

public record FeatureFlagResponse(UUID id, String name, boolean enabled) {
}
```

Then create a controller:

```java
@RestController
@RequestMapping("/api/flags")
public class FeatureFlagController {
    private final FeatureFlagService service;

    public FeatureFlagController(FeatureFlagService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<FeatureFlagResponse> create(@Valid @RequestBody CreateFeatureFlagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{id}")
    public FeatureFlagResponse find(@PathVariable UUID id) {
        return service.find(id);
    }
}
```

This uses Spring MVC, Jakarta validation, and the existing `ApiError` contract. Invalid `@Valid @RequestBody` fields produce HTTP 400 with `fieldErrors`; `ResourceNotFoundException` becomes HTTP 404. Unexpected exceptions are logged server-side and return a generic HTTP 500 without internal details.

Follow [the security policy](SECURITY_POLICY.md) when exposing new endpoints: authentication alone does not authorize access to every resource. Add explicit role or ownership checks, and tests that demonstrate both allowed and denied access.

## 5. Test at the right level

- **Unit tests**: Test domain rules and service behavior without starting the application whenever possible.
- **Web tests**: Exercise request validation and `ApiError` serialization through MockMvc. When Spring Security is active, use `@WithMockUser` for authenticated tests and `.with(csrf())` for state-changing requests.
- **JPA/H2 tests**: Verify entity mapping and simple persistence rapidly with the `test` profile.
- **PostgreSQL integration tests**: Extend `AbstractPostgresIntegrationTest` in `demo-app/src/test/java/com/notshahrad/spring_boot_base/support/` and name the class `*IT` to run with Failsafe. These tests use disposable PostgreSQL, run Flyway, and validate database-specific constraints.

Run fast tests with `mvn clean test`, then the complete pipeline with Docker and `mvn clean verify`. Both the new migration and the new entity must succeed together under PostgreSQL.

## 6. Adding reusable modules instead of app features

If the new capability is optional and reusable across applications, design a separate Maven module instead of placing its dependencies in `base-core`. Register the module in the root aggregator, declare its own dependencies, update the Maven reactor verification script and architecture documentation, then add an explicit dependency from each consuming application.

See [module architecture](MODULES.md) for parent POM conventions, module isolation rules, and opting into infrastructure packages.
