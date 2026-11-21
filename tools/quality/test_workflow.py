"""Exercise the actual workflow's event guard before its message-file writes."""

import os
from pathlib import Path
import re
import subprocess
import sys
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]


class WorkflowEventTests(unittest.TestCase):
    def run_guard(self, event, ref, actor="tidemark-labs", author="tidemark-labs"):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text()
        scripts = re.findall(r"python3 - <<'PY'\n(.*?)\n          PY", workflow, re.S)
        script = next(textwrap.dedent(s) for s in scripts if "EVENT_REF" in s)
        # Execute the real guard; exclude only its subsequent filesystem writes.
        prefix = script.split("Path('/tmp/koral-message.txt')", 1)[0]
        self.assertNotEqual(prefix, script)
        env = {
            **os.environ,
            "EVENT": event,
            "EVENT_REF": ref,
            "ACTOR": actor,
            "PR_AUTHOR": author,
            "PR_TITLE": "build(ci): validate guards",
            "PR_BODY": "Refs #31",
        }
        return subprocess.run(
            [sys.executable, "-c", prefix],
            cwd=ROOT,
            env=env,
            text=True,
            capture_output=True,
        )

    def test_manual_candidate_branch_is_rejected(self):
        result = self.run_guard("workflow_dispatch", "refs/heads/build/candidate")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("main-only", result.stderr)

    def test_owner_pr_and_protected_main_manual_run_are_allowed(self):
        self.assertEqual(
            self.run_guard("pull_request", "refs/pull/32/merge").returncode, 0
        )
        self.assertEqual(
            self.run_guard("workflow_dispatch", "refs/heads/main").returncode, 0
        )

    def test_non_owner_cannot_use_either_path(self):
        self.assertNotEqual(
            self.run_guard(
                "pull_request", "refs/pull/32/merge", author="outside"
            ).returncode,
            0,
        )
        self.assertNotEqual(
            self.run_guard(
                "workflow_dispatch", "refs/heads/main", actor="outside"
            ).returncode,
            0,
        )


if __name__ == "__main__":
    unittest.main()
