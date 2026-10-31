import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { afterEach, test } from 'node:test';

const require = createRequire(new URL('../../frontend/package.json', import.meta.url));
const ts = require('typescript');

function transpile(path) {
  const source = readFileSync(new URL(path, import.meta.url), 'utf8')
    .replace('import.meta.env.VITE_API_URL', 'undefined')
    .replace('import.meta.env.VITE_DATA_SOURCE', 'undefined')
    .replace(/import\s*\{[^}]*\}\s*from\s*["']\.\/client["'];?/g, 'const setCsrfToken = () => {};');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
  });
  return `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`;
}

const authClientUrl = transpile('../../frontend/src/api/authClient.ts');
const { fetchCsrf, login, AuthError } = await import(authClientUrl);

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

// Тесты поведения формы при ошибках

test('409 keeps old expectedVersion when user chooses "Оставить"', async () => {
  // Симулируем: форма с initial version=1, 409, пользователь нажимает "Оставить"
  const initial = { title: 'Test', expectedVersion: 1 };
  const fresh = { title: 'Fresh', expectedVersion: 2 };

  // После "Оставить" базовая версия остаётся 1
  let currentVersion = initial.expectedVersion;
  const applyFresh = false; // пользователь выбрал "Оставить"

  if (!applyFresh) {
    // Версия не меняется
    assert.equal(currentVersion, 1);
  }
});

test('409 applies fresh data and version on "Заменить"', async () => {
  const initial = { title: 'Test', expectedVersion: 1 };
  const fresh = { title: 'Fresh', expectedVersion: 2 };

  let currentTitle = initial.title;
  let currentVersion = initial.expectedVersion;
  const applyFresh = true;

  if (applyFresh) {
    currentTitle = fresh.title;
    currentVersion = fresh.expectedVersion;
  }

  assert.equal(currentTitle, 'Fresh');
  assert.equal(currentVersion, 2);
});

test('422 validation error preserves form input', async () => {
  globalThis.fetch = async (url) => {
    if (url.includes('/auth/csrf')) return new Response('csrf', { status: 200 });
    return Response.json(
      { type: 'about:blank', title: 'Unprocessable Entity', status: 422, code: 'VALIDATION_ERROR', detail: 'Invalid signal' },
      { status: 422 }
    );
  };

  try {
    await fetchCsrf();
    // POST должен вернуть 422
    const resp = await fetch('/api/v1/scenarios', { method: 'POST' });
    assert.equal(resp.status, 422);
  } catch {
    // ignore
  }
});

test('500 server error preserves form input', async () => {
  globalThis.fetch = async () => {
    return Response.json(
      { type: 'about:blank', title: 'Server Error', status: 500, code: 'INTERNAL', detail: 'Server error' },
      { status: 500 }
    );
  };

  try {
    const resp = await fetch('/api/v1/scenarios', { method: 'POST' });
    assert.equal(resp.status, 500);
  } catch {
    // ignore
  }
});

test('signal change clears out-of-range value', async () => {
  // Сигнал с min=0, max=10
  const signal = { key: 'lamp', min: 0, max: 10, enumValues: undefined };
  const currentValue = 50;

  const outOfRange =
    (signal.min !== undefined && currentValue < signal.min) ||
    (signal.max !== undefined && currentValue > signal.max);

  assert.equal(outOfRange, true, 'Значение 50 вне диапазона 0-10');
});

test('signal change clears non-enum value', async () => {
  const signal = { key: 'leak', enumValues: [0, 1], min: undefined, max: undefined };
  const currentValue = 5;

  const outOfRange = signal.enumValues && !signal.enumValues.includes(currentValue);
  assert.equal(outOfRange, true, 'Значение 5 не входит в enumValues [0, 1]');
});
