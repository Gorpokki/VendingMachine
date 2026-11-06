import unittest

from lizard_gate import ratchet


class RatchetTest(unittest.TestCase):

    def test_new_function_fails_above_the_limit(self):
        self.assertEqual(ratchet('A.java', 'A::f', [((11, 2), 3)], []), ['A.java:3: A::f CCN 0 -> 11 (max 10)'])

    def test_old_function_fails_only_when_worse_and_above_the_limit(self):
        old = [((12, 16), 1)]
        self.assertEqual(ratchet('A.java', 'A::f', [((12, 16), 4)], old), [])
        self.assertEqual(ratchet('A.java', 'A::f', [((13, 9), 4)], old), ['A.java:4: A::f CCN 12 -> 13 (max 10)'])

    def test_overloads_count_as_new(self):
        overloads = ratchet('A.java', 'A::f', [((11, 0), 1), ((1, 16), 9)], [((11, 0), 1)])
        self.assertEqual(overloads, ['A.java:1: A::f CCN 0 -> 11 (max 10)', 'A.java:9: A::f cognitive 0 -> 16 (max 15)'])


if __name__ == '__main__':
    unittest.main()
