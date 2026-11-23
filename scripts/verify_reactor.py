from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def load_pom(directory):
    return ET.parse(ROOT / directory / "pom.xml").getroot()


def value(element, name):
    return element.findtext(f"m:{name}", namespaces=NS)


root = load_pom(Path("."))
assert value(root, "packaging") == "pom"

modules = [
    module.text.strip()
    for module in root.findall("m:modules/m:module", NS)
]
bom_module = "spring-boot-base-bom"
assert set(modules) == {bom_module, "base-core", "base-data-jpa", "base-data-jpa-starter", "base-web", "base-web-starter", "base-websocket", "base-security", "base-security-starter", "base-auth", "base-auth-starter", "base-auth-oauth2", "base-auth-oauth2-starter", "base-auth-token", "base-auth-token-starter", "base-auth-email", "base-mail", "base-mail-smtp", "base-auth-phone", "base-sms", "base-mcp-server", "demo-app"}

bom = load_pom(Path(bom_module))
assert value(bom, "artifactId") == bom_module
assert value(bom, "packaging") == "pom"
assert value(bom, "groupId") == value(root, "groupId")
assert value(bom, "version") == value(root, "version")
assert bom.find("m:parent", NS) is None
assert not bom.findall("m:dependencies/m:dependency", NS)

managed_artifacts = []
for dependency in bom.findall("m:dependencyManagement/m:dependencies/m:dependency", NS):
    assert value(dependency, "groupId") == "${project.groupId}"
    assert value(dependency, "version") == "${project.version}"
    assert value(dependency, "scope") is None
    assert value(dependency, "type") in (None, "jar")
    managed_artifacts.append(value(dependency, "artifactId"))
assert sorted(managed_artifacts) == sorted(set(modules) - {bom_module, "demo-app"})

dependencies = {}
for module in modules:
    if module == bom_module:
        continue
    pom = load_pom(Path(module))
    assert value(pom, "artifactId") == module
    assert value(pom.find("m:parent", NS), "artifactId") == "spring-boot-base"
    declared = set()
    for dependency in pom.findall("m:dependencies/m:dependency", NS):
        group_id = value(dependency, "groupId")
        artifact_id = value(dependency, "artifactId")
        scope = value(dependency, "scope")
        if group_id == value(root, "groupId") and artifact_id in modules:
            assert artifact_id != "demo-app", f"{module} depends on demo-app"
            if scope not in ("test", "provided"):
                declared.add(artifact_id)
    dependencies[module] = declared

    if module != "demo-app":
        assert value(pom, "packaging") in (None, "jar"), module
        active_plugins = pom.findall("m:build/m:plugins/m:plugin", NS)
        assert all(value(plugin, "artifactId") != "spring-boot-maven-plugin" for plugin in active_plugins), module

        artifact = ROOT / module / "target" / f"{module}-{value(root, 'version')}.jar"
        if artifact.is_file():
            with zipfile.ZipFile(artifact) as jar:
                unexpected = [
                    name for name in jar.namelist()
                    if name.startswith(("BOOT-INF/", "db/migration/"))
                    or ("/" not in name and name.startswith(("application.", "application-")))
                ]
                assert not unexpected, f"{module} includes application resources: {unexpected}"

assert dependencies["base-core"] == set()
assert dependencies["base-data-jpa"].issubset({"base-core"})
assert dependencies["base-websocket"] == set()
assert dependencies["base-web"] == {"base-core"}
assert dependencies["base-web-starter"] == {"base-web"}
assert dependencies["base-security"] == {"base-web"}
assert dependencies["base-data-jpa-starter"] == {"base-data-jpa"}
assert dependencies["base-security-starter"] == {"base-security"}
assert dependencies["base-auth"] == {"base-data-jpa"}
assert dependencies["base-auth-starter"] == {"base-auth", "base-data-jpa-starter"}
assert dependencies["base-auth-oauth2"] == {"base-auth", "base-security"}
assert dependencies["base-auth-oauth2-starter"] == {"base-auth-oauth2", "base-auth-starter", "base-security-starter"}
assert dependencies["base-auth-token"] == {"base-auth", "base-security"}
assert dependencies["base-auth-token-starter"] == {"base-auth-token", "base-auth-starter", "base-security-starter"}
assert dependencies["base-auth-email"] == {"base-auth"}
assert dependencies["base-mail"] == set()
assert dependencies["base-mail-smtp"] == {"base-mail"}
assert dependencies["base-auth-phone"] == {"base-auth"}
assert dependencies["base-sms"] == set()
assert dependencies["base-mcp-server"] == set()
assert dependencies["demo-app"] == {"base-core", "base-data-jpa", "base-web", "base-websocket", "base-security", "base-auth", "base-auth-oauth2", "base-auth-token", "base-auth-email", "base-mail", "base-mail-smtp", "base-auth-phone", "base-sms", "base-mcp-server"}

visited = set()
visiting = set()


def visit(module):
    if module in visiting:
        raise AssertionError(f"Circular module dependency: {module}")
    if module in visited:
        return
    visiting.add(module)
    for dependency in dependencies[module]:
        visit(dependency)
    visiting.remove(module)
    visited.add(module)


for module in dependencies:
    visit(module)

core = load_pom(Path("base-core"))
for dependency in core.findall("m:dependencies/m:dependency", NS):
    group_id = value(dependency, "groupId") or ""
    if value(dependency, "scope") != "test":
        assert not group_id.startswith(("org.springframework", "org.hibernate", "jakarta.persistence"))
        assert group_id not in {"org.postgresql", "com.h2database"}

demo = load_pom(Path("demo-app"))
assert demo.findtext("m:properties/m:maven.deploy.skip", namespaces=NS) == "true"
assert value(demo.find("m:build/m:plugins/m:plugin", NS), "groupId") == "org.springframework.boot"
assert value(demo.find("m:build/m:plugins/m:plugin", NS), "artifactId") == "spring-boot-maven-plugin"

jars = list((ROOT / "demo-app" / "target").glob("demo-app-*.jar"))
if jars:
    executable = [jar for jar in jars if not jar.name.endswith(("-sources.jar", "-javadoc.jar"))]
    assert len(executable) == 1
    with zipfile.ZipFile(executable[0]) as jar:
        names = set(jar.namelist())
        manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8").replace("\r\n ", "")
        assert "Start-Class: com.notshahrad.spring_boot_base.SpringBootBaseApplication" in manifest
        assert any(name.startswith("BOOT-INF/classes/") for name in names)
        version = value(root, "version")
        for module in dependencies["demo-app"]:
            assert f"BOOT-INF/lib/{module}-{version}.jar" in names

token_jars = list((ROOT / "base-auth-token" / "target").glob("base-auth-token-*.jar"))
for token_jar in token_jars:
    if token_jar.name.endswith(("-sources.jar", "-javadoc.jar")):
        continue
    with zipfile.ZipFile(token_jar) as jar:
        assert not any(name.startswith("BOOT-INF/") for name in jar.namelist())
        assert any(name.endswith("/AccessTokenIssuer.class") for name in jar.namelist())

print("Maven module isolation and executable packaging verified")
