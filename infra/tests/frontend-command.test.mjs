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
  family: 'light',
  platformLink: 'BOUND',
  signals: [{ key: 'lamp', title: 'Lamp', management: 'rw', min: 0, max: 1 }],
};

class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail ?? 'error');
    this.status = status;
    this.problem = problem;
  }
}

function mount({
  downlink = { status: 'AVAILABLE', reason: 'Отправка apartment/send-signal включена' },
  send,
  check,
  state = { availability: 'UNKNOWN', values: [] },
} = {}) {
  const calls = [];
  const endpoints = {
    commands: {
      send: async (...args) => {
        calls.push(['command', ...args]);
        return send ? send(...args) : { commandId: 'cmd-1', deviceId: 'device', executionMode: 'UJIN', status: 'PENDING', reason: 'ACCEPTED_AWAITING_APPLY' };
      },
      get: async () => ({ commandId: 'cmd-1', deviceId: 'device', executionMode: 'UJIN', status: 'PENDING', reason: 'ACCEPTED_AWAITING_APPLY' }),
      check: async (id) => {
        calls.push(['check', id]);
        return check ? check(id) : { commandId: id, deviceId: 'device', executionMode: 'UJIN', status: 'PENDING', reason: 'ACCEPTED_AWAITING_APPLY' };
      },
    },
    stimuli: { send: async () => ({ status: 'PROCESSED', eventId: 'event', runIds: [] }) },
    events: { get: async () => ({ status: 'PROCESSED', eventId: 'event', runIds: [] }) },
    ujin: {
      getStatus: async () => ({
        publish: { status: 'BLOCKED', reason: 'x' },
        read: { status: 'UNKNOWN', reason: 'x' },
        downlink,
        autoscripts: { status: 'BLOCKED', reason: 'x' },
      }),
    },
    devices: { getState: async () => state, setAvailability: async () => state },
    publications: { list: async () => [], readback: async () => null, create: async () => null },
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
      useQueryClient: () => ({ invalidateQueries() {}, setQueryData() {} }),
      useQuery: options => {
        const key = options.queryKey[0];
        const base = { error: undefined, refetch() {}, isLoading: false, isFetching: false, isPending: false };
        if (key === 'device-state') return { ...base, data: state };
        if (key === 'ujin-status')
          return {
            ...base,
            data: {
              publish: { status: 'BLOCKED', reason: 'x' },
              read: { status: 'UNKNOWN', reason: 'x' },
              downlink,
              autoscripts: { status: 'BLOCKED', reason: 'x' },
            },
          };
        if (key === 'publications') return { ...base, data: [] };
        return { ...base, data: undefined };
      },
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

test('disabled downlink shows its reason instead of a generic hint', async () => {
  const { root } = mount({
    downlink: { status: 'BLOCKED', reason: 'Доставка команд Ujin выключена на сервере (флаг ujin.commands.enabled)' },
  });
  await selectUjin(root);
  const texts = root.root.findAllByType('p').map(p => String(p.children));
  assert.ok(texts.some(t => t.includes('Управление Ujin отключено') && t.includes('флаг ujin.commands.enabled')), texts.join('|'));
  const submit = root.root.findAllByType('button').find(b => b.children.includes('Отправить'));
  assert.equal(submit.props.disabled, true);
  act(() => root.unmount());
});

test('enabled downlink allows sending and acceptance stays pending', async () => {
  const { root, calls } = mount();
  await selectUjin(root);
  const submit = root.root.findAllByType('button').find(b => b.children.includes('Отправить'));
  assert.equal(submit.props.disabled, false);
  await act(async () => {
    root.root.findByType('input').props.onChange({ target: { value: '1' } });
  });
  await act(async () => {
    root.root.findByType('form').props.onSubmit({ preventDefault() {} });
  });
  assert.equal(calls.filter(c => c[0] === 'command').length, 1);
  const texts = root.root.findAllByType('p').map(p => String(p.children));
  assert.ok(texts.some(t => t.includes('Ожидает подтверждения')), texts.join('|'));
  assert.ok(!texts.some(t => t.includes('Выполнено')), texts.join('|'));
  act(() => root.unmount());
});

test('check result button exists for pending UJIN command', async () => {
  const { root, calls } = mount({
    check: (id) => ({ commandId: id, deviceId: 'device', executionMode: 'UJIN', status: 'CONFIRMED', reason: 'TASK_APPLIED' }),
  });
  await selectUjin(root);
  await act(async () => {
    root.root.findByType('input').props.onChange({ target: { value: '1' } });
  });
  await act(async () => {
    root.root.findByType('form').props.onSubmit({ preventDefault() {} });
  });
  const checkButton = root.root.findAllByType('button').find(b => b.children.includes('Проверить результат команды'));
  assert.ok(checkButton);
  await act(async () => {
    checkButton.props.onClick();
  });
  assert.equal(calls.filter(c => c[0] === 'check').length, 1);
  const texts = root.root.findAllByType('p').map(p => String(p.children));
  assert.ok(texts.some(t => t.includes('Выполнено') && t.includes('TASK_APPLIED')), texts.join('|'));
  act(() => root.unmount());
});
