# Frontend — Ujin Scenario Editor

Интерфейс редактора сценариев умных устройств на базе Ujin OS.

## Стек

React 18, TypeScript, Vite, React Router, TanStack Query, CSS Modules, Lucide React.

## Запуск

```bash
npm ci
npm run dev        # dev-сервер на http://localhost:5173
npm run build      # production build (tsc + vite)
npm run preview    # предпросмотр production build
npm run lint       # ESLint
```

## Источник данных

Приложение поддерживает два источника:

- **mock** — синтетические данные из `src/mocks/data.ts` (только при явном выборе)
- **api** — реальные запросы к backend (по умолчанию)

Переключение:

1. Через переменную окружения: `VITE_DATA_SOURCE=mock|api` в `.env`
2. Через UI: кнопка «Демо / Подключение» внизу боковой панели (сохраняется в localStorage)

Vite пересылает `/api` на `VITE_PROXY_TARGET` или `http://localhost:8080`.
При общей раздаче через Nginx оставляйте `VITE_API_URL` пустым.

Базовый URL API задаётся переменной `VITE_API_URL` (по умолчанию пустой, запросы идут на тот же origin).

## Проверки

```bash
npx tsc --noEmit    # TypeScript
npm run lint        # ESLint
npm run build       # Production build
```

## Размещение

Приложение — SPA. При размещении на сервере настроить fallback на `index.html`
для всех маршрутов (`/devices`, `/scenarios`, `/runs`).

Пример для Nginx:

```nginx
location / {
  try_files $uri $uri/ /index.html;
}
```

## Структура

```
src/
  api/        — типы, клиент, endpoints, конфигурация источника
  app/        — (резерв)
  pages/      — DevicesPage, ScenariosPage, RunsPage
  components/ — Layout, состояния (Loading/Empty/Error/MockBadge)
  styles/     — токены, глобальные стили
  mocks/      — синтетические данные по контракту
```
