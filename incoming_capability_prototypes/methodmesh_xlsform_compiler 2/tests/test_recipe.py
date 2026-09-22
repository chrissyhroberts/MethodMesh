import unittest

from methodmesh_xlsform.compiler import Member, _build_recipe, _build_canonical_calc, _context_members


class RecipeTests(unittest.TestCase):
    def test_member_order_alignment(self):
        members = [
            Member("age", "integer", "value", "odk-canonical-scalar", "", 2),
            Member("notes", "text", "sha256", "odk-lexical-utf8-sha256", "", 3),
        ]
        recipe = _build_recipe(members)
        source_paths = [x["path"] for x in recipe["members"]][len(_context_members()):]
        self.assertEqual(source_paths, ["age", "notes"])
        calc = _build_canonical_calc(members)
        self.assertLess(calc.index("age="), calc.index("notes="))


if __name__ == "__main__":
    unittest.main()
