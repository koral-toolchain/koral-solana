// Rust and TypeSOL are comparison dependencies only; no product compilation uses them.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),args=process.argv.slice(2);
function option(name,fallback){const i=args.indexOf(name);return i<0?fallback:args[i+1];}
const sdk=option('--sdk',process.env.KORAL_SBF_SDK),typesol=option('--typesol-repo',process.env.TYPESOL_REPO);
if(!sdk||!typesol)throw Error('Supply --sdk and --typesol-repo; the auxiliary baseline must be executed');
const sdkPath=path.resolve(sdk),bin=path.resolve(sdkPath,'../..'),repo=path.resolve(typesol);
const out=path.resolve(option('--out',path.join(root,'build/vault-comparison'))),cli=path.resolve(option('--cli',path.join(root,'cli/build/install/koral/bin/koral')));
const cargo=option('--cargo-build-sbf',path.join(bin,'cargo-build-sbf'));
const env={...process.env,PATH:bin+path.delimiter+process.env.PATH,TYPESOL_SBF_SDK:sdkPath,CARGO_TARGET_DIR:process.env.CARGO_TARGET_DIR??path.join(out,'rust-target')};
const capture=(command,parameters)=>execFileSync(command,parameters,{cwd:root,env,encoding:'utf8'}).trim();
const run=(command,parameters,extra={})=>execFileSync(command,parameters,{cwd:root,env:{...env,...extra},stdio:'inherit'});
const sha=p=>createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const revision=capture('git',['-C',repo,'rev-parse','HEAD']);
if(revision!=='543786e850ad426425956e497971f88103f6c987'||JSON.parse(fs.readFileSync(path.join(repo,'package.json'))).version!=='0.43.0')throw Error('Select the documented TypeSOL 0.43.0 comparison revision');
if(capture('git',['-C',repo,'diff','--name-only']))throw Error('Auxiliary comparison source has tracked modifications');
fs.mkdirSync(out,{recursive:true});fs.rmSync(path.join(out,'summary.json'),{force:true});
for(const name of ['native','pinocchio'])run(cargo,['--manifest-path',path.join(root,`benchmarks/vault/${name}/Cargo.toml`),'--sbf-out-dir',path.join(out,'rust'),'--tools-version','v1.52','--arch','v0','--','--locked']);
run(cli,['build',path.join(root,'benchmarks/vault/kotlin/vault.kt'),'--out',path.join(out,'koral'),'--sdk',sdkPath]);
run(process.execPath,[path.join(root,'benchmarks/payments/build-typesol.mjs'),repo,path.join(root,'benchmarks/vault/typesol/vault.ts'),path.join(out,'typesol'),'EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN']);
const manifest=JSON.parse(fs.readFileSync(path.join(out,'koral/counter.build.json'))),aux=JSON.parse(fs.readFileSync(path.join(out,'typesol/benchmark-build.json')));
if(aux.manifest.clang!==manifest.clang||aux.manifest.sbpf!=='v0')throw Error('LLVM/target differs');
const cases=[];
for(const [seed,bump] of [[1,250],[5,255],[6,254],[19,253]]){
 const destination=path.join(out,'bump-'+bump);
 run(process.execPath,[path.join(root,'testing/vault-benchmark.mjs'),path.join(out,'rust/native_vault_bench.so'),path.join(out,'rust/pinocchio_vault_bench.so'),path.join(out,'koral/counter.so'),destination,path.join(out,'typesol/vault.so')],{KORAL_VAULT_AUTHORITY_SEED:String(seed)});
 const result=JSON.parse(fs.readFileSync(path.join(destination,'results.json')));
 if(result.canonicalBump!==bump||result.auxiliaryMismatches.some(x=>x.measured))throw Error('Fixture bump or measured auxiliary equivalence failed');
 cases.push({authoritySeed:seed,canonicalBump:bump,searchAttempts:256-bump,checks:result.checks,auxiliaryMismatches:result.auxiliaryMismatches,auxiliaryErrorDifferences:result.auxiliaryErrorDifferences,implementations:result.implementations});
}
fs.writeFileSync(path.join(out,'summary.json'),JSON.stringify({scope:'Four explicitly selected PDA-bump strata, not a random population or universal ranking',roundsPerOperation:20,sourceHash:sha(path.join(root,'benchmarks/vault/kotlin/vault.kt')),compilerJarSha256:manifest.compilerJarSha256,cases},null,2)+'\n');
fs.writeFileSync(path.join(out,'toolchain.json'),JSON.stringify({cargoBuildSbf:capture(cargo,['--version']),sdk:manifest.clang,target:'v0',rustProfile:{lto:'fat',codegenUnits:1,overflowChecks:true},rustLockSha256:sha(path.join(root,'benchmarks/vault/Cargo.lock')),native:'solana-program=5.1.0',pinocchio:'0.10.2',typesol:{version:'0.43.0',revision,manifest:aux.manifest},runnerSha256:sha(path.join(root,'testing/vault-benchmark.mjs')),builderSha256:sha(fileURLToPath(import.meta.url))},null,2)+'\n');
console.log('Verified vault comparison saved: '+out);
