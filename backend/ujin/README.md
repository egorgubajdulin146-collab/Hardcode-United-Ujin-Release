# Backend

Технологическая основа: Java 25, Spring Boot, Gradle Wrapper,
PostgreSQL, Spring Data JPA, Flyway, Actuator.

В каталоге есть начальный Java/Spring Boot-каркас и Gradle Wrapper.
Декларации устройств, сценарии и API описаны в [контракте](../../docs/contracts/MVP.md).
Реализация предметных API, миграций и
проверенный полный запуск пока не подтверждены.

Сборка через Wrapper из этого каталога с JDK 25:

```sh
bash gradlew --no-daemon --max-workers=2 --console=plain clean bootJar
```

Альтернатива без установки JDK на хост — `bash infra/build-backend.sh` из
корня репозитория. Образ и CI описаны в [инструкции инфраструктуры](../../infra/README.md).
Web и Spring Security подключены для входа и чтения сигнала; Actuator пока не подключён.

## Вход через Ujin

Backend использует `UJIN_API_BASE_URL` (адрес API с `/api`, без токена в URL)
и `UJIN_AUTH_APP` (значение `employee.app` из выданного пакета для входа сотрудника).
`GET /api/auth/csrf` выдаёт CSRF-токен и cookie. Клиент отправляет его в
`X-XSRF-TOKEN` вместе с cookie при `POST /api/auth/login` с JSON
`{"login":"...","password":"..."}`. Backend проверяет данные через Ujin,
сохраняет только токен Ujin в серверной HTTP-сессии и возвращает cookie сессии.
Пароль после входа не хранится. `POST /api/auth/logout` завершает сессию.

После входа `GET /api/v1/ujin/devices/{serialnumber}/signals?name=dt_create`
читает сигнал с полномочиями вошедшего пользователя. Ujin может отказать в
чтении конкретного устройства; наличие сессии не означает право доступа к нему.
На назначенном тестовом light вход сотрудника и чтение `dt_create` прошли;
полный сигнал лампы пока отсутствует и возвращает 404.
