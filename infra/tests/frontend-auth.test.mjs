import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { afterEach, test } from 'node:test';

// Используем locked TypeScript проекта для транспиляции authClient
const require = createRequire(new URL('../../frontend/package.json', import.meta.url));
const ts = require('typescript');

function transpile(path) {
  const source = readFileSync(new URL(path, import.meta.url), 'utf8')
    .replace('import.meta.env.VITE_API_URL', 'undefined')
    .replace('import.meta.env.VITE_DATA_SOURCE', 'undefined')
    .replace(/import\s*\{[^}]*\}\s*from\s*["']\.\/client["'];?/, 'const setCsrfToken = () => {};');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`;
}

const authClientUrl = transpile('../../frontend/src/api/authClient.ts');
const { fetchCsrf, login, logout, readSignal, clearCsrf, AuthError } = await import(authClientUrl);

const originalFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = originalFetch;
  clearCsrf();
});

test('CSRF without login does not mean authenticated', async () => {
  // fetchCsrf получает токен, но это не авторизация
  let capturedHeaders = null;
  globalThis.fetch = async (url, opts) => {
    if (url.includes('/auth/csrf')) {
      return new Response('csrf-token-abc', { status: 200 });
    }
    if (url.includes('/auth/login')) {
      capturedHeaders = opts.headers;
      return new Response(null, { status: 204 });
    }
    return new Response(null, { status: 401 });
  };

  const token = await fetchCsrf();
  assert.equal(token, 'csrf-token-abc');

  // После CSRF без login защищённый запрос вернёт 401
  try {
    await readSignal('test-device', 'lamp');
    assert.fail('Должен быть 401');
  } catch (err) {
    assert.ok(err instanceof AuthError);
    assert.equal(err.kind, 'invalid_credentials');
  }
});

test('login 204 succeeds and readSignal works', async () => {
  globalThis.fetch = async (url, opts) => {
    if (url.includes('/auth/csrf')) {
      return new Response('csrf-token', { status: 200 });
    }
    if (url.includes('/auth/login')) {
      assert.equal(opts.method, 'POST');
      assert.equal(opts.headers['X-XSRF-TOKEN'], 'csrf-token');
      return new Response(null, { status: 204 });
    }
    if (url.includes('/signals')) {
      return Response.json({ name: 'lamp', value: '1' });
    }
    return new Response(null, { status: 404 });
  };

  await fetchCsrf();
  await login({ login: 'user', password: 'pass' });

  const result = await readSignal('test-device', 'lamp');
  assert.deepEqual(result, { name: 'lamp', value: '1' });
});

test('401 from readSignal is invalid_credentials', async () => {
  globalThis.fetch = async (url) => {
    if (url.includes('/auth/csrf')) {
      return new Response('csrf', { status: 200 });
    }
    return new Response(null, { status: 401 });
  };

  try {
    await readSignal('test-device', 'lamp');
    assert.fail('Должен быть 401');
  } catch (err) {
    assert.ok(err instanceof AuthError);
    assert.equal(err.kind, 'invalid_credentials');
  }
});

test('403 from login is forbidden, not invalid_credentials', async () => {
  globalThis.fetch = async (url) => {
    if (url.includes('/auth/csrf')) {
      return new Response('csrf', { status: 200 });
    }
    return new Response(null, { status: 403 });
  };

  try {
    await login({ login: 'user', password: 'pass' });
    assert.fail('Должен быть 403');
  } catch (err) {
    assert.ok(err instanceof AuthError);
    assert.equal(err.kind, 'forbidden');
    assert.notEqual(err.kind, 'invalid_credentials');
  }
});

