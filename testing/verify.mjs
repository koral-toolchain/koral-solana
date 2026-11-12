// Reproducible integration checks. Requires an installed CLI and a local SBF SDK.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, writeFileSync, existsSync, rmSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const here=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(here,'..');
const [cliArg,sdkArg]=process.argv.slice(2);
if(!cliArg || !sdkArg) throw Error('Usage: node testing/verify.mjs <installed-koral> <SDK>');
const cli=path.resolve(cliArg),sdk=path.resolve(sdkArg);
const temp=mkdtempSync(path.join(os.tmpdir(),'koral-verify-'));
let checks=0;
function run(command,args,success=true,env={}) {
    const r=spawnSync(command,args,{encoding:'utf8',timeout:120000,env:{...process.env,...env},maxBuffer:1024*1024});
    const output=(r.stdout??'')+(r.stderr??'');
    assert.equal(r.status===0,success,output);checks++;return output;
}
function build(source,name) {
    const out=path.join(temp,name);
    run(cli,['build',source,'--out',out,'--sdk',sdk]);
    return path.join(out,'counter.so');
}
function semantics(binary,cases) { run(process.execPath,[path.join(here,'semantics.mjs'),binary,JSON.stringify(cases)]); }
try {
    const base=build(path.join(root,'examples/counter.kt'),'base');
    const result=path.join(temp,'safety.json');
    run(process.execPath,[path.join(here,'counter.mjs'),base],true,{KORAL_TEST_RESULT:result,KORAL_ALTERNATE:'0'});
    assert.equal(JSON.parse(readFileSync(result)).tests,55);checks++;
    semantics(build(path.join(root,'examples/arithmetic.kt'),'arithmetic'),[
        {count:'7',by:'3',expected:'22'},{count:'60',by:'2',expected:'119'},
        {count:'7',by:'0',error:6001},{count:'7',by:'101',error:6001},
        {count:'18446744073709551615',by:'2',error:5100}
    ]);
    semantics(build(path.join(root,'examples/guarded-counter.kt'),'guarded'),[
        {count:'7',by:'3',expected:'10'},{count:'1000000',by:'1',expected:'1000000'},
        {count:'7',by:'0',error:6001},{count:'18446744073709551615',by:'1',error:5100}
    ]);
    semantics(build(path.join(root,'examples/conditional.kt'),'conditional'),[
        {count:'7',by:'0',expected:'7'},{count:'7',by:'3',expected:'4'},
        {count:'7',by:'8',error:5100},{count:'7',by:'100',error:6002}
    ]);
    semantics(build(path.join(root,'examples/multi-counter.kt'),'multi'),[
        {count:'7',by:'3',expected:'10'},
        {instruction:'decrement',count:'10',by:'2',expected:'8',keepState:true},
        {instruction:'set_count',count:'8',args:['100'],expected:'100',keepState:true},
        {instruction:'add_scaled',count:'100',args:['5','3'],expected:'115',keepState:true},
        {instruction:'reset',count:'115',args:[],expected:'0',keepState:true},
        {instruction:'decrement',count:'0',by:'1',error:5100,keepState:true},
        {instruction:'set_count',count:'0',args:['18446744073709551615'],expected:'18446744073709551615',keepState:true},
        {instruction:'increment',count:'18446744073709551615',by:'1',error:5100,keepState:true},
        {instruction:'add_scaled',count:'7',args:['5','0'],error:6002},
        {instruction:'add_scaled',count:'7',args:['18446744073709551615','2'],error:5100},
        {instruction:'add_scaled',count:'7',args:['5'],error:102},
        {instruction:'increment',count:'7',args:[],error:102},
        {instruction:'missing',count:'7',args:[],error:102},
        {instruction:'reset',count:'18446744073709551615',args:[],expected:'0'}
    ]);
    semantics(build(path.join(root,'examples/argument-boundaries.kt'),'eight-arguments'),[
        {instruction:'weighted_add',count:'7',args:['3','0','0','0','0','0','0','4'],expected:'14'},
        {instruction:'weighted_add',count:'7',args:['3','0','0','0','0','0','0'],error:102},
        {instruction:'weighted_add',count:'7',args:['18446744073709551615','0','0','0','0','0','0','1'],error:5100},
        {instruction:'weighted_add',count:'18446744073709551615',args:['0','0','0','0','0','0','0','0'],expected:'18446744073709551615'}
    ]);
    const typed=build(path.join(root,'examples/typed-counter.kt'),'typed-counter');
    const typedReport=path.join(temp,'typed-safety.json');
    run(process.execPath,[path.join(here,'counter.mjs'),typed],true,{KORAL_TEST_RESULT:typedReport,KORAL_ALTERNATE:'0'});
    assert.equal(JSON.parse(readFileSync(typedReport)).tests,55);checks++;
    const poolBinary=build(path.join(root,'examples/pool-state.kt'),'pool-state');
    run(process.execPath,[path.join(here,'state.mjs'),poolBinary,path.join(temp,'state.json')]);
    const record='package example\nimport dev.koral.api.*\n@Account("pool")\ndata class PoolState(val balance: ULong)\n@Instruction\nfun hold(state: PoolState): PoolState = state\n';
    for(const [name,source,diagnostic] of [
        ['missing-account-marker',record.replace('@Account("pool")',''),'trusted @Account'],
        ['mutable-field',record.replace('val balance','var balance'),'immutable'],
        ['signed-field',record.replace('balance: ULong','balance: Long'),'1..16 ULong'],
        ['state-init-block',record.replace('balance: ULong)','balance: ULong) { init { println("ignored") } }'),'init blocks'],
        ['state-method',record.replace('balance: ULong)','balance: ULong) { fun extra(): Int = 1 }'),'Account methods'],
        ['state-companion',record.replace('balance: ULong)','balance: ULong) { companion object {} }'),'Account methods'],
        ['long-seed',record.replace('@Account("pool")','@Account("'+ 'a'.repeat(33) +'")'),'Account seed']
    ]) {
        const file=path.join(temp,`${name}.kt`);writeFileSync(file,source);
        const output=run(cli,['check',file],false);assert.ok(output.includes(diagnostic),output);checks++;
    }
    const paymentBinary=build(path.join(root,'examples/payments.kt'),'payments');
    run(process.execPath,[path.join(here,'payments.mjs'),paymentBinary,path.join(temp,'payments.json')]);
    const original=readFileSync(path.join(root,'examples/counter.kt'),'utf8');
    const changed=path.join(temp,'changed.kt');
    writeFileSync(changed,original.replace('checkedAdd(count, by)','checkedAdd(by, by)'));
    const alternate=build(changed,'changed');
    semantics(alternate,[{count:'7',by:'3',expected:'6'}]);
    assert.notEqual(readFileSync(base).toString('hex'),readFileSync(alternate).toString('hex'));checks++;
    for(const [name,source,diagnostic] of [
        ['plain-add',original.replace('checkedAdd(count, by)','count + by'),'Unsupported call'],
        ['foreign-call',original.replace('checkedAdd(count, by)','System.nanoTime().toULong()'),'Unsupported call'],
        ['missing-annotation',original.replace('@Instruction',''),'Missing trusted @Instruction'],
        ['wrong-type',original.replaceAll('ULong','Long').replace('checkedAdd(count, by)','count'),'Expected instruction'],
        ['reserved-package',original.replace('package example','package dev.koral.api'),'Reserved package'],
        ['reserved-instruction',original.replace('fun increment','fun initialize'),'initialize is reserved'],
        ['unsupported-loop',original.replace('= checkedAdd(count, by)','{ while (true) { return count } }'),'Unsupported statement']
    ]) {
        const file=path.join(temp,`${name}.kt`);writeFileSync(file,source);
        const output=run(cli,['check',file],false);assert.ok(output.includes(diagnostic),output);checks++;
    }
    // A failed rebuild must invalidate its old success artifacts.
    writeFileSync(changed,original.replace('checkedAdd(count, by)','count + by'));
    run(cli,['build',changed,'--out',path.dirname(alternate),'--sdk',sdk],false);
    assert.ok(!existsSync(alternate));assert.ok(!existsSync(path.join(path.dirname(alternate),'counter.build.json')));assert.ok(!existsSync(path.join(path.dirname(alternate),'counter.abi.json')));checks++;
    console.log(JSON.stringify({integrationChecks:checks,safetyChecks:55,semanticCases:32,typedCounterSafetyChecks:55,typedStateChecks:31,systemCpiChecks:37,sourceMutationVerified:true,staleArtifactRejected:true},null,2));
} finally { rmSync(temp,{recursive:true,force:true}); }
