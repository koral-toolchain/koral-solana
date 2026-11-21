"""Check repository boundaries, local documentation and distribution contents."""

import argparse
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import tarfile
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[2]
ROOTS = {
    ".github",
    ".gitignore",
    ".editorconfig",
    ".prettierrc.json",
    "pyproject.toml",
    "AGENTS.md",
    "CHANGELOG.md",
    "CONTRIBUTING.md",
    "LICENSE",
    "README.md",
    "THIRD_PARTY_NOTICES.md",
    "benchmarks",
    "build.gradle.kts",
    "cli",
    "compiler",
    "config",
    "docs",
    "examples",
    "gradle.properties",
    "gradle",
    "gradlew",
    "gradlew.bat",
    "language",
    "licenses",
    "runtime",
    "settings.gradle.kts",
    "testing",
    "toolchain",
    "tools",
}
MODULES = {"language", "compiler", "toolchain", "cli"}
FORBIDDEN = {
    ".git",
    "node_modules",
    ".env",
    ".devnet",
    "__pycache__",
    ".gradle",
    "website",
    "worklogs",
    "experiments",
}
RUNTIME_TESTING = {
    "package.json",
    "package-lock.json",
    "client.mjs",
    "client-test.mjs",
    "counter.mjs",
    "payments.mjs",
    "vault.mjs",
    "state.mjs",
    "constraints.mjs",
    "semantics.mjs",
    "suite.mjs",
    "verify.mjs",
    "custom-template.test.mjs",
    "test-errors.mjs",
    "test-errors-test.mjs",
    "syntax-errors-test.mjs",
    "inventory-test.mjs",
}


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def check_path(name):
    path = PurePosixPath(name)
    require(not path.is_absolute() and ".." not in path.parts, "Unsafe path: " + name)
    require(
        path.parts and path.parts[0] in ROOTS, "Undeclared repository root: " + name
    )
    require(
        not FORBIDDEN.intersection(path.parts), "Forbidden repository material: " + name
    )
    require(
        not re.search(
            r"(?:keypair|credentials|account)\.(?:json|txt)$", path.name, re.I
        ),
        "Credential-shaped file: " + name,
    )
    require(
        path.suffix not in {".so", ".class", ".pyc", ".tar", ".gz", ".zip"},
        "Generated binary: " + name,
    )
    require(
        path.suffix != ".jar" or name == "gradle/wrapper/gradle-wrapper.jar",
        "Unexpected JAR: " + name,
    )
    if path.suffix == ".kt" and path.parts[0] in MODULES:
        require(
            len(path.parts) > 5
            and path.parts[1:4]
            in [("src", "main", "kotlin"), ("src", "test", "kotlin")],
            "Use a declared Kotlin source set: " + name,
        )


def check_links(root, names):
    for name in names:
        if not name.endswith(".md") or name.startswith(
            ("licenses/", "benchmarks/results/")
        ):
            continue  # Unmodified upstream texts and immutable historical reports.
        for target in re.findall(
            r"!?\[[^\]]*\]\(([^)\s]+)\)", (root / name).read_text()
        ):
            parsed = urlsplit(target)
            if parsed.scheme or parsed.netloc or not parsed.path:
                continue
            require(
                (root / name).parent.joinpath(unquote(parsed.path)).exists(),
                f"Broken local documentation link in {name}: {target}",
            )


def check_tooling(root, policy):
    build = (root / "build.gradle.kts").read_text()
    patterns = {
        "spotless": r'id\("com\.diffplug\.spotless"\) version "([^"]+)"',
        "ktlint": r'ktlint\("([^"]+)"\)',
        "detekt": r'toolVersion = "([^"]+)"',
    }
    for name, pattern in patterns.items():
        require(
            set(re.findall(pattern, build)) == {policy["tools"][name]},
            "Engineering tool pin drift: " + name,
        )
    cli_build = (root / "cli/build.gradle.kts").read_text()
    require(
        "com.tngtech.archunit:archunit:" + policy["tools"]["archunit"] in cli_build,
        "Architecture tool pin drift",
    )
    require(
        "ktlint_code_style = ktlint_official" in (root / ".editorconfig").read_text(),
        "Kotlin style profile drift",
    )


def check_policy_change(title, changed):
    governed = [
        name
        for name in changed
        if name.startswith(("config/", ".github/workflows/", "tools/quality/"))
        or name
        in {
            ".editorconfig",
            "pyproject.toml",
            "build.gradle.kts",
            "gradle/verification-metadata.xml",
        }
    ]
    if governed:
        require(
            re.match(r"^(build|ci|refactor)(?:\([^)]*\))?!?: ", title),
            "Policy changes require a separate build/ci/refactor PR, not a feature/fix PR",
        )


