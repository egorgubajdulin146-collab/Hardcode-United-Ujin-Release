import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {test} from 'node:test';
const require=createRequire(new URL('../../frontend/package.json',import.meta.url));
const ts=require('typescript');
test('Python controls call backend API routes, not SPA paths',async()=>{
 const calls=[];const exports={};
 const source=readFileSync(new URL('../../frontend/src/api/endpoints.ts',import.meta.url),'utf8');
 const {outputText}=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}});
 new Function('require','exports',outputText)(name=>name==='./client'?{api:{post:async(path,body)=>calls.push({path,body})}}:require(name),exports);
 await exports.endpoints.localPython.poll('event-1');
 await exports.endpoints.localPython.check('command-1');
 await exports.endpoints.localPython.checkEvent('event-1');
 assert.deepEqual(calls.map(c=>c.path),['/api/v1/local-python/poll','/api/v1/local-python/commands/command-1/check','/api/v1/local-python/events/event-1/check']);
 assert.deepEqual(calls[0].body,{eventId:'event-1'});
});
