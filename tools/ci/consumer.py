#!/usr/bin/env python3
"""Verify the candidate CLI archive outside its source checkout."""

import argparse
import hashlib
import json
import os
import subprocess
import tarfile
import tempfile
from pathlib import Path

from diagnostics import verify_diagnostics
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
from check import check_archive


def verify(archive_path, sdk, mode):
    check_archive(archive_path)
    with tempfile.TemporaryDirectory(prefix="koral-consumer-") as folder:
        work = Path(folder) / "consumer with spaces"
        work.mkdir()
        with tarfile.open(archive_path) as archive:
            for member in archive.getmembers():
                if {".git", "node_modules", "experiments", ".env"} & set(
                    Path(member.name).parts
                ):
                    raise ValueError("Unexpected file in distribution: " + member.name)
            archive.extractall(work, filter="data")
        roots = list(work.iterdir())
        if len(roots) != 1 or not roots[0].is_dir():
            raise ValueError("Expected one CLI distribution directory")
        root = roots[0]
        for required in [
            "LICENSE",
            "THIRD_PARTY_NOTICES.md",
            "bin/koral",
            "testing/package-lock.json",
            "licenses/kotlin/LICENSE.txt",
            "licenses/kotlin/NOTICE.txt",
            "licenses/kotlin/COPYRIGHT.txt",
        ]:
            if not (root / required).is_file():
                raise ValueError("Distribution is missing " + required)
        env = os.environ.copy()
        env["npm_config_cache"] = str(Path(folder) / "npm-cache")
        env["KORAL_CACHE_DIR"] = str(Path(folder) / "user cache")

        def run(*args, cwd=work, process_env=None):
            subprocess.run(
                [str(a) for a in args],
                cwd=cwd,
                env=process_env or env,
                check=True,
                timeout=600,
            )

        def reject(*args, cwd=work, contains, process_env=None):
            result = subprocess.run(
                [str(a) for a in args],
                cwd=cwd,
                env=process_env or env,
                capture_output=True,
                text=True,
                timeout=120,
            )
            if result.returncode == 0 or contains not in result.stdout + result.stderr:
                raise ValueError(
                    "Expected an actionable rejection: "
                    + contains
                    + "\n"
                    + result.stdout
                    + result.stderr
                )
            return result

        cli = root / "bin/koral"
        run("java", "-version")
        run("node", "--version")
        diagnostic_checks = verify_diagnostics(cli, work, env)
        run("node", root / "testing/test-errors-test.mjs")
        run("node", root / "testing/syntax-errors-test.mjs")
        if mode == "integration":
            run("npm", "ci", "--ignore-scripts", "--prefix", root / "testing")
            run(cli, "doctor", "--sdk", sdk)
            run("node", root / "testing/verify.mjs", cli, sdk)
        else:
            installed = {
                str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in root.rglob("*")
                if p.is_file()
            }
            env["KORAL_SBF_SDK"] = str(sdk)
            missing_sdk = {k: v for k, v in env.items() if k != "KORAL_SBF_SDK"}
            reject(
                cli, "doctor", contains="Export KORAL_SBF_SDK", process_env=missing_sdk
            )
            run(cli, "doctor")
            reject(cli, "build", contains="koral init")
            project = work / "hello counter"
            run(cli, "init", project)
            reject(cli, "init", project, contains="existing files are never replaced")
            run(cli, "check", cwd=project)
            run(cli, "build", cwd=project)
            output = project / "build/koral"
            reject(cli, "test", cwd=project, contains="koral setup")
            run(cli, "setup", cwd=project)
            run(cli, "doctor", cwd=project)
            run(cli, "test", cwd=project)
            if json.loads((output / "counter.test.json").read_text())["tests"] != 55:
                raise ValueError(
                    "The new project did not pass the full Counter verification"
                )
            # Retain the explicit binary invocation for existing consumers.
            run(cli, "test", output / "counter.so")
            manifest = json.loads((output / "counter.build.json").read_text())
            actual = hashlib.sha256((output / "counter.so").read_bytes()).hexdigest()
            if manifest["binarySha256"] != actual:
                raise ValueError("Binary hash differs from build manifest")
            json.loads((output / "counter.abi.json").read_text())

            # Legacy explicit-source payment compilation still works with the same archive.
            output = work / "compiled-payments"
            run(
                cli,
                "build",
                root / "examples/payments.kt",
                "--out",
                output,
                "--sdk",
                sdk,
            )
            runners = list(Path(env["KORAL_CACHE_DIR"]).glob("vm/*/payments.mjs"))
            if len(runners) != 1:
                raise ValueError("Missing the versioned cached payment verifier")
            run("node", runners[0], output / "counter.so", output / "tests.json")
            if json.loads((output / "tests.json").read_text())["checks"] != 37:
                raise ValueError("Payment compatibility verification was incomplete")

            constrained = work / "constrained-payments"
            run(
                cli,
                "build",
                root / "examples/constrained-payments.kt",
                "--out",
                constrained,
                "--sdk",
                sdk,
            )
            run(
                "node",
                runners[0].parent / "constraints.mjs",
                constrained / "counter.so",
                constrained / "tests.json",
            )
            if not all(
                json.loads((constrained / "tests.json").read_text())[key]
                for key in ["bodyFailureRollback", "dataSizeAndExecutable"]
            ):
                raise ValueError(
                    "Constrained account rollback verification was incomplete"
                )

            vault = work / "sol-vault"
            run(
                cli,
                "build",
                root / "examples/sol-vault.kt",
                "--out",
                vault,
                "--sdk",
                sdk,
            )
            run(
                "node",
                runners[0].parent / "vault.mjs",
                vault / "counter.so",
                vault / "tests.json",
            )
            if not json.loads((vault / "tests.json").read_text())[
                "bodyFailureRollback"
            ]:
                raise ValueError(
                    "Owned-state vault rollback verification was incomplete"
                )

            # Public project commands must cover the same native suites outside the checkout.
            for template, source_name, report_name, checks in [
                ("payments", "payments.kt", "payments.test.json", 37),
                ("sol-vault", "sol-vault.kt", "sol-vault.test.json", 101),
            ]:
                sample = work / (template + " project with spaces")
                run(cli, "init", sample, "--template", template)
                reject(
                    cli,
                    "init",
                    sample,
                    "--template",
                    template,
                    contains="existing files are never replaced",
                )
                run(cli, "check", cwd=sample)
                run(cli, "build", cwd=sample)
                run(cli, "test", cwd=sample)
                built = sample / "build/koral"
                result_path = built / report_name
                result = json.loads(result_path.read_text())
                if result["checks"] != checks or not result.get(
                    "bodyFailureRollback", result.get("cpiRollbackVerified")
                ):
                    raise ValueError("Incomplete " + template + " project verification")
                run(cli, "test", built / "counter.so", "--suite", template)
                if (sample / ("src/" + source_name)).read_bytes() != (
                    root / "examples" / source_name
                ).read_bytes():
                    raise ValueError(
                        "Generated template differs from the maintained example"
                    )
                # A wrong suite must fail rather than select another contract's success path.
                reject(
                    cli,
                    "test",
                    "--suite",
                    "counter",
                    cwd=sample,
                    contains="verification failed",
                )
                if (built / "counter.test.json").exists():
                    raise ValueError("Wrong-suite failure retained a passing report")
                # A stale report is invalidated even if the ABI disappeared before VM startup.
                abi = built / "counter.abi.json"
                abi_content = abi.read_bytes()
                abi.unlink()
                reject(cli, "test", cwd=sample, contains="requires its generated ABI")
                if result_path.exists():
                    raise ValueError("Missing-ABI failure retained a passing report")
                abi.write_bytes(abi_content)
                run(cli, "test", cwd=sample)
                protected = {
                    **env,
                    "KORAL_TEST_RESULT": str(built / "counter.build.json"),
                }
                before_manifest = (built / "counter.build.json").read_bytes()
                reject(
                    cli,
                    "test",
                    cwd=sample,
                    process_env=protected,
                    contains="must not overwrite",
                )
                if (built / "counter.build.json").read_bytes() != before_manifest:
                    raise ValueError("Test report clobbered build provenance")
                # Compile valid but behaviorally broken source: the real SBF suite must reject it.
                program_source = sample / "src" / source_name
                original_source = program_source.read_text()
                changed = (
                    original_source.replace(
                        "transferLamports(recipient, amount)",
                        "transferLamports(recipient, 0uL)",
                    )
                    if template == "payments"
                    else original_source.replace(
                        "ensure(false, 6201uL)", "ensure(true, 6201uL)"
                    )
                )
                if changed == original_source:
                    raise ValueError("Missing template mutation anchor")
                program_source.write_text(changed)
                run(cli, "build", cwd=sample)
                if result_path.exists():
                    raise ValueError("Rebuild retained a stale passing test report")
                result_path.write_text('{"old": "success"}')
                reject(cli, "test", cwd=sample, contains="verification failed")
                if result_path.exists():
                    raise ValueError(
                        "Behavioral failure retained a stale passing test report"
                    )
                program_source.write_text(original_source)
                run(cli, "build", cwd=sample)
                run(cli, "test", cwd=sample)
            # A new author edits both Kotlin and tests, using only the installed client.
            custom = work / "my custom program with spaces"
            run(cli, "init", custom, "--template", "custom")
            run(cli, "check", cwd=custom)
            run(cli, "build", cwd=custom)
            run(cli, "test", cwd=custom)
            custom_out = custom / "build/koral"
            custom_report = custom_out / "custom.test.json"
            if json.loads(custom_report.read_text())["mode"] != "user-authored script":
                raise ValueError(
                    "Custom script completion was mislabeled as automatic verification"
                )
            run(
                "node", runners[0].parent / "client-test.mjs", custom_out / "counter.so"
            )
            # Built-in suite mismatch must explain the user path, without an internal TypeError.
            reject(
                cli,
                "test",
                "--suite",
                "payments",
                cwd=custom,
                contains="--template custom",
            )
            failure = subprocess.run(
                [str(cli), "test", "--suite", "payments"],
                cwd=custom,
                env=env,
                capture_output=True,
                text=True,
                timeout=120,
            )
            if "TypeError" in failure.stdout + failure.stderr:
                raise ValueError(
                    "A custom contract still leaks an internal template TypeError"
                )
            # Prove the starter checks business semantics in the compiled SBF.
            custom_source = custom / "src/custom.kt"
            correct = custom_source.read_text()
            changed = correct.replace(
                "checkedMultiply(quantity, 10_000_000uL)",
                "checkedMultiply(quantity, 20_000_000uL)",
            )
            if correct == changed:
                raise ValueError("Missing custom price mutation anchor")
            custom_source.write_text(changed)
            run(cli, "build", cwd=custom)
            if custom_report.exists():
                raise ValueError("Custom rebuild retained an old success report")
            custom_report.write_text('{"old": "success"}')
            rejected_price = reject(
                cli, "test", cwd=custom, contains="exact independent price expectation"
            )
            diagnostic = rejected_price.stdout + rejected_price.stderr
            if (
                "tests/program.test.mjs:" not in diagnostic
                or "ModuleJob" in diagnostic
                or "custom template contract" in diagnostic
            ):
                raise ValueError(
                    "Custom assertion failure lost its user source location or leaked loader frames"
                )
            if custom_report.exists():
                raise ValueError("A failing custom assertion retained a success report")
            custom_source.write_text(correct)
            run(cli, "build", cwd=custom)
            run(cli, "test", cwd=custom)
            script = custom / "tests/program.test.mjs"
            protected = {**env, "KORAL_TEST_RESULT": str(script)}
            original_script = script.read_bytes()
            reject(
                cli,
                "test",
                cwd=custom,
                process_env=protected,
                contains="must not overwrite",
            )
            if script.read_bytes() != original_script:
                raise ValueError("Test result overwrote the user test script")
            # Real user edits: parse failure in the entry and an imported helper.
            script.write_text("const ok=1;\nconst broken = ;\n")
            syntax = reject(cli, "test", cwd=custom, contains="SyntaxError")
            if "program.test.mjs:2" not in syntax.stderr or custom_report.exists():
                raise ValueError(
                    "Entry syntax error lost its line or retained a success report"
                )
            helper = custom / "tests/helper.mjs"
            helper.write_text("export const ok=1;\nexport const broken = ;\n")
            script.write_text("import './helper.mjs';\n" + original_script.decode())
            syntax = reject(cli, "test", cwd=custom, contains="SyntaxError")
            if (
                "helper.mjs:2" not in syntax.stderr
                or "ModuleJob" in syntax.stderr
                or custom_report.exists()
            ):
                raise ValueError(
                    "Imported syntax error did not retain the actual helper diagnostic"
                )
            script.write_text("import './missing-helper.mjs';\n")
            missing = reject(cli, "test", cwd=custom, contains="missing-helper.mjs")
            if "program.test.mjs" not in missing.stderr or custom_report.exists():
                raise ValueError(
                    "Missing-import diagnostics lost the consumer entry or retained a result"
                )
            script.write_bytes(original_script)
            helper.unlink()
            run(cli, "test", cwd=custom)
            script.unlink()
            reject(cli, "test", cwd=custom, contains="Missing tests/program.test.mjs")
            if custom_report.exists():
                raise ValueError("Missing custom script retained an old success report")
            external_script = work / "external-script.mjs"
            external_script.write_bytes(original_script)
            script.symlink_to(external_script)
            reject(cli, "test", cwd=custom, contains="must stay inside the project")
            script.unlink()
            script.write_bytes(original_script)
            run(cli, "test", cwd=custom)

            # A different consumer domain: four-field aggregate stock, with explicit limits.
            inventory = work / "inventory consumer"
            run(cli, "init", inventory, "--template", "custom")
            (inventory / "src/custom.kt").write_bytes(
                (root / "examples/inventory.kt").read_bytes()
            )
            (inventory / "tests/program.test.mjs").write_bytes(
                (root / "testing/inventory-test.mjs").read_bytes()
            )
            run(cli, "check", cwd=inventory)
            run(cli, "build", cwd=inventory)
            inventory_report = inventory / "build/koral/inventory.test.json"
            run(
                cli,
                "test",
                cwd=inventory,
                process_env={**env, "KORAL_INVENTORY_RESULT": str(inventory_report)},
            )
            findings = json.loads(inventory_report.read_text())
            if (
                not findings["corePassed"]
                or findings["assertions"] < 64
                or not findings["findings"]
            ):
                raise ValueError(
                    "Inventory consumer scope or explicit findings were not recorded"
                )

            reject(
                cli,
                "init",
                work / "unknown project",
                "--template",
                "unknown",
                contains="Choose counter, payments, sol-vault or custom",
            )
            if (work / "unknown project").exists():
                raise ValueError("Unknown template created files")
            reject(
                cli,
                "test",
                output / "counter.so",
                "--suite",
                "unknown",
                contains="Choose counter, payments, sol-vault or custom",
            )

            source = project / "src/counter.kt"
            source.write_text(
                source.read_text().replace(
                    "checkedAdd(state.count, by)", "state.count + by"
                )
            )
            reject(cli, "build", cwd=project, contains="Unsupported call")
            for name in [
                "counter.so",
                "counter.build.json",
                "counter.abi.json",
                "counter.test.json",
            ]:
                if (project / "build/koral" / name).exists():
                    raise ValueError(
                        "Failed project build retained stale success artifact: " + name
                    )
            reject(cli, "test", cwd=project, contains="Run koral build first")
            after = {
                str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in root.rglob("*")
                if p.is_file()
            }
            if installed != after:
                raise ValueError(
                    "The first-project workflow modified the installed distribution"
                )
        print(
            json.dumps(
                {
                    "consumerVerification": "passed",
                    "mode": mode,
                    "sourceCheckoutRequired": False,
                    "sourceDiagnosticChecks": diagnostic_checks,
                }
            )
        )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--mode", choices=["smoke", "integration"], default="smoke")
    args = parser.parse_args()
    verify(args.archive.resolve(), args.sdk.resolve(), args.mode)
