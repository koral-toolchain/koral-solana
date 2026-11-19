# Post-launch roadmap

The initial launch scope is preserved in [MVP.md](MVP.md). This roadmap follows
actual consumer evidence, rather than growing a feature checklist without use.

## Current readiness cycle

| Work | Evidence and status |
| --- | --- |
| Existing public CLI | 0.3.0-preview.1 published; older archives remain immutable |
| Custom projects/client/diagnostics | Published in 0.3.0-preview.1 |
| Fresh installation | New Ubuntu24.04 x64 container, fresh JDK/Node/SDK and empty VM cache; public Counter/payment checks passed |
| Independent consumer | Aggregate inventory: zero-initialized transitions, quantities, permissions and overflow passed 66 assertions; unmet initialization requirement recorded |
| Platform qualification | Hosted Linux x64 JDK17/Node22 and JDK21/Node24; local macOS arm64; container x64 run is emulated on Apple Silicon |

Fresh containers mount only result output or an explicitly identified candidate
archive, never the host SDK, home, credentials or dependency caches. A container
is not a new physical machine. Evidence is summarized in
[the consumer review](consumer-reviews/0.3.0-preview.1.md).

## Next selected capability: opt-in initialization policy

Track implementation in [issue #26](https://github.com/koral-toolchain/koral-solana/issues/26).

The inventory user could define invariants for every update but could not reject
an invalid initial-state payload. Implement a small, explicit zero-only policy
first, preserving the generic initializer for existing source. A new source marker
must enforce the policy in the actual program before creating/writing state; client
checks alone are insufficient. No annotation or initializer policy is implemented
yet by this roadmap.

Acceptance criteria:

- Existing programs retain their documented zero/default and all-fields behavior.
- Opted-in programs reject explicit field payloads, including zero-valued payloads,
  before state creation or CPI, while empty initialization succeeds canonically.
- ABI/client metadata describe the policy; bypassing the client cannot bypass it.
- Rejected initialization preserves balances/state except normal transaction fees.
- Real SBF tests cover authority, writable permission, rent, aliases and rollback.
- The independently authored inventory workflow is repeated with an invalid-init
  attempt and its own expected state, rather than relying on a new built-in suite.

This is the next engineering proposal, not a release prerequisite added retroactively.
Full custom initializer bodies should wait until the smaller policy is validated.

## Following candidates, with reasons

1. Restricted pure helper functions: the same invariant is repeated across five
   inventory operations because ordinary helper declarations/calls are unsupported.
   Specify symbol origins, arguments/returns, recursion rejection and checked
   arithmetic before implementation. Do not remove safety checks for convenience.
2. SDK onboarding: fresh installation worked, but involves separate vendor archives,
   exact checksums and manual paths. Improve explicit setup/recovery before adding
   automatic downloading or expanding platform claims.
3. Multiple owned records or reservation IDs: an aggregate stock ledger cannot
   track individual reservations. Define a real consumer contract and authorization
   model before broadening account/data support.

SPL Token, arbitrary CPI, general JVM libraries, full DEX programs, production
readiness and broad runtime support remain outside current verified capability.
Use new consumer findings to reorder this list. Review public downloads again after
an actual release, and distinguish shipped functionality from source-only changes.
