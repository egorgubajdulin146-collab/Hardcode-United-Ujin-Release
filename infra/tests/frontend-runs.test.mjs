import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
const require = createRequire(new URL('../../frontend/package.json', import.meta.url));
const React = require('react');
const { create, act } = require('react-test-renderer');
const router = require('react-router-dom');
const ts = require('typescript');
const eventId = '11111111-1111-4111-8111-111111111111';
const scenarioId = '22222222-2222-4222-8222-222222222222';
function mount(url, total = 65) {
  let query;
  const requests = [];
  const source = readFileSync(new URL('../../frontend/src/pages/RunsPage.tsx', import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.CommonJS } });
  const mocks = {
    '@tanstack/react-query': { useQuery(options) { query = options; return { data: { items: [], total }, refetch() {} }; } },
    '../api/sessionContext': { useSession: () => ({ terminateSession() {} }) },
    '../api/dataSource': { isMock: () => false, getDataSource: () => 'api' },
    '../api/endpoints': { endpoints: { runs: { list: async (...args) => { requests.push(args); return {}; } } } },
    '../api/client': { ApiError: class extends Error {}, NetworkError: class extends Error {}, TimeoutError: class extends Error {} },
    '../mocks/data': {},
    '../components/States': { EmptyState: () => null, ErrorState: () => null, LoadingState: () => null },
  };
  const exports = {};
  new Function('require', 'exports', outputText)(name => name.endsWith('.css') ? { default: {} } : mocks[name] ?? require(name), exports);
  let root;
  act(() => { root = create(React.createElement(router.MemoryRouter, { initialEntries: [url] }, React.createElement(exports.RunsPage))); });
  return { root, requests, query: () => query };
}
test('event and scenario URL filters reach the API together with page and cancellation', async () => {
  const m = mount(`/runs?eventId=${eventId}&scenarioId=${scenarioId}&page=2`);
  const signal = new AbortController().signal;
  await m.query().queryFn({ signal });
  assert.deepEqual(m.requests[0], [{ page: 2, size: 20, eventId, scenarioId }, signal]);
  act(() => m.root.unmount());
});
test('pagination preserves filters and applies bounds', () => {
  const m = mount(`/runs?eventId=${eventId}`);
  const buttons = () => m.root.root.findAllByType('button');
  const button = name => buttons().find(b => b.children.includes(name));
  assert.equal(button('Назад').props.disabled, true);
  act(() => button('Далее').props.onClick());
  assert.equal(m.query().queryKey.at(-1), 1);
  assert.equal(m.query().queryKey[2], eventId);
  act(() => button('Далее').props.onClick());
  act(() => button('Далее').props.onClick());
  assert.equal(button('Далее').props.disabled, true);
  act(() => m.root.unmount());
});
test('applying filters resets page; reset clears both filters', () => {
  const m = mount(`/runs?eventId=${eventId}&page=2`);
  act(() => m.root.root.findAllByType('input')[1].props.onChange({ target: { value: scenarioId } }));
  act(() => m.root.root.findByType('form').props.onSubmit({ preventDefault() {} }));
  assert.deepEqual(m.query().queryKey.slice(2), [eventId, scenarioId, 0]);
  act(() => m.root.root.findAllByType('button').find(b => b.children.includes('Сбросить')).props.onClick());
  assert.deepEqual(m.query().queryKey.slice(2), ['', '', 0]);
  act(() => m.root.unmount());
});
test('invalid URL filters and pages disable API requests', () => {
  for (const url of ['/runs?eventId=bad', '/runs?page=-1', '/runs?page=Infinity', '/runs?page=0.5', '/runs?page=107374183']) {
    const m = mount(url);
    assert.equal(m.query().enabled, false, url);
    assert.equal(m.root.root.findAllByProps({ role: 'alert' }).length, 1);
    act(() => m.root.unmount());
  }
});
