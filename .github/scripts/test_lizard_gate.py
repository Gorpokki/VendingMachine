import unittest

from lizard_gate import ratchet


class RatchetTest(unittest.TestCase):

    def test_new_function_fails_above_the_limit(self):
        self.assertEqual(ratchet('A.java', 'A::f', [((6, 2), 3)], []), ['A.java:3: A::f CCN 0 -> 6 (max 5)'])

    def test_old_function_fails_only_when_worse_and_above_the_limit(self):
        old = [((7, 9), 1)]
        self.assertEqual(ratchet('A.java', 'A::f', [((7, 9), 4)], old), [])
        self.assertEqual(ratchet('A.java', 'A::f', [((8, 6), 4)], old), ['A.java:4: A::f CCN 7 -> 8 (max 5)'])

    def test_overloads_count_as_new(self):
        self.assertEqual(ratchet('A.java', 'A::f', [((6, 0), 1), ((1, 0), 9)], [((6, 0), 1)]),
                         ['A.java:1: A::f CCN 0 -> 6 (max 5)'])


if __name__ == '__main__':
    unittest.main()
