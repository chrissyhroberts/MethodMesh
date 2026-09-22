from __future__ import annotations

import argparse
from pathlib import Path
import sys

from .compiler import compile_xlsform
from .errors import CompilerError


def _parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="mmxls", description="Compile ordinary ODK XLSForms into MethodMesh-authenticated/attested release forms.")
    sub = p.add_subparsers(dest="command", required=True)
    c = sub.add_parser("compile", help="Compile one XLSForm")
    c.add_argument("source", help="Source .xlsx XLSForm")
    c.add_argument("-o", "--out-dir", default="dist", help="Output directory (default: dist)")
    c.add_argument("--study-id", help="Override/add MethodMesh study ID")
    c.add_argument("--timestamp-policy", choices=["disabled", "preferred", "required"], help="Override timestamp policy")
    c.add_argument("--overwrite", action="store_true", help="Replace existing generated outputs")
    return p


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "compile":
            result = compile_xlsform(
                args.source, args.out_dir,
                study_id=args.study_id,
                timestamp_policy=args.timestamp_policy,
                overwrite=args.overwrite,
            )
            print("MethodMesh XLSForm build PASSED")
            print(f"  release:  {result.output_xlsx}")
            print(f"  manifest: {result.manifest_json}")
            print(f"  report:   {result.report_md}")
            if result.warnings:
                print("Warnings:")
                for w in result.warnings:
                    print(f"  - {w}")
            return 0
    except CompilerError as exc:
        print(f"MethodMesh XLSForm build FAILED: {exc}", file=sys.stderr)
        return 2
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
