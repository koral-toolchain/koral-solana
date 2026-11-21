"""Prepare verified dependencies; retry only bounded dependency resolution failures."""

import argparse
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
MODES = {
    "unit": ["test", ":cli:distTar"],
    "quality": ["spotlessCheck", "detekt", ":cli:test", ":cli:distTar"],
}


def retryable(output):
    # Never retry a failed policy/checksum/compilation/test as a network operation.
    forbidden = [
        "Dependency verification failed",
        "checksum",
        "Compilation error",
        "There were failing tests",
        "There were ",
        "Analysis failed",
        "Policy check failed",
    ]
    if any(marker.lower() in output.lower() for marker in forbidden):
        return False
    return any(
        marker in output
        for marker in [
            "Could not resolve ",
            "Could not find ",
            "Could not GET ",
            "Could not HEAD ",
        ]
    )


def prepare(mode, run=subprocess.run, sleep=time.sleep):
    command = [str(ROOT / "gradlew"), "--no-daemon", *MODES[mode]]
    for attempt in range(3):
        result = run(
            command + (["--refresh-dependencies"] if attempt else []),
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=600,
        )
        output = result.stdout + result.stderr
        print(output, end="", flush=True)
        if result.returncode == 0:
            print("Verified positive preparation completed", flush=True)
            return
        if attempt == 2 or not retryable(output):
            raise RuntimeError(
                "Positive dependency/quality preparation failed; negative probes were not run"
            )
        print(
            f"Resolution failure during preparation; bounded retry {attempt + 1}/2",
            flush=True,
        )
        sleep(2 ** (attempt + 1))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=MODES, required=True)
    prepare(parser.parse_args().mode)
