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

test('saving device cannot be closed by Escape or the close button', async () => {
  const formPath = fileURLToPath(new URL('../../frontend/src/components/DeviceForm.tsx', import.meta.url));
  let cancelled = 0;
  const { DeviceForm } = loadComponent(formPath, {});
  let root;
  act(() => {
    root = create(React.createElement(DeviceForm, {
      onSubmit: async () => {}, onCancel: () => { cancelled += 1; }, loading: true,
    }));
  });
  await act(async () => { document.dispatchEvent(escapeKey()); });
  assert.equal(cancelled, 0);
  const close = root.root.findAllByType('button').find(b => b.props['aria-label'] === 'Закрыть');
  assert.equal(close?.props.disabled, true);
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

// —— Регрессии обрезки кнопок и неровных размеров —

function readCss(relative) {
  return readFileSync(fileURLToPath(new URL('../../frontend/src/' + relative, import.meta.url)), 'utf8');
}

function ruleBody(css, selector) {
  // Якорь по началу строки: внутри @media встречаются похожие селекторы.
  const at = css.indexOf('\n' + selector + ' {');
  if (at < 0) return '';
  return css.slice(at, css.indexOf('}', at));
}

test('regression: action buttons fit their label and keep one height', () => {
  for (const file of ['pages/DevicesCrudPage.module.css', 'pages/ScenariosCrudPage.module.css']) {
    const icon = ruleBody(readCss(file), '.iconBtn');
    assert.ok(!/width:\s*32px/.test(icon), `${file}: фиксированная ширина вернулась`);
    assert.match(icon, /white-space:\s*nowrap/, `${file}: текст снова может обрезаться`);
    assert.match(icon, /height:\s*36px/, `${file}: высота кнопки не 36px`);
    assert.match(icon, /flex-shrink:\s*0/, `${file}: кнопку снова можно сжать`);
    const primary = ruleBody(readCss(file), '.primaryBtn');
    assert.match(primary, /min-height:\s*36px/, `${file}: основная кнопка другого размера`);
  }
});

test('regression: journal and panel controls share the 36px height', () => {
  const journal = ruleBody(readCss('pages/Pages.module.css'), '.journalFilters input, .journalFilters button, .journalPagination button');
  assert.match(journal, /height:\s*36px/);
  const panel = ruleBody(readCss('components/DeviceRuntimePanel.module.css'), '.panel button, .panel input, .panel select');
  assert.match(panel, /height:\s*36px/);
  const rooms = readCss('pages/RoomDevicesPage.module.css');
  for (const selector of ['.addButton', '.emulateButton', '.roomTab']) {
    assert.match(ruleBody(rooms, selector), /(?:min-)?height:\s*36px/, `${selector} без общей высоты`);
  }
});

test('regression: disabled controls keep a visible disabled look', () => {
  const rooms = readCss('pages/RoomDevicesPage.module.css');
  assert.match(rooms, /\.addButton:disabled/, 'кнопка комнат без стиля disabled');
  const journal = readCss('pages/Pages.module.css');
  assert.match(journal, /\.journalFilters button:disabled/, 'фильтры журнала без стиля disabled');
});

function mountRuns({ isFetching = false } = {}) {
  const pagePath = fileURLToPath(new URL('../../frontend/src/pages/RunsPage.tsx', import.meta.url));
  const mocks = {
    '../mocks/data': { mockRunPage: { items: [], total: 0 }, mockDevices: [], mockScenarioPage: { items: [], total: 0 }, mockDevicePage: { items: [], total: 0 } },
    '../api/client': {
      ApiError: class extends Error { constructor() { super('err'); this.status = 500; } },
      NetworkError: class extends Error {}, TimeoutError: class extends Error {},
    },
    '../api/endpoints': {
      endpoints: {
        runs: { list: async () => ({ items: [], total: 0 }) },
        devices: { list: async () => ({ items: [], total: 0 }) },
      },
    },
    '../api/dataSource': { isMock: () => false, getDataSource: () => 'api' },
    '../api/sessionContext': { useSession: () => ({ terminateSession() {}, demo: false }) },
    'react-router-dom': {
      Link: (props) => React.createElement('a', props),
      useSearchParams: () => [new URLSearchParams(), () => {}],
    },
    '@tanstack/react-query': {
      useQuery: () => ({ data: { items: [], total: 0 }, error: undefined, isLoading: false, isFetching, refetch() {}, isPending: false }),
      useQueryClient: () => ({ invalidateQueries() {} }),
    },
  };
  const { RunsPage } = loadComponent(pagePath, mocks);
  let root;
  act(() => { root = create(React.createElement(RunsPage)); });
  return { root };
}

test('journal filter buttons are disabled while fetching', async () => {
  const { root } = mountRuns({ isFetching: true });
  for (const label of ['Применить', 'Сбросить']) {
    const button = root.root.findAllByType('button').find(b => textOf(b).includes(label));
    assert.ok(button, `кнопка «${label}» не найдена`);
    assert.equal(button.props.disabled, true, `«${label}» выключена во время загрузки`);
  }
  const refresh = root.root.findAllByType('button').find(b => b.props['aria-label'] === 'Обновить журнал');
  assert.ok(refresh, 'кнопка обновления журнала не найдена');
  assert.equal(refresh.props.disabled, true, 'обновление выключено во время загрузки');
  assert.ok(textOf(refresh).includes('Обновление…'), 'нет индикации загрузки');
  act(() => root.unmount());
});

test('poll row on the scenarios screen wraps instead of clipping', async () => {
  const css = readCss('pages/ScenariosCrudPage.module.css');
  const row = ruleBody(css, '.pollRow');
  assert.match(row, /flex-wrap:\s*wrap/, 'строка наблюдения не переносится');
  assert.match(ruleBody(css, '.pollRow [role="status"]'), /overflow-wrap:\s*anywhere/);
  const pagePath = fileURLToPath(new URL('../../frontend/src/pages/ScenariosCrudPage.tsx', import.meta.url));
  const source = readFileSync(pagePath, 'utf8');
  assert.match(source, /className=\{styles\.pollRow\}/, 'кнопки наблюдения вне переносимой строки');
});

test('regression: form, sidebar and login buttons keep the 36px family', () => {
  for (const [file, selectors] of [
    ['components/DeviceForm.module.css', ['.closeBtn', '.removeBtn', '.addBtn', '.cancelBtn', '.submitBtn']],
    ['components/ScenarioForm.module.css', ['.closeBtn', '.cancelBtn', '.submitBtn', '.confirmBtn', '.keepBtn']],
    ['pages/LoginPage.module.css', ['.submitButton', '.demoButton']],
    ['components/Layout.module.css', ['.sourceToggle']],
  ]) {
    const css = readCss(file);
    for (const selector of selectors) {
      const body = ruleBody(css, selector);
      assert.match(body, /(?:min-)?height:\s*36px/, `${file} ${selector} выпал из общей высоты`);
    }
  }
  const rooms = readCss('pages/RoomDevicesPage.module.css');
  assert.match(ruleBody(rooms, '.roomTab'), /height:\s*36px/, 'вкладка комнат не 36px');
  assert.match(ruleBody(rooms, '.panelClose'), /(?:min-)?height:\s*36px/, 'закрытие панели комнат не 36px');
});

test('regression: lost session on list load returns to login', async () => {
  const pagePath = fileURLToPath(new URL('../../frontend/src/pages/DevicesCrudPage.tsx', import.meta.url));
  let terminated = 0;
  class ApiError extends Error {
    constructor() { super('unauthorized'); this.status = 401; }
  }
  const mocks = {
    '../mocks/data': { mockDevicePage: { items: [], total: 0 }, mockDevices: [], mockScenarioPage: { items: [], total: 0 } },
    '../api/client': { ApiError, NetworkError: class extends Error {}, TimeoutError: class extends Error {} },
    '../api/endpoints': {
      endpoints: {
        devices: {
          list: async () => { throw new ApiError(); },
          get: async () => ({}), getState: async () => ({}), getDependencies: async () => ({ scenarioIds: [], pendingOperationIds: [] }),
        },
      },
    },
    '../api/dataSource': { isMock: () => false },
    '../api/sessionContext': { useSession: () => ({ terminateSession() { terminated += 1; }, demo: false }) },
    '@tanstack/react-query': {
      useQuery: () => ({ data: undefined, error: new ApiError(), isLoading: false, isFetching: false, refetch() {}, isPending: false }),
      useQueryClient: () => ({ invalidateQueries() {}, setQueryData() {} }),
    },
  };
  const { DevicesCrudPage } = loadComponent(pagePath, mocks);
  let root;
  await act(async () => { root = create(React.createElement(DevicesCrudPage)); });
  assert.ok(terminated >= 1, 'terminateSession вызван при 401 загрузки списка');
  act(() => root.unmount());
});

test('regression: card title wraps instead of clipping on narrow screens', () => {
  for (const file of ['pages/DevicesCrudPage.module.css', 'pages/ScenariosCrudPage.module.css']) {
    const css = readCss(file);
    const at = css.indexOf('@media (max-width: 640px)');
    assert.ok(at >= 0, `${file}: нет мобильного правила карточки`);
    const block = css.slice(at, css.indexOf('}', css.indexOf('.cardActions', at)));
    assert.match(block, /flex-wrap:\s*wrap/, `${file}: карточка не переносится`);
    assert.match(block, /white-space:\s*normal/, `${file}: заголовок снова обрезается`);
  }
});
