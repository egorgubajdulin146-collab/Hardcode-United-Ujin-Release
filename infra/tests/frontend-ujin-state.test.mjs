import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
const require = createRequire(new URL('../../frontend/package.json', import.meta.url));
const React = require('react');
const { create, act } = require('react-test-renderer');
const ts = require('typescript');

const device = {
  id: 'device',
  title: 'Device',
  family: 'leak',
  platformLink: 'BOUND',
  signals: [
    { key: 'wet', title: 'Leak', management: 'r' },
    { key: 'lamp', title: 'Lamp', management: 'rw', min: 0, max: 1 },
  ],
};

class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail ?? 'error');
    this.status = status;
    this.problem = problem;
  }
}

function mount({
  state = { availability: 'ONLINE', values: [{ signalKey: 'wet', value: 0, updatedAt: '2026-11-01T12:00:00Z' }] },
  stateError,
} = {}) {
  const calls = [];
  const endpoints = {
    commands: { send: async (...args) => { calls.push(['command', ...args]); return { status: 'PENDING', commandId: 'command' }; }, get: async () => ({ status: 'PENDING', commandId: 'command' }) },
    stimuli: { send: async () => ({ status: 'PROCESSED', eventId: 'event', runIds: [] }) },
    events: { get: async () => ({ status: 'PROCESSED', eventId: 'event', runIds: [] }) },
    ujin: {
      getStatus: async () => ({
        publish: { status: 'BLOCKED', reason: 'x' },
        read: { status: 'UNKNOWN', reason: 'x' },
        downlink: { status: 'BLOCKED', reason: 'Команды Ujin не подтверждены' },
        autoscripts: { status: 'BLOCKED', reason: 'x' },
      }),
    },
    devices: { getState: async () => state },
  };
  const source = readFileSync(new URL('../../frontend/src/components/DeviceRuntimePanel.tsx', import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  });
  const mocks = {
    '../api/endpoints': { endpoints },
    '../api/client': { ApiError },
    '../api/sessionContext': { useSession: () => ({ terminateSession() {} }) },
    'react-router-dom': { Link: props => React.createElement('a', props) },
    '@tanstack/react-query': {
      useQueryClient: () => ({ invalidateQueries() {} }),
      useQuery: options =>
        options.queryKey[0] === 'device-state'
          ? { error: stateError, data: state, refetch() {}, isLoading: false }
          : { data: undefined, error: undefined, refetch() {}, isLoading: false },
    },
  };
  const exports = {};
  new Function('require', 'exports', outputText)(
    name => (name.endsWith('.css') ? { default: {} } : mocks[name] ?? require(name)),
    exports
  );
  let root;
  act(() => {
    root = create(React.createElement(exports.DeviceRuntimePanel, { device, onClose() {} }));
  });
  return { root, calls };
}

async function selectUjin(root) {
  const select = root.root.findAllByType('select')[0];
  await act(async () => {
    select.props.onChange({ target: { value: 'UJIN' } });
  });
}

test('missing platform signal shows its reason instead of silent no data', async () => {
  const { root } = mount({
    state: {
      availability: 'UNKNOWN',
      observedAt: '2026-11-01T12:00:05Z',
      values: [],
      signalIssues: [
        { signalKey: 'wet', status: 'MISSING_SIGNAL', detail: 'Signal is absent on the platform' },
      ],
    },
  });
  await selectUjin(root);
  const texts = root.root.findAllByType('small').map(s => String(s.children));
  assert.ok(texts.some(t => t.includes('Сигнал отсутствует на платформе')), texts.join('|'));
  assert.ok(root.root.findAllByType('p').some(p => String(p.children).includes('Наблюдение')));
  act(() => root.unmount());
});

test('zero value from platform is shown as real zero', async () => {
  const { root } = mount({
    state: {
      availability: 'UNKNOWN',
      values: [{ signalKey: 'wet', value: 0, updatedAt: '2026-11-01T12:00:05Z' }],
    },
  });
  await selectUjin(root);
  assert.ok(root.root.findAllByType('p').some(p => p.children.includes('0')));
  act(() => root.unmount());
});

test('stale ONLINE does not allow commands when state refresh fails', async () => {
  const { root } = mount({ state: { availability: 'ONLINE', values: [] }, stateError: new ApiError(503, { code: 'UJIN_UNAVAILABLE', detail: 'unavailable' }) });
  const buttons = root.root
    .findAllByType('button')
    .filter(b => ['Отправить', 'Обнаружить воду', 'Вода отсутствует'].some(t => b.children.includes(t)));
  assert.ok(buttons.length > 0);
  assert.ok(buttons.every(b => b.props.disabled), 'все команды отключены');
  assert.ok(root.root.findAllByType('p').some(p => String(p.children).includes('недоступна')));
  act(() => root.unmount());
});

test('unbound device explains where to bind', async () => {
  const { root } = mount({ stateError: new ApiError(409, { code: 'PLATFORM_NOT_BOUND', detail: 'not bound' }) });
  await selectUjin(root);
  const alert = root.root.findAllByType('p').find(p => p.props.role === 'alert');
  assert.ok(String(alert.children).includes('не привязано'), String(alert.children));
  act(() => root.unmount());
});
