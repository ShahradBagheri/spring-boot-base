"""Verify a staged Maven release bundle or submit it for Central Portal validation."""

import argparse
from base64 import b64encode
from pathlib import Path
import os
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from uuid import uuid4
from xml.etree import ElementTree as ET
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
ARTIFACT_SUFFIXES = {"", "-sources", "-javadoc"}
REQUIRED_SIDECARS = [".asc", ".md5", ".sha1"]
OPTIONAL_SIDECARS = [".sha256", ".sha512"]


def create_bundle(staging, output):
    bom = ET.parse(ROOT / "spring-boot-base-bom/pom.xml")
    group = bom.findtext("m:groupId", namespaces=NS)
    version = bom.findtext("m:version", namespaces=NS)
    if version.endswith("-SNAPSHOT"):
        raise ValueError("The Central release bundle must use an immutable version.")
    jars = {entry.findtext("m:artifactId", namespaces=NS)
            for entry in bom.findall("m:dependencyManagement/m:dependencies/m:dependency", NS)}
    poms = jars | {bom.findtext("m:artifactId", namespaces=NS), "spring-boot-base"}
    namespace = staging.joinpath(*group.split("."))
    actual = {path.name for path in namespace.iterdir() if path.is_dir()}
    if poms != actual:
        raise ValueError(f"Unexpected or missing staged modules: {sorted(actual ^ poms)}")

    release_files = []
    for artifact in sorted(poms):
        directory = namespace / artifact / version
        stem = artifact + "-" + version
        required = {stem + ".pom"}
        if artifact in jars:
            required |= {stem + suffix + ".jar" for suffix in ARTIFACT_SUFFIXES}
        optional = {name + suffix for name in required for suffix in OPTIONAL_SIDECARS}
        required |= {name + suffix for name in required for suffix in REQUIRED_SIDECARS}
        absent = sorted(name for name in required if not (directory / name).is_file())
        if absent:
            raise ValueError(f"Incomplete staged artifacts for {artifact}: {absent}")
        release_files.extend(directory / name for name in sorted(required | optional)
                             if (directory / name).is_file())

    with ZipFile(output, "x", ZIP_DEFLATED) as archive:
        for path in release_files:
            archive.write(path, path.relative_to(staging).as_posix())
    print(f"Verified signed release bundle with {len(poms)} modules.")


def upload(bundle, release_tag):
    username = os.environ.get("CENTRAL_USERNAME")
    token = os.environ.get("CENTRAL_TOKEN")
    if not username or not token:
        raise ValueError("Central Portal credentials are missing from the release environment.")
    boundary = "central-" + uuid4().hex
    part = (f'--{boundary}\r\nContent-Disposition: form-data; name="bundle"; '
            'filename="central-release.zip"\r\nContent-Type: application/zip\r\n\r\n').encode()
    body = part + bundle.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    query = urlencode({"name": "Spring Boot Base " + release_tag, "publishingType": "USER_MANAGED"})
    request = Request("https://central.sonatype.com/api/v1/publisher/upload?" + query, data=body, headers={
        "Authorization": "Bearer " + b64encode(f"{username}:{token}".encode()).decode(),
        "Content-Type": "multipart/form-data; boundary=" + boundary,
    })
    try:
        with urlopen(request, timeout=180) as response:
            deployment = response.read(64).decode().strip()
            print(f"Central Portal deployment: {deployment}")
    except HTTPError as exc:
        raise ValueError(f"Central Portal rejected the bundle (HTTP {exc.code}).") from None


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upload", action="store_true")
    parser.add_argument("source", type=Path)
    parser.add_argument("destination")
    args = parser.parse_args()
    try:
        if args.upload:
            upload(args.source, args.destination)
        else:
            create_bundle(args.source, Path(args.destination))
    except (OSError, ValueError) as exc:
        parser.error(str(exc))
