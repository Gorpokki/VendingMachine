#!/usr/bin/env python3
"""Fail on Qlty code smells: every smell with `--all`, or with a base ref only smells that overlap a changed line.

`qlty smells` exits 0 either way, and `qlty smells --upstream` reports every smell in each changed file, so this
script decides what fails. A method annotated `@Override` cannot change its parameters, so its function-parameters
smell does not fail, as Checkstyle's ParameterNumber check skips overridden methods.

Usage: qlty smells --all --sarif --quiet --no-snippets | smells_gate.py --all
    qlty smells --upstream <base> --sarif --quiet --no-snippets | smells_gate.py <base>
"""
import json
import re
import subprocess
import sys

FILE = re.compile(r'^diff --git ', re.MULTILINE)
NEW_PATH = re.compile(r'^\+\+\+ b/(.+)$', re.MULTILINE)
HUNK = re.compile(r'^@@ -\S+ \+(\d+)(?:,(\d+))? @@', re.MULTILINE)
PARAMETERS = 'qlty:function-parameters'


def changed_lines(diff):
    """Map each path in a `git diff -U0` to the set of line numbers it adds or changes."""
    changed = {}
    for section in FILE.split(diff)[1:]:
        header, _, hunks = section.partition('\n@@')
        path = NEW_PATH.search(header)
        if path:
            changed[path[1]] = added_lines('@@' + hunks)
    return changed


def added_lines(hunks):
    """The new-side line numbers of each `@@ -a,b +c,d @@` hunk header."""
    spans = ((int(h[1]), int(h[2] or 1)) for h in HUNK.finditer(hunks))
    return {line for start, count in spans for line in range(start, start + count)}


def read_lines(path):
    with open(path, encoding='utf-8') as source:
        return source.read().splitlines()


def overrides(source, start):
    """Whether the declaration starting on 1-based line `start` of `source` is annotated `@Override`."""
    line = start - 1
    if '@Override' in source[line]:
        return True
    line -= 1
    while line >= 0 and source[line].strip().startswith('@'):
        if source[line].strip().startswith('@Override'):
            return True
        line -= 1
    return False


def smells(sarif, read=read_lines):
    """Each smell as (path, its lines, `path:line: rule: message`), leaving out overridden methods' parameters."""
    for result in (r for run in sarif['runs'] for r in run['results']):
        location = result['locations'][0]['physicalLocation']
        path, region = location['artifactLocation']['uri'], location['region']
        if result['ruleId'] == PARAMETERS and overrides(read(path), region['startLine']):
            continue
        lines = range(region['startLine'], region.get('endLine', region['startLine']) + 1)
        yield path, lines, f"{path}:{region['startLine']}: {result['ruleId']}: {result['message']['text']}"


def new_smells(sarif, changed, read=read_lines):
    """Each smell whose region overlaps a changed line of its file."""
    return [text for path, lines, text in smells(sarif, read) if changed.get(path, set()).intersection(lines)]


def changed_since(base):
    diff_command = ['git', 'diff', '-U0', '--no-color', f'{base}...HEAD']
    return changed_lines(subprocess.run(diff_command, capture_output=True, text=True, check=True).stdout)


if __name__ == '__main__':
    sarif = json.load(sys.stdin)
    if sys.argv[1] == '--all':
        found = [text for _, _, text in smells(sarif)]
    else:
        found = new_smells(sarif, changed_since(sys.argv[1]))
    print('\n'.join(found) or 'smells gate: pass')
    sys.exit(1 if found else 0)
