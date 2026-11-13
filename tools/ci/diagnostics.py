"""Exercise source diagnostics through an installed CLI, without an SDK or VM."""
import os
import re
import subprocess
from pathlib import Path


def verify_diagnostics(cli: Path, work: Path, env=None):
    folder = work / 'source diagnostics with spaces'
    folder.mkdir()
    base = ('package example\nimport dev.koral.api.*\n\n@Instruction\n'
            'fun increment(count: ULong, by: ULong): ULong {\n'
            '    return checkedAdd(count, by)\n}\n')
    record = ('package example\nimport dev.koral.api.*\n\n@Account("pool")\n'
              'data class PoolState(var balance: ULong)\n\n@Instruction\n'
              'fun hold(state: PoolState): PoolState = state\n')
    cases = [
        ('ordinary-add', base.replace('checkedAdd(count, by)', 'count + by'),
         6, 12, 'Unsupported call kotlin.ULong.plus'),
        ('loop', base.replace('    return checkedAdd(count, by)', '    while (true) { return count }'),
         6, 5, 'Unsupported statement: loops'),
        # The initializer visits deeper children before the local type is rejected.
        ('local-type', base.replace('    return checkedAdd(count, by)',
                                   '    val amount: Any = checkedAdd(count, by)\n    return count'),
         6, 5, 'Only ULong, Boolean'),
        ('string', base.replace('    return checkedAdd(count, by)',
                               '    val label = "unsupported"\n    return count'),
         6, 17, 'Unsupported literal: kotlin.String'),
        ('missing-marker', base.replace('@Instruction', ''),
         5, 1, 'Missing trusted @Instruction'),
        ('signature', base.replace('ULong', 'Long').replace('checkedAdd(count, by)', 'count'),
         4, 1, 'Expected instruction'),
        ('mutable-field', record, 4, 1, 'Account fields must be immutable'),
        # Model validation currently falls back to file start, not a precise field location.
        ('invalid-seed', record.replace('var balance', 'val balance').replace('"pool"', '"INVALID"'),
         1, 1, 'Invalid program: Account seed'),
        ('kotlin-error', base.replace('checkedAdd(count, by)', 'missingFunction(count)'),
         6, 12, "unresolved reference 'missingFunction'"),
    ]
    for name, source, line, column, reason in cases:
        file = folder / (name + '.kt')
        file.write_text(source)
        result = subprocess.run([str(cli), 'check', str(file)], cwd=folder, env=env or os.environ,
                                capture_output=True, text=True, timeout=120)
        output = result.stdout + result.stderr
        expected = f'{file.resolve()}:{line}:{column}:'
        location = re.search(r'^(?:Koral: )?(.*):(\d+):(\d+): error:', output, re.MULTILINE)
        positioned = False
        if location:
            reported = Path(location[1])
            if not reported.is_absolute():
                reported = folder / reported
            positioned = (reported.resolve() == file.resolve()
                          and (int(location[2]), int(location[3])) == (line, column))
        if result.returncode == 0 or not positioned or reason not in output:
            raise ValueError(f'Incorrect diagnostic for {name}: expected {expected} / {reason}\n{output}')
        if (re.search(r'^\s+at [\w.$]+\(', output, re.MULTILINE)
                or any(marker in output for marker in ['exception:', 'IllegalArgumentException',
                                                       'KoralRegistrar', 'IrSimpleTypeOnlyClassifierImpl@'])):
            raise ValueError(f'Expected source rejection leaked compiler internals: {name}\n{output}')
    valid = folder / 'valid.kt'
    valid.write_text(base)
    subprocess.run([str(cli), 'check', str(valid)], cwd=folder, env=env or os.environ,
                   check=True, timeout=120)
    return len(cases)
