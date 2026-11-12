#!/usr/bin/env python3
"""Install the checksum-pinned Linux x64 SDK into a disposable CI directory."""
import argparse
import hashlib
import platform
import shutil
import tarfile
import tempfile
from pathlib import Path
from urllib.request import urlopen

ARCHIVES = {
    'agave': (
        'https://github.com/anza-xyz/agave/releases/download/v3.1.10/solana-release-x86_64-unknown-linux-gnu.tar.bz2',
        'a7205ff29bcf0f7199740225ecae2b85a28ea9668892d5ec21bd9749882984a1',
    ),
    'tools': (
        'https://github.com/anza-xyz/platform-tools/releases/download/v1.52/platform-tools-linux-x86_64.tar.bz2',
        '8b3861e93daf085ec12b65c4fac374d9bec4587a3de16871da6b08ab0c1d907e',
    ),
}


def download(url, destination, expected):
    digest = hashlib.sha256()
    with urlopen(url, timeout=120) as response, destination.open('wb') as output:
        while chunk := response.read(1024 * 1024):
            digest.update(chunk)
            output.write(chunk)
    if digest.hexdigest() != expected:
        destination.unlink()
        raise ValueError('SDK archive checksum mismatch')


def extract_tools(archive_path, destination):
    # The pinned release starts at ./llvm and ./rust, not a wrapper directory.
    with tarfile.open(archive_path) as archive:
        archive.extractall(destination, filter='data')
    if not (destination / 'llvm/bin/clang').is_file() or not (destination / 'rust').is_dir():
        raise ValueError('Unexpected platform-tools archive layout')


def install(destination):
    if platform.system() != 'Linux' or platform.machine() not in ('x86_64', 'AMD64'):
        raise ValueError('This CI installer supports Linux x64 only; pass a local SDK elsewhere')
    if destination.exists():
        raise ValueError('SDK destination must not already exist')
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='koral-sdk-') as folder:
        temp = Path(folder)
        for name, (url, checksum) in ARCHIVES.items():
            download(url, temp / (name + '.tar.bz2'), checksum)
        prefix = 'solana-release/bin/platform-tools-sdk/sbf/'
        with tarfile.open(temp / 'agave.tar.bz2') as archive:
            members = [m for m in archive.getmembers() if m.name.startswith(prefix)]
            if not members:
                raise ValueError('Pinned archive lacks the expected SBF SDK')
            archive.extractall(temp / 'agave', members=members, filter='data')
        sdk = temp / 'agave' / prefix
        extracted_tools = temp / 'tools'
        extract_tools(temp / 'tools.tar.bz2', extracted_tools)
        tools = sdk / 'dependencies/platform-tools'
        if tools.is_symlink():
            tools.unlink()
        elif tools.exists():
            shutil.rmtree(tools)
        tools.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(extracted_tools), tools)
        if not (sdk / 'c/sbf.ld').is_file() or not (tools / 'llvm/bin/clang').is_file():
            raise ValueError('Incomplete SDK')
        shutil.move(str(sdk), destination)
    print('Installed Agave 3.1.10 / platform-tools 1.52: ' + str(destination))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination', type=Path, required=True)
    install(parser.parse_args().destination.resolve())
