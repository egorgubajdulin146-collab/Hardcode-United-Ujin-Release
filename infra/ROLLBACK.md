# Резервный релиз с изоляцией аккаунтов

Откат приложения не откатывает Flyway и не восстанавливает БД автоматически.
Нельзя возвращать версии до исправления изоляции пользователей: они могут
раскрыть чужие устройства, сценарии, операции и журнал.

## Проверенная граница

[Manifest принятой пары образов](rollback-baseline.json) содержит точные imageID.
Повторная сборка той же ревизии не заменяет принятый образ: если ID отличается,
повторите приёмку и выпустите новый manifest.

Текущая базовая версия: `6e7b2a77936c4269cc4d4fed646c5bf403ee7d33`.
Резерв: `e5fea660aaef4b02a1bf46e650441814af2a0f43`, исправленный head
изоляции до merge. Оба дерева исходников совпадают, оба применяют V1–V3 и
защищают owner-scoped данные и глобальные eventId/commandId.

Это резерв для повреждённого образа/неудачного развёртывания. Он не исправляет
общие ошибки кода обеих версий, в том числе повтор CHANGE при новом eventId.
`70ecac6` и более старые версии не входят в список безопасного отката.
Совместимость любой будущей миграции после V3 требует нового прогона.

Принятые образы сохранены на VPS в закрытом каталоге
`/var/backups/ujin/releases/e5fea660aaef4b02a1bf46e650441814af2a0f43`:
images.tar.gz, images.sha256, manifest.json и compose.tar.gz. Каталог700,
файлы600. Compose snapshot не содержит env/credentials. Не удаляйте резерв
автоматической очисткой образов или release directories.

Если принятых образов больше нет в Docker, восстановите архив до шага up:

```sh
cd /var/backups/ujin/releases/e5fea660aaef4b02a1bf46e650441814af2a0f43
sha256sum -c images.sha256
gzip -t images.tar.gz
gzip -dc images.tar.gz | docker load
```

Затем сверяйте imageID с manifest. Подмена тега другой сборкой не допускается.

Для каждого резерва сохраняйте полные SHA, imageID frontend/backend,
образный архив с SHA256, конфигурацию Compose той же версии и закрытый
backup БД. Теги не являются неизменяемыми идентификаторами: перед up
сверяйте imageID с приёмочным manifest.

## Действия оператора

1. Остановите конкурирующий deploy: используйте тот же `/run/lock/ujin-deploy.lock`,
   который держит receiver. Не отменяйте активный процесс посередине миграции.
2. Зафиксируйте текущий SHA и образные ID. Сделайте backup по инструкции
   PostgreSQL. Проверьте свободное место и наличие точной принятой пары образов.
3. Проверьте, что схема совместима с резервом. Если есть новая непроверенная
   миграция или нет владельцев данных, остановитесь: downgrade и массовое
   присвоение NULL-owner не являются rollback.
4. Укажите существующие release directory, закрытый env и точное Compose project
   name. Project name должен совпасть с рабочим: иначе получится пустая БД.
   Не выводите env/секреты и не меняйте PostgreSQL image/volume.
5. Выполните config, up без сборки и проверку. Пример для текущей серверной схемы:

```sh
# Выполнять в отдельной административной shell-сессии после проверки шагов 1–4.
exec 9>/run/lock/ujin-deploy.lock
flock -w 600 9
export UJIN_IMAGE_TAG=e5fea660aaef4b02a1bf46e650441814af2a0f43
release=/opt/ujin/releases/6e7b2a77936c4269cc4d4fed646c5bf403ee7d33
cd "$release"
compose=(docker compose --project-directory "$release" --env-file /etc/ujin/local.env
  -f infra/compose.yaml -f infra/compose.runtime.yaml -f infra/compose.catalog.yaml)
"${compose[@]}" config --quiet
docker image inspect "ujin-backend:$UJIN_IMAGE_TAG" "ujin-frontend:$UJIN_IMAGE_TAG" \
  --format '{{.Id}} {{index .Config.Labels "org.opencontainers.image.revision"}}'
# Сравните обе строки с принятым manifest. При несовпадении не продолжайте.
"${compose[@]}" up -d --no-build --wait --wait-timeout 180
"${compose[@]}" ps
binding=$("${compose[@]}" port frontend 8080)
python3 /usr/local/lib/ujin/check-static.py "http://$binding"
curl --fail --silent --show-error --output /dev/null "http://$binding/api/auth/csrf"
```

Shell-пример относится только к указанной паре и совместимым Compose-файлам.
Перед действием сверяйте реальные пути и проект. Не запускайте старый receiver
повторно для rollback: он предназначен для нового release directory.

6. Войдите заново двумя тестовыми аккаунтами. Проверьте свои списки и каталог,
   отсутствие чужих UUID в списках/runs и отказ GET чужой карточки; затем один
   LOCAL-сценарий и сохранность истории. Healthy без A/B-проверки недостаточно.
7. Запишите фактически запущенный SHA отдельно от `/opt/ujin/current`.
   При ручном rollback symlink может указывать на последний успешный deploy,
   а не на активные образы. Не выдавайте его за runtime SHA.
8. Вернитесь на исходные принятые образы той же процедурой, когда причина
   устранена. Не пересобирайте старый тег на месте и не запускайте image prune.

Восстановление backup при несовместимой схеме — отдельная операция с проверкой
потери новых данных, простоя и новой БД. Restore поверх существующей БД запрещён.
