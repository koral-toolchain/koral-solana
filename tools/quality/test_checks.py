import importlib.util
import io
import hashlib
from pathlib import Path
import tarfile
import tempfile
import unittest

spec = importlib.util.spec_from_file_location(
    "quality", Path(__file__).with_name("check.py")
)
q = importlib.util.module_from_spec(spec)
spec.loader.exec_module(q)


class QualityTests(unittest.TestCase):
    def test_accepts_owned_source_paths(self):
        for name in [
            "compiler/src/main/kotlin/dev/koral/compiler/Program.kt",
            "docs/ARCHITECTURE.md",
            "gradle/wrapper/gradle-wrapper.jar",
            "benchmarks/results/typesol-baseline.json",
        ]:
            q.check_path(name)

    def test_rejects_wrong_roots_sources_and_private_material(self):
        for name in [
            "backend/server.py",
            "../secrets.txt",
            "compiler/Program.kt",
            ".devnet/keypair.json",
            "docs/account.txt",
            "compiler/build/program.so",
            "cli/runtime.jar",
        ]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                q.check_path(name)

    def test_finding_baseline_rejection_preserves_benchmark_evidence(self):
        q.check_finding_baselines(["benchmarks/results/typesol-baseline.json"])
        for name in ["config/detekt-baseline.xml", "baseline.xml"]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                q.check_finding_baselines([name])

    def test_pr_needs_issue_scope_and_evidence(self):
        body = "Refs #29\n\n" + "\n".join(
            "## " + s + "\nConcrete explanation.\n"
            for s in [
                "Scope",
                "Acceptance",
                "Exclusions",
                "Validation",
                "Compatibility",
            ]
        )
        q.check_pr(body)
        for section in [
            "Scope",
            "Acceptance",
            "Exclusions",
            "Validation",
            "Compatibility",
        ]:
            with self.subTest(section=section), self.assertRaises(ValueError):
                q.check_pr(
                    body.replace(
                        "## " + section + "\nConcrete explanation.",
                        "## " + section + "\n<!-- fill -->",
                    )
                )
        with self.assertRaises(ValueError):
            q.check_pr(body.replace("Refs #29", "No issue"))

    def test_archive_rejects_extra_tools_and_missing_runtime(self):
        required = [
            "lib/compiler.jar",
            "bin/koral",
            "bin/koral.bat",
            "LICENSE",
            "THIRD_PARTY_NOTICES.md",
            "testing/client.mjs",
            "testing/package-lock.json",
            "licenses/kotlin/LICENSE.txt",
            "licenses/kotlin/NOTICE.txt",
            "licenses/kotlin/COPYRIGHT.txt",
        ]
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "candidate.tar"

            def archive(extra):
                with tarfile.open(path, "w") as t:
                    for name in required + extra:
                        info = tarfile.TarInfo("koral/" + name)
                        info.size = 1
                        t.addfile(info, io.BytesIO(b"x"))

            manifest = {
                "schema": "koral-runtime-classpath/1",
                "libraries": {"compiler.jar": hashlib.sha256(b"x").hexdigest()},
            }
            archive([])
            q.check_archive(path, manifest)
            for extra in [
                "testing/payment-benchmark.mjs",
                "tools/deploy.py",
                ".devnet/keypair.json",
                "lib/program.so",
                "lib/undeclared.jar",
                "lib/compiler.jar",
                "bin/extra-launcher",
                "lib/archunit-1.4.1.jar",
                "docs/deploy.py",
                "examples/wallet.json",
            ]:
                archive([extra])
                with self.subTest(extra=extra), self.assertRaises(ValueError):
                    q.check_archive(path, manifest)

    def test_runtime_manifest_rejects_missing_or_replaced_libraries(self):
        required = [
            "LICENSE",
            "THIRD_PARTY_NOTICES.md",
            "licenses/kotlin/LICENSE.txt",
            "licenses/kotlin/NOTICE.txt",
            "licenses/kotlin/COPYRIGHT.txt",
            "bin/koral",
            "bin/koral.bat",
            "testing/client.mjs",
            "testing/package-lock.json",
        ]
        manifest = {
            "schema": "koral-runtime-classpath/1",
            "libraries": {"compiler.jar": hashlib.sha256(b"good").hexdigest()},
        }
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "candidate.tar"
            for payload in [None, b"replaced", b"good"]:
                with tarfile.open(path, "w") as t:
                    for name in required:
                        item = tarfile.TarInfo("koral/" + name)
                        item.size = 1
                        t.addfile(item, io.BytesIO(b"x"))
                    if payload is not None:
                        item = tarfile.TarInfo("koral/lib/compiler.jar")
                        item.size = len(payload)
                        t.addfile(item, io.BytesIO(payload))
                if payload == b"good":
                    q.check_archive(path, manifest)
                else:
                    with self.assertRaisesRegex(ValueError, "runtimeClasspath"):
                        q.check_archive(path, manifest)
            with self.assertRaisesRegex(ValueError, "separate Gradle"):
                q.check_archive(path)

    def test_documentation_link_failure_is_not_hidden(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "README.md").write_text("[guide](missing.md)")
            with self.assertRaises(ValueError):
                q.check_links(root, ["README.md"])
            (root / "missing.md").write_text("Guide")
            q.check_links(root, ["README.md"])


if __name__ == "__main__":
    unittest.main()
