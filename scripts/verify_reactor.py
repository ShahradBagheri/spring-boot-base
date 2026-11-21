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
assert set(modules) == {"base-core", "base-data-jpa", "base-web", "base-security", "base-auth", "base-auth-oauth2", "base-auth-token", "base-auth-email", "base-mail", "base-mail-smtp", "base-auth-phone", "base-sms", "demo-app"}

dependencies = {}
for module in modules:
    pom = load_pom(Path(module))
    assert value(pom, "artifactId") == module
    assert value(pom.find("m:parent", NS), "artifactId") == "spring-boot-base"
    declared = set()
    for dependency in pom.findall("m:dependencies/m:dependency", NS):
        group_id = value(dependency, "groupId")
        artifact_id = value(dependency, "artifactId")
        scope = value(dependency, "scope")
        if group_id == "com.notshahrad" and artifact_id in modules and scope not in ("test", "provided"):
            declared.add(artifact_id)
    dependencies[module] = declared

assert dependencies["base-core"] == set()
assert dependencies["base-data-jpa"].issubset({"base-core"})
assert dependencies["base-web"] == {"base-core"}
assert dependencies["base-security"] == {"base-web"}
assert dependencies["base-auth"] == {"base-data-jpa"}
assert dependencies["base-auth-oauth2"] == {"base-auth", "base-security"}
assert dependencies["base-auth-token"] == {"base-auth", "base-security"}
assert dependencies["base-auth-email"] == {"base-auth"}
assert dependencies["base-mail"] == set()
assert dependencies["base-mail-smtp"] == {"base-mail"}
assert dependencies["base-auth-phone"] == {"base-auth"}
assert dependencies["base-sms"] == set()
assert dependencies["demo-app"] == {"base-core", "base-data-jpa", "base-web", "base-security", "base-auth", "base-auth-oauth2", "base-auth-token", "base-auth-email", "base-mail", "base-mail-smtp", "base-auth-phone", "base-sms"}

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


for module in modules:
    visit(module)

core = load_pom(Path("base-core"))
for dependency in core.findall("m:dependencies/m:dependency", NS):
    group_id = value(dependency, "groupId") or ""
    if value(dependency, "scope") != "test":
        assert not group_id.startswith(("org.springframework", "org.hibernate", "jakarta.persistence"))
        assert group_id not in {"org.postgresql", "com.h2database"}

demo = load_pom(Path("demo-app"))
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
