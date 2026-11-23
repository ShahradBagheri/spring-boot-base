"""Build generated projects with a fresh cache against staged or public Base artifacts."""

import argparse
from io import BytesIO
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
from xml.sax.saxutils import escape
from zipfile import ZipFile

from project_generator import PUBLIC_GROUP, project_zip, resolve

PRESETS = {
    "minimal": [],
    "api": ["web"],
    "jpa": ["jpa"],
    "secure-api": ["security"],
    "oauth-disabled": ["oidc"],
    "email": ["email"],
    "websocket": ["websocket"],
    "mcp-disabled": ["mcp"],
}


def verify(group, version, repository, upstream_settings):
    with TemporaryDirectory(prefix="base-generated-verification-") as tmp:
        work = Path(tmp)
        repo_option = []
        if repository:
            profile = f"""
            <profile>
                <id>base-generator-staging</id>
                <repositories>
                    <repository>
                        <id>publication-test</id><url>{escape(repository.resolve().as_uri())}</url>
                        <snapshots><enabled>true</enabled></snapshots>
                    </repository>
                </repositories>
            </profile>
"""
            settings = work / "settings.xml"
            settings.write_text(
                "<settings><profiles>" + profile + "</profiles>"
                "<activeProfiles><activeProfile>base-generator-staging</activeProfile></activeProfiles></settings>",
                encoding="utf-8",
            )
            repo_option = ["--settings", str(settings)]
            if upstream_settings:
                repo_option += ["--global-settings", str(upstream_settings)]

        for name, features in PRESETS.items():
            opts = {
                "name": name,
                "group": "example.consumer",
                "artifact": name,
                "package": "example.consumer.app",
                "database": "postgresql" if "jpa" in resolve(features) else "none",
                "features": features,
            }
            _, archive = project_zip(opts, version, group, staged=repository is not None)
            with ZipFile(BytesIO(archive)) as zip_file:
                zip_file.extractall(work)
            project = work / name
            subprocess.run(
                ["mvn", "-B", "-ntp", *repo_option, f"-Dmaven.repo.local={work / 'maven-cache'}", "verify"],
                cwd=project, check=True,
            )
            if name == "minimal":
                java = Path(os.environ["JAVA_HOME"], "bin/java") if "JAVA_HOME" in os.environ else "java"
                jar = project / "target/minimal-0.0.1-SNAPSHOT.jar"
                result = subprocess.run([java, "-jar", jar], check=True, cwd=project, timeout=30,
                                        capture_output=True, text=True)
                if "Started Application" not in result.stdout:
                    raise RuntimeError(result.stdout)
    print("All eight generated projects passed Maven verification.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--group", default=PUBLIC_GROUP)
    parser.add_argument("--version", required=True)
    parser.add_argument("--repository", type=Path)
    parser.add_argument("--upstream-settings", type=Path)
    args = parser.parse_args()
    verify(args.group, args.version, args.repository, args.upstream_settings)
