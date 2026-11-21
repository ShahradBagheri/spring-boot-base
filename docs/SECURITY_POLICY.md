# Default Spring Security policy

## Decision

The runnable `demo-app` will be **secure by default**. Spring Security will be added in issue #26; the policy in this document is the implementation contract for #26–#28.

This is a design decision, not an assertion about the current application. **At the end of issue #25, no Spring Security filter chain is installed and application endpoints are not yet protected by this policy. Do not deploy the current demo app to an untrusted network as though authentication were enforced.**

`base-core` and `base-data-jpa` remain independent of Spring Security. A reusable optional `base-security` module may be extracted later, but applications that use the executable template receive a secure-by-default configuration rather than having to opt in to basic protection.

## Public and protected endpoints

| Endpoint or request | Default rule |
| --- | --- |
| `GET /actuator/health` | Anonymous access, for load balancers and readiness checks |
| `HEAD /actuator/health` | Anonymous access |
| Any other actuator endpoint | Authentication required, subject to explicit management endpoint exposure settings |
| Application routes, including `/api/**` | Authentication required by default |
| Unknown or newly added routes | Authentication required; never implicitly public |
| Login, account registration, OAuth2 callbacks, and static content | Not automatically public; add narrowly scoped exceptions only when a relevant feature exists |

Authentication is only the baseline. Consuming applications must define role, authority, ownership, and resource-level authorization for sensitive actions. An authenticated identity must not by itself grant administrative capabilities.

Health details must not disclose secrets or privileged diagnostics to unauthenticated clients. Only the minimal health status should be public.

## Failure responses

Unauthenticated requests to protected endpoints should receive HTTP **401**. Authenticated users without sufficient permission should receive HTTP **403**. Both responses must use the public `ApiError` JSON contract, with the corresponding status, a safe message, timestamp, and an empty `fieldErrors` map. Internal authentication exceptions, token contents, and other sensitive details must remain server-side.

These responses will be implemented and tested in issue #27.

## Authentication mechanisms and session safety

Issue #26 establishes route protection without prematurely selecting application tokens, HTTP Basic authentication, or Google/Apple login. The choice among sessions, application-issued tokens, and other mechanisms remains separate work, especially for OIDC issue #33.

Do not ship a default administrator, hardcoded password, or generated credential as an implicit production authentication strategy. Do not treat an external identity provider's ID token as an application authorization token.

The CSRF policy must match the selected authentication mechanism: retain CSRF protection for session/cookie-based authenticated state-changing requests; disable it only when an explicitly stateless, non-cookie API design justifies doing so. CORS configuration must use explicit trusted origins rather than `*` with credentials.

## Extension points

- Supply application-specific `SecurityFilterChain` rules with explicit route matchers and an appropriate precedence/override mechanism when issue #26 provides the baseline.
- Define public auth endpoints only when they are implemented and covered by tests.
- Configure authentication providers, user identity lookup, roles, and authorization policies in consuming applications or optional modules; do not introduce these dependencies into `base-core` or `base-data-jpa`.
- Preserve the fail-closed fallback when adding new controllers and modules.
- Keep the `ApiError` mapping consistent between MVC exception handling and security filter-chain failures.

## Acceptance tests for the baseline

The security implementation in issues #26–#28 should prove that anonymous health requests succeed, anonymous application requests return 401, authenticated requests to authorized endpoints succeed, and access-denied requests return 403. It should also verify that unexpected new application endpoints are not accidentally public.

Until those tests pass, this document describes the **chosen policy**, not an existing security guarantee.
