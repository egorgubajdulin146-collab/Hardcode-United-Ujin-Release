# Проверка frontend

Требования: Node.js 22, npm и доступ к registry для первой установки.
Из корня репозитория:

```sh
npm --prefix frontend ci --ignore-scripts --no-audit --no-fund
npm --prefix frontend run lint
node --test infra/tests/frontend-client.test.mjs
npm --prefix frontend run build
```

`Frontend CI` выполняет те же проверки для PR в develop/main и push в эти
ветки, затем сохраняет `frontend/dist` на 7 дней. Автоматического deploy нет;
секреты платформы и доступ к серверу не требуются. `--no-audit` означает,
что этот workflow не является проверкой уязвимостей зависимостей.

Регрессионные тесты исполняют API-клиент из исходников через закреплённый
lockfile TypeScript. Vite-переменная адреса заменяется пустым base URL;
fetch подменён локальными ответами. Проверяются 204 без тела, 200 JSON,
ProblemDetail, сетевой отказ и отмена. Это не браузерный и не backend-тест:
реальные запросы, авторизация и полный сценарий проверяются отдельно на стенде.
