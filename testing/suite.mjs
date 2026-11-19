// Built-in suites assert fixed contracts. Custom tests are explicitly user-authored code.
import {readFileSync,existsSync,writeFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
const [suite,binary,report,script]=process.argv.slice(2);
const directory=path.dirname(fileURLToPath(import.meta.url));
const suites={counter:{runner:'counter.mjs',seed:'counter',fields:['count'],instructions:{increment:['by']}},
 payments:{runner:'payments.mjs',seed:'payments',fields:['paid','observed'],instructions:{pay:['amount'],observe:[],pay_if:['amount'],split:['amount_first','amount_second'],observe_writable:[]}},
 'sol-vault':{runner:'vault.mjs',seed:'vault',fields:['deposited','withdrawn'],instructions:{deposit:['amount'],withdraw:['amount'],deposit_then_fail:['amount'],withdraw_then_fail:['amount'],round_trip:['amount'],round_trip_then_fail:['amount'],withdraw_if:['amount']}}};
function mismatch(reason){throw Error(`${suite} verification failed: ${reason}. This built-in suite expects its bundled template contract. For your own contract, use koral init <new-directory> --template custom and its tests/program.test.mjs workflow; see docs/CUSTOM-PROGRAM.md.`);}
try {
 if(suite==='custom') {
  if(!script||!report)throw Error('Custom tests require a project script and result path');
  process.env.KORAL_TEST_BINARY=path.resolve(binary);
  process.env.KORAL_TEST_CLIENT=pathToFileURL(path.join(directory,'client.mjs')).href;
  process.argv=[process.execPath,path.resolve(script)];
  await import(pathToFileURL(path.resolve(script)).href);
  writeFileSync(report,JSON.stringify({mode:'user-authored script',script:path.resolve(script),completed:true,scope:'Script exit status and coverage are user-defined; not automatic contract verification'},null,2)+'\n');
 } else {
  const contract=suites[suite];if(!contract)throw Error('Unknown built-in suite');
  const abiPath=path.join(path.dirname(binary),'counter.abi.json');
  if(existsSync(abiPath)) {
   let abi;try{abi=JSON.parse(readFileSync(abiPath,'utf8'));}catch{mismatch('the generated ABI is invalid');}
   if(abi.schema!=='koral-preview-abi/1'||abi.state?.pdaSeed!==contract.seed||JSON.stringify(abi.state?.fields?.map(f=>f.name))!==JSON.stringify(contract.fields))mismatch('state layout or PDA seed differs');
   for(const [name,args] of Object.entries(contract.instructions)) {
    const ix=abi.instructions?.find(i=>i.name===name);
    if(!ix||JSON.stringify(ix.data?.map(v=>v.name))!==JSON.stringify(args))mismatch(`instruction '${name}' or its arguments differ`);
   }
  } else if(suite!=='counter')mismatch('missing generated ABI');
  const runner=path.join(directory,contract.runner);
  process.argv=[process.execPath,runner,binary,...(report?[report]:[])];
  await import(pathToFileURL(runner).href);
 }
} catch(error) {
 console.error(`Koral: ${error.message}`);process.exitCode=1;
}
