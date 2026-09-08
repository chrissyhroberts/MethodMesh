#!/usr/bin/env python3
"""
Safely remove legacy/duplicate MethodMesh XLSForm filenames after installation
of the reviewed 2026-09-07 XLSForm set.

Deletion rule:
  * file is an XLSForm under a module docs/ directory;
  * file is NOT one of the 423 reviewed canonical target paths;
  * its XLSForm settings form_id is present;
  * exactly one reviewed XLSForm in the SAME MODULE has the same form_id;
  * the reviewed canonical file actually exists;
  * the legacy file has no staged, unstaged, or untracked Git changes.

Every removed file is backed up outside the repository first.

Files with missing/ambiguous form_id, no reviewed identity match, or local Git
changes are retained and reported. Nothing else is modified.
"""

from pathlib import Path, PurePosixPath
from zipfile import ZipFile
from xml.etree import ElementTree as ET
import argparse
import datetime as dt
import json
import shutil
import subprocess
import sys

SCRIPT_DIR = Path(__file__).resolve().parent
MANIFEST_PATH = SCRIPT_DIR / "MANIFEST.json"
TARGET_BASE_REL = Path("app/src/main/java/com/example/methodmesh/modules")

NS_MAIN = {"x": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
NS_REL = {"r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships"}
PKG_REL = {"p": "http://schemas.openxmlformats.org/package/2006/relationships"}


def run_git(repo: Path, args):
    proc = subprocess.run(
        ["git", *args],
        cwd=repo,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr.strip() or f"git {' '.join(args)} failed")
    return [line.strip() for line in proc.stdout.splitlines() if line.strip()]


def find_repo(start: Path) -> Path:
    start = start.resolve()
    for c in [start, *start.parents]:
        if (c / TARGET_BASE_REL).is_dir() and (
            (c / ".git").exists()
            or (c / "settings.gradle.kts").exists()
            or (c / "settings.gradle").exists()
        ):
            return c
    raise RuntimeError("Could not locate MethodMesh repository; use --repo PATH.")


def dirty_paths(repo: Path):
    scope = TARGET_BASE_REL.as_posix()
    dirty = set()
    for args in (
        ["diff", "--name-only", "--", scope],
        ["diff", "--cached", "--name-only", "--", scope],
        ["ls-files", "--others", "--exclude-standard", "--", scope],
    ):
        dirty.update(PurePosixPath(x).as_posix() for x in run_git(repo, args))
    return dirty


def col_index(cell_ref: str) -> int:
    n = 0
    for ch in cell_ref:
        if not ch.isalpha():
            break
        n = n * 26 + (ord(ch.upper()) - 64)
    return n - 1


def read_shared_strings(z: ZipFile):
    try:
        root = ET.fromstring(z.read("xl/sharedStrings.xml"))
    except KeyError:
        return []
    vals = []
    for si in root.findall("x:si", NS_MAIN):
        parts = []
        for t in si.iter("{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t"):
            parts.append(t.text or "")
        vals.append("".join(parts))
    return vals


def sheet_path(z: ZipFile, wanted="settings"):
    wb = ET.fromstring(z.read("xl/workbook.xml"))
    rels = ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))
    relmap = {r.attrib["Id"]: r.attrib["Target"] for r in rels.findall("p:Relationship", PKG_REL)}
    for sheet in wb.find("x:sheets", NS_MAIN):
        name = sheet.attrib.get("name", "")
        if name.lower() == wanted.lower():
            rid = sheet.attrib.get("{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id")
            target = relmap[rid].lstrip("/")
            if not target.startswith("xl/"):
                target = "xl/" + target
            return target
    return None


def cell_value(cell, shared):
    typ = cell.attrib.get("t")
    if typ == "inlineStr":
        is_el = cell.find("x:is", NS_MAIN)
        if is_el is None:
            return ""
        return "".join((t.text or "") for t in is_el.iter(
            "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t"
        ))
    v = cell.find("x:v", NS_MAIN)
    if v is None:
        f = cell.find("x:f", NS_MAIN)
        return "" if f is None else (f.text or "")
    raw = v.text or ""
    if typ == "s":
        try:
            return shared[int(raw)]
        except Exception:
            return raw
    return raw


