"""Serve the generator UI and download API on a local HTTP listener."""

import argparse
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from urllib.parse import urlsplit

from project_generator import CATALOG, HERE, project_zip, release_version

MAX_REQUEST = 16_384


def handler_for(version):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            path = urlsplit(self.path).path
            if path == "/":
                return self.send_content(HTTPStatus.OK, "text/html; charset=utf-8", (HERE / "index.html").read_bytes())
            if path == "/catalog":
                return self.send_json(HTTPStatus.OK, {**CATALOG, "baseVersion": version})
            self.send_json(HTTPStatus.NOT_FOUND, {"error": "Page not found."})

        def do_POST(self):
            if urlsplit(self.path).path != "/project":
                return self.send_json(HTTPStatus.NOT_FOUND, {"error": "Endpoint not found."})
            if self.headers.get_content_type() != "application/json":
                return self.send_json(HTTPStatus.UNSUPPORTED_MEDIA_TYPE, {"error": "Send JSON project options."})
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                length = 0
            if length < 1 or length > MAX_REQUEST:
                self.close_connection = True
                return self.send_json(HTTPStatus.BAD_REQUEST, {"error": "Invalid project request length."})
            self.connection.settimeout(10)
            try:
                options = json.loads(self.rfile.read(length))
                filename, contents = project_zip(options, version)
            except (ValueError, UnicodeError, RecursionError, TimeoutError) as exc:
                message = str(exc) if type(exc) is ValueError else "Invalid project request."
                return self.send_json(HTTPStatus.BAD_REQUEST, {"error": message})
            self.send_content(HTTPStatus.OK, "application/zip", contents, filename)

        def send_json(self, status, value):
            self.send_content(status, "application/json; charset=utf-8", json.dumps(value).encode())

        def send_content(self, status, mime, contents, filename=None):
            self.send_response(status)
            self.send_header("Content-Type", mime)
            self.send_header("Content-Length", str(len(contents)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            if filename:
                self.send_header("Content-Disposition", f'attachment; filename="{filename}"')
            self.end_headers()
            self.wfile.write(contents)

    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-version", required=True)
    parser.add_argument("--port", type=int, default=8080)
    args = parser.parse_args()
    try:
        version = release_version(args.base_version)
    except ValueError as exc:
        parser.error(str(exc))
    with ThreadingHTTPServer(("127.0.0.1", args.port), handler_for(version)) as server:
        print(f"Base generator listening on http://127.0.0.1:{server.server_port}", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
