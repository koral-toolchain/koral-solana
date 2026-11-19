# Write and test your own program

Status: Unreleased source/candidate workflow. The published 0.2.0-preview.1 archive
does not contain this custom scaffold or client. Existing published bytes are unchanged.

```sh
koral init my-program --template custom
cd my-program
koral doctor
koral setup
koral check
koral build
koral test
```

Use the installed JDK17+, Agave3.1.10/platform-tools1.52 SDK and Node22.12+ with npm.
setup explicitly installs the pinned VM tools in your user cache; build/test do not
install dependencies or modify the CLI installation.

Edit src/custom.kt and tests/program.test.mjs together. The starter compiles a
receipt program with a fixed local recipient, quantity policy and checked price.
Its tests assert exact amounts, owned state, rejected quantities/addresses, actual
System CPI and rollback after a deliberate later failure. Change these expectations
when deliberately changing the contract. Compilation alone is not successful testing.

## Tests you own

A format=2 project may select test=custom. This runs exactly tests/program.test.mjs
inside that project, using Node. The script is trusted user-authored code and has
your normal local process permissions. Do not run unfamiliar projects' test scripts
without reviewing them. This execution path is not exposed by the website worker.

The CLI sets KORAL_TEST_BINARY to the built .so path and KORAL_TEST_CLIENT to a file
URL for the bundled client. Your test imports that URL; no user-cache path or
separate npm project needs to be hard-coded. For example:

```js
import assert from 'node:assert/strict';
const {createLocalProgram} = await import(process.env.KORAL_TEST_CLIENT);
const program = await createLocalProgram({
  binary: process.env.KORAL_TEST_BINARY,
  programAddress: 'EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN'
});
const buyer = await program.actor(1), recipient = await program.actor(3);
assert.equal((await program.invoke('initialize', {authority: buyer})).success, true);
const before = program.balance(recipient);
const result = await program.invoke('purchase', {
  authority: buyer, args: {quantity: 2n}, accounts: {recipient}
});
assert.equal(result.success, true, result.error);
assert.equal(program.balance(recipient) - before, 20_000_000n);
assert.deepEqual(await program.readState(buyer), {units: 2n, paid: 20_000_000n});
```

The program address and deterministic actor identities are local VM fixtures, not
deployment instructions or wallets for live funds. A separate fixture pays fees;
failed transactions still charge that payer. Check business balances and state
for rollback without claiming transaction fees are rolled back.

The custom.test.json report records script completion. Your assertions establish
the tested behavior; the CLI cannot prove arbitrary scripts tested anything useful.
Failed/timed-out runs remove the selected report, and rebuilds remove default
reports. Missing scripts fail with recovery guidance. Tests must stay in the project.

## Find a failing assertion

A failed custom assertion keeps its message and expected/actual details, then
shows up to three user file/line/column locations. Project paths are relative;
consumer helpers outside the project retain their actual file paths. Node loader,
node_modules and bundled-client frames are omitted. A missing source position is
labeled unavailable with the test entry path, rather than inventing a line.
Module parse errors retain Node's native file URL, line and source/caret frame,
including syntax errors in imported helpers outside the project. They reuse the
original exception; the test is not executed again to discover its location.
Runtime-created SyntaxErrors (for example JSON.parse in user code) keep the normal
compact user-source frames. Missing-import errors retain the requested module and
entry context; unavailable locations are not invented.

Failed tests still invalidate their report. Useful failure messages do not prove
that the user authored sufficient assertions or that the contract is secure.

## Preview ABI client

- createProgramClient({abi, programAddress}) builds a client for koral-preview-abi/1
  targeting sbpf-v0. It does not accept Anchor IDLs or arbitrary account layouts.
- instruction(name, {authority, args, accounts, state?, overrides?}) returns an
  @solana/kit instruction. It uses named arguments, emitted discriminator/offsets,
  declared account order/permissions and fixed addresses. Missing/unknown inputs,
  unsupported data types and invalid u64 values are rejected. Use bigint for large
  values. Fixed metadata constraints are enforced by the actual program on execution.
- stateAddress(authority) derives the declared canonical PDA. decodeState(account)
  reads the declared ULong fields after checking the owner/layout discriminator.
- createLocalProgram({binary, programAddress}) loads your actual SBF in LiteSVM.
  actor(seed, balance?) creates a VM-only fixture (seed1..254). invoke builds/signs
  and sends a local transaction, returning success/error/errorCode/logs/metadata.
  readState(authority) and balance(actorOrAddress) inspect actual VM accounts.
- initialize takes no args for zeroed fields, or all state fields by name. Partial
  initial values are rejected. state can explicitly override the derived account
  for permission/authority failure tests; overrides can alter named account address,
  signer and writable flags. The VM remains responsible for validating execution.

This is an experimental JavaScript consumer tool, not a published npm package,
a Kotlin/JVM runtime, an RPC client, an audit or arbitrary Solana framework support.
Applications can use instruction construction with the matching @solana/kit version;
networking, wallets and deployment are outside this workflow.

## Keep the fixed template suites honest

Counter, payments and sol-vault still run their fixed regression suites. A mismatched
ABI reports which template contract was expected and points here, rather than
running the wrong verifier and producing an internal TypeError. Matching the ABI
shape alone never establishes correct behavior: native regression assertions still
run, including failures and rollback. Legacy Counter binary-only invocation remains
supported without an adjacent ABI. Custom tests require a project and its script.
