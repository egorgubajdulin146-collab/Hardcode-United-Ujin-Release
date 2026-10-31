#!/usr/bin/env bash
set -euo pipefail
umask 077
# Supported host: Linux with Bash and GNU coreutils; see infra/README.md.

if [[ $# != 1 ]]; then
  echo "Usage: bash infra/backup-db.sh /private/path/backup.dump" >&2
  exit 2
fi

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
backup_path=$1
if [[ -e "$backup_path" || -L "$backup_path" ]]; then
  echo "Backup path already exists; refusing to overwrite it" >&2
  exit 1
fi

compose=(docker compose --project-directory "$repo_root"
  --env-file "${UJIN_ENV_FILE:-$repo_root/.env}"
  -f "$repo_root/infra/compose.yaml")

tmp_file=$(mktemp -- "${backup_path}.partial.XXXXXX")
trap 'rm -f -- "$tmp_file"' EXIT
# These variables are expanded inside the PostgreSQL container.
# shellcheck disable=SC2016
"${compose[@]}" exec -T postgres sh -eu -c \
  'pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom' > "$tmp_file"
"${compose[@]}" exec -T postgres pg_restore --list < "$tmp_file" > /dev/null

# An atomic link fails if another backup appeared at the destination meanwhile.
ln -T -- "$tmp_file" "$backup_path"
printf 'Backup written: %s\n' "$backup_path"
