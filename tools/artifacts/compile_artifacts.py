#!/usr/bin/env python3
"""Compile module-owned XLSForms with Python 3's standard library; never edit sources."""
import argparse
import hashlib
import json
import posixpath
import re
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile

NS = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}

def workbook(path):
    with ZipFile(path) as z:
        strings = []
        if 'xl/sharedStrings.xml' in z.namelist():
            strings = [''.join(n.itertext()) for n in ET.fromstring(z.read('xl/sharedStrings.xml'))]
        rels = {n.attrib['Id']: n.attrib['Target'] for n in ET.fromstring(z.read('xl/_rels/workbook.xml.rels'))}
        sheets = {}
        for sheet in ET.fromstring(z.read('xl/workbook.xml')).findall('s:sheets/s:sheet', NS):
            rid = sheet.attrib['{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id']
            target = rels[rid]
            target = target.lstrip('/') if target.startswith('/') else posixpath.normpath('xl/' + target)
            rows = []
            for row in ET.fromstring(z.read(target)).findall('s:sheetData/s:row', NS):
                cells = {}
                for cell in row:
                    column = re.sub('[0-9]', '', cell.attrib['r'])
                    value = cell.findtext('s:v', '', NS)
                    if cell.attrib.get('t') == 's':
                        value = strings[int(value)]
                    elif cell.attrib.get('t') == 'inlineStr':
                        value = ''.join(n.text or '' for n in cell.findall('.//s:t', NS))
                    cells[column] = value
                rows.append(cells)
            if rows:
                headers = rows[0]
                sheets[sheet.attrib['name'].lower()] = [
                    {headers.get(k, k): v for k, v in row.items()} for row in rows[1:]]
        return sheets

def inspect(sheets):
    findings = []
    survey = sheets.get('survey', [])
    if not survey or not any('type' in r and 'name' in r for r in survey):
        return ['NOT_XLSFORM'], []
    scope, names, global_names = [], set(), set()
    calls = []
    for row in survey:
        typ, name = row.get('type', '').strip(), row.get('name', '').strip()
        if typ in ('end group', 'end_group', 'end repeat', 'end_repeat'):
            if scope: scope.pop()
            else: findings.append('SURVEY_SCOPE_UNBALANCED')
            continue
        if name:
            key = (tuple(scope), name)
            if key in names: findings.append('SURVEY_NAME_DUPLICATE')
            if name in global_names: findings.append('KOBO_GLOBAL_NODE_NAME_COLLISION')
            names.add(key)
            global_names.add(name)
        if typ in ('begin group', 'begin_group', 'begin repeat', 'begin_repeat'):
            scope.append(name)
        for value in row.values():
            if 'com.example.methodmesh.EXECUTE_METHOD(' in value:
                calls.append(value)
    if scope: findings.append('SURVEY_SCOPE_UNBALANCED')
    if len(calls) != 1: findings.append('MM_XLS_SINGLE_INVOCATION')
    if any('methodmesh_return_namespace' in c for c in calls): findings.append('MM_XLS_RETURN_NAMESPACE')
    if not {'methodmesh_status', 'methodmesh_full_json'} <= global_names:
        findings.append('MM_XLS_REQUIRED_RETURNS')
    methods = sorted(set(m for c in calls for m in re.findall(r"method_id\s*=\s*['\"]([^'\"]+)", c)))
    return sorted(set(findings)), methods

def compile_catalog(source, output):
    entries, diagnostics, expected = [], [], set()
    for module in sorted(p for p in source.iterdir() if p.is_dir()):
        module_text = '\n'.join(p.read_text() for p in sorted(module.glob('*Module.kt')))
        def property_value(name, fallback):
            match = re.search(r'override\s+val\s+' + name + r'(?:\s*:\s*String)?\s*=\s*"([^"]+)"', module_text)
            return match.group(1) if match else fallback
        owner = property_value('moduleId', module.name)
        for path in sorted((module / 'docs').rglob('*')):
            if path.suffix.lower() != '.xlsx' or path.name.startswith('~$'): continue
            relative = path.relative_to(module).as_posix()
            try:
                sheets = workbook(path)
                findings, methods = inspect(sheets)
            except Exception as error:
                raise ValueError(f'{module.name}/{relative}: invalid XLSX: {error}') from error
            if findings == ['NOT_XLSFORM']:
                diagnostics.append({'source': f'{module.name}/{relative}', 'findings': findings})
                continue
            canonical_name = bool(re.fullmatch(r'example_odk_[A-Za-z0-9_.-]+\.xlsx', path.name))
            if not canonical_name: findings.append('LEGACY_FILENAME')
            settings = next(iter(sheets.get('settings', [])), {})
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            identity = hashlib.sha256(f'{owner}/{relative}'.encode()).hexdigest()
            asset = f'methodmesh/artifacts/xlsforms/{identity}/{path.name}'
            target = output / asset
            target.parent.mkdir(parents=True, exist_ok=True)
            if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest() != digest:
                shutil.copyfile(path, target)
            expected.add(target)
            entries.append(dict(id=f'module.{owner}.{identity}', origin='BUNDLED', lifecycle='PERSISTENT',
                moduleId=owner, moduleName=property_value('displayName', owner),
                displayName=settings.get('form_title') or path.stem, description='Module-owned XLSForm',
                assetPath=asset, sourceFileName=path.name, source=f'{module.name}/{relative}',
                centralFormId=settings.get('form_id', ''), version=settings.get('version', ''),
                capabilityIds=methods, tags=['xlsform'], sha256=digest, sizeBytes=path.stat().st_size,
                mimeType='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                canonicalName=canonical_name, findings=sorted(set(findings)),
                policyStatus='review_required' if findings else 'structural_checks_passed'))
    if len({e['id'] for e in entries}) != len(entries): raise ValueError('Duplicate artifact identity/module ID')
    root = output / 'methodmesh/artifacts'
    root.mkdir(parents=True, exist_ok=True)
    for old in (root / 'xlsforms').rglob('*'):
        if old.is_file() and old not in expected: old.unlink()
    index = root / 'index.json'
    text = json.dumps(dict(schemaVersion=1, artifacts=entries, diagnostics=diagnostics), indent=2, sort_keys=True) + '\n'
    if not index.exists() or index.read_text() != text: index.write_text(text)
    return entries

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    entries = compile_catalog(args.source, args.output)
    print(f'Indexed {len(entries)} XLSForms; {sum(bool(e["findings"]) for e in entries)} require policy review. Sources unchanged.')
