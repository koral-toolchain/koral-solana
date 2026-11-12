import assert from 'node:assert/strict';
import { writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { LiteSVM, FailedTransactionMetadata } from 'litesvm';
import { AccountRole, address, createKeyPairSignerFromPrivateKeyBytes, getAddressEncoder, getProgramDerivedAddress, lamports, createTransactionMessage, setTransactionMessageFeePayerSigner, appendTransactionMessageInstructions, signTransactionMessageWithSigners, pipe } from '@solana/kit';
const id = address('EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN');
const system = address('11111111111111111111111111111111');
const enc = getAddressEncoder();
const disc = (s) => createHash('sha256').update(s).digest().subarray(0, 8);
const num = (n) => { const b = Buffer.alloc(8); b.writeBigUInt64LE(n); return b; };
const user = await createKeyPairSignerFromPrivateKeyBytes(new Uint8Array(32).fill(1));
const other = await createKeyPairSignerFromPrivateKeyBytes(new Uint8Array(32).fill(2));
const [counter] = await getProgramDerivedAddress({ programAddress: id, seeds: ['counter', enc.encode(user.address)] });
const binary = process.argv[2] ?? process.env.KORAL_TEST_BINARY;
const svm = new LiteSVM();
svm.addProgramFromFile(id, binary);
for (const who of [user, other])
    svm.airdrop(who.address, lamports(100000000000n));
let tests = 0;
async function send(data, accounts, payer = user) {
    const ix = { programAddress: id, accounts, data };
    const tx = await pipe(createTransactionMessage({ version: 0 }), (t) => setTransactionMessageFeePayerSigner(payer, t), (t) => svm.setTransactionMessageLifetimeUsingLatestBlockhash(t), (t) => appendTransactionMessageInstructions([ix], t), (t) => signTransactionMessageWithSigners(t));
    const result = svm.sendTransaction(tx);
    svm.expireBlockhash();
    return result;
}
const meta = (a, role = AccountRole.WRITABLE, signer) => ({ address: a, role, ...(signer ? { signer } : {}) });
const roles = () => [meta(user.address, AccountRole.READONLY_SIGNER, user), meta(counter)];
const check = (condition, message) => { assert.ok(condition, message); tests++; };
function put(n = 7n, owner = id, size = 49, authority = user.address, discriminator = disc('account:Counter')) {
    const data = Buffer.alloc(size);
    discriminator.copy(data);
    Buffer.from(enc.encode(authority)).copy(data, 8);
    if (size >= 48)
        num(n).copy(data, 40);
    svm.setAccount({ address: counter, lamports: 10000000n, data, programAddress: owner, executable: false });
}
const increment = (by = 3n) => Buffer.concat([disc('global:increment'), num(by)]);
const fails = (r) => r instanceof FailedTransactionMetadata;
put();
check(!fails(await send(increment(), roles())), 'valid increment');
const got = svm.getAccount(counter);
check(got.exists && Buffer.from(got.data).readBigUInt64LE(40) === (process.env.KORAL_ALTERNATE === '1' ? 6n : 10n), 'source-derived result');
if (process.env.KORAL_ALTERNATE !== '1') {
    put();
    check(fails(await send(increment(), [meta(user.address, AccountRole.READONLY), meta(counter)], other)), 'unsigned authority');
    put();
    check(fails(await send(increment(), [meta(user.address, AccountRole.READONLY_SIGNER, user), meta(counter, AccountRole.READONLY)])), 'readonly counter');
    put(7n, system);
    check(fails(await send(increment(), roles())), 'wrong owner');
    put(7n, id, 49, user.address, Buffer.alloc(8));
    check(fails(await send(increment(), roles())), 'wrong discriminator');
    put(7n, id, 48);
    check(fails(await send(increment(), roles())), 'short account');
    put(7n, id, 49, other.address);
    check(fails(await send(increment(), roles())), 'wrong authority');
    put((1n << 64n) - 1n);
    check(fails(await send(increment(1n), roles())), 'overflow');
    const before = svm.getAccount(counter);
    check(before.exists && Buffer.from(before.data).readBigUInt64LE(40) === (1n << 64n) - 1n, 'failed update rollback');
    put();
    check(fails(await send(increment().subarray(0, 15), roles())), 'truncated arguments');
    check(fails(await send(Buffer.alloc(8), roles())), 'unknown discriminator');
    check(fails(await send(increment(), roles().slice(0, 1))), 'missing account');
    put();
    check(!fails(await send(increment(), [...roles(), meta(counter, AccountRole.READONLY)])), 'duplicate input record');
    const bumps = [];
    for (let i = 3; i <= 18; i++) {
        const who = await createKeyPairSignerFromPrivateKeyBytes(new Uint8Array(32).fill(i));
        svm.airdrop(who.address, lamports(10000000000n));
        const [pda, bump] = await getProgramDerivedAddress({ programAddress: id, seeds: ['counter', enc.encode(who.address)] });
        bumps.push(bump);
        const result = await send(disc('global:initialize'), [meta(who.address, AccountRole.WRITABLE_SIGNER, who), meta(pda), meta(system, AccountRole.READONLY)], who);
        check(!fails(result), `PDA initialize ${i}`);
        const state = svm.getAccount(pda);
        check(state.exists && state.data.length === 49 && state.data[48] === bump && Buffer.from(state.data).subarray(8, 40).equals(Buffer.from(enc.encode(who.address))), `canonical PDA state ${i}`);
        if (i === 3)
            check(fails(await send(disc('global:initialize'), [meta(who.address, AccountRole.WRITABLE_SIGNER, who), meta(pda), meta(system, AccountRole.READONLY)], who)), 'reinitialization');
    }
    check(new Set(bumps).size >= 3, 'multiple bump searches covered');
    const fresh = await createKeyPairSignerFromPrivateKeyBytes(new Uint8Array(32).fill(19));
    svm.airdrop(fresh.address, lamports(10000000000n));
    const [freshPda] = await getProgramDerivedAddress({ programAddress: id, seeds: ['counter', enc.encode(fresh.address)] });
    const init = disc('global:initialize');
    check(fails(await send(init, [meta(fresh.address, AccountRole.READONLY_SIGNER, fresh), meta(freshPda), meta(system, AccountRole.READONLY)], other)), 'readonly initialization payer');
    check(fails(await send(init, [meta(fresh.address, AccountRole.WRITABLE), meta(freshPda), meta(system, AccountRole.READONLY)], other)), 'unsigned initialization payer');
    check(fails(await send(init, [meta(fresh.address, AccountRole.WRITABLE_SIGNER, fresh), meta(freshPda, AccountRole.READONLY), meta(system, AccountRole.READONLY)], fresh)), 'readonly initialization target');
    check(fails(await send(init, [meta(fresh.address, AccountRole.WRITABLE_SIGNER, fresh), meta(counter), meta(system, AccountRole.READONLY)], fresh)), 'wrong PDA');
    check(fails(await send(init, [meta(fresh.address, AccountRole.WRITABLE_SIGNER, fresh), meta(freshPda), meta(other.address, AccountRole.READONLY)], fresh)), 'wrong system program');
    check(fails(await send(init, [meta(fresh.address, AccountRole.WRITABLE_SIGNER, fresh), meta(fresh.address), meta(system, AccountRole.READONLY)], fresh)), 'duplicate writable initialization accounts');
    check(fails(await send(init, [meta(fresh.address, AccountRole.WRITABLE_SIGNER, fresh), meta(freshPda)], fresh)), 'missing system program');
    const rent = svm.minimumBalanceForRentExemption(49n);
    writeFileSync((process.env.KORAL_TEST_RESULT ?? 'counter-test-result.json'), JSON.stringify({ tests, canonicalBumps: bumps, accountDataBytes: 49, storageMinimumLamports: String(rent), storageMinimumSol: Number(rent) / 1e9, scope: 'Local LiteSVM storage minimum; not a paid fee or live RPC quote' }, null, 2) + '\n');
}
else
    writeFileSync((process.env.KORAL_TEST_RESULT ?? 'counter-test-result.json'), JSON.stringify({ tests, alternateExpectedCount: 6, sourceChanged: true }, null, 2) + '\n');
console.log(`Native checks passed: ${tests}`);