def check_repository(root=ROOT, names=None):
    root = Path(root)
    if names is None:
        names = subprocess.check_output(
            ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
            cwd=root,
            text=True,
        ).splitlines()
    for name in names:
        check_path(name)
        if name.endswith(".kt") and "/src/" in name:
            text = (root / name).read_text()
            require(
                not re.search(r"@(?:file:)?Suppress\s*\(", text),
                "Undeclared Kotlin suppression: " + name,
            )
    require(
        not any("baseline" in PurePosixPath(n).name.lower() for n in names),
        "Finding baselines need explicit policy adoption",
    )
    check_links(root, names)
    policy = json.loads((root / "config/engineering.json").read_text())
    require(policy["schema"] == "koral-engineering/1", "Unsupported engineering policy")
    require(
        {r["id"] for r in policy["rules"]} == {f"ENG-{i:02d}" for i in range(1, 11)},
        "Missing engineering rules",
    )
    check_tooling(root, policy)
    for module in MODULES:
        require(
            (root / module / "gradle.lockfile").is_file(),
            "Missing module lock: " + module,
        )
    require(
        (root / "gradle/verification-metadata.xml").is_file(),
        "Missing dependency verification metadata",
    )
    print("Repository boundaries and local documentation passed")


def check_pr(body, sections=None):
    sections = sections or [
        "Scope",
        "Acceptance",
        "Exclusions",
        "Validation",
        "Compatibility",
    ]
    require(
        re.search(r"(?i)\b(?:refs|closes|fixes)\s+#([1-9][0-9]*)\b", body),
        "Link the product issue with Refs #number",
    )
    for section in sections:
        match = re.search(
            r"^## " + re.escape(section) + r"\s*\n(.*?)(?=^## |\Z)", body, re.M | re.S
        )
        require(
            match
            and re.sub(r"<!--.*?-->", "", match[1], flags=re.S).strip()
            not in ["", "TODO", "TBD"],
            "Missing PR explanation: " + section,
        )


def check_archive(archive):
    with tarfile.open(archive) as bundle:
        entries = bundle.getmembers()
        require(bool(entries), "Empty archive")
        roots = {PurePosixPath(m.name).parts[0] for m in entries}
        require(len(roots) == 1, "Expected one distribution root")
        present = set()
        for member in entries:
            path = PurePosixPath(member.name)
            require(
                not path.is_absolute() and ".." not in path.parts, "Unsafe archive path"
            )
            require(
                member.isdir() or member.isfile(),
                "Links and devices are not runtime files",
            )
            parts = path.parts[1:]
            if not parts:
                continue
            require(
                not FORBIDDEN.intersection(parts),
                "Forbidden archive material: " + str(path),
            )
            require(
                parts[0]
                in {
                    "bin",
                    "lib",
                    "docs",
                    "licenses",
                    "testing",
                    "examples",
                    "README.md",
                    "LICENSE",
                    "THIRD_PARTY_NOTICES.md",
                },
                "Undeclared runtime content: " + str(path),
            )
            if member.isfile():
                present.add("/".join(parts))
                if parts[0] == "bin":
                    require(
                        len(parts) == 2 and parts[1] in {"koral", "koral.bat"},
                        "Unknown launcher",
                    )
                if parts[0] == "lib":
                    require(
                        len(parts) == 2 and path.suffix == ".jar",
                        "Non-JAR runtime dependency",
                    )
                    require(
                        not re.search(
                            r"archunit|detekt|ktlint|spotless|prettier|junit",
                            path.name,
                            re.I,
                        ),
                        "Development dependency in runtime",
                    )
                if parts[0] == "docs":
                    require(path.suffix == ".md", "Non-documentation runtime file")
                if parts[0] == "examples":
                    require(
                        len(parts) == 2 and path.suffix == ".kt",
                        "Non-contract example payload",
                    )

                require(
                    not re.search(r"keypair|credentials|\.so$|\.pyc$", path.name, re.I),
                    "Private/generated archive input",
                )
                if parts[0] == "testing":
                    require(
                        len(parts) == 2 and parts[1] in RUNTIME_TESTING,
                        "Non-runtime testing file: " + str(path),
                    )
        for required in [
            "LICENSE",
            "THIRD_PARTY_NOTICES.md",
            "licenses/kotlin/LICENSE.txt",
            "licenses/kotlin/NOTICE.txt",
            "licenses/kotlin/COPYRIGHT.txt",
            "bin/koral",
            "bin/koral.bat",
            "testing/client.mjs",
            "testing/package-lock.json",
        ]:
            require(required in present, "Distribution is missing " + required)

    print("Distribution boundary passed")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path)
    parser.add_argument("--pr-body", type=Path)
    args = parser.parse_args()
    if args.archive:
        check_archive(args.archive)
    elif args.pr_body:
        check_pr(args.pr_body.read_text())
    else:
        check_repository()


if __name__ == "__main__":
    main()
