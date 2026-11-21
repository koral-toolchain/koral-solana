# Engineering rules

This product explicitly adopts engineering revision 2 from the pinned organization handbook. [config/engineering.json](../config/engineering.json) records rule IDs, tool versions and sources; [the handbook](https://github.com/koral-toolchain/handbook/blob/main/guides/engineering.md) explains governance. A new handbook commit does not automatically change product rules.

## Development workflow (ENG-01, ENG-09, ENG-10)

Start non-trivial work with an issue: user outcome, acceptance criteria and exclusions. Link that actual issue in the PR using `Refs #number` or `Closes #number`, and complete Scope, Acceptance, Exclusions, Validation and Compatibility. CI verifies issue identity and explanation presence, not product value or owner consent. The sole human maintainer is tidemark-labs; external contributions remain closed.

Critical changes are checked by a handbook checker selected from the base commit,
not the proposed policy. Module build files, architecture tests, candidate checks,
workflows, wrapper/locks and policy documents are covered. A separate merged
handbook record must bind the actual product issue, exact base SHA and SHA-256 of
every resulting critical file. The PR contains `Policy-Record: <handbook SHA>`;
the record must be merged into handbook main and still match its active record.
A different title, stale record, changed file/byte set or advanced base cannot
substitute for that scope. Records document maintainer scope; they do not prove
human intent or prevent a deliberate owner from changing workflow administration. Keep feature work separate from policy relaxations. Changes to rules, tools, exclusions, thresholds, required jobs or dependency integrity metadata must explain their reason and verification in a policy PR. Owner review remains necessary; a checker cannot approve its own removal. New Kotlin suppressions and finding baselines are rejected by repository checks. Existing rules must not be weakened to obtain a passing result.

## Code and file conventions (ENG-02, ENG-03)

Follow the official Kotlin conventions used by kotlinx.coroutines, with four-space indentation. Spotless 7.2.1 runs ktlint 1.7.1 using its documented `ktlint_official` default profile. This default supplements the official guide where mechanical decisions are needed; it is not identical to IntelliJ's `intellij_idea` profile. Files are UTF-8/LF with a final newline. Class and file names, declarations and imports follow the pinned linter and detekt checks. Related declarations may share a descriptive file; there is no invented universal one-class-per-file requirement.

Use Gradle's existing `src/main/kotlin` and `src/test/kotlin` trees and matching `dev.koral` package paths. Do not create extra backend/frontend or generic dumping-ground roots. Single-class filenames match the declaration; multi-declaration filenames describe the shared responsibility. Semantic cohesion is still reviewed by a person.

Detekt 1.23.8 runs selected non-type-resolved rules in [config/detekt.yml](../config/detekt.yml). Default thresholds are complex conditions 4, cyclomatic complexity 15, long methods 60, nesting 4, large classes 600, function parameters 6 and constructor parameters 7 (the rule defines whether the boundary is inclusive). Pure single-when dispatch is exempt from cyclomatic counting, following detekt's own configuration; method-length checks remain active. Naming, declaration/file matching and package location are checked. No baseline is used. Numeric wire tags and unused parameters in compiler intrinsics are not covered by these selected smell checks. This is not type-resolved analysis or a security audit.

JavaScript local tooling uses Prettier 3.6.2 defaults. Python tooling uses Ruff 0.12.12 formatting and its default E4/E7/E9/F lint selection. Their dependencies are pinned in tools/quality; they are development tools, not compiler/runtime requirements. Do not modify upstream licenses or immutable benchmark reports for formatting.

### Compatibility boundaries

- `examples/` is restricted contract DSL input, not JVM implementation source. Existing lower_snake_case instruction names are wire-contract behavior and are intentionally outside JVM function-naming/formatting checks. Native tests compile and execute those examples.
- `runtime/src/counter.c` is a C template containing substitution markers, not standalone C syntax. Do not apply a general C formatter to its markers. Actual rendered C compiles with the existing SDK/Clang warnings-as-errors and executes in native tests.
- `CounterProgram`, `CounterC` and `counter.*` remain compatibility names. This maintenance change does not rename CLI artifacts, ABI schemas, instruction discriminators or published archives.
- Upstream licenses and archived benchmark results retain their original bytes. Repository paths and release-content boundaries still apply.

## Architecture (ENG-04)

The CLI handles user commands. The `pipeline` Gradle module owns compilation and
provenance, and exposes the same `CompilerPipeline` check/build entry point to
JVM callers and the CLI. `toolchain` only invokes SDK tools. The compiler uses
explicit responsibility names, without web frontend/backend terminology.

| Role/package | Allowed Koral dependencies |
| --- | --- |
| `api` (`language`) | None |
| `compiler.ir` | None |
| `compiler.validation` | `compiler.ir` |
| `compiler.kotlin` | `compiler.ir`, `compiler.validation` |
| `compiler.target` | `compiler.ir`, `compiler.validation` |
| `compiler.abi` | `compiler.ir`, `compiler.validation` |
| `toolchain` | None |
| `pipeline` | API, all compiler roles, toolchain |
| `cli` | pipeline, toolchain |

External JVM/Kotlin dependencies are governed by locks/checksums, not this Koral
role table. Child packages belong to their listed role. Every production Koral
class must belong to exactly one role; unknown packages fail. ArchitectureTest
checks actual production JVM bytecode with ArchUnit 1.4.1. It also compiles a real
Java class for every forbidden role pair and requires the same rule to report each
edge. In particular, Kotlin analysis cannot reach C output or ABI writing, and
neither the compiler nor toolchain can call back into the pipeline or CLI.

These boundaries separate responsibilities, not processes. Kotlin's existing
bounded compiler subprocess and SDK invocations remain unchanged. No JNI/native
binding layer is introduced and no performance improvement is inferred from
package names. Shared representation belongs in IR; a necessary new relationship
requires an explained rule change and rejection evidence. Do not add layers solely
to preserve a diagram. Module/package naming is Koral policy, using the referenced
[ArchUnit mechanism](https://www.archunit.org/userguide/html/000_Index.html) and
[Clang driver separation](https://clang.llvm.org/docs/DriverInternals.html).

## Dependencies and archives (ENG-05, ENG-08)

Keep explicit versions, Gradle lockfiles and SHA-256 verification metadata. Verification metadata is initially generated from resolved dependencies and must be reviewed; hashes are not proof of upstream authenticity. Ordinary CI must not regenerate locks or checksums. SDK downloads retain their separate fixed archive digests.

CLI distributions explicitly exclude benchmark runners from testing tools and never include repository-quality dependencies. Gradle writes `runtime-classpath.json` separately from the archive using the CLI
JAR plus resolved runtimeClasspath JARs. Unit CI uploads both from the same
verified build. The archive guard compares the exact JAR filename set and streamed
SHA-256 values against this expected manifest. Unknown, missing, replaced or
duplicate libraries fail; an archive-contained manifest is not used as the expected
value. The guard also rejects unknown top-level content, non-runtime testing
scripts, links/devices, private paths, generated programs and missing notices. The consumer jobs run this guard before extracting and executing the candidate. They then verify an unchanged installation outside the checkout.

## Local verification (ENG-06, ENG-07)

```sh
npm ci --ignore-scripts --prefix tools/quality
python3 -m pip install --require-hashes -r tools/quality/requirements.txt
./gradlew --no-daemon qualityCheck test :cli:distTar
python3 tools/quality/check.py
python3 -m unittest discover -s tools/quality -v
python3 -m unittest discover -s tools/ci -v
ruff check tools
ruff format --check tools
node tools/quality/node_modules/prettier/bin/prettier.cjs --check 'testing/*.mjs' tools/quality/package.json tools/quality/package-lock.json config/engineering.json
python3 tools/quality/prepare.py --mode quality
python3 tools/quality/probe.py
python3 tools/quality/check.py --archive cli/build/distributions/koral-0.3.0-preview.1.tar --runtime-manifest cli/build/distributions/runtime-classpath.json
python3 tools/ci/consumer.py --archive cli/build/distributions/koral-0.3.0-preview.1.tar --runtime-manifest cli/build/distributions/runtime-classpath.json --sdk "$KORAL_SBF_SDK" --mode integration
```

`./gradlew spotlessApply` fixes supported Kotlin formatting; CI only checks. JVM quality tests do not replace real SBF/VM permission, arithmetic and rollback checks. Changes to runtime/ABI/CLI/support expectations need relevant native and independent consumer evidence, not only a prettier diff.

Local link checks exclude unmodified upstream licenses and historical benchmark reports. Example execution, diagnostics and candidate installations are checked by native CI. These controls cannot certify prose accuracy, every possible Kotlin program, or arbitrary Markdown shell instructions. Review those manually.

All quality/native/consumer/compatibility results must succeed in the final `required` gate; failure, cancellation, missing and skipped results block it. Server main protection includes administrators. A merge does not publish a release or deploy the website/blockchain; follow [RELEASING.md](RELEASING.md).

## Readiness and rejection phases

Positive dependency preparation runs before negative probes. Only classified
resolution/download failures get at most two retries with refreshed dependency
metadata; expected checksums and dependency locks are never regenerated. Checksum,
compilation, test and policy failures are not retried as network problems. Each
attempt is logged and exhausting preparation fails the job without running probes.

Formatting/long-method/checksum probes use `--offline` after preparation. Missing
cache prerequisites are an environment failure, not proof of intentional rejection.
Finally positive checks verify cleanup. PR verification is followed by the normal
main workflow; do not call adoption complete until that run is inspected too.
The source capability backlog is paused by owner direction while these controls
are hardened; see [ROADMAP.md](ROADMAP.md).
