#!/usr/bin/env python3
from __future__ import annotations
import argparse, re, sys
from dataclasses import dataclass
from pathlib import Path

CANONICAL = Path("docs/METHODMESH_MASTER_BOOK.md")
MODULE_ROOT = Path("app/src/main/java/com/example/methodmesh/modules")
ARCHIVE_ROOT = Path("docs/archive")
DOC_EXTS = {".md",".qmd",".rst",".adoc",".txt",".pdf",".docx",".html",".htm"}
FORBIDDEN_ACTIVE_DIRS = set()
ALLOWED_ROOT_DOCS = {Path("README.md")}
IGNORED_PREFIXES = (
    Path(".git"), Path(".gradle"), Path(".idea"), Path(".vscode"),
    Path("build"), Path("app/build"), Path("dist"), Path("out"),
    Path("target"), Path(".methodmesh-doc-review"),
    Path("app/src/main/assets/methodmesh/odk_templates"),

    # Generated/static documentation-site projection. These files are
    # distributable/reference output, not normative documentation sources.
    Path("docs/assets"),
    Path("docs/capabilities"),
    Path("docs/examples"),
    Path("docs/reference"),
    Path("docs/site_libs"),
    Path("docs/ANDROID_DEVELOPER_GUIDE.html"),
    Path("docs/about.html"),
    Path("docs/capabilities.html"),
    Path("docs/concepts.html"),
    Path("docs/getting-started.html"),
    Path("docs/index.html"),
    Path("docs/installation.html"),
    Path("docs/scheduling.html"),
    Path("docs/troubleshooting.html"),
    Path("docs/xlsform-integration.html"),
    Path("docs/search.json"),
    Path("docs/styles.css"),
)
PROJECT_WIDE_NAME_RE = re.compile(
    r"(master[_ -]?book|architecture[_ -]?standard|capability[_ -]?(writing|review|documentation)|"
    r"module[_ -]?(review|refresh).*manual|online[_ -]?data[_ -]?architecture|methodmesh[_ -]?philosophy|"
    r"conceptual[_ -]?model|intent[_ -]?registry|assertions?[_ -]?registry|observations?[_ -]?registry|"
    r"entity[_ -]?registry|trait[_ -]?registry|research[_ -]?intent[_ -]?language|ril[_ -]?core[_ -]?verbs|"
    r"spec[_ -]?status)", re.I
)
MODULE_LOCAL_NAME_RE = re.compile(
    r"(readme|changelog|build[_ -]?notes?|build[_ -]?report|roadmap[_ -]?note|validation|attribution|"
    r"third[_ -]?party[_ -]?notices?|implementation[_ -]?notes?|integration[_ -]?notes?|"
    r"capability[_ -]?handoff|ux[_ -]?review|test[_ -]?vectors?|drop[_ -]?in|package[_ -]?contents)", re.I
)

@dataclass
class Finding:
    severity: str
    code: str
    path: Path
    message: str

def under(path: Path, prefix: Path) -> bool:
    try:
        path.relative_to(prefix)
        return True
    except ValueError:
        return False

def ignored(path: Path) -> bool:
    return any(under(path, p) for p in IGNORED_PREFIXES)

def module_info(path: Path):
    try:
        r = path.relative_to(MODULE_ROOT)
    except ValueError:
        return None, None
    if len(r.parts) < 2:
        return None, None
    m = r.parts[0]
    return m, MODULE_ROOT / m

def collect(repo: Path, strict: bool):
    f = []
    if not (repo / CANONICAL).is_file():
        f.append(Finding("ERROR","MM-DOC-001",CANONICAL,"Canonical MethodMesh Master Book is missing."))

    for p in repo.rglob("*"):
        if not p.is_file():
            continue
        rp = p.relative_to(repo)
        if ignored(rp) or under(rp, ARCHIVE_ROOT):
            continue
        if "master" in p.name.lower() and "book" in p.name.lower() and rp != CANONICAL:
            f.append(Finding("ERROR","MM-DOC-002",rp,f"Duplicate/alternate Master Book; canonical path is {CANONICAL}."))

    for d in FORBIDDEN_ACTIVE_DIRS:
        dp = repo / d
        if dp.exists() and any(x.is_file() for x in dp.rglob("*")):
            f.append(Finding("ERROR","MM-DOC-003",d,"Parallel project-wide documentation tree exists; absorb useful doctrine into the Master Book and archive the source."))

    for p in repo.iterdir():
        if p.is_file():
            rp = p.relative_to(repo)
            if p.suffix.lower() in DOC_EXTS and rp not in ALLOWED_ROOT_DOCS:
                f.append(Finding("ERROR","MM-DOC-004",rp,"Project-root documentation is not allowed."))

    docs = repo / "docs"
    if docs.exists():
        for p in docs.iterdir():
            rp = p.relative_to(repo)
            if rp in {CANONICAL, ARCHIVE_ROOT} or p.name.startswith(".") or ignored(rp):
                continue
            if p.is_file() and p.suffix.lower() in DOC_EXTS:
                f.append(Finding("ERROR","MM-DOC-005",rp,"Standalone project-wide documentation beside the Master Book is not allowed."))
            elif p.is_dir():
                prose = [x for x in p.rglob("*") if x.is_file() and x.suffix.lower() in DOC_EXTS]
                if prose:
                    f.append(Finding("ERROR","MM-DOC-006",rp,"Additional documentation subtree exists under docs/."))

    for p in repo.rglob("*"):
        if not p.is_file():
            continue
        rp = p.relative_to(repo)
        if ignored(rp) or under(rp, ARCHIVE_ROOT) or rp == CANONICAL or p.suffix.lower() not in DOC_EXTS:
            continue
        module, mr = module_info(rp)
        in_module_docs = bool(mr and under(rp, mr / "docs"))
        if PROJECT_WIDE_NAME_RE.search(p.name) and not in_module_docs:
            f.append(Finding("ERROR","MM-DOC-007",rp,"Filename looks like project-wide doctrine outside the canonical Master Book/archive."))

    mr_abs = repo / MODULE_ROOT
    if mr_abs.exists():
        for p in mr_abs.rglob("*"):
            if not p.is_file() or p.suffix.lower() not in DOC_EXTS:
                continue
            rp = p.relative_to(repo)
            module, mr = module_info(rp)
            if not mr or under(rp, mr / "docs"):
                continue
            if MODULE_LOCAL_NAME_RE.search(p.name):
                f.append(Finding("ERROR" if strict else "WARN","MM-DOC-008",rp,f"Module documentation should live under {mr/'docs'}/."))

    return f

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=".")
    ap.add_argument("--strict", action="store_true")
    args = ap.parse_args()
    repo = Path(args.repo).resolve()
    if not (repo/"app").exists():
        print(f"ERROR: {repo} does not look like the MethodMesh repo root.", file=sys.stderr)
        return 2
    findings = collect(repo,args.strict)
    for x in findings:
        print(f"{x.severity} {x.code}: {x.path}\n  {x.message}")
    errors = sum(x.severity=="ERROR" for x in findings)
    warns = sum(x.severity=="WARN" for x in findings)
    print(f"MethodMesh documentation hygiene: {errors} error(s), {warns} warning(s).")
    return 1 if errors else 0

if __name__ == "__main__":
    raise SystemExit(main())
