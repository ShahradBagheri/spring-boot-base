"""Create an independent Spring Boot application from a Base feature selection."""

import argparse
import io
import json
from pathlib import Path
import re
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

HERE = Path(__file__).resolve().parent
CATALOG = json.loads((HERE / "catalog.json").read_text(encoding="utf-8"))
FEATURES = CATALOG["features"]
DATABASES = {"none", "postgresql"}
PUBLIC_GROUP = "io.github.shahradbagheri"
IDENTIFIER = re.compile(r"[a-z][a-z0-9_]*")
ARTIFACT = re.compile(r"[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*")
VERSION = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?")
PROJECT_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9 ._-]{0,79}")
RESERVED = set("""
abstract assert boolean break byte case catch char class const continue default do double else enum extends final
finally float for goto if implements import instanceof int interface long native new package private protected public
return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null
""".split())


def package_name(value):
    return isinstance(value, str) and 0 < len(value) <= 120 and all(
        IDENTIFIER.fullmatch(part) and part not in RESERVED for part in value.split(".")
    )


def resolve(selected):
    if not isinstance(selected, list) or len(selected) > len(FEATURES):
        raise ValueError("Provide a feature list.")
    if any(not isinstance(key, str) or key not in FEATURES for key in selected):
        raise ValueError("Unknown feature selection.")

    resolved = set(selected)
    pending = list(resolved)
    while pending:
        for requirement in FEATURES[pending.pop()].get("requires", []):
            if requirement not in resolved:
                resolved.add(requirement)
                pending.append(requirement)
    return sorted(resolved)


def validate(options):
    if not isinstance(options, dict):
        raise ValueError("Provide project options as an object.")
    name = options.get("name")
    group = options.get("group")
    artifact = options.get("artifact")
    package = options.get("package")
    database = options.get("database", "none")
    if not isinstance(name, str) or not PROJECT_NAME.fullmatch(name):
        raise ValueError("Use a project name of up to 80 letters, digits, spaces, dots, underscores, or hyphens.")
    if not package_name(group) or not package_name(package):
        raise ValueError("Use lowercase dot-separated Java identifiers for the group and package.")
    if not isinstance(artifact, str) or len(artifact) > 64 or not ARTIFACT.fullmatch(artifact):
        raise ValueError("Use a lowercase Maven artifact ID, optionally separated by dots or hyphens.")
    if not isinstance(database, str) or database not in DATABASES:
        raise ValueError("Choose none or PostgreSQL.")
    if options.get("java", CATALOG["java"]) != CATALOG["java"] or options.get("boot", CATALOG["springBoot"]) != CATALOG["springBoot"]:
        raise ValueError("Choose the supported Java and Spring Boot versions.")
    resolved = resolve(options.get("features", []))
    if ("jpa" in resolved) != (database == "postgresql"):
        raise ValueError("Select PostgreSQL when using JPA. Otherwise choose no database.")
    return name, group, artifact, package, resolved


def release_version(value):
    if not isinstance(value, str) or len(value) > 40 or not VERSION.fullmatch(value) or value.upper().endswith("-SNAPSHOT"):
        raise ValueError("Provide a pinned Base release version, such as 1.0.0 or 1.0.0-rc.1.")
    return value


def dependency(group, artifact, scope=None):
    scope_line = f"\n            <scope>{scope}</scope>" if scope else ""
    return f"""        <dependency>
            <groupId>{group}</groupId>
            <artifactId>{artifact}</artifactId>{scope_line}
        </dependency>
"""


