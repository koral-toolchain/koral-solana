import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest

spec = importlib.util.spec_from_file_location(
    "prepare", Path(__file__).with_name("prepare.py")
)
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


class PreparationTests(unittest.TestCase):
    def test_only_resolution_failures_are_retryable(self):
        self.assertTrue(p.retryable("Could not resolve plugin; Could not GET artifact"))
        for message in [
            "Dependency verification failed; Could not resolve plugin",
            "checksum mismatch",
            "Compilation error",
            "There were failing tests",
            "Analysis failed",
            "format violation",
        ]:
            with self.subTest(message=message):
                self.assertFalse(p.retryable(message))

    def test_network_retry_is_bounded_and_positive_success_is_required(self):
        calls = []

        def failing(*args, **kwargs):
            calls.append(args)
            return SimpleNamespace(
                returncode=1, stdout="Could not resolve artifact", stderr=""
            )

        with self.assertRaises(RuntimeError):
            p.prepare("quality", run=failing, sleep=lambda n: None)
        self.assertEqual(len(calls), 3)

    def test_transient_resolution_recovers_then_stops_retrying(self):
        calls = []

        def run(command, **kwargs):
            calls.append(command)
            return SimpleNamespace(
                returncode=1 if len(calls) == 1 else 0,
                stdout="Could not GET artifact"
                if len(calls) == 1
                else "Positive checks passed",
                stderr="",
            )

        p.prepare("quality", run=run, sleep=lambda n: None)
        self.assertEqual(len(calls), 2)
        self.assertNotIn("--refresh-dependencies", calls[0])
        self.assertIn("--refresh-dependencies", calls[1])

    def test_checksum_failure_is_not_retried(self):
        calls = []

        def failing(*args, **kwargs):
            calls.append(args)
            return SimpleNamespace(
                returncode=1, stdout="Dependency verification failed", stderr=""
            )

        with self.assertRaises(RuntimeError):
            p.prepare("unit", run=failing, sleep=lambda n: None)
        self.assertEqual(len(calls), 1)


if __name__ == "__main__":
    unittest.main()
