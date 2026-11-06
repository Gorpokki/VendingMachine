#!/usr/bin/env python3
"""Fail when a Java function is above CCN 10 or cognitive complexity 15.

CCN (cyclomatic complexity) counts the paths through a function. Cognitive complexity (lizard -Ecognitive)
counts how hard a function is to read: nesting costs more. A reviewed exception is a `// #lizard forgive`
comment inside the function.

`--all` checks every tracked Java file. With a base ref, only changed functions count: a function fails when it
is new or got worse and ends above a limit, so an upstream sync is gated on our own lines. Function moves and
renames count as new.

Usage: lizard_gate.py --all | lizard_gate.py <base ref> [<head ref>]
"""
import subprocess
import sys

import lizard

METRICS = (('CCN', 10), ('cognitive', 15))
ANALYZER = lizard.FileAnalyzer(lizard.get_extensions(['cognitive']))


def git(*args):
    return subprocess.run(['git', *args], capture_output=True, text=True, check=True).stdout


def complexity(rev, path):
    """Map each function name in one file to its (CCN, cognitive) scores and lines."""
    try:
        source = git('show', f'{rev}:{path}')
    except subprocess.CalledProcessError:
        return {}
    functions = {}
    for function in ANALYZER.analyze_source_code(path, source).function_list:
        name = function.name.split('(', 1)[0].strip()
        scores = (function.cyclomatic_complexity, function.cognitive_complexity)
        functions.setdefault(name, []).append((scores, function.start_line))
    return functions


def every_function():
    """Every function in every tracked Java file that ends above a limit."""
    bad = []
    for path in git('ls-files', '*.java').splitlines():
        for name, versions in complexity('HEAD', path).items():
            bad += ratchet(path, name, versions, ())
    return bad


def findings(base, head='HEAD'):
    base = git('merge-base', base, head).strip()
    changes = git('diff', '--name-status', '--find-renames', '--diff-filter=AMR', base, head, '--', '*.java')
    bad = []
    for status, *paths in (line.split('\t') for line in changes.splitlines()):
        before, path = paths[0], paths[-1]
        old = complexity(base, before) if status[0] != 'A' else {}
        for name, versions in complexity(head, path).items():
            bad += ratchet(path, name, versions, old.get(name, ()))
    return bad


def ratchet(path, name, versions, old_versions):
    """Report each metric where a function is new or worse and ends above its limit. A new function's old score is 0."""
    old = old_versions[0][0] if len(versions) == len(old_versions) == 1 else (0,) * len(METRICS)
    return [f'{path}:{line}: {name} {metric} {was} -> {score} (max {limit})'
            for scores, line in versions
            for (metric, limit), score, was in zip(METRICS, scores, old)
            if score > max(limit, was)]


if __name__ == '__main__':
    bad = every_function() if sys.argv[1] == '--all' else findings(*sys.argv[1:])
    print('\n'.join(bad) or 'lizard gate: pass')
    if bad:
        print('Split the function, or add a reviewed `// #lizard forgive` comment inside it.')
    sys.exit(1 if bad else 0)
