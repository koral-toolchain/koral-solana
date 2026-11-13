// Actual native constraint execution. No RPC, wallet connection or live funds.
import assert from 'node:assert/strict';
import {readFileSync,writeFileSync} from 'node:fs';
import path from 'node:path';
import {LiteSVM,FailedTransactionMetadata} from 'litesvm';
import {AccountRole,address,createKeyPairSignerFromPrivateKeyBytes,getAddressEncoder,getProgramDerivedAddress,lamports,createTransactionMessage,setTransactionMessageFeePayerSigner,appendTransactionMessageInstructions,signTransactionMessageWithSigners,pipe} from '@solana/kit';
const [binary,report]=process.argv.slice(2),abi=JSON.parse(readFileSync(path.join(path.dirname(binary),'counter.abi.json'),'utf8'));
const id=address('EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN'),system=address('11111111111111111111111111111111');
const [user,other,recipient,fees]=await Promise.all([1,2,3,4].map(n=>createKeyPairSignerFromPrivateKeyBytes(new Uint8Array(32).fill(n))));
const [ledger]=await getProgramDerivedAddress({programAddress:id,seeds:['payments',getAddressEncoder().encode(user.address)]});
const svm=new LiteSVM();svm.addProgramFromFile(id,binary);
for(const signer of [user,other,recipient,fees])svm.airdrop(signer.address,lamports(100_000_000_000n));
let checks=0;
const check=(condition,message)=>{assert.ok(condition,message);checks++;};
const meta=(at,role=AccountRole.WRITABLE,signer)=>({address:at,role,...(signer?{signer}:{})});
const roles=(to=recipient.address,role=AccountRole.WRITABLE)=>[meta(user.address,AccountRole.WRITABLE_SIGNER,user),meta(ledger),meta(to,role),meta(system,AccountRole.READONLY)];
const u64=n=>{const b=Buffer.alloc(8);b.writeBigUInt64LE(BigInt(n));return b;};
const account=(at=ledger)=>{const a=svm.getAccount(at);assert.ok(a.exists);return a;};
const balance=at=>account(at).lamports;
const state=()=>Buffer.from(account().data);
function owner(at,programAddress){const a=account(at);svm.setAccount({address:at,lamports:a.lamports,data:a.data,programAddress,executable:false});}
async function send(name,values,accounts){
 const descriptor=name==='initialize'?abi.initialize:abi.instructions.find(i=>i.name===name);assert.ok(descriptor);
 const ix={programAddress:id,accounts,data:Buffer.concat([Buffer.from(descriptor.discriminatorHex,'hex'),...values.map(u64)])};
 const tx=await pipe(createTransactionMessage({version:0}),t=>setTransactionMessageFeePayerSigner(fees,t),t=>svm.setTransactionMessageLifetimeUsingLatestBlockhash(t),t=>appendTransactionMessageInstructions([ix],t),t=>signTransactionMessageWithSigners(t));
 const result=svm.sendTransaction(tx);svm.expireBlockhash();return result;
}
const failed=r=>r instanceof FailedTransactionMetadata;
const cpi=r=>(failed(r)?r.meta():r).logs().some(l=>l.includes('Program 11111111111111111111111111111111 invoke'));
async function reject(name,values,accounts,code){
 const before=state(),balances=[balance(user.address),balance(recipient.address),balance(ledger)];
 const result=await send(name,values,accounts);
 check(failed(result),`${name} must fail`);
 check(result.err().err().code===code,`${name}: expected ${code}, got ${String(result.err())}`);
 check(!cpi(result),'constraint rejection precedes CPI');
 check(state().equals(before),'rejection preserves state');
 assert.deepEqual([balance(user.address),balance(recipient.address),balance(ledger)],balances);checks++;
}
const pay=abi.instructions.find(i=>i.name==='pay').accounts[2];
check(pay.owner===system && pay.address===recipient.address && pay.writable,'ABI preserves both constraints and writable permission');
check(abi.instructions.find(i=>i.name==='observe_owner').accounts[2].address===null,'owner-only has no fixed address');
check(!('owner' in abi.instructions.find(i=>i.name==='observe_address').accounts[2]),'address-only has no owner constraint');
check(!failed(await send('initialize',[],[meta(user.address,AccountRole.WRITABLE_SIGNER,user),meta(ledger),meta(system,AccountRole.READONLY)])),'initialize');
const from=balance(user.address),to=balance(recipient.address);
const paid=await send('pay',[1000],roles());check(!failed(paid) && cpi(paid),'valid constrained payment executes System CPI');
check(balance(user.address)===from-1000n && balance(recipient.address)===to+1000n,'exact payment balances');
check(state().readBigUInt64LE(40)===1000n,'payment persists state');
await reject('pay',[1],roles(other.address),3011);
owner(recipient.address,id);
await reject('pay',[1],roles(),3007);
check(!failed(await send('observe_address',[],roles(recipient.address,AccountRole.READONLY).slice(0,3))),'address-only allows a different owner');
owner(recipient.address,system);
await reject('pay',[1],roles(recipient.address,AccountRole.READONLY),3006);
await reject('pay',[1],roles().slice(0,2),3005);
await reject('pay',[1],roles(ledger),3007);
check(!failed(await send('observe_owner',[],roles(other.address,AccountRole.READONLY).slice(0,3))),'owner-only allows another address without CPI');
check(!failed(await send('observe_program_owned',[],roles(ledger,AccountRole.READONLY).slice(0,3))),'nonzero owner key and duplicate owned-state reference');
await reject('observe_program_owned',[],roles().slice(0,3),3007);
const pairRoles=[...roles(recipient.address,AccountRole.READONLY).slice(0,3),meta(recipient.address,AccountRole.READONLY)];
check(!failed(await send('pair',[],pairRoles)),'duplicate external references satisfy constraints independently');
check(state().readBigUInt64LE(48)===2n*balance(recipient.address),'aliased balances read correctly');
await reject('pay_if',[0],roles(other.address),3011);
const zero=await send('pay_if',[0],roles().slice(0,3));check(!failed(zero) && !cpi(zero),'valid zero branch needs no System Program');
const before=state(),beforeFrom=balance(user.address),beforeTo=balance(recipient.address);
const rollback=await send('pay_then_fail',[3],roles());
check(failed(rollback) && rollback.err().err().code===6200 && cpi(rollback),'valid constraints allow CPI before deliberate body failure');
check(state().equals(before) && balance(user.address)===beforeFrom && balance(recipient.address)===beforeTo,'later body failure rolls back CPI and state');
const result={checks,network:'Local LiteSVM',ownerAndAddress:true,nonzeroOwner:true,duplicateReferences:true,rejectionBeforeCpi:true,bodyFailureRollback:true};
if(report)writeFileSync(report,JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
