# Backend: устройства и сценарии

Реализована часть контракта 0.1.2-draft для редактора. Все маршруты требуют
сессии из `POST /api/auth/login`. Перед login и изменяющими запросами получите
маскированный токен через `GET /api/auth/csrf` и передайте его в `X-XSRF-TOKEN`
вместе с cookie сессии. После login получите новый CSRF-токен. Предпочтителен
один origin через proxy; cross-origin CORS в этом изменении не включён.
Текущий frontend-клиент ещё не передаёт CSRF-токен при изменениях.

| Маршрут | Результат |
| --- | --- |
| GET /api/v1/devices?page=0&size=20 | Страница устройств; size 1–100 |
| POST /api/v1/devices | DeviceWrite → 201, Device и Location |
| GET /api/v1/devices/{id} | Device или 404 |
| PUT /api/v1/devices/{id} | DeviceUpdate с expectedVersion → Device |
| GET /api/v1/devices/{id}/dependencies | ID зависимых сценариев и операций |
| DELETE /api/v1/devices/{id}?expectedVersion=1 | 204 либо конфликт версии/зависимостей |
| GET /api/v1/scenarios?page=0&size=20 | Страница сценариев |
| POST /api/v1/scenarios | ScenarioWrite → 201, Scenario и Location |
| GET /api/v1/scenarios/{id} | Scenario или 404 |
| PUT /api/v1/scenarios/{id} | ScenarioUpdate с expectedVersion → Scenario |

Поля запросов и ответов — в [OpenAPI](openapi.json). Записи создаются с version=1.
При конфликте версии — 409 VERSION_CONFLICT, при зависимом устройстве —
409 DEVICE_IN_USE. Ошибки валидации — 422 VALIDATION_ERROR, read-only действие —
422 SIGNAL_READ_ONLY, цикл — 422 SCENARIO_CYCLE. Некорректный JSON, UUID,
неизвестные поля или неверные значения параметров — 400 INVALID_REQUEST. Ошибки каталога имеют
Content-Type application/problem+json. Удаление блокируется и выключенными
сценариями. Изменение декларации связанного устройства запрещено; название
самого устройства можно менять. Сценарии не удаляются: такой маршрут не задан
текущим контрактом.

Хранение — PostgreSQL, Spring Data JPA и миграция Flyway V1. DTO запросов/ответов,
доменные записи и JPA-сущности разделены; web- и entity-мапперы явные.
Внешние ключи защищают связи сценариев с устройствами; @Version защищает версии.
Изменения каталога сериализуются короткой транзакционной блокировкой одной
служебной строки, чтобы удаление, валидация ссылок и проверка циклов были атомарны.
Чтение страниц выполняется запросами JPA с pagination. Декларации ограничены
100 сигналами и 100 событиями.

Для запуска задайте UJIN_DB_URL (JDBC URL PostgreSQL), UJIN_DB_USER и
UJIN_DB_PASSWORD вне Git. По умолчанию URL jdbc:postgresql://localhost:5432/ujin,
пользователь ujin; рабочий пароль не задан. Доступ к Ujin для входа использует
существующие UJIN_API_BASE_URL и UJIN_AUTH_APP. Hibernate проверяет схему,
а создаёт её Flyway. H2 используется только тестами.

Этот срез сохраняет декларации и правила. Он ещё не исполняет enabled-сценарии,
не запускает эмуляторы, не привязывает устройства к Ujin и не добавляет
/state, /commands, /stimuli, /events, /runs или /system. platformLink остаётся
NOT_CONFIGURED; pendingOperationIds пуст, поскольку исполнителя операций пока
нет. Наличие executionMode в сохранённом правиле не подтверждает доступность
его исполнения. Живое подключение frontend и Ujin проверяется отдельно.