def pom(name, group, artifact, resolved, version, base_group):
    dependencies = "".join(dependency(base_group, FEATURES[feature]["artifact"]) for feature in resolved)
    dependencies += dependency("org.springframework.boot", "spring-boot-starter")
    dependencies += dependency("org.springframework.boot", "spring-boot-starter-test", "test")
    if "jpa" in resolved:
        dependencies += dependency("org.postgresql", "postgresql", "runtime")
        dependencies += dependency("com.h2database", "h2", "test")
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>{CATALOG["springBoot"]}</version>
        <relativePath/>
    </parent>
    <groupId>{group}</groupId>
    <artifactId>{artifact}</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>{name}</name>
    <properties>
        <java.version>{CATALOG["java"]}</java.version>
    </properties>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>{base_group}</groupId>
                <artifactId>spring-boot-base-bom</artifactId>
                <version>{version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
{dependencies}    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
"""


def sources(package, database, web):
    path = package.replace(".", "/")
    test_options = []
    if web:
        test_options.append("webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT")
    if database:
        test_options.append(
            'properties = {"spring.datasource.url=jdbc:h2:mem:generated", '
            '"spring.datasource.username=sa", "spring.datasource.password="}'
        )
    test_properties = "(" + ", ".join(test_options) + ")" if test_options else ""
    return {
        f"src/main/java/{path}/Application.java": f"""package {package};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {{
    public static void main(String[] args) {{
        SpringApplication.run(Application.class, args);
    }}
}}
""",
        f"src/test/java/{path}/ApplicationTest.java": f"""package {package};

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest{test_properties}
class ApplicationTest {{
    @Test
    void contextLoads() {{
    }}
}}
"""
    }


def configuration(name, resolved):
    properties = {}
    for feature in resolved:
        properties.update(FEATURES[feature].get("flags", {}))
    lines = [f'spring.application.name: "{name}"']
    lines.extend(f"{key}: {str(value).lower()}" for key, value in sorted(properties.items()))
    if "jpa" in resolved:
        lines.extend([
            "spring.datasource.url: ${DB_URL}",
            "spring.datasource.username: ${DB_USERNAME}",
            "spring.datasource.password: ${DB_PASSWORD}",
            "spring.jpa.hibernate.ddl-auto: validate",
        ])
    return "\n".join(lines) + "\n"


def project_zip(options, version, base_group=PUBLIC_GROUP, *, staged=False):
    name, group, artifact, package, resolved = validate(options)
    if staged and version.endswith("-SNAPSHOT"):
        release_version(version.removesuffix("-SNAPSHOT"))
    else:
        version = release_version(version)
    if not package_name(base_group):
        raise ValueError("Invalid Base group.")
    database = "jpa" in resolved
    files = sources(package, database, "web" in resolved)
    files["pom.xml"] = pom(name, group, artifact, resolved, version, base_group)
    files["src/main/resources/application.yml"] = configuration(name, resolved)
    files[".gitignore"] = "/target/\n/.idea/\n*.iml\n.env\n"
    files[".env.example"] = "DB_URL=\nDB_USERNAME=\nDB_PASSWORD=\n" if database else ""
    if database:
        files["src/main/resources/db/migration/.gitkeep"] = ""
    readme = [
        f"# {name}", "", "Use Java 25 and run `./mvnw test` or `mvnw.cmd test` on Windows.",
        "Start the application with `./mvnw spring-boot:run`.", "",
        f"Base BOM: `{base_group}:spring-boot-base-bom:{version}`.",
    ]
    if resolved:
        readme += ["", "Included features: " + ", ".join(FEATURES[key]["label"] for key in resolved) + "."]
    if database:
        readme += ["", "Export the datasource variables from `.env.example` and apply your application-owned migrations.",
                   "The migration directory is reserved for your scripts. Configure Flyway if you use it."]
    if set(resolved) & {"security", "auth", "oidc", "token", "email", "phone", "websocket", "mcp", "smtp"}:
        readme += ["", "Keep optional features disabled until their application-owned credentials, infrastructure, "
                   "and route authorization are configured. See the [Base starter documentation]"
                   "(https://github.com/ShahradBagheri/spring-boot-base/tree/master/docs)."]
    files["README.md"] = "\n".join(readme) + "\n"
    for wrapper in ("mvnw", "mvnw.cmd", ".mvn/wrapper/maven-wrapper.properties"):
        files[wrapper] = (HERE.parent / wrapper).read_bytes()

    contents = io.BytesIO()
    with ZipFile(contents, "w") as archive:
        for path, content in sorted(files.items()):
            entry = ZipInfo(f"{artifact}/{path}")
            entry.create_system = 3
            entry.external_attr = (0o100755 if path == "mvnw" else 0o100644) << 16
            archive.writestr(entry, content, compress_type=ZIP_DEFLATED)
    return artifact + ".zip", contents.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("options", type=Path, help="JSON project options")
    parser.add_argument("--base-version", required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        name, contents = project_zip(json.loads(args.options.read_text(encoding="utf-8")), args.base_version)
        output = args.output or Path(name)
        with output.open("xb") as target:
            target.write(contents)
        print(output)
    except (OSError, ValueError, TypeError) as exc:
        parser.error(str(exc))


if __name__ == "__main__":
    main()
