# Migrate an application copied from Spring Boot Base

Use a migration branch and keep the previous application artifact available for
rollback. Rehearse against a disposable database before changing a deployed
application. This guide applies to projects such as Jiggo that copied the
reactor into their own source tree.

## Inventory the fork

- Identify the upstream Base commit the project copied. Compare each copied
  `base-*` directory with that commit. Retain or extract any app-specific
  behavior before removing a copied module.
- Record the app’s entry point, custom controllers and security chains,
  authentication and session settings, database schema history, and deployment
  assets. Inventory WebSocket handshake URLs, destinations, payloads, and
  authenticated or guest principals.
- Identify demo-only endpoints, example static assets, and test credentials
  separately from the application's own source and resources.

## Change Maven ownership

Replace the application’s `spring-boot-base` parent with a supported Spring Boot
parent. Import the published `spring-boot-base-bom` in
`dependencyManagement`, and replace each copied library with the matching
starter you use. Remove only reactor modules you have replaced. Compile and
inspect the application's dependency tree before removing the old source paths.
The [distribution guide](MAVEN_DISTRIBUTION.md) records the supported baseline.

## Keep state and contracts

| Area | Preserve |
| --- | --- |
| Flyway | Keep the applied migration files, paths, numbering and checksums. Compare schema history before and after. Add new forward migrations for intentional schema changes. |
| Authentication | Keep app-owned endpoint paths, authorization and CSRF policy, provider callback URLs, signing and OTP keys, cookie and session settings, and refresh-token storage. Rehearse existing session and refresh flows with non-production test data. |
| WebSocket | Preserve the handshake path, origin rules, identity model, STOMP destinations and payload formats. Connect existing clients before and after the migration and test denied sends and subscriptions. |
| Static and demo assets | Retain app-specific assets and deployment files. Remove copied examples only after confirming that the app no longer references them. |

Use the [auth adoption guide](AUTH_MODULE_ADOPTION.md) for activation flags and
schema ownership. If you already register MVC advice, auditing or a security
chain, test which bean supplies that behavior once the starter is present.

## Rehearse and roll back

The web rehearsal runs the same HTTP contract before and after changing the
Maven parent, dependencies and exception-handler wiring. It uses a temporary
application and a fresh Maven cache pointed at staged artifacts.

```sh
python3 verification/rehearse_web_migration.py --repository "$STAGING_REPOSITORY" \
  --group "$BASE_GROUP" --version "$BASE_VERSION"
```

For your app, also rehearse against a database copy with the existing migration
history and representative users, sessions and WebSocket clients. If the
application checks fail, redeploy the previous artifact and restore its
configuration. Keep schema changes backward-compatible during the migration so
the old app can still run. Coordinate any database restore separately from
application rollback.
