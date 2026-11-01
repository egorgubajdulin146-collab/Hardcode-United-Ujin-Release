"""Private HTTP bridge for the supplied Python models. No Ujin network client."""
import json
import math
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

from devices import REGISTRY

family = os.environ.get("EMULATOR_FAMILY", "leak")
if family not in ("leak", "light"):
    raise ValueError("Only leak and light are supported by this bridge")
device = REGISTRY[family](serial=os.environ.get("EMULATOR_SERIAL", "local"), client=None, logger=lambda _: None)
lock = threading.Lock()


def state():
    return {"family": family, "serial": device.serial, "executionMode": "LOCAL",
            "values": {name: signal.value for name, signal in device.signals.items()}}


class Handler(BaseHTTPRequestHandler):
    def reply(self, status, body):
        data = json.dumps(body, ensure_ascii=False, allow_nan=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = urlsplit(self.path).path
        with lock:
            if path == "/health":
                return self.reply(200, {"status": "UP", "executionMode": "LOCAL"})
            if path == "/state":
                return self.reply(200, state())
            if path == "/declaration":
                return self.reply(200, {"family": family, "signals": device.declaration()})
        self.reply(404, {"code": "NOT_FOUND"})

    def do_POST(self):
        path = urlsplit(self.path).path
        if path not in ("/stimuli", "/command"):
            return self.reply(404, {"code": "NOT_FOUND"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 4096:
                return self.reply(400, {"code": "INVALID_BODY"})
            body = json.loads(self.rfile.read(length))
            if not isinstance(body, dict):
                return self.reply(400, {"code": "INVALID_BODY"})
            value = body.get("value")
            if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
                return self.reply(422, {"code": "INVALID_VALUE"})
            with lock:
                if path == "/stimuli":
                    if family != "leak" or body.get("kind") != "WET" or value not in (0, 1):
                        return self.reply(422, {"code": "STIMULUS_UNSUPPORTED"})
                    device.set_wet(bool(value))
                    device.step(0)
                else:
                    key = body.get("signalKey")
                    if not isinstance(key, str) or key != "lamp" or family != "light" or value not in (0, 1):
                        return self.reply(422, {"code": "COMMAND_UNSUPPORTED"})
                    device.set_value(key, value)
                    device.on_command(key, value)
                    device.step(0)
                self.reply(200, state())
        except (ValueError, TypeError, OverflowError):
            self.reply(400, {"code": "INVALID_BODY"})

    def log_message(self, *_):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
