#!/usr/bin/env bash
set -euo pipefail
# HTTP liveness only: verifies the existing anonymous CSRF route, not Ujin/DB readiness.
# The response body contains a fresh CSRF token and must not be printed or logged.
exec 3<>/dev/tcp/127.0.0.1/8080
printf 'GET /api/auth/csrf HTTP/1.0\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
read -r -t 3 status <&3
[[ "$status" == 'HTTP/1.1 200 '* || "$status" == 'HTTP/1.0 200 '* ]]
