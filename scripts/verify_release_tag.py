"""Check that the current checkout represents a coherent release tag."""

import argparse
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def revision(ref):
    return subprocess.check_output(
        ["git", "rev-parse", "--verify", ref], cwd=ROOT, text=True
    ).strip()


def verify(tag):
    if not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?", tag):
        raise SystemExit("Use a release tag such as v1.0.0 or v1.0.0-rc.1.")
    version = tag[1:]
    if version.upper().endswith("-SNAPSHOT"):
        raise SystemExit("Release tags require an immutable version.")
    commit = revision(f"refs/tags/{tag}^{{commit}}")
    if commit != revision("HEAD"):
        raise SystemExit(f"Check out {tag} before verifying its release.")

    root = ET.parse(ROOT / "pom.xml")
    if root.findtext("m:version", namespaces=NS) != version:
        raise SystemExit(f"Parent version must match {version}.")

    for entry in root.findall("m:modules/m:module", NS):
        module = entry.text.strip()
        pom = ET.parse(ROOT / module / "pom.xml")
        parent = pom.find("m:parent", NS)
        declared = pom.findtext("m:version", namespaces=NS)
        if parent is not None:
            parent_version = parent.findtext("m:version", namespaces=NS)
            if parent_version != version:
                raise SystemExit(f"{module} inherits {parent_version}, expected {version}.")
        elif declared is None:
            raise SystemExit(f"{module} has no release version.")
        if declared is not None and declared != version:
            raise SystemExit(f"{module} declares {declared}, expected {version}.")

    java = root.findtext("m:properties/m:java.version", namespaces=NS)
    boot = root.findtext("m:parent/m:version", namespaces=NS)
    print(f"- Release tag: `{tag}`")
    print(f"- Source commit: `{commit}`")
    print(f"- Java baseline: `{java}`")
    print(f"- Spring Boot baseline: `{boot}`")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tag")
    verify(parser.parse_args().tag)
