# Frontend, backend и PostgreSQL

[README](../README.md) содержит команды сборки, настройки и первого LOCAL-сценария.
Используйте infra/compose.yaml вместе с infra/compose.runtime.yaml.
Backend подключён к PostgreSQL, Flyway применяет миграции.
LOCAL-эмуляторы исполняются внутри backend, отдельных Python-контейнеров
для этого примера не требуется.

API — источник по умолчанию, есть форма входа. Демо/MOCK включается явно.
При первом запуске нужны интернет и выданная учётная запись Ujin.
Реальные credentials остаются вне Git.

## Проверка стенда

```sh
export UJIN_IMAGE_TAG=$(git rev-parse HEAD)
docker compose --project-directory . --env-file .env -f infra/compose.yaml -f infra/compose.runtime.yaml ps
docker image inspect "ujin-backend:$UJIN_IMAGE_TAG" "ujin-frontend:$UJIN_IMAGE_TAG" --format '{{.Id}} {{index .Config.Labels "org.opencontainers.image.revision"}}'
curl --fail --silent --show-error http://127.0.0.1:8088/healthz
curl --silent --output /dev/null --write-out '%{http_code}\n' http://127.0.0.1:8088/api/v1/devices
python3 infra/check-static.py http://127.0.0.1:8088
```

При другом UJIN_HTTP_PORT замените порт. До входа каталог возвращает401.
Не выводите тело /api/auth/csrf, cookies или полный env: это секреты.
Тег сам по себе не доказывает версию: сверяйте OCI revision и imageID.
Frontend /healthz проверяет Nginx, backend health запрашивает /api/auth/csrf.
Проверка JS/CSS отдельно выявляет пустой экран при HTML200.

```sh
bash infra/test-runtime.sh
```

Smoke запускает отдельный проект/БД. Браузерная логика и Ujin проверяются отдельно.
Данные переживают restart и down без --volumes. После restart backend нужен login.
[Backup/restore](README.md), [безопасный откат](ROLLBACK.md).

Compose публикует только loopback. Для публичного стенда нужны host Nginx/TLS.
Автодеплой через ограниченный SSH-ключ принимает проверенный артефакт,
делает backup до миграций и проверяет health/assets/labels. Автоматического
downgrade схемы или отката приложения он не выполняет.
