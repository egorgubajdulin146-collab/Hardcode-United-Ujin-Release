#!/usr/bin/env bash
# Install root-owned outside release directories; invoke only via a forced SSH key.
set -euo pipefail
umask 077
[[ ${SSH_ORIGINAL_COMMAND:-} =~ ^deploy\ ([0-9a-f]{40})$ ]]
revision=${BASH_REMATCH[1]}
exec 9>/run/lock/ujin-deploy.lock
flock -w 600 9
release=/opt/ujin/releases/$revision
[[ ! -e "$release" ]]
install -d -m 700 "$release" /var/backups/ujin
cat > "$release/artifact.tar.gz"
# Reject traversal, links, devices and files outside the deployment allowlist.
python3 - "$release/artifact.tar.gz" "$release" <<'PY'
import sys,tarfile,pathlib
allowed={'.dockerignore','backend/ujin/Dockerfile','frontend/Dockerfile',
 'infra/nginx.conf','infra/backend-health.sh','infra/compose.yaml',
 'infra/compose.runtime.yaml','infra/compose.catalog.yaml','infra/check-static.py'}
with tarfile.open(sys.argv[1]) as archive:
    for item in archive.getmembers():
        path=pathlib.PurePosixPath(item.name)
        if path.is_absolute() or '..' in path.parts or not (item.isfile() or item.isdir()):
            raise SystemExit('Unsafe release archive')
        name=str(path)
        if item.isfile() and not (name in allowed or name.startswith('frontend/dist/') or
                (name.startswith('backend/ujin/build/libs/') and name.endswith('.jar'))):
            raise SystemExit('Unexpected release file')
    archive.extractall(sys.argv[2],filter='data')
PY
cd "$release"
export UJIN_IMAGE_TAG=$revision
compose=(docker compose --project-directory "$release" --env-file /etc/ujin/local.env
  -f infra/compose.yaml -f infra/compose.runtime.yaml -f infra/compose.catalog.yaml)
"${compose[@]}" config --quiet
"${compose[@]}" build
# Dump before any migrations, without exposing credentials or deleting the volume.
# shellcheck disable=SC2016
"${compose[@]}" exec -T postgres sh -eu -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' </dev/null \
  > "/var/backups/ujin/before-$revision.dump"
test -s "/var/backups/ujin/before-$revision.dump"
"${compose[@]}" up -d --wait --wait-timeout 180
binding=$("${compose[@]}" port frontend 8080)
[[ "$binding" =~ ^127\.0\.0\.1:[0-9]+$ ]]
base_url=http://$binding
python3 infra/check-static.py "$base_url"
curl --connect-timeout 5 --max-time 20 --fail --silent --show-error \
  "$base_url/api/auth/csrf" >/dev/null
[[ $(curl --connect-timeout 5 --max-time 20 --silent --show-error \
  --output /dev/null --write-out '%{http_code}' "$base_url/api/v1/devices") == 401 ]]
for service in backend frontend; do
  container=$("${compose[@]}" ps -q "$service")
  actual=$(docker inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$container")
  [[ "$actual" == "$revision" ]]
done
# Publish the successful release pointer only after health and revision checks.
ln -s "$release" /opt/ujin/current.next
mv -Tf /opt/ujin/current.next /opt/ujin/current
printf 'Deployed %s; schema rollback is manual; backups retained\n' "$revision"
