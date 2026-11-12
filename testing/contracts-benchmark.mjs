// Benchmarks selected state transitions; not full protocols or whole-program equivalence.
import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,mkdirSync,rmSync} from 'node:fs';
import {createHash} from 'node:crypto';
import path from 'node:path';
import {LiteSVM,FailedTransactionMetadata} from 'litesvm';
import {AccountRole,address,createKeyPairSignerFromPrivateKeyBytes,getAddressEncoder,getProgramDerivedAddress,lamports,createTransactionMessage,setTransactionMessageFeePayerSigner,appendTransactionMessageInstructions,signTransactionMessageWithSigners,getBase64EncodedWireTransaction,pipe} from '@solana/kit';
const [kind,native,pinocchio,koral,output,typesol]=process.argv.slice(2);
const specs={quota:{type:'Quota',ix:'consume',initial:[0n,1000000n]},pool:{type:'Pool',ix:'add_reserves',initial:[100n,200n]},split:{type:'SplitState',ix:'distribute',initial:[0n,0n]}};
const spec=specs[kind];if(!spec||!output)throw Error('Usage: contracts-benchmark.mjs <quota|pool|split> <native.so> <pinocchio.so> <koral.so> <output> [typesol.so]');
mkdirSync(output,{recursive:true});for(const file of ['results.json','provenance.json'])rmSync(path.join(output,file),{force:true});
const hash=data=>createHash('sha256').update(data).digest('hex'),disc=s=>createHash('sha256').update(s).digest().subarray(0,8);
const num=n=>{const b=Buffer.alloc(8);b.writeBigUInt64LE(BigInt(n));return b;};
const id=address('EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN'),system=address('11111111111111111111111111111111'),enc=getAddressEncoder();
const [user,other,first,fees,second]=await Promise.all([1,2,3,4,5].map(n=>createKeyPairSignerFromPrivateKeyBytes(new Uint8Array(32).fill(n))));
const [ledger,bump]=await getProgramDerivedAddress({programAddress:id,seeds:[kind,enc.encode(user.address)]});
const definitions=[['nativeRust',native],['pinocchio',pinocchio],['koral',koral],...(typesol?[['typesolInternal',typesol]]:[])],primary=['nativeRust','pinocchio','koral'];
const entries=new Map();
const initial=Buffer.concat([disc(`account:${spec.type}`),Buffer.from(enc.encode(user.address)),...spec.initial.map(num),Buffer.from([bump])]);
for(const [name,binary] of definitions){const svm=new LiteSVM();svm.addProgramFromFile(id,binary);for(const signer of [user,other,first,fees,second])svm.airdrop(signer.address,lamports(100_000_000_000n));svm.setAccount({address:ledger,lamports:10000000n,data:initial,programAddress:id,executable:false});entries.set(name,{svm,cu:[],binarySha256:hash(readFileSync(binary)),binaryBytes:readFileSync(binary).length});}
assert.equal(new Set(primary.map(name=>entries.get(name).binarySha256)).size,3,'Primary programs must be independently built ELFs');
const account=(svm,at)=>{const a=svm.getAccount(at);assert.ok(a.exists);return a;};
const fields=hex=>[Buffer.from(hex,'hex').readBigUInt64LE(40),Buffer.from(hex,'hex').readBigUInt64LE(48)];
function snapshot(svm){const state=account(svm,ledger);return {data:Buffer.from(state.data).toString('hex'),owner:state.programAddress,lamports:String(state.lamports),balances:[user.address,first.address,second.address].map(at=>String(account(svm,at).lamports)),feePayer:String(account(svm,fees.address).lamports)};}
const meta=(at,role=AccountRole.WRITABLE,signer)=>({address:at,role,...(signer?{signer}:{})});
const roles=()=>[meta(user.address,kind==='split'?AccountRole.WRITABLE_SIGNER:AccountRole.READONLY_SIGNER,user),meta(ledger),...(kind==='split'?[meta(first.address),meta(second.address),meta(system,AccountRole.READONLY)]:[])];
const main=entries.get('koral').svm,rows=[],auxiliaryMismatches=[],auxiliaryErrorDifferences=[];
async function run(label,{values=kind==='quota'?[1n]:[1n,2n],accounts=roles(),data=null,success=true,measured=false,expectedState=null,expectedDeltas=null}={}){
 const ix={programAddress:id,accounts,data:data??Buffer.concat([disc(`global:${spec.ix}`),...values.map(num)])};
 const tx=await pipe(createTransactionMessage({version:0}),t=>setTransactionMessageFeePayerSigner(fees,t),t=>main.setTransactionMessageLifetimeUsingLatestBlockhash(t),t=>appendTransactionMessageInstructions([ix],t),t=>signTransactionMessageWithSigners(t));
 const wireTransactionSha256=hash(Buffer.from(getBase64EncodedWireTransaction(tx),'base64')),outcomes={};
 for(const [name,entry] of entries){const before=snapshot(entry.svm),result=entry.svm.sendTransaction(tx);entry.svm.expireBlockhash();const failed=result instanceof FailedTransactionMetadata,metadata=failed?result.meta():result,after=snapshot(entry.svm);
  if(primary.includes(name)){
   assert.equal(!failed,success,`${kind}/${label}: ${name} ${failed?String(result.err()):'unexpected success'}`);
   if(!success){assert.equal(after.data,before.data,`${label}: state rollback`);assert.equal(after.lamports,before.lamports);assert.deepEqual(after.balances,before.balances,`${label}: balance rollback`);}
   if(measured){const previous=fields(before.data),actual=fields(after.data),x=BigInt(values[0]),y=BigInt(values[1]??0);
    const expected=kind==='quota'?[previous[0]+x,previous[1]]:kind==='pool'?[previous[0]+x,previous[1]+y]:[previous[0]+x+y,BigInt(after.balances[1])+BigInt(after.balances[2])];
    assert.deepEqual(actual,expected,`${label}: ${name} exact state transition`);
    const deltas=kind==='split'?[-x-y,x,y]:[0n,0n,0n];assert.deepEqual(after.balances.map((v,i)=>BigInt(v)-BigInt(before.balances[i])),deltas,`${label}: exact balances`);
   }
   if(expectedState)assert.deepEqual(fields(after.data),expectedState,`${label}: explicit expected state`);
   if(expectedDeltas)assert.deepEqual(after.balances.map((v,i)=>BigInt(v)-BigInt(before.balances[i])),expectedDeltas);
   if(label==='overflow-after-cpi')assert.equal(metadata.logs().filter(line=>line.includes('Program 11111111111111111111111111111111 invoke')).length,2,'both CPIs execute before arithmetic failure');
  }
  if(measured&&!failed)entry.cu.push(Number(metadata.computeUnitsConsumed()));outcomes[name]={success:!failed,cu:Number(metadata.computeUnitsConsumed()),error:failed?String(result.err()):null,after};
 }
 for(const name of primary){assert.deepEqual(outcomes[name].after,outcomes.koral.after,`${label}: ${name} state/balance mismatch`);assert.equal(outcomes[name].error,outcomes.koral.error,`${label}: ${name} error-code mismatch`);}
 if(typesol){const aux=outcomes.typesolInternal,expected=outcomes.koral;if(aux.success!==expected.success||JSON.stringify(aux.after)!==JSON.stringify(expected.after))auxiliaryMismatches.push({label,actual:aux,expected:expected.after});if(aux.error!==expected.error)auxiliaryErrorDifferences.push({label,actual:aux.error,expected:expected.error});}
 rows.push({label,measured,wireTransactionSha256,outcomes});
}
for(let n=1;n<=20;n++)await run(`${spec.ix}-${n}`,{values:kind==='quota'?[BigInt(n)]:[BigInt(n),BigInt(2*n)],measured:true});
const base=Buffer.from(account(main,ledger).data);
function put(data=base,owner=id){for(const entry of entries.values())entry.svm.setAccount({address:ledger,lamports:10000000n,data:Buffer.from(data),programAddress:owner,executable:false});}
function withFields(a,b){const d=Buffer.from(base);num(a).copy(d,40);num(b).copy(d,48);return d;}
put();let invalid=roles();invalid[0]=meta(user.address,kind==='split'?AccountRole.WRITABLE:AccountRole.READONLY);await run('unsigned-authority',{accounts:invalid,success:false});
put();invalid=roles();invalid[1]=meta(ledger,AccountRole.READONLY);await run('readonly-state',{accounts:invalid,success:false});
put(base,system);await run('wrong-owner',{success:false});
let damaged=Buffer.from(base);damaged.fill(0,0,8);put(damaged);await run('wrong-discriminator',{success:false});
put(base.subarray(0,56));await run('short-state',{success:false});
put();invalid=roles();invalid[0]=meta(other.address,kind==='split'?AccountRole.WRITABLE_SIGNER:AccountRole.READONLY_SIGNER,other);await run('wrong-authority',{accounts:invalid,success:false});
put();await run('missing-state',{accounts:roles().slice(0,1),success:false});
put();await run('truncated-arguments',{data:Buffer.concat([disc(`global:${spec.ix}`),Buffer.alloc(kind==='quota'?7:15)]),success:false});
put();await run('unknown-instruction',{data:Buffer.alloc(kind==='quota'?16:24),success:false});
const MAX=18446744073709551615n;
if(kind==='quota'){
 put();await run('zero-amount',{values:[0n],success:false});
 put();await run('limit-exceeded',{values:[1000001n],success:false});
 put(withFields(MAX,MAX));await run('arithmetic-overflow',{values:[1n],success:false});
 put(withFields(999999n,1000000n));await run('exact-limit',{values:[1n],expectedState:[1000000n,1000000n]});
}else if(kind==='pool'){
 put(withFields(MAX,200n));await run('first-field-overflow',{values:[1n,1n],success:false});
 put(withFields(100n,MAX));await run('second-field-overflow',{values:[1n,1n],success:false});
 put();await run('zero-update',{values:[0n,0n],expectedState:fields(base),expectedDeltas:[0n,0n,0n]});
}else{
 put();invalid=roles();invalid[0]=meta(user.address,AccountRole.READONLY_SIGNER,user);await run('readonly-source',{accounts:invalid,success:false});
 for(const index of [2,3]){put();invalid=roles();invalid[index]=meta(index===2?first.address:second.address,AccountRole.READONLY);await run(`readonly-recipient-${index}`,{accounts:invalid,success:false});}
 put();await run('missing-system',{accounts:roles().slice(0,4),success:false});
 put();invalid=roles();invalid[4]=meta(other.address,AccountRole.READONLY);await run('wrong-system',{accounts:invalid,success:false});
 put();await run('second-cpi-failure',{values:[1n,MAX],success:false});
 put(withFields(MAX,0n));await run('overflow-after-cpi',{values:[1n,1n],success:false});
 put();invalid=roles();invalid[3]=meta(first.address);const balance=account(main,first.address).lamports;await run('aliased-recipients',{values:[1n,2n],accounts:invalid,expectedState:[fields(base)[0]+3n,2n*(balance+3n)],expectedDeltas:[-3n,3n,0n]});
}
const implementations={};for(const [name,entry] of entries){implementations[name]={samples:entry.cu.length,meanCu:entry.cu.length===20?Math.round(entry.cu.reduce((a,b)=>a+b,0)/20):null,minCu:entry.cu.length?Math.min(...entry.cu):null,maxCu:entry.cu.length?Math.max(...entry.cu):null,binarySha256:entry.binarySha256,binaryBytes:entry.binaryBytes};}
const result={generated:new Date().toISOString(),contract:kind,instruction:spec.ix,scope:'Selected instruction with seeded 57-byte state; no initialization/whole-program size or full-protocol comparison',checks:rows.length,rounds:20,primaryEquivalence:true,primaryErrorCodesMatch:true,identicalSignedTransactions:true,additionalBaseline:typesol?'executed':'not supplied',implementations,auxiliaryMismatches,auxiliaryErrorDifferences,rows};
writeFileSync(path.join(output,'results.json'),JSON.stringify(result,null,2)+'\n');
const sourceFiles=[`benchmarks/contracts/native-${kind}/src/lib.rs`,`benchmarks/contracts/pinocchio-${kind}/src/lib.rs`,`benchmarks/contracts/kotlin/${kind}.kt`,`benchmarks/contracts/typesol/${kind}.ts`];
writeFileSync(path.join(output,'provenance.json'),JSON.stringify({runnerSha256:hash(readFileSync(new URL(import.meta.url))),node:process.version,sourceFiles:sourceFiles.map(file=>({file,sha256:hash(readFileSync(new URL('../'+file,import.meta.url)))}))},null,2)+'\n');
console.log(JSON.stringify({contract:kind,checks:rows.length,auxiliaryMismatches:auxiliaryMismatches.length,implementations},null,2));
