import unittest

from smells_gate import changed_lines, new_smells, smells

DIFF = """diff --git a/A.java b/A.java
--- a/A.java
+++ b/A.java
@@ -3,0 +4,2 @@ class A {
+++ an added line that looks like a file header
+int b;
@@ -9 +11 @@ class A {
@@ -20,2 +22,0 @@ class A {
diff --git a/Gone.java b/Gone.java
--- a/Gone.java
+++ /dev/null
@@ -1,3 +0,0 @@
"""


SOURCE = """class A {
    @Override
    @SuppressWarnings("x")
    void inherited(int a, int b, int c, int d, int e, int f) {}

    void own(int a, int b, int c, int d, int e, int f) {}

    @Override void sameLine(int a, int b, int c, int d, int e, int f) {}
}""".splitlines()


def smell(path, start, end, rule='qlty:nested-control-flow', text='Deeply nested control flow'):
    return {'ruleId': rule, 'message': {'text': text},
            'locations': [{'physicalLocation': {'artifactLocation': {'uri': path},
                                                'region': {'startLine': start, 'endLine': end}}}]}


class SmellsGateTest(unittest.TestCase):

    def test_changed_lines_reads_added_and_changed_lines_only(self):
        self.assertEqual(changed_lines(DIFF), {'A.java': {4, 5, 11}})

    def test_reports_a_smell_overlapping_a_changed_line(self):
        sarif = {'runs': [{'results': [smell('A.java', 10, 12), smell('A.java', 6, 9), smell('B.java', 4, 4)]}]}
        found = new_smells(sarif, {'A.java': {4, 5, 11}})
        self.assertEqual(found, ['A.java:10: qlty:nested-control-flow: Deeply nested control flow'])

    def test_skips_parameter_smells_on_overridden_methods_only(self):
        many = 'Function with many parameters'
        sarif = {'runs': [{'results': [
            smell('A.java', 4, 4, 'qlty:function-parameters', many),
            smell('A.java', 6, 6, 'qlty:function-parameters', many),
            smell('A.java', 8, 8, 'qlty:function-parameters', many),
            smell('A.java', 4, 4)]}]}
        found = [text for _, _, text in smells(sarif, {'A.java': SOURCE}.get)]
        self.assertEqual(found, [
            f'A.java:6: qlty:function-parameters: {many}',
            'A.java:4: qlty:nested-control-flow: Deeply nested control flow'])


if __name__ == '__main__':
    unittest.main()
