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

from diagnostics import verify_diagnostics


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
        for required in ['LICENSE', 'THIRD_PARTY_NOTICES.md', 'bin/koral', 'testing/package-lock.json',
                         'licenses/kotlin/LICENSE.txt', 'licenses/kotlin/NOTICE.txt', 'licenses/kotlin/COPYRIGHT.txt']:
            if not (root / required).is_file():
                raise ValueError('Distribution is missing ' + required)
        env = os.environ.copy()
        env['npm_config_cache'] = str(Path(folder) / 'npm-cache')
        env['KORAL_CACHE_DIR'] = str(Path(folder) / 'user cache')

        def run(*args, cwd=work):
            subprocess.run([str(a) for a in args], cwd=cwd, env=env, check=True, timeout=600)

        def reject(*args, cwd=work, contains, process_env=None):
            result = subprocess.run([str(a) for a in args], cwd=cwd, env=process_env or env,
                                    capture_output=True, text=True, timeout=120)
            if result.returncode == 0 or contains not in result.stdout + result.stderr:
                raise ValueError('Expected an actionable rejection: ' + contains + '\n' + result.stdout + result.stderr)

        cli = root / 'bin/koral'
        run('java', '-version')
        run('node', '--version')
        diagnostic_checks = verify_diagnostics(cli, work, env)
        if mode == 'integration':
            run('npm', 'ci', '--ignore-scripts', '--prefix', root / 'testing')
            run(cli, 'doctor', '--sdk', sdk)
            run('node', root / 'testing/verify.mjs', cli, sdk)
        else:
            installed = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
                         for p in root.rglob('*') if p.is_file()}
            env['KORAL_SBF_SDK'] = str(sdk)
            missing_sdk = {k: v for k, v in env.items() if k != 'KORAL_SBF_SDK'}
            reject(cli, 'doctor', contains='Export KORAL_SBF_SDK', process_env=missing_sdk)
            run(cli, 'doctor')
            reject(cli, 'build', contains='koral init')
            project = work / 'hello counter'
            run(cli, 'init', project)
            reject(cli, 'init', project, contains='existing files are never replaced')
            run(cli, 'check', cwd=project)
            run(cli, 'build', cwd=project)
            output = project / 'build/koral'
            reject(cli, 'test', cwd=project, contains='koral setup')
            run(cli, 'setup', cwd=project)
            run(cli, 'doctor', cwd=project)
            run(cli, 'test', cwd=project)
            if json.loads((output / 'counter.test.json').read_text())['tests'] != 55:
                raise ValueError('The new project did not pass the full Counter verification')
            # Retain the explicit binary invocation for existing consumers.
            run(cli, 'test', output / 'counter.so')
            manifest = json.loads((output / 'counter.build.json').read_text())
            actual = hashlib.sha256((output / 'counter.so').read_bytes()).hexdigest()
            if manifest['binarySha256'] != actual:
                raise ValueError('Binary hash differs from build manifest')
            json.loads((output / 'counter.abi.json').read_text())

            # Legacy explicit-source payment compilation still works with the same archive.
            output = work / 'compiled-payments'
            run(cli, 'build', root / 'examples/payments.kt', '--out', output, '--sdk', sdk)
            runners = list(Path(env['KORAL_CACHE_DIR']).glob('vm/*/payments.mjs'))
            if len(runners) != 1:
                raise ValueError('Missing the versioned cached payment verifier')
            run('node', runners[0], output / 'counter.so', output / 'tests.json')
            if json.loads((output / 'tests.json').read_text())['checks'] != 37:
                raise ValueError('Payment compatibility verification was incomplete')

            constrained = work / 'constrained-payments'
            run(cli, 'build', root / 'examples/constrained-payments.kt', '--out', constrained, '--sdk', sdk)
            run('node', runners[0].parent / 'constraints.mjs', constrained / 'counter.so', constrained / 'tests.json')
            if not all(json.loads((constrained / 'tests.json').read_text())[key]
                       for key in ['bodyFailureRollback', 'dataSizeAndExecutable']):
                raise ValueError('Constrained account rollback verification was incomplete')

            source = project / 'src/counter.kt'
            source.write_text(source.read_text().replace('checkedAdd(state.count, by)', 'state.count + by'))
            reject(cli, 'build', cwd=project, contains='Unsupported call')
            for name in ['counter.so', 'counter.build.json', 'counter.abi.json']:
                if (project / 'build/koral' / name).exists():
                    raise ValueError('Failed project build retained stale success artifact: ' + name)
            reject(cli, 'test', cwd=project, contains='Run koral build first')
            after = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
                     for p in root.rglob('*') if p.is_file()}
            if installed != after:
                raise ValueError('The first-project workflow modified the installed distribution')
        print(json.dumps({'consumerVerification': 'passed', 'mode': mode, 'sourceCheckoutRequired': False,
                          'sourceDiagnosticChecks': diagnostic_checks}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--mode', choices=['smoke', 'integration'], default='smoke')
    args = parser.parse_args()
    verify(args.archive.resolve(), args.sdk.resolve(), args.mode)
