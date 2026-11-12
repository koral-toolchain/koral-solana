// Additional benchmark fixtures only. The frozen launch MVP remains Counter + single payment.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'),args=process.argv.slice(2);
function option(name,fallback){const index=args.indexOf(name);return index<0?fallback:args[index+1];}
const sdk=option('--sdk',process.env.KORAL_SBF_SDK);if(!sdk)throw Error('Set KORAL_SBF_SDK or pass --sdk');
const sdkPath=path.resolve(sdk),bin=path.resolve(sdkPath,'../..'),out=path.resolve(option('--out',path.join(root,'build/contracts-comparison')));
const cargo=option('--cargo-build-sbf',fs.existsSync(path.join(bin,'cargo-build-sbf'))?path.join(bin,'cargo-build-sbf'):'cargo-build-sbf');
const cli=option('--cli',path.join(root,'cli/build/install/koral/bin/koral')),typesol=option('--typesol-repo',process.env.TYPESOL_REPO);
const env={...process.env,PATH:bin+path.delimiter+process.env.PATH,CARGO_TARGET_DIR:path.join(out,'rust-target')};
function run(command,parameters){execFileSync(command,parameters,{cwd:root,env,stdio:'inherit'});}
fs.mkdirSync(out,{recursive:true});
for(const kind of ['quota','pool','split']){
 for(const framework of ['native','pinocchio'])run(cargo,['--manifest-path',path.join(root,`benchmarks/contracts/${framework}-${kind}/Cargo.toml`),'--sbf-out-dir',path.join(out,'rust'),'--tools-version','v1.52','--arch','v0','--','--locked']);
 run(cli,['build',path.join(root,`benchmarks/contracts/kotlin/${kind}.kt`),'--out',path.join(out,kind,'koral'),'--sdk',sdkPath]);
 let auxiliary=[];
 if(typesol){
  run(process.execPath,[path.join(root,'benchmarks/payments/build-typesol.mjs'),path.resolve(typesol),path.join(root,`benchmarks/contracts/typesol/${kind}.ts`),path.join(out,kind,'typesol'),'EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN']);
  const k=JSON.parse(fs.readFileSync(path.join(out,kind,'koral/counter.build.json'),'utf8')),t=JSON.parse(fs.readFileSync(path.join(out,kind,'typesol/benchmark-build.json'),'utf8'));
  if(t.manifest.clang!==k.clang||t.manifest.sbpf!=='v0')throw Error('Auxiliary LLVM/target differs');
  auxiliary=[path.join(out,kind,`typesol/${kind}.so`)];
 }else console.error(`${kind}: additional internal baseline not run; no checkout supplied`);
 run(process.execPath,[path.join(root,'testing/contracts-benchmark.mjs'),kind,path.join(out,`rust/native_${kind}_bench.so`),path.join(out,`rust/pinocchio_${kind}_bench.so`),path.join(out,kind,'koral/counter.so'),path.join(out,kind,'results'),...auxiliary]);
}
fs.writeFileSync(path.join(out,'toolchain.json'),JSON.stringify({cargoBuildSbf:execFileSync(cargo,['--version'],{encoding:'utf8',env}).trim(),rustProfile:{lto:'fat',codegenUnits:1,overflowChecks:true},nativeDependency:'solana-program=5.1.0',pinocchioDependency:'pinocchio=0.10.2',auxiliaryBaseline:typesol?'executed':'not supplied'},null,2)+'\n');
console.log(`Saved three-contract comparison: ${out}`);
