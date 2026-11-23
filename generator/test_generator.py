import io
import json
import threading
import unittest
from http.server import ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from zipfile import ZipFile
from xml.etree import ElementTree

from project_generator import FEATURES, project_zip, resolve
from server import handler_for

PROJECT = {
    "name": "Example service",
    "group": "example.project",
    "artifact": "example-service",
    "package": "example.service",
    "database": "none",
    "features": [],
}
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


class GeneratorTest(unittest.TestCase):
    def test_minimal_archive_is_reproducible_and_contains_wrapper(self):
        name, content = project_zip(PROJECT, "1.0.0")
        self.assertEqual(name, "example-service.zip")
        self.assertEqual(content, project_zip(PROJECT, "1.0.0")[1])
        with ZipFile(io.BytesIO(content)) as archive:
            prefix = "example-service/"
            self.assertEqual(archive.getinfo(prefix + "mvnw").external_attr >> 16 & 0o777, 0o755)
            pom = ElementTree.fromstring(archive.read(prefix + "pom.xml"))
            self.assertEqual(pom.findtext("m:parent/m:artifactId", namespaces=NS), "spring-boot-starter-parent")
            dependencies = pom.findall("m:dependencies/m:dependency/m:artifactId", NS)
            self.assertEqual([dep.text for dep in dependencies], ["spring-boot-starter", "spring-boot-starter-test"])
            self.assertNotIn(b"datasource", archive.read(prefix + "src/main/resources/application.yml"))

    def test_email_includes_identity_and_jpa_but_not_smtp(self):
        opts = {**PROJECT, "features": ["email"], "database": "postgresql"}
        jar = project_zip(opts, "1.0.0-M1")[1]
        with ZipFile(io.BytesIO(jar)) as archive:
            pom = archive.read("example-service/pom.xml").decode()
            config = archive.read("example-service/src/main/resources/application.yml").decode()
            readme = archive.read("example-service/README.md").decode()
        self.assertIn(FEATURES["auth"]["artifact"], pom)
        self.assertIn(FEATURES["jpa"]["artifact"], pom)
        self.assertNotIn(FEATURES["smtp"]["artifact"], pom)
        self.assertIn("base.auth.email.password-enabled: false", config)
        self.assertIn("Apply the email authentication migrations", readme)
        self.assertNotIn("Configure the SMTP host", readme)
        self.assertEqual(resolve(["websocket"]), ["security", "web", "websocket"])

    def test_rejects_invalid_names_database_and_snapshot_versions(self):
        for replacement in ({"artifact": "../bad"}, {"package": "example.class"},
                            {"group": "x<y"}, {"features": ["jpa"]},
                            {"features": [["invalid"]]}, {"database": ["postgresql"]}):
            with self.subTest(replacement=replacement), self.assertRaises(ValueError):
                project_zip({**PROJECT, **replacement}, "1.0.0")
        with self.assertRaises(ValueError):
            project_zip(PROJECT, "1.0.0-SNAPSHOT")

    def test_download_and_invalid_request(self):
        with ThreadingHTTPServer(("127.0.0.1", 0), handler_for("1.0.0")) as server:
            thread = threading.Thread(target=server.serve_forever)
            thread.start()
            try:
                url = f"http://127.0.0.1:{server.server_port}"
                with urlopen(url + "/catalog", timeout=5) as response:
                    self.assertEqual(json.load(response)["baseVersion"], "1.0.0")
                with urlopen(self.request(url, PROJECT), timeout=5) as response:
                    self.assertEqual(response.headers["Content-Type"], "application/zip")
                    self.assertIn("example-service.zip", response.headers["Content-Disposition"])
                    self.assertTrue(response.read().startswith(b"PK"))
                with self.assertRaises(HTTPError) as failure:
                    urlopen(self.request(url, {**PROJECT, "artifact": "../bad"}), timeout=5)
                self.assertEqual(failure.exception.code, 400)
            finally:
                server.shutdown()
                thread.join()

    @staticmethod
    def request(url, body):
        return Request(url + "/project", json.dumps(body).encode(), {"Content-Type": "application/json"})


if __name__ == "__main__":
    unittest.main()
