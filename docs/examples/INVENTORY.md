# Aggregate inventory consumer

This independently authored example exercises a different domain from ticket
payments: received, available, reserved and sold counts, with receive/reserve/
cancel/dispatch/return operations. It moves no funds and implements no token.
It is an owner-operated quantity ledger, not a multi-customer reservation service.

From an extracted 0.3.0-preview.1 installation, create a custom project,
copy the bundled examples/inventory.kt over its src/custom.kt, and copy the bundled
testing/inventory-test.mjs over tests/program.test.mjs. Then run setup/check/build/test.
For example, set KORAL_HOME to the extracted installation directory:

```sh
koral init stock --template custom
cp "$KORAL_HOME/examples/inventory.kt" stock/src/custom.kt
cp "$KORAL_HOME/testing/inventory-test.mjs" stock/tests/program.test.mjs
cd stock
koral setup
koral check
koral build
koral test
```

The suite asserts independent expected transitions, conservation across four
ULong fields, permission/quantity failures, overflow rejection and unchanged state
on failure. With zero initialization, its core currently passes 66 assertions.
KORAL_INVENTORY_RESULT may specify an optional JSON evidence file in an existing
directory. That is an example-owned output, separate from the CLI completion report.

## Explicit unmet requirement

Generic initialize accepts a complete list of state values without executing the
Kotlin instruction guards. The consumer probe can initialize received=10 and
available=999, violating its application invariant. Later operations reject that
state with 7100, but initialization itself has already succeeded. Only the state's
own signing authority can initialize it; this is not an authorization bypass.
The example records this as an unmet application requirement, not a passing
invariant-enforcement claim. Use zero initialization for the demonstrated workflow.

The language currently reserves initialize, so a custom on-chain initializer
policy cannot be expressed here. The next proposed feature is an opt-in zero-only
initialization policy; see [the roadmap](../ROADMAP.md). It is not implemented by
this example or client-side input checks. A client restriction alone would not
prevent another client from submitting the existing explicit-field payload.

The first natural helper-function attempt was also rejected: shared invariant
checks must currently be repeated in the supported Kotlin subset. Individual
reservation IDs, time, delegated actors, arbitrary account data and persistent
client services remain outside this demo. Do not market this as a production
inventory service, an audit or universal program support.
