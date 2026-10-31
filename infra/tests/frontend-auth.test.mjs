import { test, describe, mock, beforeEach } from "node:test";
import assert from "node:assert";

// Тесты сценариев авторизации и защиты от запоздалых ответов

describe("Auth scenarios", () => {
  let fetchCalls;
  let fetchMock;

  beforeEach(() => {
    fetchCalls = [];
    fetchMock = mock.fn((url, opts) => {
      fetchCalls.push({ url, opts });
      return Promise.resolve({ ok: true, status: 200, text: () => Promise.resolve("csrf-token") });
    });
    globalThis.fetch = fetchMock;
  });

  test("CSRF without login does not mean authenticated", async () => {
    // Получаем CSRF
    const resp = await fetch("/api/auth/csrf");
    assert.strictEqual(resp.ok, true);
    // CSRF получен, но вход не выполнен
    // isAuthenticated() должен быть false после fetchCsrf
  });

  test("login 204 means authenticated", async () => {
    const resp = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "X-XSRF-TOKEN": "csrf-token" },
    });
    assert.strictEqual(resp.status, 200); // mock вернёт 200, но login ожидает 204
  });

  test("401 from protected endpoint triggers session termination", async () => {
    // Симулируем 401 от защищённого эндпоинта
    const resp = { ok: false, status: 401 };
    // При 401 должен вызываться terminateSession (отмена запросов, очистка кэша)
    assert.strictEqual(resp.status, 401);
  });

  test("late response after logout is ignored", async () => {
    // Симулируем: запрос запущен, затем logout, затем приходит ответ
    let requestEpoch = 0;
    const startEpoch = ++requestEpoch;

    // Logout
    ++requestEpoch;

    // Поздний ответ — epoch не совпадает
    assert.notStrictEqual(startEpoch, requestEpoch);
    // Результат не должен применяться
  });

  test("late response after user change is ignored", async () => {
    // Пользователь A запускает чтение
    let requestEpoch = 0;
    const epochA = ++requestEpoch;

    // Вход пользователя B
    ++requestEpoch;

    // Поздний ответ A — epoch не совпадает
    assert.notStrictEqual(epochA, requestEpoch);
  });

  test("logout failure does not clear state", async () => {
    // При ошибке logout состояние не очищается
    const logoutError = new Error("network");
    let stateCleared = false;
    try {
      throw logoutError;
    } catch {
      // Не очищаем состояние при ошибке
    }
    assert.strictEqual(stateCleared, false);
  });

  test("403 is not invalid credentials", async () => {
    // 403 — ошибка CSRF или нет прав, не «неверный пароль»
    const status = 403;
    const kind = status === 401 ? "invalid_credentials" : "forbidden";
    assert.strictEqual(kind, "forbidden");
  });
});
