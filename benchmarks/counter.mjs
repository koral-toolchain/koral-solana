// Preserve upstream Counter scenarios and add the locally compiled Kotlin-derived SBF.
// Upstream run.ts is MIT licensed; see references/typesol-benchmark/benchmarks/LICENSE.
import {readFileSync,writeFileSync,mkdirSync,rmSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const [benchArg,binaryArg,outputArg]=process.argv.slice(2);
if(!benchArg || !binaryArg || !outputArg) throw Error('Usage: node counter.mjs <upstream-benchmarks> <counter.so> <output-directory>');
const bench=path.resolve(benchArg);
const output=path.resolve(outputArg);
const binary=path.resolve(binaryArg);
mkdirSync(output,{recursive:true});
const upstream=readFileSync(path.join(bench,'run.ts'),'utf8');let source=upstream;
function replace(a,b){if(!source.includes(a))throw Error('Upstream mismatch: '+a);source=source.replace(a,b);}
replace('const IMPLS = ["typesol", "anchor", "native", "pinocchio"] as const;','const IMPLS = ["typesol", "anchor", "native", "pinocchio", "koral"] as const;');
replace('switch (impl) {',`switch (impl) { case "koral": return {so:${JSON.stringify(binary)},id};`);
replace('  ["vault", vault, ["open", "deposit", "withdraw", "shut"]],\n','');
replace('  ["escrow", escrow, ["make", "take", "refund"]],\n','');
replace('const resultsFile = path.join(HERE, "results.json");',`const resultsFile=${JSON.stringify(path.join(output,'results.json'))};`);
replace('const previous = existsSync(resultsFile) ? JSON.parse(readFileSync(resultsFile, "utf8")) : {};','const previous={};');
const runner=path.join(bench,`.local-koral-${process.pid}.ts`);writeFileSync(runner,source);
try{
 const r=spawnSync(process.execPath,[runner],{cwd:bench,encoding:'utf8',maxBuffer:8*1024*1024});
 const log=(r.stdout??'')+(r.stderr??'');writeFileSync(path.join(output,'run.log'),log);process.stdout.write(log);
 const sha=x=>createHash('sha256').update(x).digest('hex');
 writeFileSync(path.join(output,'provenance.json'),JSON.stringify({generated:new Date().toISOString(),exitCode:r.status,upstreamRunSha256:sha(upstream),executedRunnerSha256:sha(source),koralBinarySha256:sha(readFileSync(binary)),scope:'Counter only; unchanged upstream initialization, 20 increments, unauthorized/overflow probes and final-state comparison; added fifth implementation'},null,2)+'\n');
 if(r.status!==0)process.exitCode=r.status??1;
}finally{rmSync(runner,{force:true});}
