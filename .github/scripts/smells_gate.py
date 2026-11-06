#!/usr/bin/env python3
"""Fail a pull request when a Qlty code smell overlaps a line it changed.

`qlty smells --upstream` reports every smell in each changed file and exits 0 either way, so this script keeps the
smells whose lines overlap the pull request's added or changed lines and fails on any of them.

Usage: qlty smells --upstream <base> --sarif --quiet --no-snippets | smells_gate.py <base>
"""
import json
import re
import subprocess
import sys

HUNK = re.compile(r'^@@ -\S+ \+(\d+)(?:,(\d+))? @@', re.MULTILINE)


def changed_lines(diff):
    """Map each path in a `git diff -U0` to the set of line numbers it adds or changes."""
    changed = {}
    for section in diff.split('\n+++ ')[1:]:
        header, _, hunks = section.partition('\n')
        if header.startswith('b/'):
            changed[header[2:]] = added_lines(hunks)
    return changed


def added_lines(hunks):
    """The new-side line numbers of each `@@ -a,b +c,d @@` hunk header."""
    spans = ((int(h[1]), int(h[2] or 1)) for h in HUNK.finditer(hunks))
    return {line for start, count in spans for line in range(start, start + count)}


def new_smells(sarif, changed):
    """Each smell, as `path:line: rule: message`, whose region overlaps a changed line of its file."""
    found = []
    for result in (r for run in sarif['runs'] for r in run['results']):
        location = result['locations'][0]['physicalLocation']
        path, region = location['artifactLocation']['uri'], location['region']
        lines = range(region['startLine'], region.get('endLine', region['startLine']) + 1)
        if changed.get(path, set()).intersection(lines):
            found.append(f"{path}:{region['startLine']}: {result['ruleId']}: {result['message']['text']}")
    return found


if __name__ == '__main__':
    diff_command = ['git', 'diff', '-U0', '--no-color', f'{sys.argv[1]}...HEAD']
    diff = subprocess.run(diff_command, capture_output=True, text=True, check=True).stdout
    found = new_smells(json.load(sys.stdin), changed_lines(diff))
    print('\n'.join(found) or 'smells gate: pass')
    sys.exit(1 if found else 0)
