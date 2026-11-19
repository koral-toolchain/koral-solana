// Exercise native Node parse diagnostics through the actual dispatcher, once per run.
import assert from 'node:assert/strict';
import {mkdtempSync,mkdirSync,writeFileSync,readFileSync,existsSync,rmSync,realpathSync} from 'node:fs';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
const root=realpathSync(mkdtempSync(path.join(tmpdir(),'koral syntax (consumer) ')));
const dispatcher=fileURLToPath(new URL('./suite.mjs',import.meta.url));
let checks=0;
const check=(condition,message)=>{assert.ok(condition,message);checks++;};
function fixture(name,source,helper) {
 const project=path.join(root,name),tests=path.join(project,'tests');mkdirSync(tests,{recursive:true});
 const script=path.join(tests,'program.test.mjs'),report=path.join(project,'result.json');
 writeFileSync(script,source);if(helper)writeFileSync(path.join(tests,'helper.mjs'),helper);
 const result=spawnSync(process.execPath,[dispatcher,'custom',path.join(project,'unused.so'),report,script],{cwd:project,encoding:'utf8'});
 check(result.status!==0,'a broken test must fail');
 check(!existsSync(report),'a failed script must not create a success report');
 check(!result.stderr.includes('ModuleJob')&&!result.stderr.includes('compileSourceTextModule'),'internal loader frames omitted');
 return {project,script,report,...result};
}
try {
 const entry=fixture('entry syntax',"const ok=1;\nconst broken = ;\n");
 check(entry.stderr.includes('program.test.mjs:2'),'native entry line preserved');
 check(entry.stderr.includes('SyntaxError')&&entry.stderr.includes('const broken = ;'),'native code frame preserved');
 const imported=fixture('imported syntax',"import './helper.mjs';\nimport {writeFileSync} from 'node:fs';\nwriteFileSync('marker','ran');\n",'export const ok=1;\nexport const broken = ;\n');
 check(imported.stderr.includes('helper.mjs:2'),'actual imported helper line preserved');
 check(!existsSync(path.join(imported.project,'marker')),'entry does not execute after an imported parse failure');
 const external=path.join(root,'shared helper.mjs');writeFileSync(external,'export const ok=1;\nexport const broken = ;\n');
 const outside=fixture('outside project',"import '../../shared helper.mjs';\n");
 check(outside.stderr.includes('shared%20helper.mjs:2')||outside.stderr.includes('shared helper.mjs:2'),'external helper source remains identifiable');
 const runtime=fixture('runtime syntax',"import {appendFileSync} from 'node:fs';\nappendFileSync('marker','x');\nJSON.parse('{');\n");
 check(runtime.stderr.includes('Custom test failed:')&&/program\.test\.mjs:3:\d+/.test(runtime.stderr),'runtime SyntaxError keeps normal user frames');
 check(readFileSync(path.join(runtime.project,'marker'),'utf8')==='x','diagnostics never rerun the user module');
 const missing=fixture('missing import',"import './missing-helper.mjs';\n");
 check(missing.stderr.includes('missing-helper.mjs')&&missing.stderr.includes('program.test.mjs'),'missing import identifies requested module and entry');
 console.log(JSON.stringify({syntaxDiagnosticChecks:checks,nativeSourceFrames:true,singleExecution:true}));
} finally {rmSync(root,{recursive:true,force:true});}
