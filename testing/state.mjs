// Real owned-state account execution. This is a reserve-state example, not token movement or a DEX.
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { LiteSVM, FailedTransactionMetadata } from 'litesvm';
import { AccountRole, address, createKeyPairSignerFromPrivateKeyBytes, getAddressEncoder, getProgramDerivedAddress, lamports, createTransactionMessage, setTransactionMessageFeePayerSigner, appendTransactionMessageInstructions, signTransactionMessageWithSigners, pipe } from '@solana/kit';
const [binary,report]=process.argv.slice(2);
const id=address('EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN');
const system=address('11111111111111111111111111111111');
const enc=getAddressEncoder();
const disc=s=>createHash('sha256').update(s).digest().subarray(0,8);
const u64=n=>{const b=Buffer.alloc(8);b.writeBigUInt64LE(BigInt(n));return b;};
const user=await createKeyPairSignerFromPrivateKeyBytes(new Uint8Array(32).fill(1));
const other=await createKeyPairSignerFromPrivateKeyBytes(new Uint8Array(32).fill(2));
const third=await createKeyPairSignerFromPrivateKeyBytes(new Uint8Array(32).fill(3));
const [pool,bump]=await getProgramDerivedAddress({programAddress:id,seeds:['pool',enc.encode(user.address)]});
const svm=new LiteSVM();svm.addProgramFromFile(id,binary);
for(const who of [user,other,third])svm.airdrop(who.address,lamports(100_000_000_000n));
let checks=0;
const check=(condition,message)=>{assert.ok(condition,message);checks++;};
const meta=(at,role=AccountRole.WRITABLE,signer)=>({address:at,role,...(signer?{signer}:{})});
const updateRoles=()=>[meta(user.address,AccountRole.READONLY_SIGNER,user),meta(pool)];
const initRoles=(who,at)=>[meta(who.address,AccountRole.WRITABLE_SIGNER,who),meta(at),meta(system,AccountRole.READONLY)];
async function send(name,values,accounts=updateRoles(),payer=user){
 const ix={programAddress:id,accounts,data:Buffer.concat([disc(`global:${name}`),...values.map(u64)])};
 const tx=await pipe(createTransactionMessage({version:0}),t=>setTransactionMessageFeePayerSigner(payer,t),t=>svm.setTransactionMessageLifetimeUsingLatestBlockhash(t),t=>appendTransactionMessageInstructions([ix],t),t=>signTransactionMessageWithSigners(t));
 const result=svm.sendTransaction(tx);svm.expireBlockhash();return result;
}
const failed=r=>r instanceof FailedTransactionMetadata;
function account(at=pool){const a=svm.getAccount(at);assert.ok(a.exists);return a;}
function state(at=pool){const b=Buffer.from(account(at).data);return [b.readBigUInt64LE(40),b.readBigUInt64LE(48)];}
function put(data,owner=id){svm.setAccount({address:pool,lamports:account().lamports,data,programAddress:owner,executable:false});}
check(!failed(await send('initialize',[100,200],initRoles(user,pool))),'initialize declared fields');
check(account().data.length===57,'declared 57-byte layout');
check(Buffer.from(account().data).subarray(0,8).equals(disc('account:PoolState')),'layout-specific discriminator');
check(Buffer.from(account().data).subarray(8,40).equals(Buffer.from(enc.encode(user.address))),'authority metadata');
check(account().data[56]===bump,'layout-specific canonical PDA bump');
check(account().lamports===svm.minimumBalanceForRentExemption(57n),'dynamic rent for new layout');
assert.deepEqual(state(),[100n,200n]);checks++;
check(!failed(await send('add_reserves',[5,10])),'two-field update');assert.deepEqual(state(),[105n,210n]);checks++;
check(!failed(await send('withdraw_a',[6])),'field read and one-field update');assert.deepEqual(state(),[99n,210n]);checks++;
let before=Buffer.from(account().data);
check(failed(await send('withdraw_a',[100])),'underflow rejection');
check(Buffer.from(account().data).equals(before),'underflow rollback');
check(failed(await send('add_reserves',[5,'18446744073709551615'])),'second-field overflow');
check(Buffer.from(account().data).equals(before),'all fields remain unchanged on partial construction failure');
check(failed(await send('add_reserves',[5])),'truncated arguments');
check(failed(await send('missing',[])),'unknown instruction');
check(failed(await send('withdraw_a',[1],[meta(other.address,AccountRole.READONLY_SIGNER,other),meta(pool)],other)),'wrong authority');
check(failed(await send('withdraw_a',[1],[meta(user.address,AccountRole.READONLY_SIGNER,user),meta(pool,AccountRole.READONLY)])),'readonly state');
put(before,system);check(failed(await send('withdraw_a',[1])),'wrong owner');put(before);
const damaged=Buffer.from(before);damaged.fill(0,0,8);put(damaged);check(failed(await send('withdraw_a',[1])),'wrong account discriminator');put(before);
put(before.subarray(0,56));check(failed(await send('withdraw_a',[1])),'short state');put(before);
check(!failed(await send('reset',[])),'state local and state-valued branch');assert.deepEqual(state(),[0n,0n]);checks++;
const asymmetric=Buffer.from(account().data);u64(5).copy(asymmetric,48);put(asymmetric);
check(!failed(await send('reset',[])),'state-valued branch original-state path');assert.deepEqual(state(),[0n,5n]);checks++;
const [zeroPool]=await getProgramDerivedAddress({programAddress:id,seeds:['pool',enc.encode(other.address)]});
check(!failed(await send('initialize',[],initRoles(other,zeroPool),other)),'zero initialization without optional fields');assert.deepEqual(state(zeroPool),[0n,0n]);checks++;
const [thirdPool]=await getProgramDerivedAddress({programAddress:id,seeds:['pool',enc.encode(third.address)]});
check(failed(await send('initialize',[1],initRoles(third,thirdPool),third)),'partial initializer rejected');
check(!svm.getAccount(thirdPool).exists,'invalid initializer did not allocate state');
check(failed(await send('initialize',[1,2],initRoles(third,pool),third)),'wrong PDA for initializer');
const result={checks,layout:'PoolState',fieldCount:2,accountDataBytes:57,pdaSeed:'pool',canonicalBump:bump,network:'Local VM',scope:'Owned ULong state updates, no token CPI or DEX execution'};
if(report)writeFileSync(report,JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