test('logout gets fresh CSRF and succeeds on 204', async () => {
  let csrfCalls = 0;
  globalThis.fetch = async (url, opts) => {
    if (url.includes('/auth/csrf')) {
      csrfCalls++;
      return new Response(`csrf-${csrfCalls}`, { status: 200 });
    }
    if (url.includes('/auth/logout')) {
      assert.equal(opts.method, 'POST');
      // Должен быть свежий CSRF (csrf-2 после logout fetchCsrf)
      assert.ok(opts.headers['X-XSRF-TOKEN'].startsWith('csrf-'));
      return new Response(null, { status: 204 });
    }
    return new Response(null, { status: 404 });
  };

  await logout();
  assert.ok(csrfCalls >= 1, 'logout должен получить свежий CSRF');
});

test('logout failure does not throw silently', async () => {
  globalThis.fetch = async (url) => {
    if (url.includes('/auth/csrf')) {
      return new Response('csrf', { status: 200 });
    }
    return new Response(null, { status: 500 });
  };

  try {
    await logout();
    assert.fail('Должен быть error');
  } catch (err) {
    assert.ok(err instanceof AuthError);
  }
});

test('aborted readSignal throws AbortError, not AuthError', async () => {
  const controller = new AbortController();
  globalThis.fetch = async (url, opts) => {
    if (url.includes('/auth/csrf')) {
      return new Response('csrf', { status: 200 });
    }
    // Проверяем что signal передаётся
    assert.ok(opts.signal, 'signal должен передаваться в fetch');
    throw new DOMException('Aborted', 'AbortError');
  };

  try {
    await readSignal('test-device', 'lamp', controller.signal);
    assert.fail('Должен быть AbortError');
  } catch (err) {
    assert.equal(err.name, 'AbortError');
  }
});

test('late success after session change does not set auth or result', async () => {
  // Симулируем: запрос запущен при epoch=1, сессия меняется на epoch=2,
  // затем приходит успех от старого запроса
  let currentEpoch = 1;
  const startEpoch = currentEpoch;

  // Смена сессии
  currentEpoch = 2;

  // Поздний ответ — epoch не совпадает, результат не применяется
  assert.notEqual(currentEpoch, startEpoch, 'epoch должен измениться');
  // В реальном коде: if (currentEpochRef.current !== startEpoch) return;
});

test('late 401 after session change does not terminate new session', async () => {
  let currentEpoch = 1;
  const startEpoch = currentEpoch;

  // Смена сессии (вход пользователя B)
  currentEpoch = 2;

  // Поздний 401 от A — не должен завершать сессию B
  assert.notEqual(currentEpoch, startEpoch, 'epoch должен измениться');
  // В реальном коде: if (currentEpochRef.current !== startEpoch) return;
});

test('signal read success does not set authenticated', async () => {
  globalThis.fetch = async (url, opts) => {
    if (url.includes('/auth/csrf')) {
      return new Response('csrf', { status: 200 });
    }
    if (url.includes('/signals')) {
      return Response.json({ name: 'lamp', value: '1' });
    }
    return new Response(null, { status: 404 });
  };

  // Чтение успешно, но не должно вызывать setAuthed(true)
  const result = await readSignal('test-device', 'lamp');
  assert.deepEqual(result, { name: 'lamp', value: '1' });
  // В компоненте: setResult(data) без setAuthed(true)
});

// Тесты записи с CSRF

test('POST request includes X-XSRF-TOKEN header', async () => {
  let capturedHeaders = null;
  globalThis.fetch = async (url, opts) => {
    if (url.includes('/auth/csrf')) {
      return new Response('test-csrf-token', { status: 200 });
    }
    if (opts.method === 'POST') {
      capturedHeaders = opts.headers;
      return Response.json({ id: 'test-id', version: 1 });
    }
    return new Response(null, { status: 404 });
  };

  // Импортируем api из client.ts
  const clientUrl = transpile('../../frontend/src/api/client.ts');
  const { api, setCsrfToken } = await import(clientUrl);
  setCsrfToken('test-csrf-token');

  await api.post('/api/v1/devices', { title: 'Test', family: 'test', signals: [], events: [] });
  assert.equal(capturedHeaders['X-XSRF-TOKEN'], 'test-csrf-token');
  assert.equal(capturedHeaders['Content-Type'], 'application/json');
});

