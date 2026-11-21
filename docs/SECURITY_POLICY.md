# Default Spring Security policy

## Implemented baseline

The reusable `base-security` Maven JAR owns the secure-by-default Spring Security filter chain in `base-security/src/main/java/com/notshahrad/spring_boot_base/config/SecurityConfig.java`. The executable `demo-app` opts in via a direct Maven dependency on `base-security`, and its own `base-web` dependency supplies the shared `ApiError` contract. `base-core` and `base-data-jpa` remain independent of security.

The baseline protects all new application routes by default. Only the HTTP `GET` and `HEAD` methods on `/actuator/health` are anonymous. The health endpoint is intended for load balancers and operational checks; avoid exposing privileged health details.

| Request | Default policy |
| --- | --- |
| `GET /actuator/health` and `HEAD /actuator/health` | Public |
| `GET /api/example/me` | Authenticated |
| `GET /api/example/admin` | `ROLE_ADMIN` required |
| All other application and actuator requests | Authenticated |
| Any newly added route not explicitly matched | Authenticated |

The `SecurityExampleController` in `demo-app` demonstrates principal access and an explicit method-level `@PreAuthorize("hasRole('ADMIN')")` rule. The `base-security` filter chain does not contain application-specific or demo-specific admin URL matchers. These endpoints are examples only and do not implement user management.

Authenticated access is not necessarily authorized access: services and endpoints with sensitive operations must enforce appropriate role, ownership, tenant, and resource-specific rules.

## Security errors

Spring Security emits the same `ApiError` structure as `GlobalExceptionHandler` when authentication or authorization fails:

```json
{
  "status": 401,
  "message": "Authentication required",
  "timestamp": "2026-11-21T10:00:00Z",
  "fieldErrors": {}
}
```

A request from an authenticated principal who lacks permission receives HTTP 403 and the message `Access denied`. The JSON contains no stack traces, exception classes, credentials, or internal error messages. These responses originate in the security filter chain rather than the MVC exception handler.

## Authentication and credentials

The baseline deliberately **does not** configure a production authentication method or credentials. It disables Spring Security's built-in form login, HTTP Basic, and logout endpoints. It provides an empty `InMemoryUserDetailsManager` only to prevent automatic creation of a default user with a generated password; it is not an application user store.

Consequently, real protected endpoints **cannot be accessed by an external user until an application configures an authentication provider or an appropriate sign-in module**. The tests use `@WithMockUser` to simulate authenticated principals. A passing test does not mean Google/Apple sign-in, JWT authentication, or user registration is implemented.

OAuth2/OIDC, API bearer tokens, password or phone login, and post-login sessions remain independent design decisions in their respective issues.

## CSRF and browser safety

CSRF remains enabled at the Spring Security default; it is not globally disabled merely because the example endpoints return JSON. Any authenticated state-changing HTTP request made with session/cookie-based authentication requires CSRF protection. A consuming application can alter the policy only after selecting and validating its authentication model. Do not enable permissive credentialed CORS.

## Customization

`base-security` is an optional JAR. A consuming application adds an explicit dependency on it and imports `com.notshahrad.spring_boot_base.config.SecurityConfig` (for apps outside the library's component-scan package). The demo app already discovers that configuration through its application root package. The configuration provides its baseline `SecurityFilterChain` only when the application has not registered one, using `@ConditionalOnMissingBean`. A consuming application can supply its own filter chain with explicit public route and authority matchers. Preserve a fail-closed `anyRequest().authenticated()` fallback, or an even stricter deny-all fallback, and explicitly test added public exceptions.

The empty in-memory `UserDetailsService` is also conditional on a custom `UserDetailsService` being absent. Replace it with the authentication integration appropriate for the application; do not add hardcoded accounts or commit secrets.

Security-filter error handling is defined in the `base-security` `SecurityConfig`; general MVC exception handling lives in the `base-web` `GlobalExceptionHandler`. Both reuse `base-web`'s `ApiError` record but are separate request-processing layers.

## Optional guest routes

The `auth-guest` profile adds explicit access rules for the guest session API. The guest-only rules require an active temporary browser session and an anonymous Spring Security principal. Signed-in users follow the regular account authorization policy. See the [guest session lifecycle](GUEST_SESSION_MODEL.md) for the route table and configuration.

## Tests and limitations

`demo-app`'s `SecurityAccessMockMvcTest` verifies public health access, anonymous 401 errors, protected routes, 403 for a non-admin principal, and authorized access by simulated authenticated users. The reactor verification script checks that `base-security` depends on `base-web`, never on `demo-app`, and that the packaged executable includes both optional JARs. The existing error-handler MockMvc tests run with test authentication and a CSRF token on POST requests.

Under the baseline filter chain, only `GET` and `HEAD` health checks are public. Unknown routes still require authentication, even when a route would ultimately return 404. This is deliberate to avoid exposing the existence of internal resources to anonymous callers.

Future work includes real authentication providers, role policies for actual application operations, account lifecycle management, and production-ready session or token management. The current baseline is **not a complete authentication system**.
