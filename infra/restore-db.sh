#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 2 ]]; then
  echo "Usage: bash infra/restore-db.sh backup.dump new_database_name" >&2
  exit 2
fi

backup_path=$1
target_db=$2
if [[ ! -f "$backup_path" || ! -r "$backup_path" || ! -s "$backup_path" ]]; then
  echo "Backup must be a readable, nonempty regular file" >&2
  exit 1
fi
if [[ ! "$target_db" =~ ^[a-z_][a-z0-9_]{0,62}$ ]]; then
  echo "Use 1–63 lowercase letters, digits or underscores; start with a letter or underscore" >&2
  exit 2
fi

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
compose=(docker compose --project-directory "$repo_root"
  --env-file "${UJIN_ENV_FILE:-$repo_root/.env}"
  -f "$repo_root/infra/compose.yaml")

"${compose[@]}" exec -T postgres pg_restore --list < "$backup_path" > /dev/null
# These variables are expanded inside the PostgreSQL container.
# shellcheck disable=SC2016
"${compose[@]}" exec -T -e RESTORE_DB="$target_db" postgres sh -eu -c '
  createdb --username="$POSTGRES_USER" -- "$RESTORE_DB"
  pg_restore --username="$POSTGRES_USER" --dbname="$RESTORE_DB" \
    --no-owner --no-privileges --exit-on-error --single-transaction
' < "$backup_path"
printf 'Restored into new database: %s\n' "$target_db"
