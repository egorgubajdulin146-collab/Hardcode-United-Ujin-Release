# Резервный релиз с изоляцией аккаунтов

Откат приложения не откатывает Flyway и не восстанавливает БД автоматически.
Нельзя возвращать версии до исправления изоляции пользователей: они могут
раскрыть чужие устройства, сценарии, операции и журнал.

## Проверенная граница

### Актуальная граница V9 — 01.11.2026

Эта приёмка проверяет образы на ревизии `24129a5188a5c722f0e55c96dd09e8d25d2feedf`.
После неё `develop` продвинулся до `82c6407fa7a8d74b6de822fd5582260078d250d9`
(UI #39 и сохранение операторского Python overlay при deploy, #40). Результаты
ниже не подтверждают откат новой ревизии. Перед реальным переключением сверяйте
активную ревизию, схему БД и операторские overlay; команды ниже привязаны к
точному релизу `24129a5`. Для `82c6407` отдельная приёмка отката не выполнялась.

Проверенный в этой приёмке релиз 24129a5188a5c722f0e55c96dd09e8d25d2feedf,
резерв 30f866a1469e692c9ab2b54db9e5cd2bd182fa48. Рабочая схема — V1–V9;
резерв содержит миграции только V1–V7, но проверяется на сохранённой схеме V9.
Проверка проведена в отдельном Compose на восстановленной копии PostgreSQL17:
restart БД/backend, current→reserve→current, A/B-запреты, квитанции, state,
UNKNOWN publication, история task и fingerprint всех 20 public-таблиц
и значений sequences. Включены строки V4/V5 и наблюдения/порядок V8/V9.
Рабочие контейнеры этой проверкой не переключались.

Резерв не содержит гостевого демо, внешнего Python-адаптера и нового
наблюдения сценариев Ujin: при аварийном
откате эти функции недоступны. Закройте гостевой вход, потребуйте повторный
login и зафиксируйте ограничение для команды. Не обещайте полный набор функций
нового релиза. Схема и owner-изоляция сохраняются, downgrade Flyway не нужен.

Архив проверенных образов и compose-снимок без env находятся в
`/var/backups/ujin/releases/30f866a1469e692c9ab2b54db9e5cd2bd182fa48`:
images.tar.gz, images.sha256, compose.tar.gz, manifest.json. Каталог700/файлы600.
Архив SHA256 и IDs указаны в rollback-baseline.json. Сохраните отдельный backup
рабочей БД перед каждым реальным переключением. Новая миграция V10+ требует
новой проверки; резерв e5fea66 для V9 не принят. Не выполнять Flyway clean,
repair или downgrade ради запуска старого приложения.

Используйте операторские шаги ниже с изменениями: UJIN_IMAGE_TAG=30f866a1469e692c9ab2b54db9e5cd2bd182fa48,
release=/opt/ujin/releases/24129a5188a5c722f0e55c96dd09e8d25d2feedf,
архивный каталог30f866a. Подготовьте и проверьте эти значения до выполнения
команд; старые буквальные команды V3 ниже для текущего V9 запускать нельзя.
Сохраните реальные флаги и дополнительные overlay, восстановите их при возврате
на current. При выключении демо через UJIN_DEMO_ENABLED=false верните прежнее
значение перед up текущего релиза. Автодеплой и rollback используют один lock.

Пример V9 после backup и сверки схемы/флагов:

```bash
exec 9>/run/lock/ujin-deploy.lock
flock -w 600 9
reserve=/var/backups/ujin/releases/30f866a1469e692c9ab2b54db9e5cd2bd182fa48
cd "$reserve"
sha256sum -c images.sha256
# Если принятых образов нет: gzip -dc images.tar.gz | docker load
export UJIN_IMAGE_TAG=30f866a1469e692c9ab2b54db9e5cd2bd182fa48
export UJIN_DEMO_ENABLED=false
release=/opt/ujin/releases/24129a5188a5c722f0e55c96dd09e8d25d2feedf
cd "$release"
compose=(docker compose -p ujin-closed --project-directory "$release" --env-file /etc/ujin/local.env
  -f infra/compose.yaml -f infra/compose.runtime.yaml -f infra/compose.catalog.yaml)
"${compose[@]}" config --quiet
docker image inspect "ujin-backend:$UJIN_IMAGE_TAG" "ujin-frontend:$UJIN_IMAGE_TAG" \
  --format '{{.Id}} {{index .Config.Labels "org.opencontainers.image.revision"}}'
# Сверить обе строки с manifest; при несовпадении остановиться.
"${compose[@]}" up -d --no-build --wait --wait-timeout 180
python3 /usr/local/lib/ujin/check-static.py http://127.0.0.1:8088
```

После этого выполните A/B и LOCAL-проверки из операторских шагов6–7 ниже.
Возврат на current — с полным тегом 24129a5 и восстановленными исходными флагами.
Compose project должен оставаться рабочим `ujin-closed`; до up сверить его
с текущими container labels. Не использовать disposable project из QA.

Предварительная проверка V7 на c0dc866 сохраняется как история HTTP-проверок.
В её harness обнаружен неполный обход таблиц; старый fingerprint не является
доказательством сохранности всей БД. Текущая проверка V9 использует исправленный
обход с контролем количества таблиц и отдельной проверкой sequences.

### Историческая граница V3 — не применять к текущей V9

[Manifest принятой пары образов](rollback-baseline.json) содержит точные imageID.
Повторная сборка той же ревизии не заменяет принятый образ: если ID отличается,
повторите приёмку и выпустите новый manifest.
Поля backend_image_id/frontend_image_id относятся к containerd на VPS.
Classic Docker показывает configID в полях *_classic_docker_image_id.
После загрузки архива Config и RootFS слои сверены полностью и совпадают.

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