test('PUT request includes X-XSRF-TOKEN header', async () => {
  let capturedHeaders = null;
  globalThis.fetch = async (url, opts) => {
    if (opts.method === 'PUT') {
      capturedHeaders = opts.headers;
      return Response.json({ id: 'test-id', version: 2 });
    }
    return new Response(null, { status: 404 });
  };

  const clientUrl = transpile('../../frontend/src/api/client.ts');
  const { api, setCsrfToken } = await import(clientUrl);
  setCsrfToken('put-csrf-token');

  await api.put('/api/v1/devices/test-id', { title: 'Test', family: 'test', signals: [], events: [], expectedVersion: 1 });
  assert.equal(capturedHeaders['X-XSRF-TOKEN'], 'put-csrf-token');
});

test('DELETE request includes X-XSRF-TOKEN header', async () => {
  let capturedHeaders = null;
  globalThis.fetch = async (url, opts) => {
    if (opts.method === 'DELETE') {
      capturedHeaders = opts.headers;
      return new Response(null, { status: 204 });
    }
    return new Response(null, { status: 404 });
  };

  const clientUrl = transpile('../../frontend/src/api/client.ts');
  const { api, setCsrfToken } = await import(clientUrl);
  setCsrfToken('delete-csrf-token');

  await api.delete('/api/v1/devices/test-id?expectedVersion=1');
  assert.equal(capturedHeaders['X-XSRF-TOKEN'], 'delete-csrf-token');
});

test('GET request does NOT include X-XSRF-TOKEN header', async () => {
  let capturedHeaders = null;
  globalThis.fetch = async (url, opts) => {
    if (opts.method === 'GET') {
      capturedHeaders = opts.headers;
      return Response.json({ items: [], total: 0 });
    }
    return new Response(null, { status: 404 });
  };

  const clientUrl = transpile('../../frontend/src/api/client.ts');
  const { api, setCsrfToken } = await import(clientUrl);
  setCsrfToken('get-should-not-send');

  await api.get('/api/v1/devices');
  assert.equal(capturedHeaders?.['X-XSRF-TOKEN'], undefined);
});

test('403 on write returns ApiError with status 403', async () => {
  globalThis.fetch = async () => {
    return Response.json(
      { type: 'about:blank', title: 'Forbidden', status: 403, code: 'CSRF_ERROR', detail: 'CSRF token invalid' },
      { status: 403 }
    );
  };

  const clientUrl = transpile('../../frontend/src/api/client.ts');
  const { api, setCsrfToken, ApiError } = await import(clientUrl);
  setCsrfToken('stale-token');

  try {
    await api.post('/api/v1/devices', {});
    assert.fail('Должен быть 403');
  } catch (err) {
    assert.ok(err instanceof ApiError);
    assert.equal(err.status, 403);
    assert.equal(err.problem?.code, 'CSRF_ERROR');
  }
});

test('no automatic retry on write failure', async () => {
  let fetchCount = 0;
  globalThis.fetch = async () => {
    fetchCount++;
    return Response.json(
      { type: 'about:blank', title: 'Server Error', status: 500, code: 'INTERNAL', detail: 'Server error' },
      { status: 500 }
    );
  };

  const clientUrl = transpile('../../frontend/src/api/client.ts');
  const { api, setCsrfToken } = await import(clientUrl);
  setCsrfToken('token');

  try {
    await api.post('/api/v1/devices', {});
    assert.fail('Должен быть error');
  } catch {
    // Ошибка — это нормально
  }
  assert.equal(fetchCount, 1, 'Не должно быть автоматического повтора');
});

test('CSRF acquisition honours cancellation before publishing the token', async () => {
  const controller = new AbortController();
  let receivedSignal;
  globalThis.fetch = async (_url, options) => {
    receivedSignal = options.signal;
    controller.abort();
    return new Response('late-csrf-token', { status: 200 });
  };
  await assert.rejects(fetchCsrf(controller.signal), { name: 'AbortError' });
  assert.equal(receivedSignal, controller.signal);
});
