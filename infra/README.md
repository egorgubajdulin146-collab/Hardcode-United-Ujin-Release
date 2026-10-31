# Инфраструктура

Подготовлены сборка Java 25 backend, runtime-образ, отдельный PostgreSQL 17,
backup/restore и CI. Это инфраструктура текущего каркаса: frontend, эмуляторы,
миграции и HTTP/Actuator ещё нужно реализовать и подключить. Текущий backend
запускается и завершается, поэтому проверенный полный стенд пока не заявлен.

## Backend

Из корня репозитория, нужен Docker:

```sh
bash infra/build-backend.sh
python3 infra/verify_junit.py backend/ujin/build/test-results/test
docker build -f backend/ujin/Dockerfile \
  --build-arg VCS_REF="$(git rev-parse HEAD)" -t ujin-backend:local .
```

Скрипт использует закреплённый JDK 25 image, Wrapper проекта и кэш `work/gradle/`.
Кэш можно переопределить через `UJIN_GRADLE_CACHE`. Сборка ограничена 2 CPU и
1536 MiB RAM. При первом запуске нужны сеть и место для JDK/Gradle/dependencies.
При установленном JDK 25 можно выполнить из `backend/ujin/`:

```sh
bash gradlew --no-daemon --max-workers=2 --console=plain clean test bootJar
```

Dockerfile упаковывает уже собранный JAR; `clean` нужен, чтобы не оставить
посторонние JAR в `build/libs/`. В runtime используется JRE 25 и UID 10001;
контекст сборки ограничен Dockerfile и JAR. Revision доступен в label:

```sh
docker image inspect ujin-backend:local \
  --format '{{index .Config.Labels "org.opencontainers.image.revision"}}'
docker run --rm --network none --read-only --cap-drop ALL \
  --security-opt no-new-privileges --tmpfs /tmp:rw,size=64m \
  ujin-backend:local
```

Этот запуск проверяет текущий каркас; HTTP-порт и HTTP-healthcheck пока отсутствуют.
Label описывает исходники образа, но сам по себе не подтверждает версию стенда.

## PostgreSQL

PostgreSQL 17 выбран для начальной инфраструктуры. До работы с реальными
данными команда должна сверить major-версию с миграциями backend.
Образы JDK/JRE/PostgreSQL закреплены digest; обновлять их нужно отдельно
с повторной проверкой сборки и восстановления.

Скопируйте пример и заполните `POSTGRES_PASSWORD` локально через редактор.
Пароли не публикуются в Git, логи и описания PR:

```sh
cp .env.example .env
chmod 600 .env
docker compose --project-directory . --env-file .env -f infra/compose.yaml config --quiet
docker compose --project-directory . --env-file .env -f infra/compose.yaml up -d --wait postgres
docker compose --project-directory . --env-file .env -f infra/compose.yaml ps
```

Пустой пароль отклоняется до запуска. Порт БД на хост не публикуется;
сеть `database` внутренняя. Данные лежат в named volume и сохраняются при
restart и обычном `down`. Логи ротируются, лимит памяти БД — 512 MiB.
Для другого экземпляра задайте собственный `COMPOSE_PROJECT_NAME`.

Остановка с сохранением volume:

```sh
docker compose --project-directory . --env-file .env -f infra/compose.yaml down
```

Параметры `POSTGRES_*` используются при инициализации пустого volume.
Изменение `.env` не меняет автоматически пароль/пользователя уже созданной БД.
Backend в этой версии ещё не подключён к PostgreSQL.

## Backup и проверка восстановления

Команды `backup-db.sh`, `restore-db.sh` и `test-postgres.sh` рассчитаны на
Linux, Bash, GNU coreutils (`ln -T`, `stat -c`, `mktemp`) и Docker Compose v2;
для интеграционного теста также нужен OpenSSL. Штатные BSD-утилиты macOS
не поддерживаются. `ln -T` нужен для атомарного отказа, в том числе если
по целевому пути конкурентно появился каталог или символьная ссылка.

На Mac запускайте эти команды по SSH на согласованном Linux-хосте с Docker,
из его checkout той же ревизии. Например, заменив параметры своими:

```sh
ssh user@linux-host
cd /path/to/ujin
bash infra/test-postgres.sh
```

Для реальной копии `.env` и каталог backup должны находиться на этом
Linux-хосте с закрытыми правами; копия также создаётся там, не на Mac.
Не передавайте пароль в SSH-команде. Тест использует только свой временный
Compose-проект, но требует доступного Docker и ресурсов на выбранном хосте.

Запускайте из работающего проекта; каталог копий должен быть закрытым.
На другом окружении используйте `UJIN_ENV_FILE=/private/path/ujin.env`.
`COMPOSE_PROJECT_NAME` должен совпадать с именем исходного проекта.

