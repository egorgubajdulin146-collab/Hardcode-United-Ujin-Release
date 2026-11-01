import importlib.util
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

spec = importlib.util.spec_from_file_location('static_check', Path(__file__).parents[1] / 'check-static.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class StaticTests(unittest.TestCase):
    def probe(self, broken=False):
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_GET(self):
                self.send_response(200)
                if self.path == '/' or broken:
                    mime, body = 'text/html', b'<!doctype html><script src="/assets/app.js"></script><link rel="stylesheet" href="/assets/app.css">'
                elif self.path.endswith('.js'):
                    mime, body = 'text/javascript', b'console.log(1)'
                else:
                    mime, body = 'text/css', b'body{}'
                self.send_header('Content-Type', mime)
                self.end_headers()
                self.wfile.write(body)
        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            module.check('http://127.0.0.1:' + str(server.server_port))
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_valid_assets(self):
        self.probe()

    def test_html_fallback_rejected(self):
        with self.assertRaises(ValueError):
            self.probe(True)
