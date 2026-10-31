# Backend

Технологическая основа: Java 25, Spring Boot, Gradle Wrapper,
PostgreSQL, Spring Data JPA, Flyway, Actuator.

В каталоге есть начальный Java/Spring Boot-каркас и Gradle Wrapper.
Декларации устройств, сценарии и API описаны в [контракте](../../docs/contracts/MVP.md).
Реализация предметных API, миграций и
проверенный полный запуск пока не подтверждены.

Сборка через Wrapper из этого каталога с JDK 25:

```sh
bash gradlew --no-daemon --max-workers=2 --console=plain clean test bootJar
```

Альтернатива без установки JDK на хост — `bash infra/build-backend.sh` из
корня репозитория. Образ и CI описаны в [инструкции инфраструктуры](../../infra/README.md).
Текущий каркас не содержит web/Actuator и после запуска штатно завершается.
