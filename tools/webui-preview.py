#!/usr/bin/env python3
"""Local preview for userspace/ksud/assets/web. No npm/vite, no build step.

Usage:
    cd /sdcard/diksu
    python3 tools/webui-preview.py

Open http://127.0.0.1:8765/ on the phone, or http://<host-LAN-IP>:8765/
from another machine. Query helpers:
    ?page=settings|modules|superuser|files|keymint
    ?theme=dark|amoled
    ?page=settings&theme=amoled
"""

import json
import os
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

ROOT = os.environ.get(
    "WEBUI_ROOT", os.path.abspath("userspace/ksud/assets/web")
)
PORT = int(os.environ.get("WEBUI_PORT", "8765"))


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT, **kwargs)

    def _send(self, body: bytes, ctype: str) -> None:
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _json(self, data) -> None:
        self._send(
            json.dumps(data, ensure_ascii=False).encode(),
            "application/json; charset=utf-8",
        )

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path, query = parsed.path, parse_qs(parsed.query)

        if path in ("/", "/index.html"):
            html = open(
                os.path.join(ROOT, "index.html"), encoding="utf-8"
            ).read()
            page = query.get("page", ["home"])[0]
            theme = query.get("theme", [""])[0]
            js = []
            if page == "files":
                js.append("fmOpenFileManager()")
            elif page == "keymint":
                js.append("kmOpen()")
            elif page != "home":
                js.append("switchPage(%r)" % page)
            if theme in ("dark", "amoled"):
                js.append("THEME.mode=%r;THEME.seed='';applyTheme()" % theme)
            if js:
                inject = (
                    "<script>window.addEventListener('load',()=>"
                    "setTimeout(()=>{%s},800));</script>" % ";".join(js)
                )
                html = html.replace("</body>", inject + "</body>")
            self._send(html.encode(), "text/html; charset=utf-8")
            return

        if path == "/ui/app.css":
            self._send(
                open(os.path.join(ROOT, "app.css"), "rb").read(),
                "text/css; charset=utf-8",
            )
            return
        if path == "/ui/app.js":
            self._send(
                open(os.path.join(ROOT, "app.js"), "rb").read(),
                "application/javascript; charset=utf-8",
            )
            return
        if path.startswith("/api/"):
            self._json({"success": True, "data": {}})
            return
        super().do_GET()

    def do_POST(self) -> None:
        if self.path.startswith("/api/"):
            length = int(self.headers.get("Content-Length", 0) or 0)
            if length:
                self.rfile.read(length)
            self._json({"success": True, "data": {}})
            return
        self.send_error(405)

    def log_message(self, fmt, *args) -> None:
        print("%s - %s" % (self.address_string(), fmt % args))


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print("WebUI preview: http://127.0.0.1:%d/" % PORT)
    print("  ?page=settings|modules|superuser|files|keymint")
    print("  ?theme=dark|amoled")
    server.serve_forever()
