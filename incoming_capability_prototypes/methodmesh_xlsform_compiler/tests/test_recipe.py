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

    def test_every_recipe_member_has_required_type(self):
        members = [
            Member("age", "integer", "value", "odk-canonical-scalar", "", 2),
            Member("visit_date", "date", "value", "yyyy-mm-dd", "", 3),
            Member("outcome", "select_one outcome", "value", "stored-choice-name", "", 4, choice_list="outcome"),
            Member("notes", "text", "sha256", "odk-lexical-utf8-sha256", "", 5),
        ]
        recipe = _build_recipe(members)
        self.assertTrue(all(str(x.get("type", "")).strip() for x in recipe["members"]))
        source = recipe["members"][len(_context_members()):]
        self.assertEqual([x["type"] for x in source], ["integer", "date", "select_one", "sha256"])


if __name__ == "__main__":
    unittest.main()
