#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cache_dir=${UJIN_GRADLE_CACHE:-"$repo_root/work/gradle"}
mkdir -p -- "$cache_dir"
cache_dir=$(cd -- "$cache_dir" && pwd)

docker run --rm \
  --user "$(id -u):$(id -g)" \
  --cpus 2 --memory 1536m --pids-limit 512 \
  --cap-drop ALL --security-opt no-new-privileges \
  --env GRADLE_USER_HOME=/gradle-cache \
  --mount "type=bind,source=$repo_root/backend/ujin,target=/workspace" \
  --mount "type=bind,source=$cache_dir,target=/gradle-cache" \
  --workdir /workspace \
  eclipse-temurin:25-jdk@sha256:83f4a7fc27df94dcc7b8de04d14cbbba8da14406f921e966969c3b09000aa507 \
  bash ./gradlew --no-daemon --max-workers=2 --console=plain clean test bootJar
