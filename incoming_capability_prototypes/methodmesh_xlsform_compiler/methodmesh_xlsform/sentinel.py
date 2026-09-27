from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
import hashlib
import json
from pathlib import Path
import time
import uuid
from typing import Any

from . import __version__
from .compiler import CompileResult, compile_xlsform, _compiler_implementation_sha256, _sha256_file, _write_checksums

TASK_EVENT_SCHEMA = "methodmesh.sentinel.task_event.v1"
TASK_TYPE_FORM_COMPILE = "form.compile"
TASK_SCHEMA_VERSION = "1"


@dataclass
class SentinelTaskResult:
    request_id: str
    task_type: str
    status: str
    event_json: Path
    compile_result: CompileResult
    event: dict[str, Any]


def _now_local() -> datetime:
    return datetime.now().astimezone()


def _artifact(role: str, path: Path) -> dict[str, Any]:
    return {
        "role": role,
        "filename": path.name,
        "sha256": _sha256_file(path),
        "bytes": path.stat().st_size,
    }


def _write_json(path: Path, obj: dict[str, Any]) -> None:
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def run_form_compile_task(
    source_path: str | Path,
    out_dir: str | Path,
    *,
    study_id: str | None = None,
    timestamp_policy: str | None = None,
    overwrite: bool = False,
    request_id: str | None = None,
    request_source: str = "cli",
    operator_id: str | None = None,
    operator_assertion_basis: str | None = None,
) -> SentinelTaskResult:
    """Run a form compilation through the Sentinel task contract.

    This is the supported orchestration entry point for the CLI today and for a
    future Sentinel VM/API or desktop GUI. The XLSForm transformation remains in
    ``compile_xlsform``; this layer adds a task identity, execution timing and a
    machine-readable task event suitable for later sidecar ingestion.
    """
    source = Path(source_path).resolve()
    out_root = Path(out_dir).resolve()
    out_root.mkdir(parents=True, exist_ok=True)

    rid = request_id or str(uuid.uuid4())
    started = _now_local()
    started_mono_ns = time.monotonic_ns()
    source_sha256 = _sha256_file(source) if source.exists() else None

    try:
        compiled = compile_xlsform(
            source,
            out_root,
            study_id=study_id,
            timestamp_policy=timestamp_policy,
            overwrite=overwrite,
        )
    except Exception as exc:
        completed = _now_local()
        duration_ms = max(0, (time.monotonic_ns() - started_mono_ns) // 1_000_000)
        failure_dir = out_root / "_sentinel_failed_tasks"
        failure_dir.mkdir(parents=True, exist_ok=True)
        event_path = failure_dir / f"{rid}.json"
        event = {
            "schema": TASK_EVENT_SCHEMA,
            "task_type": TASK_TYPE_FORM_COMPILE,
            "task_schema_version": TASK_SCHEMA_VERSION,
            "request": {
                "request_id": rid,
                "source": request_source,
                "operator": (
                    {
                        "id": operator_id,
                        "assertion_basis": operator_assertion_basis or "request_context",
                    }
                    if operator_id else None
                ),
                "input": {
                    "filename": source.name,
                    "sha256": source_sha256,
                },
                "parameters": {
                    "study_id_override": study_id,
                    "timestamp_policy_override": timestamp_policy,
                    "overwrite_compatibility_flag": bool(overwrite),
                },
            },
            "execution": {
                "status": "failed",
                "started_at_local": started.isoformat(timespec="seconds"),
                "completed_at_local": completed.isoformat(timespec="seconds"),
                "monotonic_duration_ms": duration_ms,
                "implementation": {
                    "component": "methodmesh-xlsform",
                    "version": __version__,
                    "implementation_sha256": _compiler_implementation_sha256(),
                },
            },
            "result": {
                "error_type": type(exc).__name__,
                "error": str(exc),
                "outputs": [],
            },
        }
        _write_json(event_path, event)
        raise

    completed = _now_local()
    duration_ms = max(0, (time.monotonic_ns() - started_mono_ns) // 1_000_000)
    event_path = compiled.release_dir / "sentinel_task_event.json"

    # Advertise the task event in the existing machine manifest before the event
    # hashes that manifest. This is intentionally one-way: the manifest points to
    # the event filename/request ID, while the checksum inventory protects both.
    manifest = json.loads(compiled.manifest_json.read_text(encoding="utf-8"))
    manifest.setdefault("bundle", {}).setdefault("contents", {})["sentinel_task_event"] = event_path.name
    manifest["sentinel_task"] = {
        "event_schema": TASK_EVENT_SCHEMA,
        "task_type": TASK_TYPE_FORM_COMPILE,
        "task_schema_version": TASK_SCHEMA_VERSION,
        "request_id": rid,
        "event_file": event_path.name,
    }
    _write_json(compiled.manifest_json, manifest)
    compiled.manifest = manifest

    # Add a concise human-readable note without duplicating the machine event.
    with compiled.human_manifest_md.open("a", encoding="utf-8") as fh:
        fh.write(
            "\n## Sentinel task provenance\n\n"
            f"- Task: `{TASK_TYPE_FORM_COMPILE}` schema `{TASK_SCHEMA_VERSION}`\n"
            f"- Request ID: `{rid}`\n"
            f"- Task event: `{event_path.name}`\n\n"
            "The task event is the machine-readable execution receipt for this compilation. "
            "A future Sentinel VM service can persist the same event contract into the ODK trial sidecar project.\n"
        )

    outputs = [
        _artifact("source_copy", compiled.source_copy),
        _artifact("release_xlsform", compiled.output_xlsx),
        _artifact("commitment_manifest", compiled.manifest_json),
        _artifact("human_manifest", compiled.human_manifest_md),
        _artifact("build_report", compiled.report_md),
    ]

    event = {
        "schema": TASK_EVENT_SCHEMA,
        "task_type": TASK_TYPE_FORM_COMPILE,
        "task_schema_version": TASK_SCHEMA_VERSION,
        "request": {
            "request_id": rid,
            "source": request_source,
            "operator": (
                {
                    "id": operator_id,
                    "assertion_basis": operator_assertion_basis or "request_context",
                }
                if operator_id else None
            ),
            "input": {
                "filename": source.name,
                "sha256": source_sha256,
            },
            "parameters": {
                "study_id_override": study_id,
                "timestamp_policy_override": timestamp_policy,
                "overwrite_compatibility_flag": bool(overwrite),
            },
        },
        "execution": {
            "status": "succeeded",
            "started_at_local": started.isoformat(timespec="seconds"),
            "completed_at_local": completed.isoformat(timespec="seconds"),
            "monotonic_duration_ms": duration_ms,
            "implementation": {
                "component": "methodmesh-xlsform",
                "version": __version__,
                "implementation_sha256": _compiler_implementation_sha256(),
            },
        },
        "result": {
            "release_id": compiled.release_dir.name,
            "form": {
                "study_id": manifest["form"]["study_id"],
                "form_id": manifest["form"]["form_id"],
                "form_version": manifest["form"]["version"],
            },
            "outputs": outputs,
            "warnings": list(compiled.warnings),
        },
    }
    _write_json(event_path, event)

    # The task receipt is now part of the release bundle. Rebuild the checksum
    # inventory so it covers the receipt as well as the compiler artefacts.
    _write_checksums(
        compiled.checksums_sha256,
        [
            compiled.source_copy,
            compiled.output_xlsx,
            compiled.manifest_json,
            compiled.human_manifest_md,
            compiled.report_md,
            event_path,
        ],
    )

    return SentinelTaskResult(
        request_id=rid,
        task_type=TASK_TYPE_FORM_COMPILE,
        status="succeeded",
        event_json=event_path,
        compile_result=compiled,
        event=event,
    )
