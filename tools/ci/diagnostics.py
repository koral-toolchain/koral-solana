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
         5, 1, 'Expected instruction'),
        ('mutable-field', record, 4, 1, 'Account fields must be immutable'),
        # Model validation currently falls back to file start, not a precise field location.
        ('invalid-seed', record.replace('var balance', 'val balance').replace('"pool"', '"INVALID"'),
         1, 1, 'Invalid program: Account seed'),
        ('kotlin-error', base.replace('checkedAdd(count, by)', 'missingFunction(count)'),
         6, 12, "unresolved reference 'missingFunction'"),
    ]
    constrained = ('package example\nimport dev.koral.api.*\n\n@Instruction\n'
                   'fun observe(\n    count: ULong,\n'
                   '    @AccountOwner("bad") target: AccountInfo\n): ULong = count\n')
    cases += [
        ('invalid-owner', constrained, 7, 5, '@AccountOwner: Public key'),
        ('invalid-address', constrained.replace('@AccountOwner("bad")', '@AccountAddress("")'),
         7, 5, '@AccountAddress: Public key'),
        ('scalar-constraint', constrained.replace('@AccountOwner("bad") target: AccountInfo',
                                                  '@AccountOwner("11111111111111111111111111111111") amount: ULong'),
         7, 5, 'requires an external AccountInfo or WritableAccount'),
        ('state-constraint', constrained.replace('count: ULong,',
                                                 '@AccountOwner("11111111111111111111111111111111") count: ULong,')
                                       .replace('@AccountOwner("bad") ', ''),
         6, 5, 'requires an external AccountInfo or WritableAccount'),
        ('field-constraint', record.replace('var balance',
                                           '@AccountOwner("11111111111111111111111111111111") val balance'),
         4, 1, 'not state fields'),
    ]
    cases.append(('negative-size', constrained.replace('@AccountOwner("bad")', '@AccountDataSize(-1)'),
                  7, 5, '@AccountDataSize requires a constant non-negative Int'))
    for name,value in [('AccountDataSize','0'),('AccountExecutable','false')]:
        annotation=f'@{name}({value})'
        cases += [
            (name+'-scalar', constrained.replace('@AccountOwner("bad") target: AccountInfo', annotation+' amount: ULong'),
             7, 5, 'requires an external AccountInfo or WritableAccount'),
            (name+'-state', constrained.replace('count: ULong,', annotation+' count: ULong,')
                                      .replace('@AccountOwner("bad") ', ''),
             6, 5, 'requires an external AccountInfo or WritableAccount'),
            (name+'-field', record.replace('var balance', annotation+' val balance'),
             4, 1, 'not state fields'),
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
