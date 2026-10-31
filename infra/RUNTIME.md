# Локальная сборка компонентов

Это промежуточный запуск frontend + backend + PostgreSQL, не полный продукт.
Backend подключается к PostgreSQL по внутренней сети после healthcheck БД;
Flyway применяет миграции при запуске. Эмуляторы в этот Compose не включены.
Бизнес-сценарий и реальная интеграция Ujin этим запуском не подтверждены.

Требования: Linux/GNU, Bash, Docker/Compose v2, Node22/npm, curl и OpenSSL.
Из корня чистого checkout выбранной ревизии:

```sh
bash infra/build-backend.sh
npm --prefix frontend ci --ignore-scripts --no-audit --no-fund
npm --prefix frontend run build
export UJIN_IMAGE_TAG=$(git rev-parse HEAD)
# Заполните .env локально по .env.example; chmod 600 .env. Не публикуйте значения.
docker compose --project-directory . --env-file .env -f infra/compose.yaml -f infra/compose.runtime.yaml build
docker compose --project-directory . --env-file .env -f infra/compose.yaml -f infra/compose.runtime.yaml up -d --wait
```

UI доступен только на `http://127.0.0.1:8088`, порт можно сменить через
UJIN_HTTP_PORT. API идёт через тот же Nginx /api proxy; backend и БД не имеют
host ports. Для удалённого согласованного стенда используйте SSH-туннель,
не открывайте 8088 наружу. TLS/домен/публичный доступ этим overlay не настроены.

По умолчанию frontend — MOCK, в API-режиме без входа будет ошибка авторизации;
формы входа пока нет. UJIN_API_BASE_URL по умолчанию указывает на localhost
внутри backend, реальных credentials нет. Не направляйте тест на Ujin без
отдельного согласования. Для live app/config передаются закрытым env-файлом.

Backend health исполняет реальный HTTP GET /api/auth/csrf и проверяет 200,
не выводя тело с токеном. Это liveness HTTP/security stack, не готовность
платформы/БД. Статический /healthz подтверждает только Nginx. /actuator/
проксируется в backend, а не в index.html: отсутствующий endpoint не станет 200.

Изолированный smoke после сборки образов (случайный пароль, временная БД,
локальный случайный порт, удаление только собственного проекта/volume):

```sh
bash infra/test-runtime.sh
```

Тест проверяет routes, HTTP, 401, отсутствие ложного Actuator 200, порты,
revision labels и restart. Не проверяет логин/live/эмуляторы/миграции.
Версию работающих контейнеров сверяйте по label с фактическим IMAGE_TAG;
сам тег или merge не доказывают работающий стенд.

Остановка рабочего проекта с сохранением данных: та же compose-команда с
`down` без `--volumes`. Откат: сохраните предыдущие immutable образы/тег,
остановите новый экземпляр и запустите overlay с предыдущим UJIN_IMAGE_TAG
без пересборки. Совместимость схемы/сессий требует отдельного теста;
автоматический downgrade миграций и restore поверх существующей БД запрещены.
Backup/restore PostgreSQL — по infra/README.md, с тем же COMPOSE_PROJECT_NAME.
