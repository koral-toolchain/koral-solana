// Actual custom SBF plus independent consumer-side argument/ABI boundary checks.
import assert from "node:assert/strict";
import { createLocalProgram } from "./client.mjs";
const p = await createLocalProgram({
    binary: process.argv[2],
    programAddress: "EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN",
});
const buyer = await p.actor(11),
    recipient = await p.actor(3);
const valid = {
    authority: buyer,
    args: { quantity: 1n },
    accounts: { recipient },
};
let checks = 0;
async function reject(fn, pattern) {
    await assert.rejects(fn, pattern);
    checks++;
}
await reject(
    () => p.instruction("purchase", { ...valid, args: {} }),
    /Missing argument/,
);
await reject(
    () =>
        p.instruction("purchase", {
            ...valid,
            args: { quantity: 1n, typo: 2n },
        }),
    /Unknown argument/,
);
for (const quantity of [
    -1n,
    1n << 64n,
    NaN,
    Infinity,
    Number.MAX_SAFE_INTEGER + 1,
    "2",
    null,
]) {
    await reject(
        () => p.instruction("purchase", { ...valid, args: { quantity } }),
        /u64|safe integer/,
    );
}
await reject(
    () =>
        p.instruction("purchase", {
            ...valid,
            accounts: { recipent: recipient },
        }),
    /Unknown account/,
);
await reject(
    () =>
        p.instruction("purchase", {
            ...valid,
            overrides: { typo: { writable: false } },
        }),
    /Unknown account override/,
);
await reject(
    () => p.instruction("purchase", { ...valid, args: null }),
    /named object/,
);
await reject(
    () =>
        p.instruction("purchase", {
            ...valid,
            overrides: { recipient: { writeable: false } },
        }),
    /Unknown permission override/,
);
await reject(
    () =>
        p.instruction("purchase", {
            ...valid,
            overrides: { recipient: { writable: "false" } },
        }),
    /must be Boolean/,
);
await reject(() => p.instruction("typo", valid), /not in the program ABI/);
await reject(
    () =>
        p.instruction("initialize", { authority: buyer, args: { units: 1n } }),
    /no values or all state fields/,
);
await reject(() => p.actor(255), /fixture seed/);
const initialized = await p.invoke("initialize", {
    authority: buyer,
    args: { units: 7n, paid: 9n },
});
assert.equal(initialized.success, true, initialized.error);
checks++;
assert.deepEqual(await p.readState(buyer), { units: 7n, paid: 9n });
checks++;
const before = p.balance(recipient);
const paid = await p.invoke("purchase", valid);
assert.equal(paid.success, true, paid.error);
checks++;
assert.equal(p.balance(recipient) - before, 10_000_000n);
checks++;
assert.deepEqual(await p.readState(buyer), { units: 8n, paid: 10_000_009n });
checks++;
const readonly = await p.invoke("purchase", {
    ...valid,
    overrides: { recipient: { writable: false } },
});
assert.equal(readonly.success, false);
assert.equal(readonly.errorCode, 3006);
checks += 2;
const missingSigner = await p.invoke("purchase", {
    ...valid,
    overrides: { authority: { signer: false } },
});
assert.equal(missingSigner.success, false);
assert.equal(missingSigner.errorCode, 3010);
checks += 2;
const actual = p.vm.getAccount(await p.stateAddress(buyer));
assert.throws(
    () => p.decodeState({ ...actual, data: new Uint8Array(8) }),
    /does not match/,
);
checks++;
assert.throws(
    () =>
        p.decodeState({
            ...actual,
            programAddress: "11111111111111111111111111111111",
        }),
    /does not match/,
);
checks++;
console.log(
    JSON.stringify({
        clientChecks: checks,
        scope: "named inputs, exact data/state, permissions and decoder rejection in local VM",
    }),
);
