# Use an auth module in another Spring Boot service

A consuming service owns its PostgreSQL schema, Flyway history, HTTP endpoints and delivery configuration. The auth JARs contain the Java implementation. The reference SQL, controllers and runtime profiles live in [demo-app](../demo-app/README.md).

## Choose the schema for your features

The version numbers below identify the source scripts in the [demo migration directory](../demo-app/src/main/resources/db/migration/). For multiple features, take the union of their scripts and apply each once in source order.

| Feature used by the service | Source migrations | What they provide |
| --- | --- | --- |
| Local users or Google/Apple OIDC | V2 | Local accounts and issuer/subject identity mappings |
| Token issuance with persistent refresh sessions | V2, V3, V4 | Session families, credentials and the unique index enforcing one unused credential per family |
| Email identity lookup | V2, V5 | Local accounts and verified email identities |
| Email/password signup, login and reset | V2, V5, V6 | Email identities, password hashes and verification/reset challenges |
| Phone identity lookup | V2, V7 | Local accounts and verified phone numbers |
| Phone signup/login with OTP | V2, V7, V8 | Phone identities and OTP challenges |

V1 belongs to the demo settings example. V9 belongs to the optional guest draft example. Sending notifications through `base-mail`, `base-mail-smtp` or `base-sms` alone requires no auth tables.

## Example: email/password in a separate service

1. From this repository, run `mvn -pl base-auth-email -am install` to install the parent POM, email library and its reactor dependencies locally. In the consuming service declare `com.notshahrad:base-auth-email:0.0.1-SNAPSHOT`. For a shared build, publish those artifacts to a Maven repository accessible to the service.
2. Add `org.springframework.boot:spring-boot-starter-flyway`, `org.flywaydb:flyway-database-postgresql` and the runtime driver `org.postgresql:postgresql`. Configure the service's PostgreSQL datasource.
3. Copy the V2, V5 and V6 SQL scripts into the service's `src/main/resources/db/migration/`. For a new history, the original numbers can be retained. For an existing Flyway history, inspect existing tables and assign the next unused versions **before** applying the scripts. For example, a service at V12 can adopt them as V13, V14 and V15. Preserve the constraints and indexes. Once applied, keep those files immutable and bring future upstream schema changes into the service as new migrations.
4. Register the email components, auth entities, repositories and auditing configuration. For a service outside the library package tree, the following annotations can go on an application-owned configuration class. Include the service's own entity and repository packages in the respective scans if it has them.

   ```java
   @Configuration(proxyBeanMethods = false)
   @Import({EmailIdentityRepository.class, EmailPasswordService.class, JpaAuditingConfiguration.class})
   @EntityScan(basePackageClasses = AppUser.class)
   @EnableJpaRepositories(basePackageClasses = AppUserRepository.class)
   class EmailAuthConfiguration {}
   ```

   `AppUser` and `AppUserRepository` are in `com.notshahrad.spring_boot_base.auth`, and the email components are in its `email` subpackage. Import `JpaAuditingConfiguration` from `com.notshahrad.spring_boot_base.common.config`. Supply an `AuditorAware<String>` bean named `auditorAware` that resolves the actor for this service. The demo's [JpaConfig](../demo-app/src/main/java/com/notshahrad/spring_boot_base/config/JpaConfig.java) shows one implementation.
5. Enable `base.auth.email.enabled=true` and `base.auth.email.password-enabled=true`. Inject `EmailPasswordService` into the application's signup and reset flows. The demo's [email web integration](../demo-app/src/main/java/com/notshahrad/spring_boot_base/security/email/) shows how its endpoints, session handling and challenge delivery fit together. If using SMTP, follow the [email configuration guide](EMAIL_CONFIGURATION.md).

## Verify the integration

Start the consuming service against a fresh PostgreSQL test database with Flyway enabled and `spring.jpa.hibernate.ddl-auto=validate`. Check the selected migrations in `flyway_schema_history`, then exercise the feature through the service. For email/password, request signup, capture the challenge through a test sender, complete verification and log in. This exercises the JDBC-managed challenge and credential tables as well as the JPA user mapping. When adopting into an existing service, also test the upgrade from its previous schema version.

For the other integrations, see [OIDC provider setup](OIDC_PROVIDER_SETUP.md), [token operations](TOKEN_OPERATIONS.md) and [phone authentication setup](PHONE_AUTHENTICATION_SETUP.md). Their demo profiles and HTTP routes show how to wire the features into an executable application.
