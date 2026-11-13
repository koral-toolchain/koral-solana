# Product CI contract

The product workflow runs on main pushes, pull requests and owner-requested manual
verification. Local verification does not establish a successful hosted workflow
run; inspect the Actions results for the revision being reviewed. No package
publication or live-chain deployment is configured.

## Current checks

| Job | Actual work |
| --- | --- |
| policy | Owner-only submissions, proposed landed message, compiler/toolchain/version agreement against a fixed handbook commit |
| unit | JDK 17 Gradle build, compiler tests and candidate CLI TAR |
| sbf-integration | Fresh candidate extraction, JDK 17 / Node 22, full native VM integration suite |
| consumer-install | Fresh candidate extraction, init/setup/project build/test with an unchanged installation, actionable failures, manifest/ABI and Counter/payment checks |
| compatibility | The same candidate on JDK 21 / Node 24, Counter and payment VM checks |
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
  --archive cli/build/distributions/koral-0.1.0-dev.tar \
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

Every native consumer job also invokes the extracted CLI on twenty-one rejected source
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
