// Identical signed transactions, independently compiled programs, selected updates only.
import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,mkdirSync,rmSync} from 'node:fs';
import {createHash} from 'node:crypto';
import path from 'node:path';
import {LiteSVM,FailedTransactionMetadata} from 'litesvm';
import {AccountRole,address,createKeyPairSignerFromPrivateKeyBytes,getAddressEncoder,getProgramDerivedAddress,lamports,createTransactionMessage,setTransactionMessageFeePayerSigner,appendTransactionMessageInstructions,signTransactionMessageWithSigners,getBase64EncodedWireTransaction,pipe} from '@solana/kit';
const [native,pinocchio,koral,output,typesol]=process.argv.slice(2);
if(!native||!pinocchio||!koral||!output)throw Error('Pass native, Pinocchio, Koral ELF paths, output and optional TypeSOL ELF');
mkdirSync(output,{recursive:true});for(const f of ['results.json','provenance.json'])rmSync(path.join(output,f),{force:true});
const sha=b=>createHash('sha256').update(b).digest('hex'),disc=s=>createHash('sha256').update(s).digest().subarray(0,8);
const u64=n=>{const b=Buffer.alloc(8);b.writeBigUInt64LE(BigInt(n));return b;};
const id=address('EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN'),system=address('11111111111111111111111111111111');
const authoritySeed=Number(process.env.KORAL_VAULT_AUTHORITY_SEED??1);
assert.ok(Number.isInteger(authoritySeed) && authoritySeed>=1 && authoritySeed<=255 && ![2,3,4].includes(authoritySeed),'Choose a distinct deterministic authority seed');
const [user,other,recipient,fees]=await Promise.all([authoritySeed,2,3,4].map(n=>createKeyPairSignerFromPrivateKeyBytes(new Uint8Array(32).fill(n))));
const enc=getAddressEncoder(),[vault,bump]=await getProgramDerivedAddress({programAddress:id,seeds:['vault',enc.encode(user.address)]});
const definitions=[['nativeRust',native],['pinocchio',pinocchio],['koral',koral],...(typesol?[['typesolInternal',typesol]]:[])],primary=['nativeRust','pinocchio','koral'];
const states=new Map(),initial=Buffer.concat([disc('account:VaultState'),Buffer.from(enc.encode(user.address)),u64(0),u64(0),Buffer.from([bump])]);
for(const [name,binary] of definitions){
 const svm=new LiteSVM();svm.addProgramFromFile(id,binary);
 for(const who of [user,other,recipient,fees])svm.airdrop(who.address,lamports(100_000_000_000n));
 svm.setAccount({address:vault,lamports:10_000_000n,data:initial,programAddress:id,executable:false});
 states.set(name,{svm,cu:{deposit:[],withdraw:[]},binarySha256:sha(readFileSync(binary)),binaryBytes:readFileSync(binary).length});
}
assert.equal(new Set(primary.map(n=>states.get(n).binarySha256)).size,3,'Independent primary ELFs required');
const account=(svm,at)=>{const a=svm.getAccount(at);assert.ok(a.exists);return a;};
const snapshot=svm=>({data:Buffer.from(account(svm,vault).data).toString('hex'),owner:account(svm,vault).programAddress,vaultLamports:String(account(svm,vault).lamports),balances:[user,recipient].map(w=>String(account(svm,w.address).lamports)),feePayer:String(account(svm,fees.address).lamports)});
const meta=(at,role=AccountRole.WRITABLE,signer)=>({address:at,role,...(signer?{signer}:{})});
const depositRoles=()=>[meta(user.address,AccountRole.WRITABLE_SIGNER,user),meta(vault),meta(system,AccountRole.READONLY)];
const withdrawRoles=(to=recipient.address)=>[meta(user.address,AccountRole.READONLY_SIGNER,user),meta(vault),meta(to)];
const main=states.get('koral').svm,rows=[],auxiliaryMismatches=[],auxiliaryErrorDifferences=[];
async function run(label,{instruction='withdraw',amount=1n,accounts=null,data=null,success=true,measured=false}={}){
 accounts??=instruction==='deposit'?depositRoles():withdrawRoles();
 const ix={programAddress:id,accounts,data:data??Buffer.concat([disc('global:'+instruction),u64(amount)])};
 const tx=await pipe(createTransactionMessage({version:0}),t=>setTransactionMessageFeePayerSigner(fees,t),t=>main.setTransactionMessageLifetimeUsingLatestBlockhash(t),t=>appendTransactionMessageInstructions([ix],t),t=>signTransactionMessageWithSigners(t));
 const wireTransactionSha256=sha(Buffer.from(getBase64EncodedWireTransaction(tx),'base64')),outcomes={};
 for(const [name,entry] of states){
  const before=snapshot(entry.svm),result=entry.svm.sendTransaction(tx);entry.svm.expireBlockhash();
  const failed=result instanceof FailedTransactionMetadata,metadata=failed?result.meta():result,after=snapshot(entry.svm);
  const cpi=metadata.logs().some(l=>l.includes('Program 11111111111111111111111111111111 invoke'));
  if(primary.includes(name)){
   assert.equal(!failed,success,`${label}: ${name} ${failed?String(result.err()):'unexpected success'}`);
   if(measured){
    const a=Buffer.from(after.data,'hex'),b=Buffer.from(before.data,'hex');
    if(instruction==='deposit'){
     assert.equal(BigInt(before.balances[0])-BigInt(after.balances[0]),BigInt(amount),`${label}: ${name} exact authority debit`);
     assert.equal(BigInt(after.vaultLamports)-BigInt(before.vaultLamports),BigInt(amount),`${label}: ${name} exact vault credit`);
     assert.equal(a.readBigUInt64LE(40)-b.readBigUInt64LE(40),BigInt(amount),`${label}: ${name} deposited update`);
     assert.equal(after.balances[1],before.balances[1]);assert.equal(a.readBigUInt64LE(48),b.readBigUInt64LE(48));assert.ok(cpi);
    }else{
     assert.equal(BigInt(before.vaultLamports)-BigInt(after.vaultLamports),BigInt(amount),`${label}: ${name} exact vault debit`);
     assert.equal(BigInt(after.balances[1])-BigInt(before.balances[1]),BigInt(amount),`${label}: ${name} exact recipient credit`);
     assert.equal(a.readBigUInt64LE(48)-b.readBigUInt64LE(48),BigInt(amount),`${label}: ${name} withdrawn update`);
     assert.equal(after.balances[0],before.balances[0]);assert.equal(a.readBigUInt64LE(40),b.readBigUInt64LE(40));assert.ok(!cpi);
    }
   }
   if(!success){assert.equal(after.data,before.data);assert.equal(after.vaultLamports,before.vaultLamports);assert.deepEqual(after.balances,before.balances);}
  }
  if(measured&&!failed)entry.cu[instruction].push(Number(metadata.computeUnitsConsumed()));
  outcomes[name]={success:!failed,cu:Number(metadata.computeUnitsConsumed()),error:failed?String(result.err()):null,cpi,after};
 }
 for(const name of primary){assert.deepEqual(outcomes[name].after,outcomes.koral.after,`${label}: ${name} final snapshot`);assert.equal(outcomes[name].error,outcomes.koral.error,`${label}: ${name} error code`);}
 if(typesol){const a=outcomes.typesolInternal,e=outcomes.koral;if(a.error!==e.error)auxiliaryErrorDifferences.push({label,actual:a.error,expected:e.error});if(a.success!==e.success||JSON.stringify(a.after)!==JSON.stringify(e.after))auxiliaryMismatches.push({label,measured,outcome:a,expected:e.after});}
 rows.push({label,instruction,amount:String(amount),measured,wireTransactionSha256,outcomes});
}
for(let n=1;n<=20;n++)await run('deposit-'+n,{instruction:'deposit',amount:BigInt(n),measured:true});
for(let n=1;n<=20;n++)await run('withdraw-'+n,{amount:BigInt(n),measured:true});
const base=Buffer.from(account(main,vault).data),floor=BigInt(main.minimumBalanceForRentExemption(57n));
function put(data=base,owner=id,at=vault,funds=10_000_000n){for(const {svm} of states.values())svm.setAccount({address:at,lamports:funds,data:Buffer.from(data),programAddress:owner,executable:false});}
put();let d=Buffer.from(base);u64(18446744073709551615n).copy(d,40);put(d);await run('deposit-accounting-overflow',{instruction:'deposit',success:false});
d=Buffer.from(base);u64(18446744073709551615n).copy(d,48);put(d);await run('withdraw-accounting-overflow',{success:false});
put();await run('insufficient-funds',{amount:18446744073709551615n,success:false});
put();await run('cross-rent-floor',{amount:10_000_000n-floor+1n,success:false});
put();await run('zero-withdraw',{amount:0n,success:false});
put();await run('zero-deposit',{instruction:'deposit',amount:0n,success:false});
put();const unsigned=withdrawRoles();unsigned[0]=meta(user.address,AccountRole.READONLY);await run('unsigned-authority',{accounts:unsigned,success:false});
put();const readSource=depositRoles();readSource[0]=meta(user.address,AccountRole.READONLY_SIGNER,user);await run('readonly-depositor',{instruction:'deposit',accounts:readSource,success:false});
put();const readonly=withdrawRoles();readonly[2]=meta(recipient.address,AccountRole.READONLY);await run('readonly-recipient',{accounts:readonly,success:false});
put();const readState=withdrawRoles();readState[1]=meta(vault,AccountRole.READONLY);await run('readonly-vault',{accounts:readState,success:false});
put(base,system);await run('wrong-owner',{success:false});
d=Buffer.from(base);d.fill(0,0,8);put(d);await run('wrong-discriminator',{success:false});
put(base.subarray(0,56));await run('short-state',{success:false});
put();const wrong=withdrawRoles();wrong[0]=meta(other.address,AccountRole.READONLY_SIGNER,other);await run('wrong-authority',{accounts:wrong,success:false});
d=Buffer.from(base);d[56]^=1;put(d);await run('bad-bump',{success:false});
put();await run('missing-recipient',{accounts:withdrawRoles().slice(0,2),success:false});
put();await run('missing-system',{instruction:'deposit',accounts:depositRoles().slice(0,2),success:false});
put();const badSystem=depositRoles();badSystem[2]=meta(other.address,AccountRole.READONLY);await run('wrong-system',{instruction:'deposit',accounts:badSystem,success:false});
put();await run('truncated-argument',{data:Buffer.concat([disc('global:withdraw'),Buffer.alloc(7)]),success:false});
put();await run('unknown-instruction',{data:Buffer.alloc(16),success:false});
put();await run('self-recipient',{accounts:withdrawRoles(vault),success:false});
put();await run('authority-recipient-alias',{accounts:withdrawRoles(user.address)});
put();await run('exact-rent-floor',{amount:10_000_000n-floor});assert.equal(BigInt(snapshot(main).vaultLamports),floor);
put();put(base,id,other.address);
const forged=withdrawRoles();forged[1]=meta(other.address);await run('wrong-canonical-key',{accounts:forged,success:false});
put();
for(const {svm} of states.values()){
 const a=account(svm,system);svm.setAccount({address:system,lamports:a.lamports,data:a.data,programAddress:a.programAddress,executable:false});
}
await run('nonexecutable-system',{instruction:'deposit',accounts:depositRoles(),success:false});
const implementations={};
for(const [name,entry] of states){const measure={};for(const op of ['deposit','withdraw']){const a=entry.cu[op];measure[op]={samples:a.length,meanCu:a.length===20?Math.round(a.reduce((s,n)=>s+n,0)/20):null,minCu:a.length?Math.min(...a):null,maxCu:a.length?Math.max(...a):null};}implementations[name]={...measure,binarySha256:entry.binarySha256,binaryBytes:entry.binaryBytes};}
const result={generated:new Date().toISOString(),scope:'Selected deposit/withdraw on seeded canonical state; initializer and full ELF-size ranking excluded',programId:id,accountDataBytes:57,authoritySeed,canonicalBump:bump,pdaSearchAttempts:256-bump,roundsPerOperation:20,checks:rows.length,identicalSignedTransactions:true,primaryEquivalence:true,primaryErrorCodesMatch:true,auxiliaryMismatches,auxiliaryErrorDifferences,additionalBaseline:typesol?'typesolInternal':'not supplied',implementations,rows};
writeFileSync(path.join(output,'results.json'),JSON.stringify(result,null,2)+'\n');
writeFileSync(path.join(output,'provenance.json'),JSON.stringify({runnerSha256:sha(readFileSync(new URL(import.meta.url))),node:process.version,sourceFiles:['benchmarks/vault/native/src/lib.rs','benchmarks/vault/pinocchio/src/lib.rs','benchmarks/vault/kotlin/vault.kt','benchmarks/vault/typesol/vault.ts'].map(file=>({file,sha256:sha(readFileSync(new URL('../'+file,import.meta.url)))}))},null,2)+'\n');
console.log(JSON.stringify({checks:rows.length,primaryEquivalence:true,auxiliaryMismatches:auxiliaryMismatches.length,implementations},null,2));