```sh
install -d -m 700 work/backups
bash infra/backup-db.sh work/backups/check.dump
bash infra/restore-db.sh work/backups/check.dump ujin_restore_check
```

Backup создаётся с правами 600, проверяется через `pg_restore --list` и
не перезаписывает существующий файл. Restore создаёт только новую БД;
существующая БД отклоняется без изменения её данных. Восстановление идёт
в одной транзакции без переноса владельцев и ACL. Если восстановление упало,
новая БД может остаться пустой: причина выводится, автоматического удаления нет.
Копия содержит данные приложения, храните её отдельно от исходников и логов.
Этот инструмент не является автоматическим откатом несовместимых миграций.

## Проверки и CI

```sh
shellcheck infra/*.sh
python3 -m unittest discover -s infra/tests -v
bash infra/test-postgres.sh
```

Интеграционная проверка сама создаёт отдельный Compose-проект со случайным
паролем и синтетическими данными. Проверяет health, отсутствие опубликованных
портов, данные после restart, backup/restore, запрет перезаписи backup и
восстановления поверх существующей БД, а также гонку двух backup-процессов:
ровно один создаёт файл с правами 600, второй получает отказ. При завершении удаляет только свой
тестовый проект, volume и временную копию. Рабочая БД не используется.

GitHub Actions выполняет сборку backend, требует ненулевое число выполненных
тестов, сохраняет JUnit/HTML/JAR и проверяет runtime-образ под непривилегированным
пользователем. Второй job запускает проверки инфраструктуры. Workflow имеет
`contents: read`, без ключей сервера и автоматического deploy. Результат
локальной проверки не подменяет реальный прогон GitHub Actions.

Следующий этап: frontend и два эмулятора, datasource/Flyway и HTTP health,
согласованные local/live конфигурации, TLS, runtime SHA и проверка отката релиза.
Приватные credentials платформы должны оставаться на стороне backend.

Официальные справочники: [Compose services](https://docs.docker.com/reference/compose-file/services/),
[PostgreSQL image](https://hub.docker.com/_/postgres),
[setup-java](https://github.com/actions/setup-java).

## Локальный запуск PostgreSQL и Vite

Для разработки из корня репозитория создайте `.env` по `.env.example` и задайте
`POSTGRES_PASSWORD`. `docker compose up -d --wait` запускает PostgreSQL на
`127.0.0.1:5432` и Vite на `127.0.0.1:5173`. Backend запускается отдельно
на хосте на порту 8080. Compose не передаёт ему переменные из `.env`.
Перед запуском задайте адрес API платформы в `UJIN_API_BASE_URL` (или адрес
локального эмулятора). Не используйте значение по умолчанию `https://localhost/api`
для проверки входа. Пароль и адрес платформы не записывайте в Git или логи.

PowerShell, из корня репозитория:

```powershell
$config = @{}
Get-Content .env | Where-Object { $_ -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$' } |
  ForEach-Object { $config[$Matches[1]] = $Matches[2].Trim('"', "'") }
$database = if ($config['POSTGRES_DB']) { $config['POSTGRES_DB'] } else { 'ujin' }
$user = if ($config['POSTGRES_USER']) { $config['POSTGRES_USER'] } else { 'ujin' }
$env:UJIN_DB_URL = "jdbc:postgresql://localhost:5432/$database"
$env:UJIN_DB_USER = $user
$env:UJIN_DB_PASSWORD = $config['POSTGRES_PASSWORD']
$env:UJIN_API_BASE_URL = '<адрес API платформы или эмулятора>'
Set-Location backend/ujin
.\gradlew.bat bootRun
```

При отсутствии `POSTGRES_DB` или `POSTGRES_USER` используются значения Compose
`ujin`. Для Bash/macOS/Linux из корня репозитория:

```sh
set -a
. ./.env
set +a
export UJIN_DB_URL="jdbc:postgresql://localhost:5432/${POSTGRES_DB:-ujin}"
export UJIN_DB_USER="${POSTGRES_USER:-ujin}"
export UJIN_DB_PASSWORD="$POSTGRES_PASSWORD"
export UJIN_API_BASE_URL='<адрес API платформы или эмулятора>'
cd backend/ujin
./gradlew bootRun
```

Загружайте только собственный проверенный `.env`: Bash выполняет его как shell-файл.
Контейнер Vite пересылает `/api` на `host.docker.internal:8080`; на Linux
имя добавляется через `extra_hosts` с `host-gateway`. Если Docker не поддерживает
`host-gateway`, задайте доступный контейнеру адрес хоста вручную.

`infra/local.ps1 start|status|logs|restart|stop` и `Makefile` управляют этим
локальным Compose. `docker compose down` сохраняет данные PostgreSQL.
Изолированный runtime и его проверки описаны в `infra/RUNTIME.md` и используют
отдельные Compose-файлы `infra/compose*.yaml`.
