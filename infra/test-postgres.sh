#!/usr/bin/env bash
set -euo pipefail
# Supported host: Linux with Bash, GNU coreutils and OpenSSL.

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
export COMPOSE_PROJECT_NAME="ujin-p0-test-$$-$RANDOM"
export UJIN_ENV_FILE=/dev/null
export POSTGRES_DB=ujin
export POSTGRES_USER=ujin
POSTGRES_PASSWORD=$(openssl rand -hex 32)
export POSTGRES_PASSWORD
compose=(docker compose --project-directory "$repo_root" --env-file /dev/null
  -f "$repo_root/infra/compose.yaml")
tmp_dir=$(mktemp -d)

cleanup() {
  "${compose[@]}" down --volumes --remove-orphans > /dev/null
  rm -f -- "$tmp_dir/probe.dump" "$tmp_dir/race.dump"
  rmdir -- "$tmp_dir"
}
trap cleanup EXIT

if (unset POSTGRES_PASSWORD; "${compose[@]}" config --quiet > /dev/null 2>&1); then
  echo "Missing password was unexpectedly accepted" >&2
  exit 1
fi

"${compose[@]}" up -d --wait --wait-timeout 90 postgres
container_id=$("${compose[@]}" ps -q postgres)
bindings=$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$container_id")
if [[ "$bindings" != '{}' && "$bindings" != 'null' ]]; then
  echo "PostgreSQL unexpectedly publishes host ports" >&2
  exit 1
fi

# These variables are expanded inside the PostgreSQL container.
# shellcheck disable=SC2016
"${compose[@]}" exec -T postgres sh -eu -c \
  'psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --set=ON_ERROR_STOP=1' <<'SQL'
CREATE TABLE ops_probe (id integer PRIMARY KEY, payload text NOT NULL);
INSERT INTO ops_probe VALUES (1, 'alpha'), (2, 'beta');
SQL

check_data() {
  local actual
  # Variables and SQL run inside the PostgreSQL container.
  # shellcheck disable=SC2016
  actual=$("${compose[@]}" exec -T -e CHECK_DB="$1" postgres sh -eu -c \
    'psql --username="$POSTGRES_USER" --dbname="$CHECK_DB" --tuples-only --no-align \
      --set=ON_ERROR_STOP=1 --command="SELECT id, payload FROM ops_probe ORDER BY id"')
  [[ "$actual" == $'1|alpha\n2|beta' ]]
}

check_data ujin
"${compose[@]}" restart postgres
"${compose[@]}" up -d --wait --wait-timeout 90 postgres
check_data ujin
bash "$repo_root/infra/backup-db.sh" "$tmp_dir/probe.dump"
[[ $(stat -c '%a' "$tmp_dir/probe.dump") == 600 ]]
backup_checksum=$(sha256sum "$tmp_dir/probe.dump")
if bash "$repo_root/infra/backup-db.sh" "$tmp_dir/probe.dump" > /dev/null 2>&1; then
  echo "An existing backup was unexpectedly overwritten" >&2
  exit 1
fi
[[ $(sha256sum "$tmp_dir/probe.dump") == "$backup_checksum" ]]
bash "$repo_root/infra/restore-db.sh" "$tmp_dir/probe.dump" ujin_restore_check
check_data ujin_restore_check
if bash "$repo_root/infra/restore-db.sh" "$tmp_dir/probe.dump" ujin > /dev/null 2>&1; then
  echo "An existing database was unexpectedly accepted as a restore target" >&2
  exit 1
fi
check_data ujin
if bash "$repo_root/infra/restore-db.sh" "$tmp_dir/probe.dump" bad-name > /dev/null 2>&1; then
  echo "An invalid database name was unexpectedly accepted" >&2
  exit 1
fi
# Both writers target the same new path. Publication must be no-clobber even
# if both pass backup-db.sh's initial existence check before either finishes.
bash "$repo_root/infra/backup-db.sh" "$tmp_dir/race.dump" > /dev/null 2>&1 &
first_writer=$!
bash "$repo_root/infra/backup-db.sh" "$tmp_dir/race.dump" > /dev/null 2>&1 &
second_writer=$!
successes=0
if wait "$first_writer"; then successes=$((successes + 1)); fi
if wait "$second_writer"; then successes=$((successes + 1)); fi
[[ "$successes" == 1 ]]
[[ $(stat -c '%a' "$tmp_dir/race.dump") == 600 ]]
bash "$repo_root/infra/restore-db.sh" "$tmp_dir/race.dump" ujin_race_check
check_data ujin_race_check
echo "PostgreSQL: health, private ports, restart, backup/restore, concurrent backup and negative checks PASS"
