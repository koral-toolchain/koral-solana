import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "probe", Path(__file__).with_name("probe.py")
)
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


class OfflineProbeTests(unittest.TestCase):
    def test_expected_rejection_uses_offline_and_no_shell(self):
        result = SimpleNamespace(
            returncode=1, stdout="KoralQualityProbe.kt rejected by formatter", stderr=""
        )
        with patch.object(p.subprocess, "run", return_value=result) as run:
            p.rejects(["spotlessCheck"], ["KoralQualityProbe.kt"])
            self.assertIn("--offline", run.call_args.args[0])
            self.assertNotIn("shell", run.call_args.kwargs)

    def test_dependency_failure_is_not_counted_as_expected_rejection(self):
        result = SimpleNamespace(
            returncode=1,
            stdout="Could not resolve dependency for KoralQualityProbe.kt",
            stderr="",
        )
        with (
            patch.object(p.subprocess, "run", return_value=result),
            self.assertRaisesRegex(RuntimeError, "prerequisites"),
        ):
            p.rejects(["spotlessCheck"], ["KoralQualityProbe.kt"])

    def test_success_is_not_a_negative_control(self):
        result = SimpleNamespace(returncode=0, stdout="KoralQualityProbe.kt", stderr="")
        with (
            patch.object(p.subprocess, "run", return_value=result),
            self.assertRaises(RuntimeError),
        ):
            p.rejects(["spotlessCheck"], ["KoralQualityProbe.kt"])


if __name__ == "__main__":
    unittest.main()
