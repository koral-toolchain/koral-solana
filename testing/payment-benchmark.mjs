// A selected-instruction benchmark, not whole-program or DEX equivalence.
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { LiteSVM, FailedTransactionMetadata } from 'litesvm';
import { AccountRole, address, createKeyPairSignerFromPrivateKeyBytes, getAddressEncoder, getProgramDerivedAddress, lamports, createTransactionMessage, setTransactionMessageFeePayerSigner, appendTransactionMessageInstructions, signTransactionMessageWithSigners, getBase64EncodedWireTransaction, pipe } from '@solana/kit';
const [native,pinocchio,koral,output,typesol]=process.argv.slice(2);
if(!native||!pinocchio||!koral||!output)throw Error('Usage: node testing/payment-benchmark.mjs <native.so> <pinocchio.so> <koral.so> <output> [typesol.so]');
mkdirSync(output,{recursive:true});
for(const name of ["results.json","provenance.json"])rmSync(path.join(output,name),{force:true});
const sha=data=>createHash('sha256').update(data).digest('hex');
const disc=s=>createHash('sha256').update(s).digest().subarray(0,8);
const u64=n=>{const b=Buffer.alloc(8);b.writeBigUInt64LE(BigInt(n));return b;};
const id=address('EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN'),system=address('11111111111111111111111111111111');
const enc=getAddressEncoder();
const [user,other,recipient,fees]=await Promise.all([1,2,3,4].map(n=>createKeyPairSignerFromPrivateKeyBytes(new Uint8Array(32).fill(n))));
const [ledger,bump]=await getProgramDerivedAddress({programAddress:id,seeds:['payments',enc.encode(user.address)]});
const definitions=[['nativeRust',native],['pinocchio',pinocchio],['koral',koral],...(typesol?[['typesolInternal',typesol]]:[])];
const primary=['nativeRust','pinocchio','koral'];
const states=new Map();
const initial=Buffer.concat([disc('account:PaymentState'),Buffer.from(enc.encode(user.address)),u64(0),u64(0),Buffer.from([bump])]);
for(const [name,binary] of definitions){
 const svm=new LiteSVM();svm.addProgramFromFile(id,binary);
 for(const signer of [user,other,recipient,fees])svm.airdrop(signer.address,lamports(100_000_000_000n));
 svm.setAccount({address:ledger,lamports:10_000_000n,data:initial,programAddress:id,executable:false});
 states.set(name,{svm,cu:[],binarySha256:sha(readFileSync(binary)),binaryBytes:readFileSync(binary).length});
}
assert.equal(new Set(primary.map(name=>states.get(name).binarySha256)).size,3,'Independent primary implementations must not be the same ELF');
const account=(svm,at)=>{const a=svm.getAccount(at);assert.ok(a.exists);return a;};
const balances=svm=>[user.address,recipient.address].map(at=>String(account(svm,at).lamports));
function snapshot(svm){const state=account(svm,ledger);return {state:Buffer.from(state.data).toString('hex'),stateOwner:state.programAddress,stateLamports:String(state.lamports),balances:balances(svm),feePayer:String(account(svm,fees.address).lamports)};}
const meta=(at,role=AccountRole.WRITABLE,signer)=>({address:at,role,...(signer?{signer}:{})});
const roles=()=>[meta(user.address,AccountRole.WRITABLE_SIGNER,user),meta(ledger),meta(recipient.address),meta(system,AccountRole.READONLY)];
const main=states.get('koral').svm;
const rows=[],auxiliaryMismatches=[],auxiliaryErrorDifferences=[];
async function run(label,{amount=1n,accounts=roles(),data=null,success=true,measured=false}={}){
 const ix={programAddress:id,accounts,data:data??Buffer.concat([disc('global:pay'),u64(amount)])};
 // The identical signed transaction is replayed in all VMs. No blockhash checks are disabled.
 const tx=await pipe(createTransactionMessage({version:0}),t=>setTransactionMessageFeePayerSigner(fees,t),t=>main.setTransactionMessageLifetimeUsingLatestBlockhash(t),t=>appendTransactionMessageInstructions([ix],t),t=>signTransactionMessageWithSigners(t));
 const wireTransactionSha256=sha(Buffer.from(getBase64EncodedWireTransaction(tx),'base64'));
 const outcomes={};
 for(const [name,entry] of states){
  const before=snapshot(entry.svm),result=entry.svm.sendTransaction(tx);entry.svm.expireBlockhash();
  const failed=result instanceof FailedTransactionMetadata,metadata=failed?result.meta():result;
  const after=snapshot(entry.svm);
  if(primary.includes(name)){
   assert.equal(!failed,success,`${label}: ${name} ${failed?String(result.err()):'unexpected success'}`);
   if(measured){
    const beforeBytes=Buffer.from(before.state,'hex'),afterBytes=Buffer.from(after.state,'hex');
    assert.equal(BigInt(before.balances[0])-BigInt(after.balances[0]),BigInt(amount),`${label}: ${name} exact source debit`);
    assert.equal(BigInt(after.balances[1])-BigInt(before.balances[1]),BigInt(amount),`${label}: ${name} exact destination credit`);
    assert.equal(afterBytes.readBigUInt64LE(40)-beforeBytes.readBigUInt64LE(40),BigInt(amount),`${label}: ${name} paid update`);
    assert.equal(afterBytes.readBigUInt64LE(48),BigInt(after.balances[1]),`${label}: ${name} observed balance`);
   }
   if(!success){assert.equal(after.state,before.state,`${label}: ${name} state rollback`);assert.equal(after.stateLamports,before.stateLamports);assert.deepEqual(after.balances,before.balances,`${label}: ${name} balance rollback`);}
  }
  if(measured&&!failed)entry.cu.push(Number(metadata.computeUnitsConsumed()));
  outcomes[name]={success:!failed,cu:Number(metadata.computeUnitsConsumed()),error:failed?String(result.err()):null,after};
 }
 const expected=outcomes.koral;
 for(const name of primary){
  assert.deepEqual(outcomes[name].after,expected.after,`${label}: ${name} final state/balances differ`);
  assert.equal(outcomes[name].error,expected.error,`${label}: ${name} failure code differs`);
 }
 if(typesol){const auxiliary=outcomes.typesolInternal;
 if(auxiliary.error!==expected.error)auxiliaryErrorDifferences.push({label,actual:auxiliary.error,expected:expected.error});
 if(auxiliary.success!==expected.success||JSON.stringify(auxiliary.after)!==JSON.stringify(expected.after))auxiliaryMismatches.push({label,outcome:auxiliary,expected:expected.after});}
 rows.push({label,measured,wireTransactionSha256,outcomes});
}
for(let n=1;n<=20;n++)await run(`pay-${n}`,{amount:BigInt(n),measured:true});
// Each failure starts from an explicitly shared state fixture. Wallet/fee history stays shared.
const base=Buffer.from(account(main,ledger).data);
function put(data=base,owner=id){for(const entry of states.values())entry.svm.setAccount({address:ledger,lamports:10_000_000n,data:Buffer.from(data),programAddress:owner,executable:false});}
put();const overflow=Buffer.from(base);u64('18446744073709551615').copy(overflow,40);put(overflow);
await run('overflow-after-cpi',{success:false});
put();const unsigned=roles();unsigned[0]=meta(user.address,AccountRole.WRITABLE);await run('unsigned-authority',{accounts:unsigned,success:false});
put();const readSource=roles();readSource[0]=meta(user.address,AccountRole.READONLY_SIGNER,user);await run('readonly-authority',{accounts:readSource,success:false});
put();const readState=roles();readState[1]=meta(ledger,AccountRole.READONLY);await run('readonly-state',{accounts:readState,success:false});
put();const readTo=roles();readTo[2]=meta(recipient.address,AccountRole.READONLY);await run('readonly-recipient',{accounts:readTo,success:false});
put(base,system);await run('wrong-state-owner',{success:false});
const badDisc=Buffer.from(base);badDisc.fill(0,0,8);put(badDisc);await run('wrong-discriminator',{success:false});
put(base.subarray(0,56));await run('short-state',{success:false});
put();const stranger=roles();stranger[0]=meta(other.address,AccountRole.WRITABLE_SIGNER,other);await run('wrong-authority',{accounts:stranger,success:false});
put();await run('missing-recipient',{accounts:roles().slice(0,2),success:false});
put();await run('missing-system',{accounts:roles().slice(0,3),success:false});
put();const badSystem=roles();badSystem[3]=meta(other.address,AccountRole.READONLY);await run('wrong-system',{accounts:badSystem,success:false});
put();await run('truncated-argument',{data:Buffer.concat([disc('global:pay'),Buffer.alloc(7)]),success:false});
put();await run('unknown-instruction',{data:Buffer.alloc(16),success:false});
put();await run('insufficient-funds',{amount:18446744073709551615n,success:false});
// Alias behavior is tested separately and never included in mean CU.
put();const alias=roles();alias[2]=meta(ledger);await run('recipient-aliases-state',{accounts:alias});
const measured={};
for(const [name,entry] of states){const values=entry.cu;measured[name]={samples:values.length,meanCu:values.length===20?Math.round(values.reduce((a,b)=>a+b,0)/values.length):null,minCu:values.length?Math.min(...values):null,maxCu:values.length?Math.max(...values):null,binarySha256:entry.binarySha256,binaryBytes:entry.binaryBytes};}
const result={generated:new Date().toISOString(),scope:'Selected pay instruction on seeded state; initialization and whole-program ELF-size ranking are excluded',programId:id,accountDataBytes:57,rounds:20,checks:rows.length,identicalSignedTransactions:true,primaryEquivalence:true,additionalBaseline:typesol?'typesolInternal':'not supplied',auxiliaryMismatches,auxiliaryErrorDifferences,primaryErrorCodesMatch:true,implementations:measured,rows};
writeFileSync(path.join(output,'results.json'),JSON.stringify(result,null,2)+'\n');
writeFileSync(path.join(output,'provenance.json'),JSON.stringify({runnerSha256:sha(readFileSync(new URL(import.meta.url))),node:process.version,sourceFiles:['benchmarks/payments/native/src/lib.rs','benchmarks/payments/pinocchio/src/lib.rs','examples/payment-benchmark.kt','benchmarks/payments/typesol/payment.ts'].map(file=>({file,sha256:sha(readFileSync(new URL('../'+file,import.meta.url)))}))},null,2)+'\n');
console.log(JSON.stringify({checks:rows.length,primaryEquivalence:true,auxiliaryMismatches:auxiliaryMismatches.length,implementations:measured},null,2));
