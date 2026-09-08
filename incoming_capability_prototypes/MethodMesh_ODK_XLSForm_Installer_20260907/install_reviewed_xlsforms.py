#!/usr/bin/env python3
"""
Install the reviewed 2026-09-07 MethodMesh XLSForms into the canonical
module docs folders without touching Kotlin or other module files.

Default behaviour is conservative:
  * installs new reviewed XLSForms;
  * replaces differing reviewed XLSForms only when the target is not dirty in Git;
  * skips any reviewed target with staged, unstaged, or untracked local changes;
  * backs up every replaced target outside the repository;
  * never deletes extra XLSForms;
  * verifies SHA-256 for the full 423-file payload before changing anything.

Use --overwrite-dirty only after deciding that the reviewed workbook should
replace a locally modified workbook. The local file is still backed up first.
"""

from pathlib import Path, PurePosixPath
import argparse
import datetime as dt
import hashlib
import json
import shutil
import subprocess
import sys

SCRIPT_DIR = Path(__file__).resolve().parent
PAYLOAD_ROOT = SCRIPT_DIR / "payload"
MANIFEST_PATH = SCRIPT_DIR / "MANIFEST.json"
TARGET_BASE_REL = Path("app/src/main/java/com/example/methodmesh/modules")


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


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
    candidates = [start, *start.parents]
    for c in candidates:
        if (c / TARGET_BASE_REL).is_dir() and (
            (c / ".git").exists()
            or (c / "settings.gradle.kts").exists()
            or (c / "settings.gradle").exists()
        ):
            return c
    raise RuntimeError(
        "Could not locate the MethodMesh repository. Run this from the repository "
        "root, or pass --repo /path/to/MethodMesh."
    )


def dirty_paths(repo: Path):
    scope = TARGET_BASE_REL.as_posix()
    dirty = set()
    for args in (
        ["diff", "--name-only", "--", scope],
        ["diff", "--cached", "--name-only", "--", scope],
        ["ls-files", "--others", "--exclude-standard", "--", scope],
    ):
        for line in run_git(repo, args):
            dirty.add(PurePosixPath(line).as_posix())
    return dirty


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, help="MethodMesh repository root")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument(
        "--overwrite-dirty",
        action="store_true",
        help="Back up and replace reviewed XLSForms even if Git reports local changes",
    )
    args = parser.parse_args()

    repo = args.repo.resolve() if args.repo else find_repo(Path.cwd())
    if not (repo / TARGET_BASE_REL).is_dir():
        raise RuntimeError(f"Not a MethodMesh repository: {repo}")

    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    files = manifest["files"]
    if len(files) != 423:
        raise RuntimeError(f"Installer manifest should contain 423 XLSForms, found {len(files)}")

    # Verify the entire payload before writing anything.
    for rec in files:
        src = PAYLOAD_ROOT / Path(rec["source_path"])
        if not src.is_file():
            raise RuntimeError(f"Payload file missing: {rec['source_path']}")
        got = sha256(src)
        if got != rec["sha256"]:
            raise RuntimeError(f"Payload hash mismatch: {rec['source_path']}")

    dirty = dirty_paths(repo)

    stamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_root = repo.parent / f"{repo.name}_ODK_XLSForm_backup_{stamp}"
    report_path = repo.parent / f"{repo.name}_ODK_XLSForm_install_{stamp}.txt"

    new = []
    replaced = []
    unchanged = []
    skipped_dirty = []

    for rec in files:
        src = PAYLOAD_ROOT / Path(rec["source_path"])
        target_rel = PurePosixPath(rec["target_path"]).as_posix()
        dst = repo / Path(target_rel)

        if dst.exists() and sha256(dst) == rec["sha256"]:
            unchanged.append(target_rel)
            continue

        if target_rel in dirty and not args.overwrite_dirty:
            skipped_dirty.append(target_rel)
            continue

        if dst.exists():
            replaced.append(target_rel)
            if not args.dry_run:
                backup = backup_root / Path(target_rel)
                backup.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(dst, backup)
        else:
            new.append(target_rel)

        if not args.dry_run:
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dst)

    reviewed_targets = {PurePosixPath(r["target_path"]).as_posix() for r in files}
    current_xlsx = set()
    target_base = repo / TARGET_BASE_REL
    if target_base.exists():
        for p in target_base.rglob("*.xlsx"):
            try:
                rel = p.relative_to(repo).as_posix()
            except ValueError:
                continue
            current_xlsx.add(rel)
    extras = sorted(current_xlsx - reviewed_targets)

    lines = [
        "MethodMesh reviewed XLSForm installation",
        f"Repository: {repo}",
        f"Mode: {'DRY RUN' if args.dry_run else 'INSTALL'}",
        f"Reviewed payload: {len(files)} XLSForms "
        f"({manifest['showcase_xlsforms']} showcases + {manifest['broader_xlsforms']} broader examples)",
        f"Modules represented: {manifest['modules_with_xlsforms']}",
        "",
        f"New: {len(new)}",
        f"Replaced: {len(replaced)}",
        f"Unchanged: {len(unchanged)}",
        f"Skipped because locally dirty: {len(skipped_dirty)}",
        f"Extra existing XLSForms not in reviewed set: {len(extras)}",
        "",
    ]

    if skipped_dirty:
        lines += [
            "REVIEW REQUIRED — locally dirty reviewed targets were NOT overwritten:",
            *[f"  {p}" for p in skipped_dirty],
            "",
            "Re-run with --overwrite-dirty only if the reviewed copy should win.",
            "",
        ]

    if extras:
        lines += [
            "Extra XLSForms were left untouched (no automatic deletion):",
            *[f"  {p}" for p in extras],
            "",
        ]

    if replaced and not args.dry_run:
        lines += [f"Backup of replaced files: {backup_root}", ""]

    lines += [
        "No Kotlin, Markdown, generated assets, or other module files were modified by this installer.",
        "The installer never deletes XLSForms.",
    ]

    report = "\n".join(lines) + "\n"
    print(report)
    if not args.dry_run:
        report_path.write_text(report, encoding="utf-8")
        print(f"Install report: {report_path}")

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        raise SystemExit(1)
