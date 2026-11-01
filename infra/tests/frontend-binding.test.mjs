import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
const require = createRequire(new URL('../../frontend/package.json', import.meta.url));
const React = require('react');
const { create, act } = require('react-test-renderer');
const ts = require('typescript');

class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail ?? 'error');
    this.status = status;
    this.problem = problem;
  }
}

function mount({ binding, bindingError, putBinding } = {}) {
  const calls = [];
  const endpoints = {
    devices: {
      getBinding: async () => {
        calls.push(['getBinding']);
        if (bindingError) throw bindingError;
        return binding;
      },
      putBinding: async (id, data) => {
        calls.push(['putBinding', id, data]);
        return putBinding
          ? putBinding(id, data)
          : { deviceId: id, serialNumber: data.serialNumber, signalMappings: data.signalMappings, version: (data.expectedVersion ?? 0) + 1 };
      },
    },
  };
  const source = readFileSync(new URL('../../frontend/src/components/PlatformBindingSection.tsx', import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  });
  const mocks = {
    '../api/endpoints': { endpoints },
    '../api/client': { ApiError, NetworkError: class extends Error {}, TimeoutError: class extends Error {} },
    '@tanstack/react-query': {
      useQueryClient: () => ({ invalidateQueries() {} }),
      useQuery: () =>
        bindingError
          ? { data: undefined, error: bindingError, isLoading: false }
          : { data: binding, error: undefined, isLoading: false },
    },
  };
  const exports = {};
  new Function('require', 'exports', outputText)(
    name => (name.endsWith('.css') ? { default: {} } : mocks[name] ?? require(name)),
    exports
  );
  const signals = [{ key: 'leak', title: 'Leak' }, { key: 'lamp', title: 'Lamp' }];
  let root;
  act(() => {
    root = create(
      React.createElement(exports.PlatformBindingSection, {
        deviceId: 'device-1',
        signals,
        platformLink: 'NOT_CONFIGURED',
      })
    );
  });
  return { root, calls };
}

async function fill(root, label, text) {
  const input = root.root.findAllByType('input').find(i => i.props['aria-label'] === label || i.props.id === label);
  assert.ok(input, `input ${label}`);
  await act(async () => {
    input.props.onChange({ target: { value: text } });
  });
}

test('first binding sends SN as string without expectedVersion', async () => {
  const { root, calls } = mount({ bindingError: new ApiError(404, { code: 'PLATFORM_NOT_BOUND', detail: 'no binding' }) });
  assert.ok(root.root.findAllByType('p').some(p => String(p.children).includes('Привязка отсутствует')));
  await fill(root, 'binding-sn', '9007199254740993123');
  await fill(root, 'Имя сигнала Ujin для leak', 'leak');
  await act(async () => {
    root.root.findByType('form').props.onSubmit({ preventDefault() {} });
  });
  const put = calls.find(c => c[0] === 'putBinding');
  assert.ok(put);
  assert.equal(put[2].serialNumber, '9007199254740993123');
  assert.equal(typeof put[2].serialNumber, 'string');
  assert.deepEqual(put[2].signalMappings, [{ signalKey: 'leak', platformName: 'leak' }]);
  assert.equal('expectedVersion' in put[2], false);
  assert.ok(root.root.findAllByType('p').some(p => String(p.children).includes('Привязка сохранена')));
  act(() => root.unmount());
});

test('replace binding sends expectedVersion from loaded binding', async () => {
  const { root, calls } = mount({
    binding: {
      deviceId: 'device-1',
      serialNumber: 'sn-target',
      signalMappings: [{ signalKey: 'leak', platformName: 'leak' }],
      version: 3,
    },
  });
  await act(async () => {
    root.root.findByType('form').props.onSubmit({ preventDefault() {} });
  });
  const put = calls.find(c => c[0] === 'putBinding');
  assert.ok(put);
  assert.equal(put[2].expectedVersion, 3);
  assert.equal(put[2].serialNumber, 'sn-target');
  act(() => root.unmount());
});

test('invisible serial number keeps previous binding message', async () => {
  const { root } = mount({
    binding: {
      deviceId: 'device-1',
      serialNumber: 'sn-target',
      signalMappings: [{ signalKey: 'leak', platformName: 'leak' }],
      version: 1,
    },
    putBinding: async () => {
      throw new ApiError(422, { code: 'UJIN_SN_NOT_VISIBLE', detail: 'not visible' });
    },
  });
  await act(async () => {
    root.root.findByType('form').props.onSubmit({ preventDefault() {} });
  });
  assert.ok(
    root.root.findAllByType('p').some(p => String(p.children).includes('не видит этот SN'))
  );
  act(() => root.unmount());
});

test('empty mappings are rejected locally before saving', async () => {
  const { root, calls } = mount({
    binding: {
      deviceId: 'device-1',
      serialNumber: 'sn-target',
      signalMappings: [{ signalKey: 'leak', platformName: 'leak' }],
      version: 1,
    },
  });
  await fill(root, 'Имя сигнала Ujin для leak', '');
  await act(async () => {
    root.root.findByType('form').props.onSubmit({ preventDefault() {} });
  });
  assert.equal(calls.filter(c => c[0] === 'putBinding').length, 0);
  assert.ok(
    root.root.findAllByType('p').some(p => String(p.children).includes('хотя бы одно соответствие'))
  );
  act(() => root.unmount());
});

test('numeric platform signal identifier is rejected before saving', async () => {
  const { root, calls } = mount({
    binding: {
      deviceId: 'device-1',
      serialNumber: 'sn-target',
      signalMappings: [{ signalKey: 'leak', platformName: '1704887761001995898' }],
      version: 1,
    },
  });
  await act(async () => {
    root.root.findByType('form').props.onSubmit({ preventDefault() {} });
  });
  assert.equal(calls.filter(c => c[0] === 'putBinding').length, 0);
  assert.ok(root.root.findAllByType('p').some(p => String(p.children).includes('число')));
  assert.ok(root.root.findAllByType('small').some(p => String(p.children).includes('числовой ID')));
  act(() => root.unmount());
});
