#!/usr/bin/env python3
"""Generate MethodMesh's packaged ODK/XLSForm design-template catalogue.

Source of truth:
  src/main/java/com/example/methodmesh/modules/<module>/docs/*.xlsx

The generator emits one catalogue entry per XLSForm. A module may own one form or many.
It performs two validation layers:

1. Always-on MethodMesh/XLSForm structural + naming lint (no third-party Python deps).
2. Optional authoritative pyxform + ODK Validate when ``xls2xform`` is installed on the
   build machine. pyxform distributes ODK Validate and is the same conversion family used
   by ODK Central.

Validation never silently rewrites form IDs, filenames, or formulas. It reports issues for
review because deployed form IDs and versions are contracts.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from xml.etree import ElementTree as ET

NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
NS_REL_DOC = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
NS_REL_PKG = "http://schemas.openxmlformats.org/package/2006/relationships"

CANONICAL_FILENAME = re.compile(r"^example_odk_[a-z0-9]+(?:_[a-z0-9]+)*\.xlsx$")
LOWER_SNAKE = re.compile(r"^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$")
# Common XPath 2.0-style functions that JavaRosa/ODK does not implement. This is not an
# allow-list: pyxform/ODK Validate remains authoritative when available.
KNOWN_UNSUPPORTED_FUNCTIONS = {
    "replace": "Use translate() for simple character substitution/removal, or restructure the expression using ODK-supported functions.",
    "tokenize": "Use split-style data modelling or explicit substring logic; JavaRosa does not implement XPath tokenize().",
    "matches": "Use ODK's regex() function instead of XPath matches().",
    "lower-case": "ODK/JavaRosa does not implement XPath lower-case(). Avoid case conversion or perform it in MethodMesh.",
    "upper-case": "ODK/JavaRosa does not implement XPath upper-case(). Avoid case conversion or perform it in MethodMesh.",
    "string-join": "Use concat() for known fields or construct the value in MethodMesh; JavaRosa does not implement XPath string-join().",
}
EXPRESSION_COLUMNS = {
    "calculation", "relevant", "constraint", "choice_filter", "repeat_count",
    "required", "trigger", "readonly", "read_only"
}
SELECT_RE = re.compile(r"^select_(?:one|multiple)\s+([^\s]+)", re.I)
VAR_REF_RE = re.compile(r"\$\{([^}]+)\}")
FUNCTION_RE = re.compile(r"(?<![A-Za-z0-9_.-])([A-Za-z][A-Za-z0-9_.-]*)\s*\(")


def _cell_column(ref: str) -> str:
    match = re.match(r"([A-Za-z]+)", ref or "")
    return match.group(1).upper() if match else ""


def _shared_strings(zf: zipfile.ZipFile) -> list[str]:
    try:
        root = ET.fromstring(zf.read("xl/sharedStrings.xml"))
    except (KeyError, ET.ParseError):
        return []
    return ["".join(t.text or "" for t in si.iter(f"{{{NS_MAIN}}}t")) for si in root.findall(f"{{{NS_MAIN}}}si")]


def _sheet_paths(zf: zipfile.ZipFile) -> dict[str, str]:
    try:
        workbook = ET.fromstring(zf.read("xl/workbook.xml"))
        rels = ET.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
    except (KeyError, ET.ParseError):
        return {}
    rel_map = {
        rel.attrib.get("Id", ""): rel.attrib.get("Target", "")
        for rel in rels.findall(f"{{{NS_REL_PKG}}}Relationship")
    }
    result: dict[str, str] = {}
    for sheet in workbook.findall(f".//{{{NS_MAIN}}}sheet"):
        name = sheet.attrib.get("name", "").strip()
        rid = sheet.attrib.get(f"{{{NS_REL_DOC}}}id", "")
        target = rel_map.get(rid, "")
        if not name or not target:
            continue
        target_path = PurePosixPath(target)
        result[name.lower()] = str(target_path).lstrip("/") if str(target_path).startswith("/") else str(PurePosixPath("xl") / target_path)
    return result


def _sheet_rows(zf: zipfile.ZipFile, sheet_path: str) -> list[dict[str, str]]:
    shared = _shared_strings(zf)
    try:
        root = ET.fromstring(zf.read(sheet_path))
    except (KeyError, ET.ParseError):
        return []
    rows: list[dict[str, str]] = []
    for row in root.findall(f".//{{{NS_MAIN}}}row"):
        values: dict[str, str] = {}
        for cell in row.findall(f"{{{NS_MAIN}}}c"):
            col = _cell_column(cell.attrib.get("r", ""))
            if not col:
                continue
            cell_type = cell.attrib.get("t", "")
            if cell_type == "inlineStr":
                value = "".join(t.text or "" for t in cell.iter(f"{{{NS_MAIN}}}t"))
            else:
                vnode = cell.find(f"{{{NS_MAIN}}}v")
                raw = vnode.text if vnode is not None and vnode.text is not None else ""
                if cell_type == "s" and raw.isdigit():
                    idx = int(raw)
                    value = shared[idx] if 0 <= idx < len(shared) else raw
                else:
                    value = raw
            values[col] = value.strip()
        if values:
            rows.append(values)
    return rows


def _table_from_rows(rows: list[dict[str, str]], expected_headers: set[str]) -> tuple[list[str], list[dict[str, str]]]:
    for i, row in enumerate(rows):
        by_col = {col: value.strip() for col, value in row.items() if value.strip()}
        lowered = {value.lower() for value in by_col.values()}
        if not (expected_headers & lowered):
            continue
        headers = [value.strip() for _, value in sorted(by_col.items())]
        header_by_col = {col: value.strip() for col, value in by_col.items()}
        data: list[dict[str, str]] = []
        for source in rows[i + 1 :]:
            mapped = {header_by_col[col].strip().lower(): source.get(col, "").strip() for col in header_by_col}
            if any(mapped.values()):
                data.append(mapped)
        return [h.lower() for h in headers], data
    return [], []


def workbook_tables(path: Path) -> dict[str, tuple[list[str], list[dict[str, str]]]]:
    try:
        with zipfile.ZipFile(path) as zf:
            paths = _sheet_paths(zf)
            result: dict[str, tuple[list[str], list[dict[str, str]]]] = {}
            for name, sheet_path in paths.items():
                rows = _sheet_rows(zf, sheet_path)
                expected = {"type", "name"} if name == "survey" else ({"list_name", "name"} if name == "choices" else ({"form_id", "form_title", "version"} if name == "settings" else set()))
                if expected:
                    result[name] = _table_from_rows(rows, expected)
                else:
                    result[name] = ([], [])
            return result
    except (zipfile.BadZipFile, OSError):
        return {}


def xlsform_settings(path: Path) -> dict[str, str]:
    _, rows = workbook_tables(path).get("settings", ([], []))
    return rows[0] if rows else {}


def slug(value: str, fallback: str = "item") -> str:
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "_", value).strip("._-")
    return cleaned or fallback


def snake(value: str, fallback: str = "form") -> str:
    cleaned = re.sub(r"[^A-Za-z0-9]+", "_", value).strip("_").lower()
    cleaned = re.sub(r"_+", "_", cleaned)
    if not cleaned or not cleaned[0].isalpha():
        cleaned = f"{fallback}_{cleaned}".strip("_")
    return cleaned


def humanize(value: str) -> str:
    text = re.sub(r"[._-]+", " ", value).strip()
    return " ".join(word[:1].upper() + word[1:] for word in text.split()) or "Other"


def form_name_from_file(path: Path) -> str:
    stem = re.sub(r"^example_odk[_ .-]*", "", path.stem, flags=re.I)
    return humanize(stem)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


@dataclass(frozen=True)
class SourceForm:
    module_id: str
    module_name: str
    path: Path
    relative_path: str


def _workbook_sheet_names(path: Path) -> set[str]:
    try:
        with zipfile.ZipFile(path) as zf:
            return set(_sheet_paths(zf))
    except (zipfile.BadZipFile, OSError):
        return set()


def _looks_like_xlsform(path: Path) -> bool:
    names = _workbook_sheet_names(path)
    return "survey" in names and bool(names & {"settings", "choices"})


def discover(source_root: Path, include_all_xlsx: bool) -> list[SourceForm]:
    modules_root = source_root / "modules"
    found: list[SourceForm] = []
    if modules_root.is_dir():
        for module_dir in sorted(p for p in modules_root.iterdir() if p.is_dir()):
            docs = module_dir / "docs"
            if not docs.is_dir():
                continue
            for form in sorted(docs.rglob("*.xlsx")):
                if form.name.startswith("~$"):
                    continue
                canonical = form.name.lower().startswith("example_odk")
                if not include_all_xlsx and not canonical and not _looks_like_xlsform(form):
                    continue
                found.append(SourceForm(module_dir.name, humanize(module_dir.name), form, form.relative_to(source_root).as_posix()))

    captured = {item.path.resolve() for item in found}
    for docs in sorted(p for p in source_root.rglob("docs") if p.is_dir()):
        if modules_root in docs.parents or docs == modules_root or "ui" in docs.parts:
            continue
        for form in sorted(docs.rglob("*.xlsx")):
            if form.resolve() in captured or form.name.startswith("~$"):
                continue
            canonical = form.name.lower().startswith("example_odk")
            if not include_all_xlsx and not canonical and not _looks_like_xlsform(form):
                continue
            owner = docs.parent.name
            found.append(SourceForm(owner, humanize(owner), form, form.relative_to(source_root).as_posix()))
    return sorted(found, key=lambda x: (x.module_id.lower(), x.path.name.lower()))


def issue(severity: str, code: str, message: str, *, location: str = "", suggestion: str = "", source: str = "MethodMesh XLSForm lint", details: str = "") -> dict:
    return {
        "severity": severity,
        "code": code,
        "message": message,
        "location": location,
        "suggestion": suggestion,
        "source": source,
        "details": details,
    }


def static_validate(item: SourceForm, settings: dict[str, str]) -> list[dict]:
    problems: list[dict] = []
    tables = workbook_tables(item.path)
    sheets = set(tables)
    module_snake = snake(item.module_id, "module")

    if not sheets:
        return [issue("error", "WORKBOOK_INVALID", "The XLSX workbook could not be parsed.", location=item.relative_path, suggestion="Open and resave the workbook as a valid .xlsx file.")]
    if "survey" not in sheets:
        problems.append(issue("error", "SURVEY_SHEET_MISSING", "XLSForm has no survey sheet.", location="survey", suggestion="Add a survey sheet using the XLSForm standard."))
    if "settings" not in sheets:
        problems.append(issue("error", "SETTINGS_SHEET_MISSING", "MethodMesh XLSForms must carry a settings sheet with stable form metadata.", location="settings", suggestion="Add form_title, form_id, and version in a settings sheet."))

    canonical_filename = CANONICAL_FILENAME.match(item.path.name) is not None
    if not canonical_filename:
        proposed = f"example_odk_{snake(form_name_from_file(item.path))}.xlsx"
        problems.append(issue(
            "style", "FILENAME_NONSTANDARD",
            f"Filename '{item.path.name}' does not follow the MethodMesh XLSForm naming convention.",
            location=item.relative_path,
            suggestion=f"Use lower-snake-case names such as '{proposed}'. Rename the repository file only after checking references.",
        ))

    form_id = settings.get("form_id", "").strip()
    title = settings.get("form_title", "").strip()
    version = settings.get("version", "").strip()
    if not title:
        problems.append(issue("error", "FORM_TITLE_MISSING", "settings.form_title is blank.", location="settings.form_title", suggestion="Give the form a concise human-readable title."))
    if not form_id:
        problems.append(issue("error", "FORM_ID_MISSING", "settings.form_id is blank.", location="settings.form_id", suggestion=f"Use a stable lower-snake-case ID, preferably beginning '{module_snake}_'."))
    else:
        if not LOWER_SNAKE.match(form_id):
            problems.append(issue("warning", "FORM_ID_NONSTANDARD", f"form_id '{form_id}' is not lower_snake_case.", location="settings.form_id", suggestion="Do not automatically rename a deployed form_id. For new forms use lower_snake_case."))
        elif not form_id.startswith(module_snake + "_") and form_id != module_snake:
            problems.append(issue("style", "FORM_ID_MODULE_PREFIX", f"form_id '{form_id}' does not identify its owning module.", location="settings.form_id", suggestion=f"For new forms prefer IDs beginning '{module_snake}_'. Existing deployed IDs are compatibility contracts and should not be changed casually."))
    if not version:
        problems.append(issue("warning", "FORM_VERSION_MISSING", "settings.version is blank.", location="settings.version", suggestion="Use an incrementing version. MethodMesh recommends YYYYMMDDrr for new forms."))
    elif not re.fullmatch(r"\d{10}", version):
        problems.append(issue("style", "FORM_VERSION_NONSTANDARD", f"version '{version}' does not use the MethodMesh YYYYMMDDrr convention.", location="settings.version", suggestion="ODK accepts other version strings, but MethodMesh recommends a 10-digit YYYYMMDDrr value for consistency."))

    survey_headers, survey_rows = tables.get("survey", ([], []))
    if survey_headers:
        for required in ("type", "name"):
            if required not in survey_headers:
                problems.append(issue("error", "SURVEY_HEADER_MISSING", f"survey sheet is missing required '{required}' column.", location=f"survey.{required}"))
    names: list[str] = []
    referenced_lists: set[str] = set()
    # ODK/XLSForm node names are scoped by their parent group/repeat path. Reusing the
    # same leaf name in separate sibling groups is valid and is a deliberate MethodMesh
    # pattern for canonical return keys. Only duplicates within the same parent scope
    # are blocking. Row numbers are used as internal scope identities so an already-invalid
    # duplicate group name does not cause a cascade of false duplicate findings below it.
    group_stack: list[tuple[str, str, int]] = []
    names_by_scope: dict[tuple[int, ...], dict[str, int]] = {}
    expressions: list[tuple[str, str, int]] = []

    def scope_key() -> tuple[int, ...]:
        return tuple(entry[2] for entry in group_stack)

    def scope_label() -> str:
        labels = [entry[1] or f"<unnamed@{entry[2]}>" for entry in group_stack]
        return "/" + "/".join(labels) if labels else "/ (root)"

    for row_index, row in enumerate(survey_rows, start=2):
        qtype = row.get("type", "").strip()
        name = row.get("name", "").strip()
        qtype_lower = qtype.lower()
        if not qtype:
            continue
        if qtype_lower not in {"end_group", "end group", "end_repeat", "end repeat"} and not name:
            problems.append(issue("error", "SURVEY_NAME_MISSING", f"Survey row {row_index} has type '{qtype}' but no name.", location=f"survey row {row_index}"))
        if name:
            names.append(name)
            if any(ch.isspace() for ch in name):
                problems.append(issue("error", "SURVEY_NAME_SPACE", f"Field name '{name}' contains whitespace.", location=f"survey.name row {row_index}", suggestion="ODK field names must not contain spaces."))

            # Register the node in its *parent* scope before opening a begin_group or
            # begin_repeat. The same name may therefore appear in another group path, but
            # not twice as siblings in this one.
            parent_key = scope_key()
            scoped_names = names_by_scope.setdefault(parent_key, {})
            first_row = scoped_names.get(name)
            if first_row is not None:
                problems.append(issue(
                    "error",
                    "SURVEY_NAME_DUPLICATE",
                    f"Field/group name '{name}' appears more than once in the same group scope.",
                    location=f"survey.name row {row_index}",
                    suggestion="Names only need to be unique among siblings in the same group/repeat. Reuse in separate groups is valid.",
                    details=f"Scope: {scope_label()}; first occurrence: survey row {first_row}; duplicate: survey row {row_index}",
                ))
            else:
                scoped_names[name] = row_index

        if qtype_lower in {"begin_group", "begin group"}:
            group_stack.append(("group", name, row_index))
        elif qtype_lower in {"begin_repeat", "begin repeat"}:
            group_stack.append(("repeat", name, row_index))
        elif qtype_lower in {"end_group", "end group"}:
            if not group_stack or group_stack[-1][0] != "group":
                problems.append(issue("error", "GROUP_UNBALANCED", f"end_group at survey row {row_index} does not match an open group.", location=f"survey row {row_index}"))
            else:
                group_stack.pop()
        elif qtype_lower in {"end_repeat", "end repeat"}:
            if not group_stack or group_stack[-1][0] != "repeat":
                problems.append(issue("error", "REPEAT_UNBALANCED", f"end_repeat at survey row {row_index} does not match an open repeat.", location=f"survey row {row_index}"))
            else:
                group_stack.pop()
        match = SELECT_RE.match(qtype)
        if match and not qtype_lower.startswith("select_one_from_file") and not qtype_lower.startswith("select_multiple_from_file"):
            referenced_lists.add(match.group(1))
        for col, value in row.items():
            if col in EXPRESSION_COLUMNS and value:
                expressions.append((col, value, row_index))
    if group_stack:
        problems.append(issue("error", "GROUP_STRUCTURE_UNCLOSED", f"Survey ends with {len(group_stack)} unclosed group/repeat block(s).", location="survey"))

    known_names = set(names)
    for col, expression, row_index in expressions:
        for ref in VAR_REF_RE.findall(expression):
            if ref not in known_names:
                problems.append(issue("error", "UNKNOWN_FIELD_REFERENCE", f"Expression references unknown field '${{{ref}}}'.", location=f"survey.{col} row {row_index}", details=expression))
        funcs = {m.group(1).lower() for m in FUNCTION_RE.finditer(expression)}
        for func in sorted(funcs & set(KNOWN_UNSUPPORTED_FUNCTIONS)):
            problems.append(issue("error", "UNSUPPORTED_XPATH_FUNCTION", f"Expression uses unsupported XPath function '{func}()'.", location=f"survey.{col} row {row_index}", suggestion=KNOWN_UNSUPPORTED_FUNCTIONS[func], details=expression))

    choice_headers, choice_rows = tables.get("choices", ([], []))
    choice_lists: set[str] = set()
    choice_pairs: list[tuple[str, str]] = []
    if choice_rows or choice_headers:
        for required in ("list_name", "name"):
            if required not in choice_headers:
                problems.append(issue("error", "CHOICES_HEADER_MISSING", f"choices sheet is missing required '{required}' column.", location=f"choices.{required}"))
        for row_index, row in enumerate(choice_rows, start=2):
            list_name = row.get("list_name", "").strip()
            name = row.get("name", "").strip()
            if list_name:
                choice_lists.add(list_name)
            if list_name and name:
                choice_pairs.append((list_name, name))
            if any(ch.isspace() for ch in name):
                problems.append(issue("error", "CHOICE_NAME_SPACE", f"Choice name '{name}' contains whitespace.", location=f"choices.name row {row_index}", suggestion="Choice names must not contain spaces."))
        duplicate_choices = sorted({pair for pair in choice_pairs if choice_pairs.count(pair) > 1})
        for list_name, name in duplicate_choices:
            problems.append(issue("error", "CHOICE_DUPLICATE", f"Choice '{name}' is duplicated in list '{list_name}'.", location="choices"))
    if referenced_lists and not choice_rows:
        problems.append(issue("error", "CHOICES_SHEET_REQUIRED", "Survey uses internal select lists but no readable choices sheet was found.", location="choices"))
    for missing in sorted(referenced_lists - choice_lists):
        problems.append(issue("error", "CHOICE_LIST_MISSING", f"Survey references choice list '{missing}' but it is not defined in choices.", location="choices.list_name"))

    return problems


def authoritative_validate(path: Path, enabled: bool) -> tuple[bool, str, list[dict]]:
    if not enabled:
        return False, "disabled", []
    cli = shutil.which("xls2xform")
    if not cli:
        return False, "pyxform/ODK Validate unavailable", []
    try:
        proc = subprocess.run([cli, str(path), "--json"], capture_output=True, text=True, timeout=180)
    except Exception as exc:
        return True, "pyxform + ODK Validate", [issue("warning", "VALIDATOR_EXECUTION_FAILED", "The authoritative validator could not be executed.", source="pyxform + ODK Validate", details=str(exc))]
    raw = (proc.stdout or "").strip() or (proc.stderr or "").strip()
    problems: list[dict] = []
    parsed = None
    if raw:
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            parsed = None
    if proc.returncode != 0:
        message = "ODK validation failed."
        details = raw
        if isinstance(parsed, dict):
            message = str(parsed.get("message") or parsed.get("error") or message)
            details = json.dumps(parsed, indent=2, ensure_ascii=False)
        elif raw:
            first = next((line.strip() for line in raw.splitlines() if line.strip()), message)
            message = first[:500]
        problems.append(issue("error", "ODK_VALIDATE_FAILED", message, source="pyxform + ODK Validate", details=details))
    elif isinstance(parsed, dict):
        warnings = parsed.get("warnings") or []
        if isinstance(warnings, str):
            warnings = [warnings]
        for warning in warnings:
            problems.append(issue("warning", "PYXFORM_WARNING", str(warning), source="pyxform"))
    return True, "pyxform + ODK Validate", problems


def build_entry(item: SourceForm, asset_path: str, authoritative: bool) -> dict:
    settings = xlsform_settings(item.path)
    file_fallback = form_name_from_file(item.path)
    display_name = settings.get("form_title", "").strip() or file_fallback
    form_id = settings.get("form_id", "").strip()
    version = settings.get("version", "").strip()
    stable_id = f"{slug(item.module_id)}.{slug(item.path.stem)}"
    problems = static_validate(item, settings)
    auth_available, auth_engine, auth_issues = authoritative_validate(item.path, authoritative)
    problems.extend(auth_issues)
    return {
        "id": stable_id,
        "moduleId": item.module_id,
        "moduleName": item.module_name,
        "displayName": display_name,
        "description": "",
        "sourceFileName": item.path.name,
        "sourceRelativePath": item.relative_path,
        "centralFormId": form_id or stable_id,
        "formVersion": version,
        "assetPath": asset_path,
        "sha256": sha256(item.path),
        "capabilityIds": [],
        "tags": [],
        "validation": {
            "authoritativeAvailable": auth_available,
            "engine": auth_engine,
            "issues": problems,
        },
    }


def _global_validation(entries: list[dict]) -> None:
    by_form_id: dict[str, list[dict]] = {}
    by_title: dict[str, list[dict]] = {}
    for entry in entries:
        form_id = entry.get("centralFormId", "").strip()
        title = entry.get("displayName", "").strip().lower()
        if form_id:
            by_form_id.setdefault(form_id, []).append(entry)
        if title:
            by_title.setdefault(title, []).append(entry)
    for form_id, group in by_form_id.items():
        if len(group) <= 1:
            continue
        locations = ", ".join(e["sourceRelativePath"] for e in group)
        for entry in group:
            entry["validation"]["issues"].append(issue("error", "FORM_ID_DUPLICATE", f"form_id '{form_id}' is used by {len(group)} MethodMesh XLSForms.", location="settings.form_id", suggestion="Every form_id must be globally unique. Preserve already-deployed IDs and resolve the collision deliberately.", details=locations))
    for title, group in by_title.items():
        if len(group) <= 1:
            continue
        for entry in group:
            entry["validation"]["issues"].append(issue("style", "FORM_TITLE_DUPLICATE", f"Form title '{entry['displayName']}' is used by more than one template.", location="settings.form_title", suggestion="Use distinct human-readable titles so testers can identify the intended form."))


def generate(source_root: Path, assets_root: Path, include_all_xlsx: bool, authoritative: bool) -> tuple[int, int, dict]:
    forms = discover(source_root, include_all_xlsx)
    catalog_root = assets_root / "methodmesh" / "odk_templates"
    if catalog_root.exists():
        shutil.rmtree(catalog_root)
    catalog_root.mkdir(parents=True, exist_ok=True)

    entries: list[dict] = []
    per_module: dict[str, int] = {}
    for item in forms:
        target_dir = catalog_root / slug(item.module_id)
        target_dir.mkdir(parents=True, exist_ok=True)
        target = target_dir / item.path.name
        shutil.copy2(item.path, target)
        entries.append(build_entry(item, target.relative_to(assets_root).as_posix(), authoritative))
        per_module[item.module_id] = per_module.get(item.module_id, 0) + 1
    _global_validation(entries)

    severity_counts = {"error": 0, "warning": 0, "style": 0, "info": 0}
    forms_needing_revision = 0
    authoritative_count = 0
    for entry in entries:
        issues = entry["validation"]["issues"]
        if issues:
            forms_needing_revision += 1
        if entry["validation"]["authoritativeAvailable"]:
            authoritative_count += 1
        for finding in issues:
            sev = finding.get("severity", "info")
            severity_counts[sev] = severity_counts.get(sev, 0) + 1
    summary = {
        "formsNeedingRevision": forms_needing_revision,
        "cleanForms": len(entries) - forms_needing_revision,
        "severityCounts": severity_counts,
        "authoritativeValidatedForms": authoritative_count,
        "authoritativeValidationAvailable": bool(entries) and authoritative_count == len(entries),
        "engine": "pyxform + ODK Validate + MethodMesh XLSForm lint" if entries and authoritative_count == len(entries) else "MethodMesh XLSForm lint",
    }
    index = {
        "schema": "methodmesh.odk_template_index.v3",
        "generatedFrom": source_root.as_posix(),
        "templateCount": len(entries),
        "moduleCount": len(per_module),
        "validationSummary": summary,
        "templates": entries,
    }
    (catalog_root / "index.json").write_text(json.dumps(index, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return len(entries), len(per_module), summary


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, default=Path("src/main/java/com/example/methodmesh"))
    parser.add_argument("--assets-root", type=Path, default=Path("src/main/assets"))
    parser.add_argument("--all-xlsx", action="store_true", help="Include every XLSX in docs/ rather than detected XLSForms only.")
    parser.add_argument("--no-authoritative-validation", action="store_true", help="Skip optional pyxform + ODK Validate even if xls2xform is installed.")
    args = parser.parse_args(argv)
    source_root = args.source_root.resolve()
    assets_root = args.assets_root.resolve()
    if not source_root.is_dir():
        parser.error(f"source root does not exist: {source_root}")
    assets_root.mkdir(parents=True, exist_ok=True)
    count, modules, summary = generate(source_root, assets_root, args.all_xlsx, not args.no_authoritative_validation)
    print(f"Generated {count} ODK template(s) from {modules} owner(s) into {assets_root / 'methodmesh/odk_templates'}")
    print(f"Validation: {summary['cleanForms']} clean, {summary['formsNeedingRevision']} need revision, {summary['severityCounts'].get('error', 0)} error(s).")
    if not summary["authoritativeValidationAvailable"]:
        print("Authoritative pyxform/ODK Validate was not available for every form. Install pyxform on the build machine for full validation.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
