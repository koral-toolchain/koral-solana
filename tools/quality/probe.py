"""Prove that real quality tools reject controlled temporary violations."""

from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def rejects(args, expected):
    result = subprocess.run(
        [str(ROOT / "gradlew"), "--no-daemon", *args],
        cwd=ROOT,
        text=True,
        capture_output=True,
        timeout=240,
    )
    output = result.stdout + result.stderr
    if result.returncode == 0 or any(value not in output for value in expected):
        raise RuntimeError(
            "Expected quality rejection: " + ", ".join(expected) + "\n" + output[-6000:]
        )
    print("Rejected controlled violation:", ", ".join(expected), flush=True)


def main():
    # This path is reserved to the probe, never silently overwrite real source.
    probe = ROOT / "cli/src/main/kotlin/dev/koral/cli/KoralQualityProbe.kt"
    if probe.exists():
        raise RuntimeError("Probe path already exists")
    try:
        probe.write_text("package dev.koral.cli\nobject KoralQualityProbe{val bad=1}\n")
        rejects(["spotlessCheck"], ["KoralQualityProbe.kt"])
        probe.write_text(
            "package dev.koral.cli\n\nobject KoralQualityProbe {\n    fun tooLong() {\n"
            + "        println(1)\n" * 70
            + "    }\n}\n"
        )
        rejects([":cli:detekt"], ["LongMethod", "KoralQualityProbe.kt"])
    finally:
        probe.unlink(missing_ok=True)

    metadata = ROOT / "gradle/verification-metadata.xml"
    original = metadata.read_bytes()
    ns = {"v": "https://schema.gradle.org/dependency-verification"}
    tree = ET.fromstring(original)
    artifact = tree.find(".//v:artifact[@name='kotlin-stdlib-2.2.21.jar']/v:sha256", ns)
    if artifact is None:
        raise RuntimeError("Missing known verification fixture")
    digest = artifact.attrib["value"].encode()
    try:
        # Keep the actual downloaded JAR untouched; change only the expected digest.
        metadata.write_bytes(
            original.replace(b'value="' + digest + b'"', b'value="' + b"0" * 64 + b'"')
        )
        rejects(
            [":cli:compileKotlin"], ["Dependency verification failed", "kotlin-stdlib"]
        )
    finally:
        metadata.write_bytes(original)


if __name__ == "__main__":
    main()
