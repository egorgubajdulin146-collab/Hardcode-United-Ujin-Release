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

// Минимальный DOM для обработчиков Escape без браузера.
if (!globalThis.document) {
  globalThis.document = new EventTarget();
}
function escapeKey() {
  const event = new Event('keydown');
  event.key = 'Escape';
  return event;
}

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

function device(overrides) {
  return {
    id: 'dev-1', title: 'Датчик', family: 'leak', version: 1, platformLink: 'NOT_CONFIGURED',
    signals: [{ key: 'leak', title: 'Протечка', management: 'r', group: 'property', valueType: 'NUMBER', control: 'list' }],
    events: [{ key: 'changed', title: 'Изменение', signalKey: 'leak', emission: 'CHANGE' }],
    ...overrides,
  };
}

function mountDevices(devices, { isFetching = false } = {}) {
  const calls = [];
  const pagePath = fileURLToPath(new URL('../../frontend/src/pages/DevicesCrudPage.tsx', import.meta.url));
  const mocks = {
    '../mocks/data': { mockDevicePage: { items: [], total: 0 }, mockDevices: [], mockScenarioPage: { items: [], total: 0 } },
    '../api/client': {
      ApiError: class extends Error { constructor() { super('err'); this.status = 500; } },
      NetworkError: class extends Error {}, TimeoutError: class extends Error {},
    },
    '../api/endpoints': {
      endpoints: {
        devices: {
          list: async () => { calls.push(['list']); return { items: devices, total: devices.length }; },
          get: async () => devices[0],
          getState: async () => ({ deviceId: 'dev-1', executionMode: 'LOCAL', values: [], availability: 'ONLINE' }),
          getDependencies: async () => ({ deviceId: 'dev-1', scenarioIds: [], pendingOperationIds: [] }),
        },
      },
    },
    '../api/dataSource': { isMock: () => false },
    '../api/sessionContext': { useSession: () => ({ terminateSession() {}, demo: false }) },
    '@tanstack/react-query': {
      useQuery: () => ({ data: { items: devices, total: devices.length }, error: undefined, isLoading: false, isFetching, refetch() {}, isPending: false }),
      useQueryClient: () => ({ invalidateQueries() {}, setQueryData() {} }),
    },
  };
  const { DevicesCrudPage } = loadComponent(pagePath, mocks);
  let root;
  act(() => { root = create(React.createElement(DevicesCrudPage)); });
  return { root, calls };
}

function textOf(node) {
  return node.children
    .map(child => (child && typeof child === 'object' ? textOf(child) : String(child)))
    .join('');
}

test('device meta uses correct Russian plural forms', async () => {
  for (const [count, expected] of [[1, '1 сигнал'], [2, '2 сигнала'], [5, '5 сигналов']]) {
    const signals = Array.from({ length: count }, (_, i) => ({ key: `s${i}`, title: `S${i}`, management: 'r', group: 'property', valueType: 'NUMBER', control: 'input' }));
    const { root } = mountDevices([device({ signals, events: [] })]);
    const texts = root.root.findAllByType('span').map(textOf);
    assert.ok(texts.some(t => t.includes(expected)), `count=${count}: ${texts.join('|')}`);
    act(() => root.unmount());
  }
});

test('device meta pluralizes events too', async () => {
  const events = [
    { key: 'e1', title: 'E1', signalKey: 'leak', emission: 'CHANGE' },
    { key: 'e2', title: 'E2', signalKey: 'leak', emission: 'CHANGE' },
  ];
  const { root } = mountDevices([device({ events })]);
  const texts = root.root.findAllByType('span').map(textOf);
  assert.ok(texts.some(t => t.includes('2 события')), texts.join('|'));
  act(() => root.unmount());
});

test('refresh button is labelled and disabled while loading', async () => {
  const { root } = mountDevices([device()], { isFetching: true });
  const refresh = root.root.findAllByType('button').find(b => b.props['aria-label'] === 'Обновить список устройств');
  assert.ok(refresh, 'у кнопки обновления есть aria-label');
  assert.equal(refresh.props.disabled, true, 'во время загрузки кнопка выключена');
  act(() => root.unmount());
});

test('mock screen shows a reason for the disabled add button', async () => {
  const pagePath = fileURLToPath(new URL('../../frontend/src/pages/RoomDevicesPage.tsx', import.meta.url));
  const mocks = {
    '../api/dataSource': { isMock: () => true },
    '../api/sessionContext': { useSession: () => ({ terminateSession() {}, demo: false }) },
  };
  const { RoomDevicesPage } = loadComponent(pagePath, mocks);
  let root;
  act(() => { root = create(React.createElement(RoomDevicesPage)); });
  const add = root.root.findAllByType('button').find(b => textOf(b).includes('Добавить устройство'));
  assert.ok(add);
  assert.equal(add.props.disabled, true);
  assert.match(String(add.props['aria-label']), /недоступно/i);
  const hints = root.root.findAllByType('small').map(textOf);
  assert.ok(hints.some(t => t.includes('Создание доступно')), hints.join('|'));
  act(() => root.unmount());
});

test('Escape closes the device form like the close button', async () => {
  const formPath = fileURLToPath(new URL('../../frontend/src/components/DeviceForm.tsx', import.meta.url));
  let cancelled = 0;
  const { DeviceForm } = loadComponent(formPath, {});
  let root;
  act(() => {
    root = create(React.createElement(DeviceForm, {
      onSubmit: async () => {}, onCancel: () => { cancelled += 1; },
    }));
  });
  await act(async () => {
    document.dispatchEvent(escapeKey());
  });
  assert.equal(cancelled, 1);
  act(() => root.unmount());
});

test('Escape closes the runtime panel when not busy', async () => {
  const panelPath = fileURLToPath(new URL('../../frontend/src/components/DeviceRuntimePanel.tsx', import.meta.url));
  const deviceObj = device();
  let closed = 0;
  const mocks = {
    '../api/endpoints': {
      endpoints: {
        devices: { getState: async () => ({ deviceId: 'dev-1', executionMode: 'LOCAL', values: [], availability: 'ONLINE' }), setAvailability: async () => ({}) },
        ujin: { getStatus: async () => ({ publish: { status: 'BLOCKED', reason: 'x' }, read: { status: 'UNKNOWN', reason: 'x' }, downlink: { status: 'BLOCKED', reason: 'x' }, autoscripts: { status: 'BLOCKED', reason: 'x' } }) },
        commands: { send: async () => ({}), get: async () => ({}), check: async () => ({}) },
        stimuli: { send: async () => ({}) },
        events: { get: async () => ({}) },
        publications: { list: async () => [], create: async () => ({}), readback: async () => ({}) },
        localPython: { poll: async () => ({}), check: async () => ({}), checkEvent: async () => ({}) },
      },
    },
    '../api/client': { ApiError: class extends Error {} },
    '../api/sessionContext': { useSession: () => ({ terminateSession() {}, demo: false }) },
    'react-router-dom': { Link: (props) => React.createElement('a', props) },
    '@tanstack/react-query': {
      useQueryClient: () => ({ invalidateQueries() {}, setQueryData() {} }),
      useQuery: () => ({ data: undefined, error: undefined, refetch() {}, isLoading: false, isFetching: false, isPending: false }),
    },
  };
  const { DeviceRuntimePanel } = loadComponent(panelPath, mocks);
  let root;
  act(() => { root = create(React.createElement(DeviceRuntimePanel, { device: deviceObj, onClose: () => { closed += 1; } })); });
  await act(async () => {
    document.dispatchEvent(escapeKey());
  });
  assert.equal(closed, 1);
  act(() => root.unmount());
});
