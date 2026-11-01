"""Explicit offline HTTP acceptance; resets only this pair to leak0/lamp0."""
import json
import os
import urllib.error
import urllib.request

leak = os.environ.get("LEAK_URL", "http://127.0.0.1:18191")
light = os.environ.get("LIGHT_URL", "http://127.0.0.1:18192")


def call(base, path, data=None, expected=200):
    payload = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(base + path, data=payload, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=3) as response:
            code, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        code, raw = error.code, error.read()
    assert code == expected, (path, code, expected)
    return json.loads(raw)


for base in (leak, light):
    assert call(base, "/health")["status"] == "UP"
    assert call(base, "/declaration")["signals"]
    call(base, "/missing", expected=404)
for value in (1, 1, 0):
    assert call(leak, "/stimuli", {"signalKey": "leak", "value": value})["values"]["leak"] == value
    assert call(leak, "/state")["values"]["leak"] == value
    assert call(light, "/command", {"signalKey": "lamp", "value": value})["values"]["lamp"] == value
    assert call(light, "/state")["values"]["lamp"] == value
for value in (-1, 2, True, "1"):
    call(leak, "/stimuli", {"signalKey": "leak", "value": value}, 422)
    call(light, "/command", {"signalKey": "lamp", "value": value}, 422)
call(leak, "/command", {"signalKey": "leak", "value": 1}, 422)
call(light, "/stimuli", {"signalKey": "leak", "value": 1}, 422)
call(leak, "/stimuli", {"signalKey": "lamp", "value": 1}, 422)
call(leak, "/stimuli", {"kind": "WET", "value": 1}, 422)
assert call(leak, "/state")["values"]["leak"] == 0
assert call(light, "/state")["values"]["lamp"] == 0
print("PASS: health, declarations, leak/lamp1→1→0, readback, domain errors, old WET rejected, read-only sensor; reset0/0")
