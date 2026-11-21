// Experimental local consumer tools for koral-preview-abi/1. No RPC or deployment.
import { readFileSync } from "node:fs";
import path from "node:path";
import { LiteSVM, FailedTransactionMetadata } from "litesvm";
import {
    AccountRole,
    address,
    createKeyPairSignerFromPrivateKeyBytes,
    getAddressEncoder,
    getProgramDerivedAddress,
    lamports,
    createTransactionMessage,
    setTransactionMessageFeePayerSigner,
    appendTransactionMessageInstructions,
    signTransactionMessageWithSigners,
    pipe,
} from "@solana/kit";
export { AccountRole };
const at = (value) =>
    typeof value === "string" ? address(value) : address(value.address);
const u64 = (value) => {
    if (typeof value === "number" && !Number.isSafeInteger(value))
        throw Error("Use bigint for u64 values above the safe integer range");
    if (!["bigint", "number"].includes(typeof value))
        throw Error("u64 arguments must be bigint or safe integer values");
    const n = BigInt(value);
    if (n < 0n || n > 0xffffffffffffffffn)
        throw Error("u64 argument is out of range");
    return n;
};
function known(values, names, label) {
    if (values === null || typeof values !== "object" || Array.isArray(values))
        throw Error(`${label} values must be a named object`);
    for (const key of Object.keys(values))
        if (!names.includes(key)) throw Error(`Unknown ${label} '${key}'`);
}
export function loadProgramAbi(binary) {
    const abi = JSON.parse(
        readFileSync(
            path.join(path.dirname(binary), "counter.abi.json"),
            "utf8",
        ),
    );
    if (abi.schema !== "koral-preview-abi/1" || abi.target !== "sbpf-v0")
        throw Error("Expected koral-preview-abi/1 targeting sbpf-v0");
    return abi;
}
export function createProgramClient({ abi, programAddress }) {
    if (abi.schema !== "koral-preview-abi/1" || abi.target !== "sbpf-v0")
        throw Error("Unsupported preview ABI");
    const program = address(programAddress),
        system = address("11111111111111111111111111111111");
    async function stateAddress(authority) {
        return (
            await getProgramDerivedAddress({
                programAddress: program,
                seeds: [
                    abi.state.pdaSeed,
                    getAddressEncoder().encode(at(authority)),
                ],
            })
        )[0];
    }
    async function instruction(
        name,
        { authority, state, args = {}, accounts = {}, overrides = {} } = {},
    ) {
        if (!authority) throw Error("An authority signer is required");
        const descriptor =
            name === "initialize"
                ? abi.initialize
                : abi.instructions.find((ix) => ix.name === name);
        if (!descriptor)
            throw Error(`Instruction '${name}' is not in the program ABI`);
        known(
            args,
            descriptor.data.map((v) => v.name),
            "argument",
        );
        const external = descriptor.accounts.filter(
            (a) => !["authority", "state", "system_program"].includes(a.name),
        );
        known(
            accounts,
            external.map((a) => a.name),
            "account",
        );
        known(
            overrides,
            descriptor.accounts.map((a) => a.name),
            "account override",
        );
        const initializing = name === "initialize";
        const count = Object.keys(args).length;
        if (initializing && count !== 0 && count !== descriptor.data.length)
            throw Error("Initialize with no values or all state fields");
        if (!initializing)
            for (const item of descriptor.data)
                if (!Object.hasOwn(args, item.name))
                    throw Error(`Missing argument '${item.name}'`);
        const data = Buffer.alloc(
            8 + 8 * (initializing && count === 0 ? 0 : descriptor.data.length),
        );
        if (!/^[0-9a-f]{16}$/i.test(descriptor.discriminatorHex))
            throw Error("Invalid instruction discriminator");
        Buffer.from(descriptor.discriminatorHex, "hex").copy(data);
        if (data.length > 8)
            for (const item of descriptor.data) {
                if (item.type !== "u64")
                    throw Error("Unsupported ABI data type");
                data.writeBigUInt64LE(u64(args[item.name]), item.offset);
            }
        const ledger = state ? at(state) : await stateAddress(authority);
        const metas = descriptor.accounts.map((item) => {
            let value =
                item.name === "authority"
                    ? authority
                    : item.name === "state"
                      ? ledger
                      : item.name === "system_program"
                        ? system
                        : (accounts[item.name] ?? item.address);
            if (!value) throw Error(`Missing account '${item.name}'`);
            const override = overrides[item.name] ?? {};
            known(
                override,
                ["address", "signer", "writable"],
                "permission override",
            );
            for (const flag of ["signer", "writable"])
                if (flag in override && typeof override[flag] !== "boolean")
                    throw Error(`${flag} override must be Boolean`);
            const writable = override.writable ?? item.writable,
                signing = override.signer ?? item.signer;
            if (override.address) value = override.address;
            const role = signing
                ? writable
                    ? AccountRole.WRITABLE_SIGNER
                    : AccountRole.READONLY_SIGNER
                : writable
                  ? AccountRole.WRITABLE
                  : AccountRole.READONLY;
            const signer =
                signing && typeof value === "object" && value.signTransactions
                    ? value
                    : undefined;
            return { address: at(value), role, ...(signer ? { signer } : {}) };
        });
        return { programAddress: program, accounts: metas, data };
    }
    function decodeState(account) {
        if (!account?.exists)
            throw Error("State account does not exist; initialize it first");
        const data = Buffer.from(account.data);
        if (
            data.length < abi.state.bytes ||
            at(account.programAddress ?? account.owner) !== program ||
            data.subarray(0, 8).toString("hex") !== abi.state.discriminatorHex
        )
            throw Error("Account does not match this program state ABI");
        return Object.fromEntries(
            abi.state.fields.map((f) => [
                f.name,
                data.readBigUInt64LE(f.offset),
            ]),
        );
    }
    return {
        abi,
        programAddress: program,
        stateAddress,
        instruction,
        decodeState,
    };
}
export async function createLocalProgram({ binary, programAddress }) {
    const client = createProgramClient({
        abi: loadProgramAbi(binary),
        programAddress,
    });
    const vm = new LiteSVM();
    vm.addProgramFromFile(client.programAddress, binary);
    // A separate VM-only fixture pays fees, allowing exact business-balance assertions.
    const feePayer = await createKeyPairSignerFromPrivateKeyBytes(
        new Uint8Array(32).fill(255),
    );
    vm.airdrop(feePayer.address, lamports(1_000_000_000_000n));
    async function actor(seed, balance = 1_000_000_000n) {
        if (!Number.isInteger(seed) || seed < 1 || seed > 254)
            throw Error(
                "Local fixture seed must be 1..254 (never use it as a real wallet)",
            );
        const who = await createKeyPairSignerFromPrivateKeyBytes(
            new Uint8Array(32).fill(seed),
        );
        vm.airdrop(who.address, lamports(u64(balance)));
        return who;
    }
    async function invoke(name, options) {
        const ix = await client.instruction(name, options);
        const tx = await pipe(
            createTransactionMessage({ version: 0 }),
            (t) => setTransactionMessageFeePayerSigner(feePayer, t),
            (t) => vm.setTransactionMessageLifetimeUsingLatestBlockhash(t),
            (t) => appendTransactionMessageInstructions([ix], t),
            (t) => signTransactionMessageWithSigners(t),
        );
        const result = vm.sendTransaction(tx);
        vm.expireBlockhash();
        const success = !(result instanceof FailedTransactionMetadata),
            metadata = success ? result : result.meta();
        const error = success ? null : String(result.err());
        return {
            success,
            error,
            errorCode: error?.match(/code:\s*(\d+)/)?.[1]
                ? Number(error.match(/code:\s*(\d+)/)[1])
                : null,
            logs: metadata.logs(),
            metadata,
        };
    }
    const balance = (who) => {
        const a = vm.getAccount(at(who));
        if (!a.exists) throw Error("Account does not exist");
        return a.lamports;
    };
    async function readState(authority) {
        return client.decodeState(
            vm.getAccount(await client.stateAddress(authority)),
        );
    }
    return { ...client, vm, actor, invoke, readState, balance, feePayer };
}
