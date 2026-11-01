import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
const require = createRequire(new URL('../../frontend/package.json', import.meta.url));
const React = require('react');
const { create, act } = require('react-test-renderer');
const ts = require('typescript');
const device = { id: 'device', title: 'Device', family: 'leak', platformLink: 'NOT_CONFIGURED', signals: [
  { key: 'wet', title: 'Leak', management: 'r' },
  { key: 'lamp', title: 'Lamp', management: 'rw', min: 0, max: 1 },
] };
function mount({ provider, availability = 'ONLINE', stateError, stimulus, availabilityUpdate, bound = false,
  receipts = [], publicationSend, publicationCheck,
  send = async () => ({ status: 'PENDING', commandId: 'command' }) } = {}) {
  const calls = [];
  const endpoints = { localPython: { poll: async eventId => { calls.push(['python-poll', eventId]); return {eventId, status:'PROCESSED', runIds:[]}; } }, publications: {
    send: async (id, data) => { calls.push(['publication', id, data]); return publicationSend ? publicationSend(id, data) : { ...data, deviceId: id, status: 'CONFIRMED' }; },
    readback: async (id, publicationId) => { calls.push(['readback', id, publicationId]); return publicationCheck ? publicationCheck(id, publicationId) : { ...receipts[0], deviceId: id, publicationId, status: 'CONFIRMED' }; },
  }, devices: { setAvailability: async (...args) => {
    calls.push(['availability', ...args]);
    if (availabilityUpdate) return availabilityUpdate(...args);
    return { availability: args[1], values: [] };
  } }, commands: { send: async (...args) => { calls.push(['command', ...args]); return send(...args); } },
    stimuli: { send: async (...args) => { calls.push(['stimulus', ...args]); if (stimulus) return stimulus(...args); return { status: 'PROCESSED', eventId: 'event', runIds: [] }; } } };
  class ApiError extends Error {}
  const source = readFileSync(new URL('../../frontend/src/components/DeviceRuntimePanel.tsx', import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } });
  const mocks = {
    '../api/endpoints': { endpoints }, '../api/client': { ApiError },
    '../api/sessionContext': { useSession: () => ({ terminateSession() {} }) },
    'react-router-dom': { Link: props => React.createElement('a', props) },
    '@tanstack/react-query': { useQueryClient: () => ({ invalidateQueries() {}, setQueryData() {} }),
      useQuery: options => ({ error: options.queryKey[0] === 'device-state' ? stateError : undefined, data: options.queryKey[0] === 'device-state' ? { provider, availability, values: [{ signalKey: 'wet', value: 0, updatedAt: '2026-10-31T12:00:00Z' }] } : options.queryKey[0] === 'publications' ? receipts : undefined, refetch() {} }) },
  };
  const exports = {};
  new Function('require','exports', outputText)(name => name.endsWith('.css') ? { default: {} } : mocks[name] ?? require(name), exports);
  let root;
  act(() => { root = create(React.createElement(exports.DeviceRuntimePanel, { device: { ...device, platformLink: bound ? 'BOUND' : device.platformLink }, onClose() {} })); });
  return { root, calls };
}
test('read-only signal has no command form and numeric zero is displayed', () => {
  const { root } = mount();
  assert.equal(root.root.findAllByType('form').length, 1);
  assert.ok(root.root.findAllByType('p').some(p => p.children.includes('0')));
  act(() => root.unmount());
});

test('publication sends current numeric zero and only confirms after server readback', async () => {
  const { root, calls } = mount({ bound: true });
  const publish = root.root.findAllByType('button').find(b => b.children.includes('Опубликовать '));
  assert.ok(publish); assert.equal(!!publish.props.disabled, false);
  await act(async () => { publish.props.onClick(); });
  assert.equal(calls.length, 1); assert.equal(calls[0][0], 'publication');
  assert.equal(calls[0][2].value, 0); assert.equal(calls[0][2].signalKey, 'wet');
  assert.ok(root.root.findAllByType('p').some(p => p.children.includes('значение прочитано в Ujin')));
  act(() => root.unmount());
});

test('lost publication response retains the ID and blocks a new send', async () => {
  const { root, calls } = mount({ bound: true, publicationSend: async () => { throw new Error('Lost response'); } });
  await act(async () => { root.root.findAllByType('button').find(b => b.children.includes('Опубликовать ')).props.onClick(); });
  assert.equal(calls.length, 1);
  assert.ok(root.root.findAllByType('button').find(b => b.children.includes('Опубликовать ')).props.disabled);
  assert.ok(root.root.findAllByType('p').some(p => p.children.includes('результат неизвестен')));
  const lookup = root.root.findAllByType('button').find(b => b.children.includes('Проверить чтением'));
  await act(async () => { lookup.props.onClick(); });
  assert.deepEqual(calls[1], ['readback', 'device', calls[0][2].publicationId]);
  act(() => root.unmount());
});

