# Product CI contract

The product workflow runs on main pushes, pull requests and owner-requested manual
verification. Local verification does not establish a successful hosted workflow
run; inspect the Actions results for the revision being reviewed. No package
publication or live-chain deployment is configured. See [engineering rules](ENGINEERING.md) for the exact controls and review boundaries.

## Current checks

| Job | Actual work |
| --- | --- |
| policy | Owner-only submissions, proposed landed message, compiler/toolchain/version agreement against a fixed handbook commit |
| quality | Pinned Kotlin/JS/Python formatting, selected static analysis, bytecode boundaries, repository/docs checks and PR issue/explanation checks |
| unit | JDK 17 Gradle build, compiler tests and candidate CLI TAR |
| sbf-integration | Fresh candidate extraction, JDK 17 / Node 22, full native VM integration suite |
| consumer-install | Fresh candidate extraction, init/setup/project build/test with an unchanged installation, actionable failures, manifest/ABI and Counter/payment/vault project checks |
| compatibility | The same candidate on JDK 21 / Node 24, Counter/payment/vault VM checks |
| required | Every required result must be success; missing, failed, cancelled and skipped results are rejected |

The native jobs use Ubuntu 24.04 hosted Linux x64 runners. Each downloads Agave
3.1.10 and platform-tools 1.52, verifies fixed SHA-256 values, and extracts the
SDK. It does not silently use the runner's globally installed Solana tools.
The selected JDK is used through JAVA_HOME by the extracted CLI; runtime versions
are printed. Tests inspect the installed archive, not source-project substitution.

macOS arm64 has local verification evidence but is not a hosted CI matrix entry.
JDK 25, Node 26, Windows, a published Gradle plugin and a complete cross-platform
release matrix remain outside this workflow. Changes to supported targets need
actual execution evidence and a handbook update.

## Security and enforcement

Third-party actions and the public handbook are pinned to full commit hashes.
The workflow has read-only contents permission, no cloud OIDC and no deployment
or package secrets. External contributions are currently closed. There is no
pull_request_target job that checks out a contributor's code and no production
server runner. Updating the pinned handbook is an explicit reviewed change.

The public product repository requires a passing `required` status before merging
into main. Branch protection includes administrators and rejects force pushes and
branch deletion. Pull requests are required; the sole maintainer does not need a
second person to approve their own changes. External contributions remain closed.
Do not disable these controls merely to make a failing change mergeable.

An eventual release workflow must separately verify benchmark provenance, artifact
integrity and owner authorization, then depend on successful gates. These jobs are
not simulated here and main pushes do not publish a release.

## Local reproduction

From this directory's parent (the utility root):

```sh
./gradlew --no-daemon test :cli:distTar
python3 tools/ci/consumer.py \
  --archive cli/build/distributions/koral-0.3.0-preview.1.tar \
  --sdk "$KORAL_SBF_SDK" --mode integration
```

On Linux x64, `python3 tools/ci/prepare_sdk.py --destination /new/sdk/path`
installs the pinned SDK. Elsewhere pass an independently installed matching SDK.
The installer requires a new destination and does not replace existing tools.

## First-project regression coverage

The consumer-install and compatibility jobs run the actual CLI from the candidate
archive in a clean workspace. They create a project with spaces in its path,
install VM dependencies through setup into an isolated user cache, and build/test
without positional arguments. They also check missing SDK/dependencies, refusal
to overwrite a project, unsupported changed source, stale success artifacts,
explicit legacy binary/source commands, payment compatibility and an unchanged
installed distribution. The full existing native integration suite remains a
separate job with the legacy testing dependency layout.

## Source diagnostic regression coverage

Every native consumer job also invokes the extracted CLI on twenty-three rejected source
fixtures and a valid control. Checks cover exact one-based file/line/column,
readable reasons, no internal exception trace for expected rejections, and the
file-start fallback for model validation. A nested initializer fixture checks that
the rejected local declaration retains its own location. Kotlin type/name errors
remain Kotlin diagnostics. These checks do not require the SBF SDK or a live chain.

The native suites also compile constrained-payments.kt and execute constraints.mjs
against LiteSVM: fixed owner/address, exact data-size and true/false executable validation, independent alias constraints,
rejection before CPI, permission failures and rollback after a later body failure.

## Publication decision

[The product release checklist](RELEASING.md) applies organization release policy
v1. Product CI verifies its declared jobs; it does not approve release timing,
publish a candidate or announce changes. Handbook policy metadata is checked in
the handbook repository and does not silently replace the checker pinned here.

The candidate suites also build sol-vault.kt and execute vault.mjs. Tests cover
canonical initialization, System-CPI deposits, direct owned-state withdrawals,
authority/permissions, actual-size rent retention, alias/overflow rejection and
CPI/direct-update rollback. Rust/Pinocchio/auxiliary performance comparisons are
separate reproducible evidence, not silently added product build dependencies.

## Payment/vault project regression coverage

Consumer and compatibility jobs generate payments/sol-vault projects from the
extracted archive, run check/build/test with no positional arguments, and select
each suite explicitly for the produced binary. They require 37/101 native checks,
rollback evidence and exact source parity with the bundled maintained examples.
Wrong suites, unknown templates/configuration, missing ABI and protected report
paths must fail. A compiled but behaviorally mutated payment/vault must fail its
real SBF test and remove a planted stale success report. Restored source must pass
again, and the installed distribution's files must remain unchanged.

## Custom consumer regression coverage (published in 0.3.0-preview.1)

The extracted candidate creates a custom project with spaces in its path and
runs its user-owned assertions on actual SBF. A compiled wrong-price mutation
must fail independent balance expectations and invalidate the prior report.
Client checks cover named inputs, u64 boundaries, initialized fields, permission
failures and owner/layout decoding. Missing or escaped scripts and protected
report paths fail without overwriting the consumer's test. Built-in suites still
execute all native assertions after a template-contract preflight, and an edited
custom contract must receive actionable guidance without an internal TypeError.
A custom completion report does not certify arbitrary user-defined coverage.

Consumer jobs also execute real Node assertion-location fixtures in paths with
spaces and parentheses, external helpers, missing positions and filtered internal
frames. The wrong-price SBF regression must identify the consumer test file/line
while invalidating its report. These checks do not relax required success gates.

Custom syntax fixtures exercise the actual dispatcher for entry/helper parse
errors, external imported modules, runtime SyntaxErrors and missing imports.
Native file/line/code frames must survive, internal loader frames must not leak,
and a side-effect marker must show exactly one execution. Extracted-CLI tests
also verify report invalidation and a successful return to the real SBF test after
the user fixes those source/import errors.

Consumer and compatibility jobs also run the independent inventory example. Its
four-field conservation, reservation transitions, permission/overflow rejection
and explicit initialization-policy finding are recorded separately. A passing
zero-initialized core scope does not mean arbitrary initial-state invariants or
per-reservation IDs are supported.

The final required gate now includes quality. Ordinary CI uses strict dependency checksum metadata and never regenerates it. Consumer jobs validate the archive allowlist before extraction. Quality tests include rejection controls; main server protection still requires the stable `required` check.

## Control hardening

Policy scope executes from a base-pinned handbook checker against a separate merged
record and exact critical-file digests. Positive Gradle preparation precedes offline
negative probes, and only classified resolution failures have bounded retries.
Candidate jobs compare runtime JAR names and bytes against the separate Gradle
runtimeClasspath manifest. See [engineering rules](ENGINEERING.md) for trust and
manual-review limits. Inspect the post-merge main result as well as the PR gate.
