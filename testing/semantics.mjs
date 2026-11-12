// Execute explicit input/output cases against a real SBF artifact.
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { LiteSVM, FailedTransactionMetadata } from 'litesvm';
import { AccountRole, address, createKeyPairSignerFromPrivateKeyBytes, getAddressEncoder, lamports, createTransactionMessage, setTransactionMessageFeePayerSigner, appendTransactionMessageInstructions, signTransactionMessageWithSigners, pipe } from '@solana/kit';
const [binary,casesJson]=process.argv.slice(2);
const cases=JSON.parse(casesJson);
const id=address('EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN');
const counter=address('HVWmZ4JYMsvmLQoZMXDcjoat1GHHfC1GsgQjSjbGFGX');
const user=await createKeyPairSignerFromPrivateKeyBytes(new Uint8Array(32).fill(1));
const enc=getAddressEncoder();
const disc=s=>createHash('sha256').update(s).digest().subarray(0,8);
const u64=n=>{const b=Buffer.alloc(8);b.writeBigUInt64LE(BigInt(n));return b;};
const svm=new LiteSVM();svm.addProgramFromFile(id,binary);svm.airdrop(user.address,lamports(100_000_000_000n));
for(const c of cases) {
    const data=Buffer.concat([disc('account:Counter'),Buffer.from(enc.encode(user.address)),u64(c.count),Buffer.alloc(1)]);
    if(!c.keepState) svm.setAccount({address:counter,lamports:10_000_000n,data,programAddress:id,executable:false});
    const argumentsData=c.args ?? [c.by];
    const instructionData=c.rawHex ? Buffer.from(c.rawHex,'hex') : Buffer.concat([disc(`global:${c.instruction ?? 'increment'}`),...argumentsData.map(u64)]);
    const ix={programAddress:id,accounts:[{address:user.address,role:AccountRole.READONLY_SIGNER,signer:user},{address:counter,role:AccountRole.WRITABLE}],data:instructionData};
    const tx=await pipe(createTransactionMessage({version:0}),t=>setTransactionMessageFeePayerSigner(user,t),t=>svm.setTransactionMessageLifetimeUsingLatestBlockhash(t),t=>appendTransactionMessageInstructions([ix],t),t=>signTransactionMessageWithSigners(t));
    const result=svm.sendTransaction(tx);svm.expireBlockhash();
    const failed=result instanceof FailedTransactionMetadata;
    assert.equal(failed,Boolean(c.error),JSON.stringify(c));
    if(c.error) assert.equal(result.err().err().code,c.error,String(result.err()));
    const state=svm.getAccount(counter);assert.ok(state.exists);
    assert.equal(Buffer.from(state.data).readBigUInt64LE(40),BigInt(c.error ? c.count : c.expected),JSON.stringify(c));
}
console.log(`Source semantics passed: ${cases.length} cases`);
