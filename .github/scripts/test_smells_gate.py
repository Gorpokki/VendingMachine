import unittest

from smells_gate import changed_lines, new_smells

DIFF = """diff --git a/A.java b/A.java
--- a/A.java
+++ b/A.java
@@ -3,0 +4,2 @@ class A {
@@ -9 +11 @@ class A {
@@ -20,2 +22,0 @@ class A {
diff --git a/Gone.java b/Gone.java
--- a/Gone.java
+++ /dev/null
@@ -1,3 +0,0 @@
"""


def smell(path, start, end):
    return {'ruleId': 'qlty:nested-control-flow', 'message': {'text': 'Deeply nested control flow'},
            'locations': [{'physicalLocation': {'artifactLocation': {'uri': path},
                                                'region': {'startLine': start, 'endLine': end}}}]}


class SmellsGateTest(unittest.TestCase):

    def test_changed_lines_reads_added_and_changed_lines_only(self):
        self.assertEqual(changed_lines(DIFF), {'A.java': {4, 5, 11}})

    def test_reports_a_smell_overlapping_a_changed_line(self):
        sarif = {'runs': [{'results': [smell('A.java', 10, 12), smell('A.java', 6, 9), smell('B.java', 4, 4)]}]}
        found = new_smells(sarif, {'A.java': {4, 5, 11}})
        self.assertEqual(found, ['A.java:10: qlty:nested-control-flow: Deeply nested control flow'])


if __name__ == '__main__':
    unittest.main()
