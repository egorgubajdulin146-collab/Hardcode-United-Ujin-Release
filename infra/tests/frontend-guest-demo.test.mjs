import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
const require=createRequire(new URL('../../frontend/package.json',import.meta.url));
const React=require('react');
const {create,act}=require('react-test-renderer');
const ts=require('typescript');
function load(file,mocks){
  const {outputText}=ts.transpileModule(readFileSync(new URL(file,import.meta.url),'utf8'),{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}});
  const exports={};new Function('require','exports',outputText)(name=>name.endsWith('.css')?{default:{}}:mocks[name]??require(name),exports);return exports;
}
test('try without Ujin is an independent action, not a credential form submission',async()=>{
  const calls=[];
  const {LoginPage}=load('../../frontend/src/pages/LoginPage.tsx',{
    'react-router-dom':{useNavigate:()=>path=>calls.push(['navigate',path])},
    '../api/sessionContext':{useSession:()=>({demoAvailable:true,onLogin:guest=>calls.push(['session',guest])})},
    '../api/authClient':{fetchCsrf:async()=>calls.push(['csrf']),loginDemo:async()=>calls.push(['demo']),login:async()=>{throw Error('credentials must not be sent');},AuthError:class extends Error{}},
  });
  let root;act(()=>{root=create(React.createElement(LoginPage));});
  const button=root.root.findAllByType('button').find(b=>b.children.includes('Попробовать без Ujin'));
  assert.equal(button.props.type,'button');
  await act(async()=>{await button.props.onClick();});
  assert.deepEqual(calls,[['csrf'],['demo'],['session',true],['navigate','/devices']]);
  act(()=>root.unmount());
});
test('guest restore uses server session metadata and forces the interactive API source',async()=>{
  const Context=React.createContext({});const sources=[];let state;
  const client={clear(){},cancelQueries(){}};
  const {SessionProvider}=load('../../frontend/src/api/session.tsx',{
    './sessionContext':{SessionContext:Context},
    './authClient':{fetchCsrf:async()=>{},clearCsrf(){}},
    './dataSource':{setDataSource:source=>sources.push(source)},
    '@tanstack/react-query':{useQueryClient:()=>client},
  });
  const original=globalThis.fetch;
  globalThis.fetch=async url=>{assert.equal(url,'/api/auth/session');return Response.json({authenticated:true,demo:true,demoAvailable:true});};
  function Read(){state=React.useContext(Context);return null;}
  let root;
  try{
    await act(async()=>{root=create(React.createElement(SessionProvider,null,React.createElement(Read)));});
    assert.equal(state.authed,true);assert.equal(state.demo,true);assert.equal(state.demoAvailable,true);assert.equal(state.checking,false);assert.deepEqual(sources,['api']);
    act(()=>state.onLogout());assert.equal(state.authed,false);assert.equal(state.demo,false);
  }finally{globalThis.fetch=original;act(()=>root.unmount());}
});