def xlsform_settings(path: Path):
    try:
        with ZipFile(path, "r") as z:
            shared = read_shared_strings(z)
            sp = sheet_path(z, "settings")
            if not sp:
                return {"form_id": "", "form_title": "", "version": "", "_error": "no settings sheet"}
            root = ET.fromstring(z.read(sp))
            rows = []
            data = root.find("x:sheetData", NS_MAIN)
            if data is None:
                return {"form_id": "", "form_title": "", "version": "", "_error": "empty settings sheet"}
            for row in data.findall("x:row", NS_MAIN):
                vals = {}
                for c in row.findall("x:c", NS_MAIN):
                    ref = c.attrib.get("r", "")
                    vals[col_index(ref)] = cell_value(c, shared)
                if vals:
                    maxcol = max(vals)
                    rows.append([vals.get(i, "") for i in range(maxcol + 1)])
            keys = {"form_id", "form_title", "version"}
            for i, row in enumerate(rows[:10]):
                normalized = [str(v).strip().lower() for v in row]
                if "form_id" in normalized:
                    headers = {str(v).strip().lower(): j for j, v in enumerate(row)}
                    for datarow in rows[i + 1:]:
                        if any(str(x).strip() for x in datarow):
                            out = {}
                            for k in keys:
                                j = headers.get(k)
                                out[k] = str(datarow[j]).strip() if j is not None and j < len(datarow) else ""
                            out["_error"] = ""
                            return out
            return {"form_id": "", "form_title": "", "version": "", "_error": "form_id header not found"}
    except Exception as e:
        return {"form_id": "", "form_title": "", "version": "", "_error": str(e)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", type=Path)
    ap.add_argument("--apply", action="store_true", help="Back up and remove verified legacy duplicates")
    args = ap.parse_args()

    repo = args.repo.resolve() if args.repo else find_repo(Path.cwd())
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    reviewed = manifest["files"]
    reviewed_paths = {PurePosixPath(r["target_path"]).as_posix(): r for r in reviewed}

    # Current reviewed identity index, deliberately built from the files installed
    # in the user's repository rather than trusting filenames alone.
    by_module_form_id = {}
    missing_reviewed = []
    reviewed_meta = {}

    for rec in reviewed:
        rel = PurePosixPath(rec["target_path"]).as_posix()
        p = repo / Path(rel)
        if not p.is_file():
            missing_reviewed.append(rel)
            continue
        meta = xlsform_settings(p)
        reviewed_meta[rel] = meta
        fid = meta.get("form_id", "")
        module = rec["module"]
        if fid:
            by_module_form_id.setdefault((module, fid), []).append(rel)

    if missing_reviewed:
        print("ERROR: reviewed set is incomplete; refusing cleanup.")
        for p in missing_reviewed:
            print("  missing:", p)
        return 2

    dirty = dirty_paths(repo)

    all_xlsx = []
    base = repo / TARGET_BASE_REL
    for p in base.rglob("*.xlsx"):
        rel = p.relative_to(repo).as_posix()
        # Only module-owned docs workbooks.
        parts = PurePosixPath(rel).parts
        if len(parts) >= 10 and parts[-2] == "docs":
            all_xlsx.append((p, rel))

    extras = [(p, rel) for p, rel in all_xlsx if rel not in reviewed_paths]

    verified = []
    skipped_dirty = []
    no_identity = []
    no_match = []
    ambiguous = []

    for p, rel in sorted(extras, key=lambda x: x[1]):
        parts = PurePosixPath(rel).parts
        try:
            module = parts[parts.index("modules") + 1]
        except Exception:
            no_match.append((rel, "cannot determine module"))
            continue

        meta = xlsform_settings(p)
        fid = meta.get("form_id", "")
        if not fid:
            no_identity.append((rel, meta.get("_error", "") or "blank form_id"))
            continue

        matches = by_module_form_id.get((module, fid), [])
        if len(matches) == 0:
            no_match.append((rel, fid))
            continue
        if len(matches) > 1:
            ambiguous.append((rel, fid, matches))
            continue

        canonical = matches[0]
        if rel in dirty:
            skipped_dirty.append((rel, canonical, fid))
            continue

        verified.append((rel, canonical, fid, meta, reviewed_meta.get(canonical, {})))

    stamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_root = repo.parent / f"{repo.name}_ODK_legacy_XLSForm_backup_{stamp}"
    report_path = repo.parent / f"{repo.name}_ODK_legacy_XLSForm_cleanup_{stamp}.txt"

    lines = [
        "MethodMesh legacy XLSForm cleanup",
        f"Repository: {repo}",
        f"Mode: {'APPLY' if args.apply else 'DRY RUN'}",
        f"Reviewed canonical set: {len(reviewed_paths)}",
        f"Extra XLSForms found: {len(extras)}",
        f"Verified same-form_id legacy duplicates: {len(verified)}",
        f"Locally dirty duplicates retained: {len(skipped_dirty)}",
        f"Missing/invalid form_id retained: {len(no_identity)}",
        f"No reviewed same-form_id match retained: {len(no_match)}",
        f"Ambiguous identity retained: {len(ambiguous)}",
        "",
    ]

    if verified:
        lines.append("VERIFIED LEGACY DUPLICATES:")
        for rel, canonical, fid, oldm, newm in verified:
            lines.append(f"  REMOVE {rel}")
            lines.append(f"      -> {canonical}")
            lines.append(f"      form_id={fid}")
            if oldm.get("version") or newm.get("version"):
                lines.append(f"      version old={oldm.get('version','')} canonical={newm.get('version','')}")
        lines.append("")

    if skipped_dirty:
        lines.append("RETAINED — LOCALLY DIRTY:")
        for rel, canonical, fid in skipped_dirty:
            lines.append(f"  {rel} -> {canonical} [form_id={fid}]")
        lines.append("")

    if no_identity:
        lines.append("RETAINED — NO USABLE FORM_ID:")
        for rel, why in no_identity:
            lines.append(f"  {rel} [{why}]")
        lines.append("")

    if no_match:
        lines.append("RETAINED — NO SAME-MODULE REVIEWED FORM_ID MATCH:")
        for rel, why in no_match:
            lines.append(f"  {rel} [{why}]")
        lines.append("")

    if ambiguous:
        lines.append("RETAINED — AMBIGUOUS:")
        for rel, fid, matches in ambiguous:
            lines.append(f"  {rel} [form_id={fid}]")
            for m in matches:
                lines.append(f"      candidate: {m}")
        lines.append("")

    if args.apply:
        for rel, canonical, fid, oldm, newm in verified:
            src = repo / Path(rel)
            backup = backup_root / Path(rel)
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, backup)
            src.unlink()
        lines.append(f"Removed: {len(verified)}")
        if verified:
            lines.append(f"Backup: {backup_root}")
        lines.append("")

    lines.append("No canonical reviewed XLSForm, Kotlin file, Markdown file, or generated asset was modified.")
    report = "\n".join(lines) + "\n"
    print(report)

    if args.apply:
        report_path.write_text(report, encoding="utf-8")
        print(f"Cleanup report: {report_path}")

    if not args.apply and verified:
        print("Dry run only. Re-run with --apply to remove exactly the verified duplicates.")

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        raise SystemExit(1)
