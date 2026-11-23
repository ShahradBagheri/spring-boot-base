"""Rehearse a copied Base web application's move to a BOM-managed starter."""

import argparse
from pathlib import Path
import shutil
import subprocess
from tempfile import TemporaryDirectory
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parent
FIXTURE = ROOT / "web-consumer"
HTTP_TEST = """
package example.standalone;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = ConsumerApplication.class)
class MigrationContractTest {
    @LocalServerPort
    int port;

    @Test
    void keepsExistingSuccessAndErrorContracts() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var success = request(client, "/example");
            assertThat(success.statusCode()).isEqualTo(200);
            var mapper = new JsonMapper();
            assertThat(mapper.readTree(success.body()).get("data").asText()).isEqualTo("standalone");

            var error = request(client, "/missing");
            assertThat(error.statusCode()).isEqualTo(404);
            assertThat(mapper.readTree(error.body()).get("message").asText())
                    .isEqualTo("Example not found with id: 42");
        }
    }

    private HttpResponse<String> request(HttpClient client, String path) throws Exception {
        var url = URI.create("http://127.0.0.1:" + port + path);
        return client.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
"""

LEGACY_POM = """<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>{group}</groupId>
        <artifactId>spring-boot-base</artifactId>
        <version>{version}</version>
        <relativePath/>
    </parent>
    <groupId>example.standalone</groupId>
    <artifactId>copied-web-app</artifactId>
    <version>{version}</version>
    <dependencies>
        <dependency>
            <groupId>{group}</groupId>
            <artifactId>base-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--group", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--global-settings", type=Path)
    args = parser.parse_args()

    with TemporaryDirectory(prefix="base-web-migration-") as directory:
        work = Path(directory)
        settings = work / "settings.xml"
        settings.write_text(f"""
<settings>
    <profiles><profile>
        <id>staged-base</id>
        <repositories><repository>
            <id>publication-test</id>
            <url>{escape(args.repository.resolve().as_uri())}</url>
            <snapshots><enabled>true</enabled></snapshots>
        </repository></repositories>
    </profile></profiles>
    <activeProfiles><activeProfile>staged-base</activeProfile></activeProfiles>
</settings>
""", encoding="utf-8")
        options = ["-s", str(settings), f"-Dmaven.repo.local={work / 'cache'}", "-Dtest=MigrationContractTest"]
        if args.global_settings:
            options += ["-gs", str(args.global_settings)]
        for variant in ("copied", "standalone"):
            app = work / variant
            source = app / "src/main/java/example/standalone"
            test = app / "src/test/java/example/standalone"
            source.mkdir(parents=True)
            test.mkdir(parents=True)
            shutil.copy(FIXTURE / "src/main/java/example/standalone/ConsumerApplication.java", source)
            (test / "MigrationContractTest.java").write_text(HTTP_TEST, encoding="utf-8")
            if variant == "copied":
                (app / "pom.xml").write_text(LEGACY_POM.format(group=escape(args.group),
                                                              version=escape(args.version)), encoding="utf-8")
                java = source / "ConsumerApplication.java"
                java.write_text(java.read_text().replace(
                    "@SpringBootApplication",
                    "@SpringBootApplication\n@org.springframework.context.annotation.Import("
                    "com.notshahrad.spring_boot_base.common.exception.GlobalExceptionHandler.class)"
                ), encoding="utf-8")
            else:
                shutil.copy(FIXTURE / "pom.xml", app)
            subprocess.run(["mvn", "-B", "-ntp", *options, f"-Dbase.group={args.group}",
                            f"-Dbase.version={args.version}", "test"], cwd=app, check=True)
    print("Both application layouts preserved the HTTP contract.")


if __name__ == "__main__":
    main()
