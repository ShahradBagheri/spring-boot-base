# Local identities and account linking

The optional `base-auth` module contains the local user model, external OIDC identity mapping and transactional provisioning service. It depends on `base-data-jpa`, not `base-security`, `base-web` or an external OIDC client.

## Identity model

- `AppUser` is a local principal identified by its own UUID. Optional email, email-verification status and display name are **profile fields**, not login identifiers.
- `ExternalIdentity` associates one local user with an **issuer + subject (`iss` + `sub`)** from a verified external login. PostgreSQL enforces `UNIQUE (issuer, subject)` so the same external identity cannot belong to multiple local users.
- One local user can have many external identities, including both Google and Apple. Two users may have the same email, and email equality never silently merges them.

The schema is defined in `demo-app/src/main/resources/db/migration/V2__create_auth_users_and_external_identities.sql`. The schema lives in the consuming application, while reusable JPA entities and repositories live in `base-auth`.

## Provisioning contract

`OidcIdentityService.resolveOrCreate(VerifiedProviderIdentity)` must only be invoked **after** the OIDC client has cryptographically validated the provider ID token, issuer, audience, nonce and authorization state. The method assumes a verified identity; its DTO name is a trust-boundary contract, not a verification mechanism.

- First login for an unknown `iss + sub` creates a new `AppUser` and mapping in one transaction.
- Returning login resolves the existing local user and optionally refreshes profile fields.
- Missing email and display name do not block login.
- Unverified email cannot overwrite an existing verified email.
- A matching email from a different provider never automatically links accounts.
- Concurrent first logins may encounter the unique database constraint; callers must handle retry/identity lookup under an appropriate transaction boundary. Database uniqueness is the final integrity safeguard.

## Intentional account linking

`OidcIdentityService.linkToAuthenticatedUser(localUserId, identity)` is **fail-closed**. It refuses to link accounts unless a consuming application supplies an `AccountLinkApproval` bean that has verified the existing local user's active authentication, the external identity proof, and any required account-link challenge or reauthentication step.

No approval bean is supplied by default, so linking is blocked. A caller cannot treat email equality as approval. If the provider identity is already associated with a different local user, linking is rejected even if an approval bean returns true.

Do not expose a public endpoint that accepts arbitrary `localUserId` values. The caller must derive the local user ID from an authenticated, trusted principal and ensure the external identity was freshly validated for the linking operation. A full application linking UI and challenge protocol is future work.

## Validation and tests

`base-auth` unit tests cover first and returning login, email-independent identity resolution and link-denial rules. PostgreSQL Testcontainers integration tests in `demo-app` verify persisted identities, issuer separation and uniqueness. A real provider-specific OIDC adapter is added separately in `base-auth-oauth2`; this module does not call Google or Apple.
