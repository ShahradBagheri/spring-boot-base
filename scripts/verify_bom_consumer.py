"""Verify Base artifacts from a project outside the reactor."""

import argparse
import json
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--repository", type=Path, help="File repository for a clean consumer build")
repository = parser.parse_args().repository

root = ET.parse(ROOT / "pom.xml")
bom = ET.parse(ROOT / "spring-boot-base-bom" / "pom.xml")
boot_version = root.findtext("m:parent/m:version", namespaces=NS)
base_group = bom.findtext("m:groupId", namespaces=NS)
bom_artifact = bom.findtext("m:artifactId", namespaces=NS)
base_version = bom.findtext("m:version", namespaces=NS)
libraries = sorted(
    entry.findtext("m:artifactId", namespaces=NS)
    for entry in bom.findall("m:dependencyManagement/m:dependencies/m:dependency", NS)
)

repositories = ""
if repository is not None:
    group_directory = repository.joinpath(*base_group.split("."))
    deployed = {entry.name for entry in group_directory.iterdir() if entry.is_dir()}
    parent_artifact = root.findtext("m:artifactId", namespaces=NS)
    assert deployed == {*libraries, parent_artifact, bom_artifact}, deployed

    repositories = f"""
    <repositories>
        <repository>
            <id>publication-test</id>
            <url>{repository.resolve().as_uri()}</url>
            <snapshots><enabled>true</enabled></snapshots>
        </repository>
    </repositories>"""

library_dependencies = "\n".join(
    f"""                <dependency>
                    <groupId>{base_group}</groupId>
                    <artifactId>{library}</artifactId>
                </dependency>"""
    for library in libraries
)

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
    </dependencyManagement>{repositories}
    <profiles>
        <profile>
            <id>base-web-starter</id>
            <dependencies>
                <dependency>
                    <groupId>{base_group}</groupId>
                    <artifactId>base-web-starter</artifactId>
                </dependency>
            </dependencies>
        </profile>
        <profile>
            <id>all-libraries</id>
            <dependencies>
{library_dependencies}
            </dependencies>
        </profile>
    </profiles>
</project>
"""


def dependencies(project, *options):
    result = project / "dependency-tree.json"
    repository_options = (
        [f"-Dmaven.repo.local={project / 'maven-cache'}"] if repository is not None else []
    )
    subprocess.run(
        ["mvn", "--batch-mode", "--no-transfer-progress", *repository_options, *options,
         "dependency:resolve", "dependency:tree", "-DoutputType=json", f"-DoutputFile={result}"],
        cwd=project,
        check=True,
    )
    with result.open() as tree:
        return json.load(tree).get("children", [])


def coordinates(entries):
    for entry in entries:
        yield entry["groupId"], entry["artifactId"]
        yield from coordinates(entry.get("children", []))


def effective_versions(entries):
    for entry in entries:
        yield entry["groupId"], entry["artifactId"], entry["version"]
        yield from effective_versions(entry.get("children", []))


with TemporaryDirectory(prefix="spring-boot-base-consumer-") as directory:
    project = Path(directory)
    (project / "pom.xml").write_text(CONSUMER_POM, encoding="utf-8")

    imported = dependencies(project)
    assert imported == [], f"BOM import added dependencies: {imported}"

    selected = dependencies(project, "-Pbase-web-starter")
    assert [(entry["artifactId"], entry["version"]) for entry in selected] == [("base-web-starter", base_version)], selected

    web = selected[0]["children"]
    assert [(entry["artifactId"], entry["version"]) for entry in web] == [("base-web", base_version)], web
    transitive = {
        (entry["groupId"], entry["artifactId"]): entry["version"]
        for entry in web[0]["children"]
    }
    assert transitive[(base_group, "base-core")] == base_version, transitive
    assert transitive[("org.springframework.boot", "spring-boot-starter-webmvc")] == boot_version, transitive
    resolved = set(coordinates(selected))
    assert {artifact for group, artifact in resolved if group == base_group} == {
        "base-web-starter", "base-web", "base-core"
    }, resolved
    assert not any(group.startswith(("org.springframework.security", "org.hibernate", "jakarta.persistence"))
                   or artifact.startswith(("spring-boot-data-jpa", "spring-boot-starter-data-jpa"))
                   for group, artifact in resolved), resolved

    if repository is not None:
        selected = dependencies(project, "-Pall-libraries")
        assert {(entry["groupId"], entry["artifactId"], entry["version"]) for entry in selected} == {
            (base_group, library, base_version) for library in libraries
        }, selected

        versions = set(effective_versions(selected))
        expected_families = {
            "org.springframework.boot": "4.1.2",
            "org.springframework.security": "7.1.2",
            "org.springframework.ai": "2.0.3",
        }
        for group, expected in expected_families.items():
            family = {(artifact, version) for owner, artifact, version in versions if owner == group}
            assert family and {version for _, version in family} == {expected}, (group, expected, family)

        resolved = set(coordinates(selected))
        forbidden = {
            (base_group, "demo-app"),
            ("com.h2database", "h2"),
            ("org.postgresql", "postgresql"),
            ("org.projectlombok", "lombok"),
            ("org.springframework.boot", "spring-boot-starter-test"),
            ("org.springframework.boot", "spring-boot-starter-flyway"),
        }
        assert not resolved & forbidden, f"Unexpected consumer dependencies: {resolved & forbidden}"

print("Standalone Spring Boot BOM resolution verified")
