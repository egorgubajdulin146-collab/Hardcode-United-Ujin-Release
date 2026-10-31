import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
const require = createRequire(new URL('../../frontend/package.json', import.meta.url));
const React = require('react');
const { create, act } = require('react-test-renderer');
const ts = require('typescript');
const { outputText } = ts.transpileModule(
  readFileSync(new URL('../../frontend/src/components/DeviceForm.tsx', import.meta.url), 'utf8'),
  { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }
);
const exports = {};
new Function('require', 'exports', outputText)(name => name.endsWith('.css') ? { default: {} } : require(name), exports);
function mount(initial) {
  const submissions = [];
  let root;
  act(() => { root = create(React.createElement(exports.DeviceForm, { initial, onSubmit: async data => submissions.push(data), onCancel() {} })); });
  return { root, submissions };
}
const templateButton = (root, title) => root.root.findAllByType('button').find(b => b.children.includes(title));
for (const [title, family, key] of [['Датчик протечки', 'leak', 'leak'], ['Сигнальная лампа', 'light', 'lamp']]) {
  test(`${family} template submits numeric enum and a usable declaration only after explicit submit`, async () => {
    const { root, submissions } = mount();
    act(() => templateButton(root, title).props.onClick());
    assert.equal(submissions.length, 0);
    await act(async () => { await root.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
    assert.equal(submissions.length, 1);
    const data = submissions[0];
    assert.equal(data.family, family);
    assert.deepEqual(data.signals.find(s => s.key === key).enumValues, [0, 1]);
    assert.equal(data.signals.find(s => s.key === key).management, family === 'leak' ? 'r' : 'rw');
    if (family === 'leak') assert.ok(data.events.some(e => e.signalKey === key && e.emission === 'CHANGE'));
    assert.equal('expectedVersion' in data, false);
    act(() => root.unmount());
  });
}
test('template cannot replace a partially completed form', async () => {
  const { root, submissions } = mount();
  act(() => root.root.findAllByType('input')[0].props.onChange({ target: { value: 'Моё устройство' } }));
  assert.equal(templateButton(root, 'Датчик протечки').props.disabled, true);
  act(() => templateButton(root, 'Датчик протечки').props.onClick());
  await act(async () => { await root.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
  assert.equal(submissions[0].title, 'Моё устройство');
  assert.deepEqual(submissions[0].signals, []);
  act(() => root.unmount());
});
test('editing an existing declaration does not offer templates', () => {
  const { root } = mount({ title: 'Existing', family: 'light', signals: [], events: [], expectedVersion: 2 });
  assert.equal(templateButton(root, 'Датчик протечки'), undefined);
  act(() => root.unmount());
});
