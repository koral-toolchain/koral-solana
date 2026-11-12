#!/usr/bin/env python3
"""Verify the candidate CLI archive outside its source checkout."""
import argparse
import hashlib
import json
import os
import subprocess
import tarfile
import tempfile
from pathlib import Path


def verify(archive_path, sdk, mode):
    with tempfile.TemporaryDirectory(prefix='koral-consumer-') as folder:
        work = Path(folder) / 'consumer with spaces'
        work.mkdir()
        with tarfile.open(archive_path) as archive:
            for member in archive.getmembers():
                if {'.git', 'node_modules', 'experiments', '.env'} & set(Path(member.name).parts):
                    raise ValueError('Unexpected file in distribution: ' + member.name)
            archive.extractall(work, filter='data')
        roots = list(work.iterdir())
        if len(roots) != 1 or not roots[0].is_dir():
            raise ValueError('Expected one CLI distribution directory')
        root = roots[0]
        for required in ['LICENSE', 'THIRD_PARTY_NOTICES.md', 'bin/koral', 'testing/package-lock.json']:
            if not (root / required).is_file():
                raise ValueError('Distribution is missing ' + required)
        env = os.environ.copy()
        env['npm_config_cache'] = str(Path(folder) / 'npm-cache')

        def run(*args):
            subprocess.run([str(a) for a in args], cwd=work, env=env, check=True, timeout=600)

        cli = root / 'bin/koral'
        run('java', '-version')
        run('node', '--version')
        run('npm', 'ci', '--ignore-scripts', '--prefix', root / 'testing')
        run(cli, 'doctor', '--sdk', sdk)
        if mode == 'integration':
            run('node', root / 'testing/verify.mjs', cli, sdk)
        else:
            for example in ['counter', 'payments']:
                source = root / ('examples/' + example + '.kt')
                output = work / ('compiled-' + example)
                run(cli, 'check', source)
                run(cli, 'build', source, '--out', output, '--sdk', sdk)
                manifest = json.loads((output / 'counter.build.json').read_text())
                actual = hashlib.sha256((output / 'counter.so').read_bytes()).hexdigest()
                if manifest['binarySha256'] != actual:
                    raise ValueError('Binary hash differs from build manifest')
                json.loads((output / 'counter.abi.json').read_text())
                if example == 'counter':
                    run(cli, 'test', output / 'counter.so')
                else:
                    run('node', root / 'testing/payments.mjs', output / 'counter.so', output / 'tests.json')
        print(json.dumps({'consumerVerification': 'passed', 'mode': mode, 'sourceCheckoutRequired': False}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--mode', choices=['smoke', 'integration'], default='smoke')
    args = parser.parse_args()
    verify(args.archive.resolve(), args.sdk.resolve(), args.mode)
