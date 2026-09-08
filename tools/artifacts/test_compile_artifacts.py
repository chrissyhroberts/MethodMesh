import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile
from xml.sax.saxutils import escape
from compile_artifacts import compile_catalog, inspect


def make_workbook(path, title='Consent'):
    path.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(path, 'w') as z:
        z.writestr('xl/workbook.xml', '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="survey" r:id="r1"/><sheet name="settings" r:id="r2"/></sheets></workbook>')
        z.writestr('xl/_rels/workbook.xml.rels', '<Relationships><Relationship Id="r1" Target="worksheets/sheet1.xml"/><Relationship Id="r2" Target="worksheets/sheet2.xml"/></Relationships>')
        rows = [[['type','name','appearance'],['begin group','call',"com.example.methodmesh.EXECUTE_METHOD(method_id='ink.sign')"],['text','methodmesh_status'],['text','methodmesh_full_json'],['end group']], [['form_id','form_title','version'],['stable_id',title,'2026090801']]]
        for i, sheet in enumerate(rows, 1):
            xml = '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>'
            for r, row in enumerate(sheet, 1):
                xml += '<row>' + ''.join(f'<c r="{chr(65+c)}{r}" t="inlineStr"><is><t>{escape(v)}</t></is></c>' for c,v in enumerate(row)) + '</row>'
            z.writestr(f'xl/worksheets/sheet{i}.xml', xml + '</sheetData></worksheet>')

class CompilerTest(unittest.TestCase):
    def test_identity_bytes_incremental_and_removal(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); source=root/'modules'; out=root/'generated'
            form=source/'foo/docs/legacy.xlsx'; make_workbook(form)
            (source/'foo/FooModule.kt').write_text('override val moduleId: String = "actual-owner"')
            original=form.read_bytes(); first=compile_catalog(source,out)[0]
            self.assertEqual('actual-owner', first['moduleId'])
            self.assertEqual('stable_id', first['centralFormId'])
            self.assertIn('LEGACY_FILENAME', first['findings'])
            target=out/first['assetPath']; modified=target.stat().st_mtime_ns
            self.assertEqual(original,target.read_bytes())
            self.assertEqual(original,form.read_bytes())
            compile_catalog(source,out)
            self.assertEqual(modified,target.stat().st_mtime_ns)
            make_workbook(form,'Revised'); revised=compile_catalog(source,out)[0]
            self.assertEqual(first['id'],revised['id'])
            self.assertNotEqual(first['sha256'],revised['sha256'])
            form.unlink(); self.assertEqual([],compile_catalog(source,out)); self.assertFalse(target.exists())

    def test_generic_duplicates_are_scope_aware(self):
        rows=[{'type':'begin group','name':'a'},{'type':'text','name':'x'},{'type':'end group'},
              {'type':'begin group','name':'b'},{'type':'text','name':'x'},{'type':'end group'}]
        findings,_=inspect({'survey':rows})
        self.assertNotIn('SURVEY_NAME_DUPLICATE',findings)
        self.assertIn('KOBO_GLOBAL_NODE_NAME_COLLISION',findings)
        rows.insert(2,{'type':'text','name':'x'})
        self.assertIn('SURVEY_NAME_DUPLICATE',inspect({'survey':rows})[0])

    def test_same_filename_in_two_modules_is_distinct(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            for module in ['one','two']: make_workbook(root/'modules'/module/'docs/example_odk_consent.xlsx')
            entries=compile_catalog(root/'modules',root/'out')
            self.assertEqual(2,len({e['id'] for e in entries}))
            self.assertEqual(2,len({e['assetPath'] for e in entries}))

if __name__ == '__main__': unittest.main()
