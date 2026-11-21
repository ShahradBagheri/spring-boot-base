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
assert set(modules) == {"base-core", "base-data-jpa", "demo-app"}

dependencies = {}
for module in modules:
    pom = load_pom(Path(module))
    assert value(pom, "artifactId") == module
    assert value(pom, "parent") is None
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
assert dependencies["demo-app"] == {"base-core", "base-data-jpa"}

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
        manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8")
        assert "Start-Class: com.notshahrad.spring_boot_base.SpringBootBaseApplication" in manifest
        assert any(name.startswith("BOOT-INF/classes/") for name in names)
        assert any("base-core-" in name for name in names if name.startswith("BOOT-INF/lib/"))
        assert any("base-data-jpa-" in name for name in names if name.startswith("BOOT-INF/lib/"))

print("Maven module isolation and executable packaging verified")
