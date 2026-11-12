// Reproduce the frozen MVP comparison. Rust is a benchmark dependency, not a Koral build dependency.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const args=process.argv.slice(2);
function option(name,fallback){const i=args.indexOf(name);return i<0?fallback:args[i+1];}
const sdk=option('--sdk',process.env.KORAL_SBF_SDK);
if(!sdk)throw Error('Pass --sdk <Agave SBF SDK> or set KORAL_SBF_SDK');
const sdkPath=path.resolve(sdk),bin=path.resolve(sdkPath,'../..');
const output=path.resolve(option('--out',path.join(root,'build/payment-comparison')));
const typesol=option('--typesol-repo',process.env.TYPESOL_REPO);
const cargoTool=option('--cargo-build-sbf',fs.existsSync(path.join(bin,'cargo-build-sbf'))?path.join(bin,'cargo-build-sbf'):'cargo-build-sbf');
const env={...process.env,PATH:bin+path.delimiter+process.env.PATH,CARGO_TARGET_DIR:path.join(output,'rust-target')};
function run(command,parameters){execFileSync(command,parameters,{cwd:root,env,stdio:'inherit'});}
fs.mkdirSync(output,{recursive:true});
for(const name of ['results.json','provenance.json','toolchain.json'])fs.rmSync(path.join(output,name),{force:true});
for(const name of ['native','pinocchio'])run(cargoTool,['--manifest-path',path.join(root,`benchmarks/payments/${name}/Cargo.toml`),'--sbf-out-dir',path.join(output,'rust'),'--tools-version','v1.52','--arch','v0','--','--locked']);
const cli=option('--cli',path.join(root,'cli/build/install/koral/bin/koral'));
run(cli,['build',path.join(root,'examples/payment-benchmark.kt'),'--out',path.join(output,'koral'),'--sdk',sdkPath]);
let auxiliary=[];
if(typesol){
 run(process.execPath,[path.join(root,'benchmarks/payments/build-typesol.mjs'),path.resolve(typesol),path.join(root,'benchmarks/payments/typesol/payment.ts'),path.join(output,'typesol'),'EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN']);
 const koral=JSON.parse(fs.readFileSync(path.join(output,'koral/counter.build.json'),'utf8'));
 const ts=JSON.parse(fs.readFileSync(path.join(output,'typesol/benchmark-build.json'),'utf8'));
 if(ts.manifest.clang!==koral.clang || ts.manifest.sbpf!=='v0')throw Error('Auxiliary compiler uses a different LLVM/target; comparison stopped');
 auxiliary=[path.join(output,'typesol/payment.so')];
}else console.error('Additional internal baseline not run: no --typesol-repo or TYPESOL_REPO supplied.');
run(process.execPath,[path.join(root,'testing/payment-benchmark.mjs'),path.join(output,'rust/native_payment_bench.so'),path.join(output,'rust/pinocchio_payment_bench.so'),path.join(output,'koral/counter.so'),output,...auxiliary]);
fs.writeFileSync(path.join(output,'toolchain.json'),JSON.stringify({cargoBuildSbf:execFileSync(cargoTool,['--version'],{encoding:'utf8',env}).trim(),rustProfile:{lto:'fat',codegenUnits:1,overflowChecks:true},nativeDependency:'solana-program=5.1.0',pinocchioDependency:'pinocchio=0.10.2',auxiliaryBaseline:typesol?'executed':'not supplied'},null,2)+'\n');
console.log(`Saved comparison: ${output}`);
