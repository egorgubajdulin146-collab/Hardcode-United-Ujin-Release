#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
export COMPOSE_PROJECT_NAME="ujin-runtime-test-$$-$RANDOM"
export UJIN_IMAGE_TAG=${UJIN_IMAGE_TAG:?Build and identify the tested revision first}
export UJIN_HTTP_PORT=0
export POSTGRES_PASSWORD
POSTGRES_PASSWORD=$(openssl rand -hex 32)
export UJIN_API_BASE_URL=https://localhost/api
export UJIN_AUTH_APP=
compose=(docker compose --project-directory "$repo_root" --env-file /dev/null
  -f "$repo_root/infra/compose.yaml" -f "$repo_root/infra/compose.runtime.yaml"
  -f "$repo_root/infra/compose.catalog.yaml")
cleanup() { "${compose[@]}" down --volumes --remove-orphans > /dev/null; }
trap cleanup EXIT
"${compose[@]}" up -d --wait --wait-timeout 150
binding=$("${compose[@]}" port frontend 8080)
[[ "$binding" == 127.0.0.1:* ]]
base_url="http://$binding"
python3 "$repo_root/infra/check-static.py" "$base_url"
for route in / /devices /scenarios /runs; do
  curl --fail --silent --show-error "$base_url$route" | grep -q '<div id="root">'
done
# CSRF creates a token; consume it without printing or saving it.
curl --fail --silent --show-error "$base_url/api/auth/csrf" > /dev/null
[[ $(curl --silent --output /dev/null --write-out '%{http_code}' "$base_url/api/v1/runs") == 401 ]]
# The missing Actuator route must not become SPA HTML success.
[[ $(curl --silent --output /dev/null --write-out '%{http_code}' "$base_url/actuator/health") != 200 ]]
for service in backend postgres; do
  container_id=$("${compose[@]}" ps -q "$service")
  bindings=$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$container_id")
  [[ "$bindings" == '{}' || "$bindings" == null ]]
done
for service in backend frontend; do
  container_id=$("${compose[@]}" ps -q "$service")
  revision=$(docker inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$container_id")
  [[ "$revision" == "$UJIN_IMAGE_TAG" ]]
done
"${compose[@]}" restart backend
"${compose[@]}" up -d --wait --wait-timeout 150
curl --fail --silent --show-error "$base_url/api/auth/csrf" > /dev/null
echo "Runtime: SPA routes, HTTP liveness, anonymous denial, private ports, revision and restart PASS"
