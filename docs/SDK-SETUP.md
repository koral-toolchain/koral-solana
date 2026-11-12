# Install the pinned external SBF SDK

Koral does not bundle or silently download Agave/LLVM. Use Agave 3.1.10 and
platform-tools 1.52 for the preview's recorded results. Keep upstream licenses.
These commands do not install a wallet or submit a blockchain transaction.

## Linux x64 from a source checkout

Python 3.12+ and network access are needed for this optional CI helper:

```sh
python3 tools/ci/prepare_sdk.py --destination "$HOME/.cache/koral-sdk-3.1.10"
export KORAL_SBF_SDK="$HOME/.cache/koral-sdk-3.1.10"
```

The helper verifies the official archives' fixed SHA-256 values and requires a
new destination. It does not overwrite an existing SDK. It is Linux x64 only;
it is not a Python dependency of the installed compiler.

## Manual setup for an extracted CLI

Choose the two official release assets matching your host:

| Host | Agave 3.1.10 archive | Platform-tools 1.52 archive |
| --- | --- | --- |
| macOS arm64 | [solana-release-aarch64-apple-darwin.tar.bz2](https://github.com/anza-xyz/agave/releases/download/v3.1.10/solana-release-aarch64-apple-darwin.tar.bz2) | [platform-tools-osx-aarch64.tar.bz2](https://github.com/anza-xyz/platform-tools/releases/download/v1.52/platform-tools-osx-aarch64.tar.bz2) |
| Linux x64 | [solana-release-x86_64-unknown-linux-gnu.tar.bz2](https://github.com/anza-xyz/agave/releases/download/v3.1.10/solana-release-x86_64-unknown-linux-gnu.tar.bz2) | [platform-tools-linux-x86_64.tar.bz2](https://github.com/anza-xyz/platform-tools/releases/download/v1.52/platform-tools-linux-x86_64.tar.bz2) |

Expected SHA-256 values, checked against the official release asset metadata:

```text
839693d40bdd0bd76d9569f7df3d38fb3bc69aa5028fc60bef3aa948eee453a5  solana-release-aarch64-apple-darwin.tar.bz2
1727dfb31e833ce777d01e70cb4b3cebd26b376bf09d80527f02457d4cf6fd00  platform-tools-osx-aarch64.tar.bz2
a7205ff29bcf0f7199740225ecae2b85a28ea9668892d5ec21bd9749882984a1  solana-release-x86_64-unknown-linux-gnu.tar.bz2
8b3861e93daf085ec12b65c4fac374d9bec4587a3de16871da6b08ab0c1d907e  platform-tools-linux-x86_64.tar.bz2
```

Verify the downloaded archives with shasum -a 256 on macOS or sha256sum on Linux
before extracting. Extract Agave into a new directory. Inside the resulting
solana-release/bin/platform-tools-sdk/sbf directory, create
dependencies/platform-tools and unpack the platform-tools archive there.
Its llvm and rust directories must be immediate children of platform-tools.

The final SDK must include:

```text
sbf/
  c/inc/
  c/sbf.ld
  dependencies/platform-tools/llvm/bin/clang
  dependencies/platform-tools/llvm/bin/ld.lld
  dependencies/platform-tools/rust/
```

Set KORAL_SBF_SDK to that sbf directory, then run koral doctor. Pointing to
solana-release itself or to the LLVM bin directory is incorrect. An explicit
`--sdk /path/to/sbf` overrides the environment for doctor/build.

Windows and Intel macOS SDK setup are outside this preview's verified targets.
