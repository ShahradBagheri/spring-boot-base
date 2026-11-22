"""Verify the installed BOM from a project outside the reactor."""

import json
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(ROOT / "pom.xml")
bom = ET.parse(ROOT / "spring-boot-base-bom" / "pom.xml")
boot_version = root.findtext("m:parent/m:version", namespaces=NS)
base_group = bom.findtext("m:groupId", namespaces=NS)
bom_artifact = bom.findtext("m:artifactId", namespaces=NS)
base_version = bom.findtext("m:version", namespaces=NS)

CONSUMER_POM = f"""<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>{boot_version}</version>
        <relativePath/>
    </parent>
    <groupId>example.consumer</groupId>
    <artifactId>standalone-bom-consumer</artifactId>
    <version>1.0.0</version>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>{base_group}</groupId>
                <artifactId>{bom_artifact}</artifactId>
                <version>{base_version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <profiles>
        <profile>
            <id>base-web</id>
            <dependencies>
                <dependency>
                    <groupId>{base_group}</groupId>
                    <artifactId>base-web</artifactId>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""


def dependencies(project, *options):
    result = project / "dependency-tree.json"
    subprocess.run(
        ["mvn", "--batch-mode", "--no-transfer-progress", *options, "dependency:tree",
         "-DoutputType=json", f"-DoutputFile={result}"],
        cwd=project,
        check=True,
    )
    with result.open() as tree:
        return json.load(tree).get("children", [])


with TemporaryDirectory(prefix="spring-boot-base-consumer-") as directory:
    project = Path(directory)
    (project / "pom.xml").write_text(CONSUMER_POM, encoding="utf-8")

    imported = dependencies(project)
    assert imported == [], f"BOM import added dependencies: {imported}"

    selected = dependencies(project, "-Pbase-web")
    assert [(entry["artifactId"], entry["version"]) for entry in selected] == [("base-web", base_version)], selected

    transitive = {
        (entry["groupId"], entry["artifactId"]): entry["version"]
        for entry in selected[0]["children"]
    }
    assert transitive[(base_group, "base-core")] == base_version, transitive
    assert transitive[("org.springframework.boot", "spring-boot-starter-webmvc")] == boot_version, transitive

print("Standalone Spring Boot BOM resolution verified")