test('persisted UNKNOWN survives remount and readback never sends a new publication', async () => {
  const receipts = [{ publicationId: 'persisted', deviceId: 'device', signalKey: 'wet', value: 0, status: 'UNKNOWN' }];
  const { root, calls } = mount({ bound: true, receipts });
  assert.ok(root.root.findAllByType('button').find(b => b.children.includes('Опубликовать ')).props.disabled);
  await act(async () => { root.root.findAllByType('button').find(b => b.children.includes('Проверить чтением')).props.onClick(); });
  assert.deepEqual(calls, [['readback', 'device', 'persisted']]);
  assert.ok(root.root.findAllByType('p').some(p => p.children.includes('значение прочитано в Ujin')));
  act(() => root.unmount());
});
test('offline device cannot receive commands or stimuli', () => {
  const { root } = mount({ availability: 'OFFLINE' });
  const buttons = root.root.findAllByType('button').filter(b => ['Отправить','Обнаружить воду','Вода отсутствует'].some(t => b.children.includes(t)));
  assert.equal(buttons.length, 3);
  assert.ok(buttons.every(b => b.props.disabled));
  act(() => root.unmount());
});
test('pending command is not presented as executed and blocks duplicate sending', async () => {
  const { root, calls } = mount();
  await act(async () => { root.root.findAllByType('input').find(i => i.props.type === 'number').props.onChange({ target: { value: '1' } }); });
  await act(async () => { root.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
  assert.equal(calls.length, 1);
  assert.equal(calls[0][2].executionMode, 'LOCAL');
  assert.ok(root.root.findAllByType('p').some(p => p.children.includes('Ожидает подтверждения')));
  assert.ok(root.root.findAllByType('button').find(b => b.children.includes('Отправить')).props.disabled);
  act(() => root.unmount());
});
test('lost command response keeps UNKNOWN and prevents a new command id', async () => {
  const { root, calls } = mount({ send: async () => { throw new Error('Network failure'); } });
  await act(async () => { root.root.findAllByType('input').find(i => i.props.type === 'number').props.onChange({ target: { value: '1' } }); });
  await act(async () => { root.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
  assert.equal(calls.length, 1);
  assert.ok(root.root.findAllByType('p').some(p => p.children.includes('Результат неизвестен')));
  assert.ok(root.root.findAllByType('button').find(b => b.children.includes('Отправить')).props.disabled);
  act(() => root.unmount());
});
test('dry stimulus preserves numeric zero', async () => {
  const { root, calls } = mount();
  await act(async () => { root.root.findAllByType('button').find(b => b.children.includes('Вода отсутствует')).props.onClick(); });
  assert.equal(calls[0][2].value, 0);
  assert.equal(calls[0][2].kind, 'WET');
  act(() => root.unmount());
});

test('failed state refresh disables control despite cached ONLINE state', () => {
  const { root } = mount({ stateError: new Error('State unavailable') });
  assert.ok(root.root.findAllByType('button').find(b => b.children.includes('Отправить')).props.disabled);
  act(() => root.unmount());
});
test('lost stimulus response does not fabricate ACCEPTED', async () => {
  const { root, calls } = mount({ stimulus: async () => { throw new Error('Lost response'); } });
  await act(async () => { root.root.findAllByType('button').find(b => b.children.includes('Вода отсутствует')).props.onClick(); });
  assert.equal(calls.length, 1);
  assert.ok(root.root.findAllByType('p').some(p => p.children.includes('Приём события не подтверждён. Проверяем результат. ')));
  assert.ok(!root.root.findAllByType('p').some(p => p.children.includes('Событие принято')));
  assert.ok(root.root.findAllByType('button').find(b => b.children.includes('Вода отсутствует')).props.disabled);
  act(() => root.unmount());
});


test('offline emulator can be restored without sending a device command', async () => {
  const { root, calls } = mount({ availability: 'OFFLINE' });
  const toggle = root.root.findAllByType('input').find(i => i.props.type === 'checkbox');
  assert.equal(toggle.props.checked, false);
  assert.equal(toggle.props.disabled, false);
  await act(async () => { await toggle.props.onChange({ target: { checked: true } }); });
  assert.deepEqual(calls, [['availability', 'device', 'ONLINE']]);
  act(() => root.unmount());
});

test('failed availability update shows an error without reporting success', async () => {
  const { root } = mount({ availabilityUpdate: async () => { throw new Error('Unavailable'); } });
  await act(async () => { await root.root.findAllByType('input').find(i => i.props.type === 'checkbox').props.onChange({ target: { checked: false } }); });
  assert.ok(root.root.findAllByType('p').some(p => p.children.some(c => typeof c === 'string' && c.includes('Обновите состояние'))));
  assert.equal(root.root.findAllByType('input').find(i => i.props.type === 'checkbox').props.checked, true);
  act(() => root.unmount());
});

test('Python source offers observation instead of writing Java WET state', async () => {
  const { root, calls } = mount({ provider: 'PYTHON' });
  assert.equal(root.root.findAllByType('button').filter(b => b.children.includes('Обнаружить воду') || b.children.includes('Вода отсутствует')).length, 0);
  await act(async () => root.root.findAllByType('button').find(b => b.children.includes('Проверить показание Python')).props.onClick());
  assert.equal(calls.length, 1);
  assert.equal(calls[0][0], 'python-poll');
  assert.equal(typeof calls[0][1], 'string');
  act(() => root.unmount());
});

test('Python observation stops when the checkbox is off or the panel unmounts', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const { root, calls } = mount({ provider: 'PYTHON' });
  const watch = root.root.findAllByType('input').find(input => input.props.type === 'checkbox' && input.props.onChange && input.parent?.children.some(child => typeof child === 'string' && child.includes('Наблюдать Python')));
  assert.ok(watch);
  await act(async () => watch.props.onChange({ target: { checked: true } }));
  await act(async () => t.mock.timers.tick(3000));
  assert.equal(calls.filter(call => call[0] === 'python-poll').length, 1);
  await act(async () => watch.props.onChange({ target: { checked: false } }));
  await act(async () => t.mock.timers.tick(6000));
  assert.equal(calls.filter(call => call[0] === 'python-poll').length, 1);
  await act(async () => watch.props.onChange({ target: { checked: true } }));
  act(() => root.unmount());
  await act(async () => t.mock.timers.tick(6000));
  assert.equal(calls.filter(call => call[0] === 'python-poll').length, 1);
  t.mock.timers.reset();
});
