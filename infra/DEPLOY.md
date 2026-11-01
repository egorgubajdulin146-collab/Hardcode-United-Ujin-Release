# Automatic deployment

After merge into develop, CI tests backend, frontend and infrastructure, then
deploys the exact tested artifact. PRs and main never deploy. New pushes do not
cancel an active deployment; superseded revisions are skipped before SSH.

Repository variables: DEPLOY_HOST, DEPLOY_USER. Secrets: DEPLOY_SSH_KEY and
DEPLOY_KNOWN_HOSTS (host key independently verified, never trust ssh-keyscan alone).
Use a dedicated key restricted in authorized_keys:

```text
restrict,command="/usr/local/sbin/ujin-deploy-receive" ssh-ed25519 PUBLIC_KEY
```

Install infra/deploy-receive.sh root-owned mode700 at that exact path. This key
can deploy trusted develop artifacts only; it cannot provide an interactive
shell, tunnel or arbitrary SSH command. Docker builds still execute trusted
repository instructions with host privileges: protect develop and review PRs.
Never expose this key to PR jobs or fork code.

Server prerequisites: Linux/GNU, Bash, Python3.12+, Docker/Compose, flock;
running PostgreSQL under the existing project. /etc/ujin/local.env mode600 must
hold the existing COMPOSE_PROJECT_NAME, DB credentials and live configuration.
Do not create a new project/volume on every release. Images and directories
are immutable by SHA; retry of a previously attempted SHA fails closed and
requires an operator to inspect it. No pruning is automatic.

Each deployment builds before downtime, saves a private DB dump, starts the
three Compose files, verifies HTTP/anonymous401 and revision labels, then
updates /opt/ujin/current. Backups remain /var/backups/ujin mode600.
On failure: do not claim success; inspect service health without printing
environment/tokens. A failed migration may have changed the schema. Select
the previous compatible release and apply its three compose files using the
same env/project with UJIN_IMAGE_TAG set to that release SHA. Restore only
into a new database; switch DB only after verification. Never down --volumes.
Automatic DB rollback is intentionally not performed.

Bootstrap is an operator action; adding this workflow does not prove a real
Actions deployment. Record the first successful Actions run and runtime SHA
after review/merge. Existing manual frontend preview overlays must not be
used by automatic deployment.
