# Maven distribution decision

Spring Boot Base will distribute public releases through Maven Central using Sonatype Central Portal. A new application can use the Spring Boot parent, import the Base BOM, and declare only the starters it needs. Maven Central is already Maven's default repository and public downloads require no credentials. GitHub Packages requires authentication for Maven installs, which would add setup for each consuming app and its CI. [Maven Central](https://central.sonatype.org/pages/about/) · [GitHub Packages Maven registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)

## Coordinates and artifact names

The selected public groupId is `io.github.shahradbagheri`, based on the [repository owner's GitHub account](https://github.com/ShahradBagheri). Confirm the exact namespace and publishing access in Central Portal before the coordinate cutover. Make the cutover once across the parent, BOM, libraries, starters, and their dependency references before the first public milestone. The existing source reactor still uses `com.notshahrad`. Java package names and consumer imports are independent of the Maven groupId.

| Artifact type | Artifact IDs |
| --- | --- |
| Library build parent POM | `spring-boot-base` is available to resolve published library POMs that inherit it. External apps can keep their own Spring Boot parent. |
| Consumer BOM | `spring-boot-base-bom` manages all published Base libraries and starters at one release version. It leaves Spring Boot's dependency management to the application. |
| Implementation JARs | `base-core`, `base-web`, `base-data-jpa`, `base-security`, `base-auth`, `base-auth-token`, `base-auth-oauth2`, `base-auth-email`, `base-auth-phone`, `base-mail`, `base-mail-smtp`, `base-sms`, `base-websocket`, `base-mcp-server` |
| Opt-in starter JARs | `base-web-starter`, `base-data-jpa-starter`, `base-security-starter`, `base-auth-starter`, `base-auth-token-starter`, `base-auth-oauth2-starter`, `base-auth-email-starter`, `base-auth-phone-starter`, `base-mail-smtp-starter`, `base-websocket-starter`, `base-mcp-server-starter` |
| Separate auto-configuration JARs where needed | `base-<feature>-autoconfigure`. Publish and manage them in the BOM when a starter needs the split. |

The [feature matrix](STARTER_FEATURE_MATRIX.md) records which library implements each starter and which setup the consumer owns. The demo remains a reactor consumer. The generator has its own distribution and uses the BOM and starter coordinates as inputs.

## Compatibility

| Component | Initial release policy |
| --- | --- |
| Java | Java 25 for compilation and supported runtime. Admit newer runtimes after CI verifies them. Raising the minimum Java version requires a Base major release once 1.0 is stable. |
| Spring Boot | Build against Spring Boot 4.1.2. Qualify Boot 4.1 patch updates through the compatibility suite before listing them as supported. Evaluate each new Boot minor before adding support. Dropping a supported Boot line requires a Base major release once 1.0 is stable. |
| Maven | Maven 3.9 or later in the 3.x line for the initial supported toolchain. Verify other major versions before listing them as supported. |
| Spring AI | Spring AI 2.0.3 is the initial MCP test baseline. Check its compatibility with Boot before changing it and scope its dependencies to MCP consumers. |

Keep Boot-managed frameworks on the consumer's supported Boot dependency set. Pin Base-managed libraries and starters to the same Base version and test the effective dependency tree when changing an external dependency or a supported Boot version. Publish the verified Java and Boot combinations in each release's notes. [Compatibility suite issue #175](https://github.com/ShahradBagheri/spring-boot-base/issues/175)

## Version rules

| Channel | Example | Policy |
| --- | --- | --- |
| Development snapshot | `0.1.0-SNAPSHOT` | Mutable build for development and CI. Local builds are sufficient initially. If remote snapshots are needed, use the separate Central Portal snapshot repository as an opt-in for testers. |
| Public milestone or release candidate | `0.1.0-M1` or `1.0.0-rc.1` | Publish an immutable, tagged version to Maven Central. Document included starters and any breaking changes. Start with the first tested starter set. |
| Stable release | `1.0.0` | Publish an immutable, tagged version to Maven Central. Keep the parent, BOM, and all included artifacts on the same version. |

Use explicit versions throughout the published dependency graph. Releases, including milestones and candidates, must resolve without snapshots. After 1.0, follow semantic versioning for Base's documented APIs, configuration properties and defaults, and consumer upgrade paths. Use patch versions for backward-compatible fixes, minor versions for backward-compatible features and deprecations, and major versions for breaking changes. Publish a new version to correct a release. [Central snapshot repository](https://central.sonatype.org/publish/publish-portal-snapshots/) · [Central release immutability](https://central.sonatype.org/publish/requirements/immutability/) · [Semantic Versioning](https://semver.org/)

## Keep existing applications pinned

An application consuming releases should pin its Base BOM and Spring Boot versions independently. Declare starter dependencies without individual versions, and upgrade them together by changing the BOM version. Pin the same exact Base version on all direct Base dependencies if the application uses the libraries without the BOM. Version ranges and snapshots are unsuitable for reproducible production builds. [Maven dependency management](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)

An existing app that vendors or clones the reactor can stay on its current commit or tag until it chooses to migrate. If it resolves locally installed `com.notshahrad` snapshots, pin the source commit and build those artifacts into an isolated Maven repository in CI so a later snapshot install cannot replace them. The public groupId is a different Maven coordinate. Migrate the app in one change to the public BOM and selected dependencies, removing the old duplicate dependencies during that migration. [Existing application migration issue #182](https://github.com/ShahradBagheri/spring-boot-base/issues/182)

## Publishing prerequisites

1. Sign in to Central Portal with the repository owner's GitHub identity and confirm the intended namespace is **Verified**. GitHub sign-in normally provisions the personal `io.github.<username>` namespace. Use the exact namespace shown by the Portal. If provisioning fails, follow Sonatype's GitHub namespace instructions before changing source coordinates. Ownership verification in the account remains pending. [Central Portal namespace guide](https://central.sonatype.org/register/namespace/#github-namespaces)
2. Prepare a Central Portal publishing token and a PGP signing key, and make the signing public key available for Central validation. Store the publishing credentials and private signing material in protected CI secrets when implementing publishing. [Portal token guide](https://central.sonatype.org/publish/generate-portal-token/) · [Signing requirements](https://central.sonatype.org/publish/requirements/gpg/)
3. Adopt Apache License 2.0 for public releases after the repository owner approves the grant. Add the repository license text and matching POM metadata before publication, and preserve any applicable third-party notices. The current repository has no license file. [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
4. Assemble release POMs with project, developer, license and SCM metadata, plus sources and Javadoc for published JARs and signatures and checksums for the deployment. Validate in Central Portal before releasing. Test the published artifacts from an external app and a clean Maven cache. [Central publishing requirements](https://central.sonatype.org/publish/requirements/) · [Publishing issue #172](https://github.com/ShahradBagheri/spring-boot-base/issues/172) · [External consumer issue #174](https://github.com/ShahradBagheri/spring-boot-base/issues/174)

## Verify the artifacts locally

The standard Maven deploy lifecycle stages the `spring-boot-base` parent POM, the BOM, and the library JARs together. Library POMs refer to the parent by Maven coordinates when resolved from the repository. The demo participates in the reactor build and skips artifact deployment.

Run this from the reactor with Java 25, Maven, and Docker. It deploys to a new temporary file repository and resolves the staged artifacts from a standalone Boot POM using a separate empty Maven cache.

```bash
staging=$(mktemp -d)
mvn clean deploy "-DaltDeploymentRepository=publication-test::file://${staging}"
python3 scripts/verify_bom_consumer.py --repository "${staging}"
```

The [standalone web consumer](../verification/web-consumer/pom.xml) builds and starts from staged artifacts in CI. The [release verification workflow](../.github/workflows/released-consumer.yml) resolves the same fixture from Maven Central using a clean cache when a GitHub release is published, or on manual dispatch with an existing release version. Publish the artifacts and confirm Central can resolve them before publishing the GitHub release.

## Verify a release candidate

Set the same release version in the reactor parent, each module's parent reference, and the standalone BOM. Use an immutable tag such as `v1.0.0-rc.1` on that commit. Run the **Verify a Base release candidate** workflow with the tag. The workflow checks the version alignment and prints the Java and Spring Boot baselines for the release notes, then runs the full reactor verification and temporary staging build on that tag.

Keep the release notes and deployment tied to the verified commit. If verification or staging fails, resolve the failure before promoting a candidate. After an artifact version is publicly available, use a new version for changes. See the [release version and immutability policy](#version-rules).

## Tested starter compatibility

| Java | Spring Boot | Spring Security | Spring AI |
| --- | --- | --- | --- |
| 25 | 4.1.2 | 7.1.2 | 2.0.3 |

The standalone consumer checks resolved versions from a staged Maven repository. The starter matrix starts a plain Boot MVC context, then starts an external context with all starters present and exercises the combination of JPA auditing, local identities, WebSocket, and MCP. The scoped MCP chain and the application's default chain both have to protect their own routes. Update the matrix and release notes when qualifying a new baseline.

## Stage and publish through Central Portal

1. Verify the namespace in Sonatype Central Portal, adopt the verified group in the build,
   and commit the approved license text and POM metadata. Use a Portal publishing token
   created by an account with access to that namespace.
2. Set up a protected GitHub Actions environment named `maven-central` with
   `CENTRAL_USERNAME`, `CENTRAL_TOKEN`, `CENTRAL_GPG_PRIVATE_KEY`, and
   `CENTRAL_GPG_PASSPHRASE` as environment secrets. Publish the signing public key
   to a Central-supported keyserver so Central can verify signatures.
3. Dispatch **Stage a release in Maven Central Portal** with the release tag. The
   workflow verifies the exact commit, runs the full reactor and staged consumer
   checks, and uploads one signed release bundle. The Portal retains control of
   the public publish step. Review and publish the validated deployment there.
4. Confirm the BOM and starters resolve publicly, then publish the GitHub release
   to trigger the standalone consumer and generator checks.

If a workflow fails before uploading, fix the candidate and rerun from the intended
tag. If the workflow loses its connection during upload, inspect Central Portal
for an existing deployment before retrying. Drop an invalid unpublished deployment
before uploading a corrected bundle. Publish a new version for any change after
public release.

Snapshots use Central's separate snapshot repository and the same Portal token when
explicitly deployed. The protected workflow accepts immutable releases only. Consumers
fetch public releases from Maven Central without a token.
