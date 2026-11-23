# Spring Boot starter conventions

Use `base-<feature>` for reusable implementation code and `base-<feature>-starter` for the dependency bundle an application selects. Both are ordinary JARs. The starter declares the implementation and any framework dependencies required to activate the feature. Keep auto-configuration in the implementation module when the classpath and activation conditions are safe for its direct consumers. Extract `base-<feature>-autoconfigure` when the implementation needs to stay independent of Boot or an optional integration requires classpath isolation. Add new publishable modules to the reactor and `spring-boot-base-bom`.

## Register and activate a feature

- Use `@AutoConfiguration` and list each class in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Place new auto-configurations outside application scans. Register individual beans with `@Bean` or `@Import`.
- Use `@ConditionalOnClass` for optional dependencies and `@ConditionalOnWebApplication` for web-only features. Put methods whose signatures reference optional types inside a guarded configuration class so the condition can run before those types are loaded.
- Use `@ConditionalOnMissingBean` for defaults that consumers can override. Check for the public contract, qualify the check when the contract can have multiple independent instances, and verify that the application-provided bean works with the feature configuration.
- Use existing `base.<feature>` prefixes, such as `base.mail.smtp` and `base.websocket`. Require `enabled=true` for sensitive features that expose a route, add a filter chain, require credentials, or contact an external service. Keep their prerequisites and settings validation behind that condition. Keep sample controllers, demo routes, and sample security policies in the application.
- If a configuration depends on conditions evaluated by another auto-configuration, declare the required order with `@AutoConfiguration(after = ...)` or `before = ...`. Use the name variants for optional configuration classes. Express bean initialization dependencies through injection. For scoped security chains, test route matching and chain precedence separately from configuration order.

[Spring Boot auto-configuration reference](https://docs.spring.io/spring-boot/4.1/reference/features/developing-auto-configuration.html)

## Bind and document settings

Use typed `@ConfigurationProperties` registered by `@EnableConfigurationProperties`. Use `@DefaultValue` for bindable defaults and validate invalid combinations in the properties constructor, or use `@Validated` with Jakarta Validation when the feature provides a validator. Keep validation errors actionable and leave credentials out of messages and default values.

Add `org.springframework.boot:spring-boot-configuration-processor` to the Maven compiler annotation processor path in the module that binds the settings. The reactor defines an inherited processor path. The [SMTP POM](../base-mail-smtp/pom.xml) replaces that path because the module only needs the configuration processor. Modules that also use Lombok need both processors on their path, with Lombok first. Use `META-INF/additional-spring-configuration-metadata.json` for properties read directly by conditions, such as the SMTP activation flag, and for hints the processor cannot infer. Verify `META-INF/spring-configuration-metadata.json` is generated and packaged. [Spring Boot configuration metadata reference](https://docs.spring.io/spring-boot/4.1/specification/configuration-metadata/annotation-processor.html)

## Test the consumer context

Use `ApplicationContextRunner` for regular configurations and the servlet or reactive variant for web-specific configurations. Include a test using `@EnableAutoConfiguration` without application scanning to verify discovery through the imports file.

| Scenario | Check |
| --- | --- |
| Feature enabled with prerequisites | The expected bean is available and receives its bound settings. |
| Feature disabled or missing a prerequisite | Context starts without the feature bean, endpoint, or filter chain. Use `FilteredClassLoader` for missing dependencies. |
| Consumer override | The configured application bean wins without requiring settings for the default. |
| Enabled with invalid settings | Startup fails with a clear validation message. |
| Optional web or security feature omitted | Verify that its route mappings and scoped filter chain are absent. An HTTP 401 alone can come from a fallback chain and does not prove routing is absent. |

The existing [SMTP configuration](../base-mail-smtp/src/main/java/com/notshahrad/spring_boot_base/mail/smtp/SmtpEmailAutoConfiguration.java) and [context tests](../base-mail-smtp/src/test/java/com/notshahrad/spring_boot_base/mail/smtp/SmtpEmailAutoConfigurationTest.java) exercise classpath gating, explicit enablement, override, settings validation, and registration. The [demo's servlet tests](../demo-app/src/test/java/com/notshahrad/spring_boot_base/security/SecurityAccessMockMvcTest.java) guard against registering WebSocket or MCP routes and the MCP filter chain without explicit activation.
