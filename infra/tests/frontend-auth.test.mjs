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
    .replace('import.meta.env.VITE_DATA_SOURCE', 'undefined');
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
