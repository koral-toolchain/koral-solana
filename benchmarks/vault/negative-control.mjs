// Prove the comparison rejects a real SBF program that omits the withdrawal.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync,spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),args=process.argv.slice(2);
function option(name,fallback){const i=args.indexOf(name);return i<0?fallback:args[i+1];}
const sdk=option('--sdk',process.env.KORAL_SBF_SDK),comparison=path.resolve(option('--comparison-out',path.join(root,'build/vault-comparison'))),cli=path.resolve(option('--cli',path.join(root,'cli/build/install/koral/bin/koral')));
if(!sdk)throw Error('Pass --sdk or set KORAL_SBF_SDK');
const folder=path.join(comparison,'negative-control');fs.mkdirSync(folder,{recursive:true});
const source=fs.readFileSync(path.join(root,'benchmarks/vault/kotlin/vault.kt'),'utf8');
const changed=source.replace('    withdrawLamports(recipient, amount)\n','');
if(changed===source)throw Error('The intended mutation no longer matches source');
const file=path.join(folder,'vault.kt');fs.writeFileSync(file,changed);
execFileSync(cli,['build',file,'--out',path.join(folder,'compiled'),'--sdk',path.resolve(sdk)],{cwd:root,stdio:'inherit'});
const manifest=JSON.parse(fs.readFileSync(path.join(folder,'compiled/counter.build.json'))),original=JSON.parse(fs.readFileSync(path.join(comparison,'koral/counter.build.json')));
if(manifest.compilerJarSha256!==original.compilerJarSha256||manifest.clang!==original.clang)throw Error('Negative control must use the same compiler and SDK');
const result=spawnSync(process.execPath,[path.join(root,'testing/vault-benchmark.mjs'),path.join(comparison,'rust/native_vault_bench.so'),path.join(comparison,'rust/pinocchio_vault_bench.so'),path.join(folder,'compiled/counter.so'),path.join(folder,'results'),path.join(comparison,'typesol/vault.so')],{cwd:root,env:{...process.env,KORAL_VAULT_AUTHORITY_SEED:'1'},encoding:'utf8',timeout:120000});
const output=(result.stdout??'')+(result.stderr??'');fs.writeFileSync(path.join(folder,'rejection.log'),output);
if(result.status===0||!output.includes('withdraw-1: koral exact vault debit'))throw Error('Incorrect withdrawal was not rejected for the expected reason: '+output);
const report={mutation:'Removed withdrawLamports while retaining accounting changes',compilerJarSha256:manifest.compilerJarSha256,sourceSha256:createHash('sha256').update(changed).digest('hex'),binarySha256:manifest.binarySha256,rejected:true,assertion:'withdraw-1: koral exact vault debit'};
fs.writeFileSync(path.join(folder,'negative-control.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
