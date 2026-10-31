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
const initial = { id: 'rule', version: 1, title: 'Original', enabled: true, executionMode: 'LOCAL',
  trigger: { deviceId: 'sensor', signalKey: 'wet', eventKey: 'changed', operator: 'EQ', value: 1 },
  action: { deviceId: 'lamp', signalKey: 'power', value: 1 } };
const devices = [
  { id: 'sensor', title: 'Sensor', signals: [{ key: 'wet', management: 'r' }], events: [{ key: 'changed', signalKey: 'wet' }] },
  { id: 'lamp', title: 'Lamp', signals: [{ key: 'power', management: 'rw' }], events: [] },
];
function loadComponent(filename, mocks) {
  const source = readFileSync(filename, 'utf8');
  const { outputText } = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } });
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
const pagePath = fileURLToPath(new URL('../../frontend/src/pages/ScenariosCrudPage.tsx', import.meta.url));
for (const choice of ['Оставить', 'Заменить']) {
  test(`conflict reload: ${choice} keeps fields and expectedVersion consistent through parent`, async () => {
    const writes = [];
    const fresh = { ...initial, version: 2, title: 'Fresh' };
    class ApiError extends Error { constructor() { super('Conflict'); this.status = 409; } }
    const mocks = {
      '../mocks/data': { mockDevices: [], mockScenarioPage: { items: [], total: 0 } },
      '../api/client': { ApiError, NetworkError: class extends Error {}, TimeoutError: class extends Error {} },
      '../api/endpoints': { endpoints: { scenarios: {
        update: async (_id, data) => { writes.push(data); throw new ApiError(); }, get: async () => fresh,
      } } },
      '../api/dataSource': { isMock: () => false },
      '../api/sessionContext': { useSession: () => ({ terminateSession() {} }) },
      '@tanstack/react-query': {
        useQuery: ({ queryKey }) => ({ data: queryKey[0] === 'all-devices' ? devices : { items: [initial], total: 1 }, refetch() {} }),
        useQueryClient: () => ({ invalidateQueries() {} }),
      },
    };
    const { ScenariosCrudPage } = loadComponent(pagePath, mocks);
    let root;
    await act(async () => { root = create(React.createElement(ScenariosCrudPage)); });
    await act(async () => { root.root.findByProps({ title: 'Редактировать' }).props.onClick(); });
    const titleInput = () => root.root.findAllByType('input').find(n => n.props.maxLength === 120);
    await act(async () => { titleInput().props.onChange({ target: { value: 'My draft' } }); });
    const submit = async () => act(async () => { await root.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
    await submit();
    assert.equal(titleInput().props.value, 'My draft');
    const reload = root.root.findAllByType('button').find(n => n.children.some(c => c === ' Загрузить актуальную версию'));
    await act(async () => { await reload.props.onClick(); });
    const button = root.root.findAllByType('button').find(n => n.children.includes(choice));
    await act(async () => { button.props.onClick(); });
    assert.equal(titleInput().props.value, choice === 'Оставить' ? 'My draft' : 'Fresh');
    await submit();
    assert.equal(writes[1].expectedVersion, choice === 'Оставить' ? 1 : 2);
    assert.equal(writes[1].title, choice === 'Оставить' ? 'My draft' : 'Fresh');
    await act(async () => root.unmount());
  });
}


for (const legacy of [false, true]) {
  test(`UJIN ${legacy ? 'legacy enabled rule' : 'draft'} cannot be submitted enabled`, async () => {
    const writes = [];
    const filename = fileURLToPath(new URL('../../frontend/src/components/ScenarioForm.tsx', import.meta.url));
    const { ScenarioForm } = loadComponent(filename, {});
    let root;
    await act(async () => { root = create(React.createElement(ScenarioForm, {
      initial: { ...initial, executionMode: 'UJIN', enabled: legacy }, devices,
      onSubmit: async data => writes.push(data), onCancel() {},
    })); });
    const toggle = () => root.root.findAllByType('input').find(n => n.props.type === 'checkbox');
    assert.equal(toggle().props.checked, legacy);
    assert.equal(toggle().props.disabled, !legacy);
    assert.equal(root.root.findAllByType('select')[0].props.value, 'UJIN');
    assert.ok(root.root.findAllByProps({ role: 'status' }).length);
    await act(async () => { await root.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
    assert.equal(writes.length, legacy ? 0 : 1);
    if (legacy) await act(async () => toggle().props.onChange({ target: { checked: false } }));
    await act(async () => { await root.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
    assert.equal(writes.at(-1).enabled, false);
    assert.equal(writes.at(-1).executionMode, 'UJIN');
    await act(async () => root.unmount());
  });
}

test('choosing UJIN explicitly creates a disabled draft', async () => {
  const filename = fileURLToPath(new URL('../../frontend/src/components/ScenarioForm.tsx', import.meta.url));
  const { ScenarioForm } = loadComponent(filename, {});
  const writes = [];
  let root;
  await act(async () => { root = create(React.createElement(ScenarioForm, { initial, devices, onSubmit: async d => writes.push(d), onCancel() {} })); });
  await act(async () => root.root.findAllByType('select')[0].props.onChange({ target: { value: 'UJIN' } }));
  await act(async () => root.root.findByType('form').props.onSubmit({ preventDefault() {} }));
  assert.equal(writes[0].executionMode, 'UJIN');
  assert.equal(writes[0].enabled, false);
  await act(async () => root.unmount());
});

test('human-readable binary choices preserve numeric scenario payload', async () => {
  const fixture = [
    { id: 'leak', title: 'Датчик', family: 'leak', signals: [{ key: 'leak', title: 'Протечка', management: 'r', enumValues: [0, 1] }], events: [{ key: 'changed', title: 'Изменение', signalKey: 'leak' }] },
    { id: 'light', title: 'Лампа', family: 'light', signals: [{ key: 'lamp', title: 'Свет', management: 'rw', enumValues: [0, 1] }], events: [] },
  ];
  const writes = [];
  const { ScenarioForm } = loadComponent(fileURLToPath(new URL('../../frontend/src/components/ScenarioForm.tsx', import.meta.url)), {});
  let root;
  act(() => { root = create(React.createElement(ScenarioForm, {
    initial: { title: 'Протечка', enabled: true, executionMode: 'LOCAL', trigger: { deviceId: 'leak', signalKey: 'leak', eventKey: 'changed', operator: 'EQ', value: 0 }, action: { deviceId: 'light', signalKey: 'lamp', value: 0 } },
    devices: fixture, onSubmit: async data => writes.push(data), onCancel() {},
  })); });
  const choice = text => root.root.findAllByType('select').find(select => select.findAllByType('option').some(option => option.children.includes(text)));
  assert.ok(choice('1 — Протечка'));
  assert.ok(choice('0 — Свет выключен'));
  act(() => choice('1 — Протечка').props.onChange({ target: { value: '1' } }));
  act(() => choice('1 — Свет включён').props.onChange({ target: { value: '1' } }));
  await act(async () => { await root.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
  assert.equal(writes[0].trigger.value, 1);
  assert.equal(writes[0].action.value, 1);
  assert.equal(writes[0].trigger.signalKey, 'leak');
  act(() => root.unmount());
});

test('numeric value field keeps arbitrary values and ranges without inventing binary meanings', () => {
  const { SignalValueField } = loadComponent(fileURLToPath(new URL('../../frontend/src/components/SignalValueField.tsx', import.meta.url)), {});
  let root;
  act(() => { root = create(React.createElement(SignalValueField, {
    signal: { key: 'temperature', min: -20, max: 50, unit: '°C' }, family: 'custom', label: 'Температура', value: '2.5', onChange() {},
  })); });
  const input = root.root.findByType('input');
  assert.equal(input.props.value, '2.5');
  assert.equal(input.props.min, -20);
  assert.equal(input.props.max, 50);
  assert.equal(input.props.step, 'any');
  act(() => root.unmount());
});
