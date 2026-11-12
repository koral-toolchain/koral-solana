# Install the CLI and run your first Counter

This preview creates a local project, compiles Kotlin to native Solana SBF, and
verifies the artifact in LiteSVM. It never creates a wallet, deploys a program or
sends funds. Use is subject to the supplied LICENSE; public source visibility
does not change the proprietary terms.

## Prerequisites

- JDK 17 or later to run the CLI. Building Koral itself uses the JDK 17 toolchain.
- An installed Agave 3.1.10 SBF SDK with platform-tools 1.52. Follow
  [SDK setup](SDK-SETUP.md) if it is not installed.
- Node 22.12+ and npm for local VM testing. Compilation does not need Node.
- Network access for the explicit, one-time `koral setup` dependency installation.

The source is public, but no tagged CLI release is assumed by this guide. A TAR
from a CI run is a candidate build. A maintainer can produce that same package
from the source checkout:

```sh
./gradlew --no-daemon test :cli:distTar
```

The candidate is cli/build/distributions/koral-0.1.0-dev.tar. An extracted CLI
does not need Gradle or the source checkout. Extract it into your chosen install
directory and add its bin directory to PATH:

```sh
tar -xf /path/to/koral-0.1.0-dev.tar -C /your/install/directory
export PATH="/your/install/directory/koral-0.1.0-dev/bin:$PATH"
export KORAL_SBF_SDK=/path/to/solana-release/bin/platform-tools-sdk/sbf
koral doctor
```

Replace the example paths with actual directories. doctor prints Java, the SDK's
Clang version, Node and VM dependency readiness. Missing SDK instructions cause a
nonzero exit; missing Node/VM tools are reported separately because they are not
needed to compile. doctor does not install anything.

## Create and run

In a writable workspace outside the installed CLI:

```sh
koral init hello-counter
cd hello-counter
koral setup
koral check
koral build
koral test
```

Expected results:

1. init creates src/counter.kt, koral.properties, README.md and .gitignore. It
   refuses an existing destination, including an empty directory.
2. setup installs the locked VM dependencies into the user cache, then imports
   them to confirm the native VM can load. The installed CLI directory is unchanged.
3. check validates the example without invoking the SDK.
4. build creates build/koral/counter.so and its C, model, ABI and hash manifest.
5. test reports `Native checks passed: 55` and writes counter.test.json beside
   the binary. The VM exercises an actual increment, initialization, authority,
   account permissions, arithmetic boundaries and failure rollback.

The verifier is specific to the supplied Counter contract. It is not a universal
test runner for arbitrary Kotlin programs. Passing it is not a security audit.

## Project configuration

```properties
format=1
source=src/counter.kt
output=build/koral
```

These are project-relative paths; absolute paths, paths escaping the project and
source/output directory overlap are rejected. Commands read koral.properties in
the current directory. Unknown settings and unsupported format versions fail.
No SDK path, secret, keypair or shell command is written into this file.

The original explicit-source commands still work outside a project:

```sh
koral check /path/to/counter.kt
koral build /path/to/counter.kt --out /path/to/output --sdk /path/to/sdk
koral test /path/to/output/counter.so
```

## Cache and failure behavior

VM dependencies are stored under the user cache, keyed by platform and bundled
tool contents. KORAL_CACHE_DIR overrides the cache root. setup requires npm and
network access, uses the pinned package-lock.json with lifecycle scripts disabled,
and is explicit: build/test never install dependencies automatically.

The prior `npm ci --prefix <installed-cli>/testing` layout remains supported for
existing users. Direct Node verifier commands need dependencies alongside those
scripts; the new cached setup is used by the CLI's Counter test command.

A missing project points to init; a missing binary points to build; missing VM
dependencies point to setup. A failed project rebuild invalidates the old .so,
ABI and success manifest so a later test cannot silently reuse a stale artifact.

## Verification boundary

The consumer CI extracts the actual candidate outside the source checkout, uses
fresh dependency caches, and exercises this flow with directory names containing
spaces. It checks refusal to overwrite, missing SDK/dependencies, failed rebuilds,
legacy commands, binary hashes and an unchanged installation directory. The
broader existing native integration suite runs separately. See CI.md in a source
checkout for the exact qualified runtime pairs and hosted run results.
