from __future__ import annotations

import argparse
import sys

from .errors import CompilerError
from .sentinel import run_form_compile_task


def _parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="mmxls", description="Compile ordinary ODK XLSForms into MethodMesh-authenticated/attested release forms.")
    sub = p.add_subparsers(dest="command", required=True)
    c = sub.add_parser("compile", help="Compile one XLSForm")
    c.add_argument("source", help="Source .xlsx XLSForm")
    c.add_argument("-o", "--out-dir", default="dist", help="Output directory (default: dist)")
    c.add_argument("--study-id", help="Override/add MethodMesh study ID")
    c.add_argument("--timestamp-policy", choices=["disabled", "preferred", "required"], help="Override timestamp policy")
    c.add_argument("--overwrite", action="store_true", help="Compatibility flag; existing release bundles are never overwritten")
    return p


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "compile":
            task = run_form_compile_task(
                args.source,
                args.out_dir,
                study_id=args.study_id,
                timestamp_policy=args.timestamp_policy,
                overwrite=args.overwrite,
                request_source="cli",
            )
            result = task.compile_result
            print("MethodMesh XLSForm build PASSED")
            print(f"  release bundle: {result.release_dir}")
            print(f"  release XLSForm: {result.output_xlsx}")
            print(f"  preserved source: {result.source_copy}")
            print(f"  human manifest: {result.human_manifest_md}")
            print(f"  machine manifest: {result.manifest_json}")
            print(f"  build report: {result.report_md}")
            print(f"  Sentinel task event: {task.event_json}")
            print(f"  checksums: {result.checksums_sha256}")
            if result.warnings:
                print("Warnings:")
                for warning in result.warnings:
                    print(f"  - {warning}")
            return 0
    except CompilerError as exc:
        print(f"MethodMesh XLSForm build FAILED: {exc}", file=sys.stderr)
        return 2
    except Exception as exc:
        print(f"MethodMesh XLSForm build FAILED unexpectedly: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 3
    return 1
