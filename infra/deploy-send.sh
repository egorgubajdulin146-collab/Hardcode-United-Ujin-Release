#!/usr/bin/env bash
set -euo pipefail
umask 077
[[ $# == 2 && $2 =~ ^[0-9a-f]{40}$ ]]
: "${DEPLOY_HOST:?Configure repository variable DEPLOY_HOST}"
: "${DEPLOY_USER:?Configure repository variable DEPLOY_USER}"
: "${DEPLOY_SSH_KEY:?Configure repository secret DEPLOY_SSH_KEY}"
: "${DEPLOY_KNOWN_HOSTS:?Configure verified DEPLOY_KNOWN_HOSTS secret}"
private=$(mktemp -d)
trap 'rm -f -- "$private/key" "$private/known_hosts"; rmdir -- "$private"' EXIT
printf '%s\n' "$DEPLOY_SSH_KEY" > "$private/key"
printf '%s\n' "$DEPLOY_KNOWN_HOSTS" > "$private/known_hosts"
unset DEPLOY_SSH_KEY DEPLOY_KNOWN_HOSTS
ssh -i "$private/key" -o IdentitiesOnly=yes -o BatchMode=yes \
  -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$private/known_hosts" \
  -o ConnectTimeout=15 "$DEPLOY_USER@$DEPLOY_HOST" "deploy $2" < "$1"
