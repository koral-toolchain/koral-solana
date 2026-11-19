import assert from 'node:assert/strict';
import {mkdtempSync,mkdirSync,writeFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import {formatCustomTestError} from './test-errors.mjs';
const root=mkdtempSync(path.join(tmpdir(),'koral diagnostics '));
let checks=0;
try {
 const project=path.join(root,'my (custom) project'),tests=path.join(project,'tests');mkdirSync(tests,{recursive:true});
 const script=path.join(tests,'program.test.mjs');
 writeFileSync(script,"import assert from 'node:assert/strict';\nassert.equal(20n,30n,'exact price');\n");
 let formatted;
 try{await import(pathToFileURL(script).href);}catch(e){formatted=formatCustomTestError(e,script);}
 assert.ok(formatted.includes('exact price'));checks++;
 assert.match(formatted,/tests[/\\]program\.test\.mjs:2:\d+/);checks++;
 assert.ok(!formatted.includes('ModuleJob')&&!formatted.includes('%20'));checks++;
 // A helper can be consumer-owned even outside the project directory.
 const helper=path.join(root,'shared helper.mjs');writeFileSync(helper,"throw Error('helper failed');\n");
 try{await import(pathToFileURL(helper).href);}catch(e){formatted=formatCustomTestError(e,script);}
 assert.ok(formatted.includes(helper+':1:'));checks++;
 const error=new Error('bad input');
 error.stack='Error: bad input\n    at invoke ('+new URL('./client.mjs',import.meta.url).href+':1:2)\n    at internal (node:internal/modules/esm/module_job:1:2)\n    at pkg ('+pathToFileURL(path.join(project,'node_modules/pkg/index.mjs')).href+':3:4)';
 formatted=formatCustomTestError(error,script);
 assert.ok(!formatted.includes('client.mjs')&&!formatted.includes('node_modules')&&!formatted.includes('module_job'));checks++;
 assert.ok(formatted.includes('source location unavailable'));checks++;
 assert.ok(!/program\.test\.mjs:\d+/.test(formatted));checks++;
 error.stack='Error: bad input\n'+[1,1,2,3,4].map(n=>'    at '+pathToFileURL(path.join(tests,'helper '+n+'.mjs')).href+':2:3').join('\n');
 formatted=formatCustomTestError(error,script);
 assert.equal(formatted.split('\n').filter(v=>v.startsWith('  at ')).length,3);checks++;
 assert.ok(!formatted.includes('helper 4'));checks++;
 formatted=formatCustomTestError('plain thrown value',script);
 assert.ok(formatted.includes('plain thrown value')&&formatted.includes('source location unavailable'));checks++;
 console.log(JSON.stringify({testFailureChecks:checks,actualNodeAssertionLocation:true}));
} finally {rmSync(root,{recursive:true,force:true});}
