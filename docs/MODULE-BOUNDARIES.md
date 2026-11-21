# Module boundaries and dependency rules

This is the target structure for [issue #87](https://github.com/ShahradBagheri/spring-boot-base/issues/87). The project currently builds as a single Maven artifact. The [implementation roadmap](IMPLEMENTATION-ROADMAP.md#phase-1--architecture-and-module-foundation) tracks the changes that will create and verify these boundaries.

## Initial modules

| Module | Owns | Allowed project dependencies |
| --- | --- | --- |
| `base-core` | Framework-independent Java types and contracts shared by consumers, including application exceptions. | None. Keep Spring, HTTP, persistence, security, and provider APIs off the module's classpath. |
| `base-data-jpa` | The reusable JPA foundation: `BaseEntity`, entity auditing, and reusable persistence support. | `base-core`, when it uses a core type. No web, security, provider, or application modules. |
| `demo-app` | The executable Spring Boot application: feature wiring, runtime profiles, database drivers, application migrations, and end-to-end examples. | `base-core`, `base-data-jpa`, and any optional libraries it explicitly chooses to use. |

In the diagram, arrows point from a consumer to a module it may depend on. Declare a direct Maven dependency for each module whose API you use.

```text
demo-app ────────► base-core
    │                  ▲
    └──► base-data-jpa ┘
```

The root POM will aggregate the modules and manage shared versions and plugin settings. Parent inheritance and reactor membership do not create library dependencies. Declare dependencies where they are used; keep application starters and drivers out of inherited defaults. Only `demo-app` produces an executable Spring Boot artifact. The reusable modules produce library JARs.

`demo-app` owns the main class and feature wiring, the environment profiles and datasource settings, and the Flyway dependency, configuration, and migration scripts. Those migrations describe the schema for the application that runs them; using the JPA library must not pull in that schema, a database driver, or environment configuration.

## Ownership of the existing code

| Current code | Target ownership |
| --- | --- |
| `BaseException`, `BusinessException`, `ResourceNotFoundException` | `base-core`. The exceptions carry application meaning; an HTTP adapter chooses the status and response body. |
| `BaseEntity` and reusable auditing setup | `base-data-jpa`. The consuming application supplies the current auditor policy. |
| `ApiResponse`, `ValidationErrorResponse`, `GlobalExceptionHandler` | Optional `base-web` when introduced. Until then, `demo-app` can retain the existing HTTP behavior during the split. |
| `SpringBootBaseApplication`, `application*.yaml`, and application context tests | `demo-app`. Tests of reusable behavior move with their owning library. |

## Optional capabilities

Add an optional module when its feature is implemented and has a consumer. An empty module would add build configuration and a dependency boundary before there is code to justify either. Keep the initial reactor at three modules so consumers can adopt the foundation without pulling in unused features.

| Module | Owns | Allowed project dependencies |
| --- | --- | --- |
| `base-web` | Spring MVC response types, validation errors, and exception-to-HTTP mapping. | `base-core` |
| `base-security` | Shared Spring Security configuration, access rules, and authentication and authorization contracts. | `base-core` |
| `base-mail` | Outbound email delivery interface and adapters. | None |
| `base-sms` | Outbound SMS delivery interface and adapters. | None |
| `base-auth-oauth2` | OAuth2/OIDC sign-in and identity-provider integration. | `base-core`, `base-security` |
| `base-auth-email` | Email signup, sign-in, verification, and password reset. | `base-core`, `base-security`, `base-mail` |
| `base-auth-phone` | Phone signup, sign-in, and the OTP lifecycle. | `base-core`, `base-security`, `base-sms` |
| `base-mcp-server` | Optional MCP web transport and bearer-token security. Consuming applications register domain capabilities. | None |

The tables list all allowed direct dependencies between project modules. Each edge goes down this order: `demo-app` → authentication modules → JPA, web, security, delivery, and MCP modules → `base-core`. Edges may skip a level, and modules in the same level cannot depend on each other. Since every edge goes down and `base-core` depends on no project module, a cycle cannot form. A library must never depend on `demo-app`.

When features must work together without a listed dependency, wire them through interfaces in `demo-app`. For example, keep provider SDK types behind delivery interfaces, wire the persistence of email or phone identities through application adapters, and configure the MCP issuer and resource in the application, and apply domain authorization in MCP handlers. Application-specific guest data ownership, account conversion, and cleanup policy also stay in `demo-app`, using security and persistence building blocks as needed.

During the split, verify these rules by inspecting the resolved Maven dependency trees, including transitive dependencies, and checking that each library builds without `demo-app` or the optional modules. The reactor verification belongs to [issue #92](https://github.com/ShahradBagheri/spring-boot-base/issues/92).
