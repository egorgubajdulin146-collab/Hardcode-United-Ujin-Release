import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { afterEach, test } from 'node:test';

// Use the project's locked TypeScript, without changing the application source.
const require = createRequire(new URL('../../frontend/package.json', import.meta.url));
const ts = require('typescript');
const source = readFileSync(new URL('../../frontend/src/api/client.ts', import.meta.url), 'utf8')
  .replace('import.meta.env.VITE_API_URL', 'undefined');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
});
const { api, ApiError, NetworkError, CancelledError } = await import(
  `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
);
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test('DELETE 204 succeeds without decoding an empty body', async () => {
  globalThis.fetch = async (_, options) => {
    assert.equal(options.method, 'DELETE');
    return new Response(null, { status: 204 });
  };
  assert.equal(await api.delete('/api/v1/devices/test'), undefined);
});

test('GET 200 returns JSON', async () => {
  globalThis.fetch = async () => Response.json({ items: [], total: 0 });
  assert.deepEqual(await api.get('/api/v1/devices'), { items: [], total: 0 });
});

test('HTTP error preserves ProblemDetail and status', async () => {
  const problem = { status: 409, code: 'DEVICE_IN_USE', detail: 'Device is in use' };
  globalThis.fetch = async () => Response.json(problem, { status: 409 });
  await assert.rejects(api.delete('/api/v1/devices/test'), (error) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.status, 409);
    assert.deepEqual(error.problem, problem);
    return true;
  });
});

test('network failure is NetworkError, not success', async () => {
  globalThis.fetch = async () => { throw new TypeError('fetch failed'); };
  await assert.rejects(api.get('/api/v1/devices'), NetworkError);
});

test('already cancelled request does not call fetch', async () => {
  globalThis.fetch = async () => { assert.fail('Unexpected fetch'); };
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(api.get('/api/v1/devices', controller.signal), CancelledError);
});
