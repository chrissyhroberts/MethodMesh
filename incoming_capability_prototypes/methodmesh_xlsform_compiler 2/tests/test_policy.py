import unittest

from methodmesh_xlsform.errors import ValidationError
from methodmesh_xlsform.policy import decide_commit


class PolicyTests(unittest.TestCase):
    def test_text_auto_hashes(self):
        d = decide_commit("text", "", "notes")
        self.assertEqual(d.mode, "sha256")

    def test_integer_auto_value(self):
        d = decide_commit("integer", "auto", "age")
        self.assertEqual(d.mode, "value")

    def test_select_multiple_auto_hashes_lexical(self):
        d = decide_commit("select_multiple symptoms", "", "symptoms")
        self.assertEqual(d.mode, "sha256")
        self.assertIn("selection-order", d.transform)

    def test_calculate_auto_excluded(self):
        d = decide_commit("calculate", "", "helper")
        self.assertEqual(d.mode, "exclude")

    def test_media_requires_explicit_exclude(self):
        with self.assertRaises(ValidationError):
            decide_commit("image", "", "photo")
        d = decide_commit("image", "exclude", "photo")
        self.assertEqual(d.mode, "exclude")

    def test_text_raw_value_rejected(self):
        with self.assertRaises(ValidationError):
            decide_commit("text", "value", "unsafe_text")


if __name__ == "__main__":
    unittest.main()
