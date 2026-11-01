import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { test } from 'node:test';
const require = createRequire(new URL('../../frontend/package.json', import.meta.url));
const React = require('react');
const { create, act } = require('react-test-renderer');
const ts = require('typescript');

function loadComponent(filename, mocks) {
  const source = readFileSync(filename, 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  });
  const exports = {};
  const localRequire = (name) => {
    if (name in mocks) return mocks[name];
    if (name.endsWith('.css')) return { default: {} };
    if (name.startsWith('.')) return loadComponent(path.resolve(path.dirname(filename), name + '.tsx'), mocks);
    return require(name);
  };
  new Function('require', 'exports', outputText)(localRequire, exports);
  return exports;
}

const device = {
  id: 'light', title: 'Лампа', family: 'light',
  signals: [{ key: 'lamp', title: 'Свет', management: 'rw', enumValues: [0, 1] }], events: [],
};
const ujinScenario = {
  id: 'rule-ujin', version: 1, title: 'Ujin rule', enabled: true, executionMode: 'UJIN',
  trigger: { deviceId: 'leak', signalKey: 'leak', eventKey: 'changed', operator: 'EQ', value: 1 },
  action: { deviceId: 'light', signalKey: 'lamp', value: 1 },
};
const localScenario = {
  id: 'rule-local', version: 1, title: 'Local rule', enabled: true, executionMode: 'LOCAL',
  trigger: { deviceId: 'leak', signalKey: 'leak', eventKey: 'changed', operator: 'EQ', value: 1 },
  action: { deviceId: 'light', signalKey: 'lamp', value: 1 },
};
const leak = {
  id: 'leak', title: 'Датчик', family: 'leak',
  signals: [{ key: 'leak', title: 'Протечка', management: 'r', enumValues: [0, 1] }],
  events: [{ key: 'changed', title: 'Изменение', signalKey: 'leak' }],
};

function mountPage(polls) {
  const calls = [];
  const pagePath = fileURLToPath(new URL('../../frontend/src/pages/ScenariosCrudPage.tsx', import.meta.url));
  const mocks = {
    '../mocks/data': { mockDevices: [], mockScenarioPage: { items: [], total: 0 } },
    '../api/client': {
      ApiError: class extends Error { constructor() { super('err'); this.status = 500; } },
      NetworkError: class extends Error {}, TimeoutError: class extends Error {},
    },
    '../api/endpoints': {
      endpoints: {
        scenarios: {
          list: async () => ({ items: [ujinScenario, localScenario], total: 2 }),
          poll: async (id, pollId) => {
            calls.push(['poll', id, pollId]);
            return polls ? polls(id, pollId) : { pollId, scenarioId: id, status: 'NO_CHANGE' };
          },
          update: async () => ujinScenario, create: async () => ujinScenario, get: async () => ujinScenario,
        },
        devices: {
          list: async () => ({ items: [leak, device], total: 2 }),
          get: async () => device,
        },
      },
    },
    '../api/dataSource': { isMock: () => false },
    '../api/sessionContext': { useSession: () => ({ terminateSession() {} }) },
    '@tanstack/react-query': {
      useQuery: ({ queryKey }) => ({
        data: queryKey[0] === 'all-devices' ? [leak, device] : { items: [ujinScenario, localScenario], total: 2 },
        refetch() {}, isLoading: false, error: undefined,
      }),
      useQueryClient: () => ({ invalidateQueries() {} }),
    },
  };
  const { ScenariosCrudPage } = loadComponent(pagePath, mocks);
  let root;
  const tree = () => {
    let value;
    act(() => { value = create(React.createElement(ScenariosCrudPage)); });
    return value;
  };
  root = tree();
  return { root, calls, remount: tree };
}

test('UJIN scenario shows poll button and reports the result', async () => {
  const { root, calls } = mountPage();
  const buttons = root.root.findAllByType('button').filter(b => b.children.includes('Проверить показание Ujin'));
  assert.equal(buttons.length, 1, 'кнопка только для UJIN-сценария');
  await act(async () => { buttons[0].props.onClick(); });
  assert.equal(calls.length, 1);
  assert.equal(calls[0][1], 'rule-ujin');
  assert.ok(calls[0][2], 'pollId передаётся');
  const statuses = root.root.findAllByProps({ role: 'status' }).map(n => String(n.children));
  assert.ok(statuses.some(t => t.includes('Значение не изменилось')), statuses.join('|'));
  act(() => root.unmount());
});

test('watch toggle starts and stops observation', async () => {
  const { root, calls } = mountPage();
  const checkbox = root.root.findAllByType('input').find(n => n.props.type === 'checkbox');
  assert.ok(checkbox, 'переключатель наблюдения присутствует');
  await act(async () => { checkbox.props.onChange({ target: { checked: true } }); });
  await new Promise(resolve => setTimeout(resolve, 20));
  assert.ok(calls.length >= 1, 'наблюдение запускает проверку');
  const afterStart = calls.length;
  await act(async () => {
    root.root.findAllByType('input').find(n => n.props.type === 'checkbox').props.onChange({ target: { checked: false } });
  });
  await new Promise(resolve => setTimeout(resolve, 30));
  assert.equal(calls.length, afterStart, 'после выключения новые проверки не отправляются');
  act(() => root.unmount());
});

test('page explains that backend executes the rule', async () => {
  const { root } = mountPage();
  const texts = root.root.findAllByType('p').map(p => String(p.children));
  assert.ok(
    texts.some(t => t.includes('backend') && t.toLowerCase().includes('закрытая страница не наблюдает')),
    texts.join('|')
  );
  act(() => root.unmount());
});
