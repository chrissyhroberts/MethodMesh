from __future__ import annotations

from copy import copy
from dataclasses import dataclass, asdict
import hashlib
import json
from pathlib import Path
import re
from typing import Any, Iterable

from openpyxl import load_workbook
from openpyxl.styles import PatternFill, Font
from openpyxl.utils import get_column_letter

from . import __version__
from .errors import CompilerError, ValidationError
from .policy import (
    SAFE_TOKEN,
    CommitDecision,
    decide_commit,
    is_begin_group,
    is_begin_repeat,
    is_calculate,
    is_end_group,
    is_end_repeat,
    is_select_one,
    is_select_multiple,
    is_select_one_from_file,
    is_select_multiple_from_file,
    normalize_type,
    type_head,
)

MANIFEST_SCHEMA = "methodmesh.xlsform_commitment_manifest.v1"
RECIPE_SCHEMA = "methodmesh.commitment_recipe.v1"
GENERATED_MARKER = "methodmesh-xlsform-compiler:v1"

MM_COMMIT_VALUES = {"", "auto", "value", "sha256", "exclude"}

# Names injected into the generated survey. Build fails if the source already uses any.
AUTH_OUTPUTS = [
    ("text", "mm_auth_methodmesh_execution_id"),
    ("text", "mm_auth_methodmesh_method_id"),
    ("text", "mm_auth_methodmesh_status"),
    ("text", "mm_auth_credential_verified"),
    ("text", "mm_auth_credential_verification_message"),
    ("text", "mm_auth_credential_id"),
    ("text", "mm_auth_credential_subject_id"),
    ("text", "mm_auth_pin_verified"),
    ("text", "mm_auth_issuer_signature_valid"),
    ("text", "mm_auth_issuer_trust_status"),
    ("text", "mm_auth_issuer_key_id"),
    ("text", "mm_auth_credential_envelope_hash"),
    ("text", "mm_auth_credential_verified_time_iso"),
    ("text", "mm_auth_methodmesh_full_json"),
]

ATT_OUTPUTS = [
    ("text", "mm_att_methodmesh_execution_id"),
    ("text", "mm_att_methodmesh_method_id"),
    ("text", "mm_att_methodmesh_status"),
    ("text", "mm_att_attestation_schema_version"),
    ("text", "mm_att_attestation_id"),
    ("text", "mm_att_study_id"),
    ("text", "mm_att_event_type"),
    ("text", "mm_att_event_payload_hash"),
    ("text", "mm_att_event_payload_mode"),
    ("text", "mm_att_commitment_recipe"),
    ("text", "mm_att_commitment_recipe_sha256"),
    ("text", "mm_att_verification_method"),
    ("text", "mm_att_verification_evidence_format"),
    ("text", "mm_att_verification_evidence_hash"),
    ("text", "mm_att_device_event_time_iso"),
    ("integer", "mm_att_device_monotonic_counter"),
    ("text", "mm_att_previous_attestation_hash"),
    ("text", "mm_att_attestation_hash"),
    ("text", "mm_att_hash_algorithm"),
    ("text", "mm_att_public_key_id"),
    ("text", "mm_att_public_key_algorithm"),
    ("text", "mm_att_public_key_format"),
    ("text", "mm_att_public_key_base64"),
    ("text", "mm_att_signature"),
    ("text", "mm_att_signature_algorithm"),
    ("text", "mm_att_trusted_timestamp_policy"),
    ("text", "mm_att_trusted_timestamp_status"),
    ("text", "mm_att_trusted_timestamp_authority"),
    ("text", "mm_att_trusted_timestamp_time_iso"),
    ("text", "mm_att_trusted_timestamp_serial"),
    ("text", "mm_att_trusted_timestamp_attested_hash"),
    ("text", "mm_att_trusted_timestamp_token_sha256"),
    ("text", "mm_att_trusted_timestamp_token_base64"),
    ("text", "mm_att_diagnostic_reason"),
    ("text", "mm_att_methodmesh_full_json"),
]

INJECTED_NAMES = {
    "mm_compiler_marker",
    "mm_workflow_instance_id", "mm_study_id", "mm_form_id", "mm_form_version",
    "mm_timestamp_policy", "mm_trusted_issuer_key_ids", "mm_auth_ok",
    "mm_authenticate_operator", "mm_authenticate_operator_end",
    "mm_credential_id_sha256", "mm_credential_subject_id_sha256",
    "mm_canonical_commitment_live", "mm_current_event_payload_hash",
    "mm_finalize_for_attestation", "mm_frozen_canonical_commitment", "mm_event_payload_hash",
    "mm_commitment_recipe", "mm_current_data_matches_frozen_commitment",
    "mm_create_attestation", "mm_create_attestation_end",
    "mm_attested_hash_matches_odk_hash", "mm_ready_to_submit",
    "mm_submission_guard", "mm_status_ready", "mm_status_not_ready",
    *[n for _, n in AUTH_OUTPUTS], *[n for _, n in ATT_OUTPUTS],
}

VISIBLE_QUESTION_HEADS = {
    "text", "integer", "decimal", "date", "datetime", "dateTime", "time", "geopoint", "geotrace", "geoshape",
    "barcode", "acknowledge", "rank", "range", "image", "audio", "video", "file", "hidden",
}


@dataclass
class Member:
    name: str
    xlsform_type: str
    mode: str
    transform: str
    reason: str
    source_row: int
    source_column: str | None = None
    choice_list: str | None = None

    def manifest_dict(self) -> dict[str, Any]:
        out = asdict(self)
        return {k: v for k, v in out.items() if v is not None}


@dataclass
class Exclusion:
    name: str
    xlsform_type: str
    reason: str
    explicit: bool
    source_row: int


@dataclass
class CompileResult:
    output_xlsx: Path
    manifest_json: Path
    report_md: Path
    manifest: dict[str, Any]
    warnings: list[str]


def _sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def _headers(ws) -> dict[str, int]:
    result: dict[str, int] = {}
    for cell in ws[1]:
        if cell.value is None:
            continue
        key = str(cell.value).strip()
        if key and key not in result:
            result[key] = cell.column
    return result


def _ensure_column(ws, headers: dict[str, int], name: str) -> int:
    if name in headers:
        return headers[name]
    col = ws.max_column + 1
    ws.cell(1, col, name)
    headers[name] = col
    return col


def _first_matching_header(headers: dict[str, int], prefix: str) -> str | None:
    if prefix in headers:
        return prefix
    for key in headers:
        if key.startswith(prefix + "::"):
            return key
    return None


def _all_matching_headers(headers: dict[str, int], prefix: str) -> list[str]:
    exact = [prefix] if prefix in headers else []
    localized = [k for k in headers if k.startswith(prefix + "::")]
    return exact + localized


def _cell(ws, row: int, headers: dict[str, int], name: str) -> Any:
    col = headers.get(name)
    return ws.cell(row, col).value if col else None


def _set(ws, row: int, headers: dict[str, int], name: str, value: Any) -> None:
    col = _ensure_column(ws, headers, name)
    ws.cell(row, col, value)


def _boolean_expr(existing: Any) -> str:
    e = str(existing or "").strip()
    if e.lower() in {"yes", "true", "true()"}:
        return "true()"
    if e.lower() in {"no", "false", "false()"}:
        return "false()"
    return e


def _combine_and(existing: Any, extra: str) -> str:
    e = _boolean_expr(existing)
    return f"({e}) and ({extra})" if e else extra


def _combine_or(existing: Any, extra: str) -> str:
    e = _boolean_expr(existing)
    return f"({e}) or ({extra})" if e else extra


def _read_settings(ws) -> dict[str, str]:
    headers = _headers(ws)
    if not headers:
        raise ValidationError("settings sheet is empty.")
    result: dict[str, str] = {}
    for key, col in headers.items():
        val = ws.cell(2, col).value
        if val is not None:
            result[key] = str(val).strip()
    return result


def _read_methodmesh_config(wb) -> dict[str, str]:
    if "methodmesh" not in wb.sheetnames:
        return {}
    ws = wb["methodmesh"]
    headers = _headers(ws)
    if "key" not in headers or "value" not in headers:
        raise ValidationError("methodmesh sheet must have columns named key and value.")
    out: dict[str, str] = {}
    for r in range(2, ws.max_row + 1):
        key = ws.cell(r, headers["key"]).value
        value = ws.cell(r, headers["value"]).value
        if key is None or str(key).strip() == "":
            continue
        k = str(key).strip()
        if k in out:
            raise ValidationError(f"methodmesh sheet contains duplicate key {k!r}.")
        out[k] = "" if value is None else str(value).strip()
    return out


def _safe_config_token(value: str, what: str) -> str:
    if not value or not SAFE_TOKEN.fullmatch(value):
        raise ValidationError(
            f"{what} must contain only letters, numbers, underscore, hyphen or period for compiler v0.1; got {value!r}."
        )
    return value


def _choice_list_name(type_value: str) -> str | None:
    t = type_value.strip()
    parts = t.split(maxsplit=1)
    if len(parts) != 2:
        return None
    head = parts[0].lower()
    if head in {"select_one", "select_multiple", "select", "select_one_from_file", "select_multiple_from_file"}:
        return parts[1]
    if t.lower().startswith("select one ") or t.lower().startswith("select multiple "):
        return t.split(maxsplit=2)[2]
    return parts[1] if head.startswith("select_") else None


def _validate_choice_names(wb, members: list[Member]) -> None:
    if "choices" not in wb.sheetnames:
        internal_selects = [m for m in members if is_select_one(m.xlsform_type) or is_select_multiple(m.xlsform_type)]
        if internal_selects:
            raise ValidationError("Committed internal select field(s) found but choices sheet is missing.")
        return
    ws = wb["choices"]
    headers = _headers(ws)
    if "list_name" not in headers or "name" not in headers:
        raise ValidationError("choices sheet must contain list_name and name columns.")
    by_list: dict[str, list[str]] = {}
    for r in range(2, ws.max_row + 1):
        ln = ws.cell(r, headers["list_name"]).value
        nm = ws.cell(r, headers["name"]).value
        if ln is None or nm is None:
            continue
        by_list.setdefault(str(ln), []).append(str(nm))
    for m in members:
        if not is_select_one(m.xlsform_type):
            continue
        list_name = m.choice_list
        if not list_name or list_name not in by_list:
            raise ValidationError(f"{m.name}: choice list {list_name!r} not found in choices sheet.")
        bad = [x for x in by_list[list_name] if "|" in x or "=" in x]
        if bad:
            raise ValidationError(
                f"{m.name}: committed select_one list {list_name!r} has choice name(s) containing '|' or '=': {bad}."
            )


def _field_expr(member: Member) -> str:
    ref = "${" + member.name + "}"
    if member.mode == "sha256":
        return f"digest(string({ref}), 'SHA-256', 'hex')"
    if member.mode != "value":
        raise CompilerError(f"Internal error: cannot emit expression for mode {member.mode}")
    head = type_head(member.xlsform_type).lower()
    if head == "date":
        return f"if(string-length(string({ref})) > 0, format-date({ref}, '%Y-%m-%d'), '')"
    return f"string({ref})"


def _context_members() -> list[dict[str, Any]]:
    return [
        {"path": "study_id", "type": "string", "commitment": "value", "transform": "safe-token"},
        {"path": "form_id", "type": "string", "commitment": "value", "transform": "safe-token"},
        {"path": "form_version", "type": "string", "commitment": "value", "transform": "safe-token"},
        {"path": "workflow_instance_id", "type": "string", "commitment": "value", "transform": "uuid"},
        {"path": "credential_id_sha256", "type": "sha256", "commitment": "text-utf8-sha256", "source_field": "mm_auth_credential_id"},
        {"path": "credential_subject_id_sha256", "type": "sha256", "commitment": "text-utf8-sha256", "source_field": "mm_auth_credential_subject_id"},
    ]


def _recipe_member(m: Member) -> dict[str, Any]:
    if m.mode == "sha256":
        commitment = "text-utf8-sha256"
    else:
        commitment = "value"
    out = {
        "path": m.name,
        "xlsform_type": m.xlsform_type,
        "commitment": commitment,
        "transform": m.transform,
    }
    if m.choice_list:
        out["choice_list"] = m.choice_list
    return out


def _build_recipe(members: list[Member]) -> dict[str, Any]:
    return {
        "schema": RECIPE_SCHEMA,
        "canonicalization": "ordered-kv-v1",
        "hash_algorithm": "SHA-256",
        "encoding": "UTF-8",
        "pair_separator": "|",
        "key_value_separator": "=",
        "escaping": "none",
        "members": _context_members() + [_recipe_member(m) for m in members],
    }


def _build_canonical_calc(members: list[Member]) -> str:
    parts: list[str] = [
        "'study_id='", "${mm_study_id}", "'|'",
        "'form_id='", "${mm_form_id}", "'|'",
        "'form_version='", "${mm_form_version}", "'|'",
        "'workflow_instance_id='", "${mm_workflow_instance_id}", "'|'",
        "'credential_id_sha256='", "${mm_credential_id_sha256}", "'|'",
        "'credential_subject_id_sha256='", "${mm_credential_subject_id_sha256}",
    ]
    for m in members:
        parts.extend(["'|'", repr(m.name + "=").replace('"', "'"), _field_expr(m)])
    # repr gives a single-quoted Python literal for ordinary XLSForm names.
    return "concat(" + ", ".join(parts) + ")"


def _is_visible_or_interactive(type_value: str) -> bool:
    t = normalize_type(type_value)
    tl = t.lower()
    head = type_head(t)
    hl = head.lower()
    if is_begin_group(t) or is_begin_repeat(t):
        return True
    if is_end_group(t) or is_end_repeat(t) or is_calculate(t):
        return False
    if tl in {"start", "end", "today", "deviceid", "username", "phonenumber", "start-geopoint", "audit"}:
        return False
    return hl in {x.lower() for x in VISIBLE_QUESTION_HEADS} or is_select_one(t) or is_select_multiple(t) or is_select_one_from_file(t) or is_select_multiple_from_file(t) or tl == "note"


def _is_editable_question(type_value: str) -> bool:
    t = normalize_type(type_value)
    tl = t.lower()
    if is_begin_group(t) or is_end_group(t) or is_begin_repeat(t) or is_end_repeat(t) or is_calculate(t) or tl == "note":
        return False
    if tl in {"start", "end", "today", "deviceid", "username", "phonenumber", "start-geopoint", "audit"}:
        return False
    return True


def _blank_row(headers: dict[str, int]) -> list[Any]:
    return [None] * max(headers.values())


def _make_row(headers: dict[str, int], **values: Any) -> list[Any]:
    row = _blank_row(headers)
    for key, value in values.items():
        col = headers.get(key)
        if col:
            row[col - 1] = value
    return row


def _apply_labels(row: list[Any], headers: dict[str, int], label: str | None = None, hint: str | None = None, constraint_message: str | None = None) -> None:
    if label is not None:
        label_headers = _all_matching_headers(headers, "label")
        if not label_headers:
            label_headers = ["label"]
        for h in label_headers:
            if h in headers:
                row[headers[h] - 1] = label
    if hint is not None:
        hint_headers = _all_matching_headers(headers, "hint")
        if not hint_headers and "hint" in headers:
            hint_headers = ["hint"]
        for h in hint_headers:
            if h in headers:
                row[headers[h] - 1] = hint
    if constraint_message is not None:
        cm_headers = _all_matching_headers(headers, "constraint_message")
        if not cm_headers and "constraint_message" in headers:
            cm_headers = ["constraint_message"]
        for h in cm_headers:
            if h in headers:
                row[headers[h] - 1] = constraint_message


def _append_rows(ws, rows: Iterable[list[Any]]) -> None:
    for row in rows:
        ws.append(row)


def _inject_choice_yes(wb) -> None:
    if "choices" not in wb.sheetnames:
        ws = wb.create_sheet("choices")
        ws.append(["list_name", "name", "label"])
    else:
        ws = wb["choices"]
    headers = _headers(ws)
    if "list_name" not in headers:
        ws.cell(1, ws.max_column + 1, "list_name")
        headers = _headers(ws)
    if "name" not in headers:
        ws.cell(1, ws.max_column + 1, "name")
        headers = _headers(ws)
    label_headers = _all_matching_headers(headers, "label")
    if not label_headers:
        ws.cell(1, ws.max_column + 1, "label")
        headers = _headers(ws)
        label_headers = ["label"]
    for r in range(2, ws.max_row + 1):
        if str(ws.cell(r, headers["list_name"]).value or "") == "mm_yes" and str(ws.cell(r, headers["name"]).value or "") == "yes":
            return
    row = [None] * ws.max_column
    row[headers["list_name"] - 1] = "mm_yes"
    row[headers["name"] - 1] = "yes"
    for h in label_headers:
        row[headers[h] - 1] = "Yes — attestation verified"
    ws.append(row)


def compile_xlsform(
    source_path: str | Path,
    out_dir: str | Path,
    *,
    study_id: str | None = None,
    timestamp_policy: str | None = None,
    overwrite: bool = False,
) -> CompileResult:
    source = Path(source_path).resolve()
    out_dir = Path(out_dir).resolve()
    if not source.exists():
        raise ValidationError(f"Source XLSForm not found: {source}")
    out_dir.mkdir(parents=True, exist_ok=True)

    source_hash = _sha256_file(source)
    wb = load_workbook(source)
    if "survey" not in wb.sheetnames or "settings" not in wb.sheetnames:
        raise ValidationError("XLSForm must contain survey and settings sheets.")

    settings = _read_settings(wb["settings"])
    form_id = settings.get("form_id", "")
    form_version = settings.get("version", "")
    form_title = settings.get("form_title", form_id)
    if not form_id:
        raise ValidationError("settings.form_id is required; compiler will not invent a deployed form identity.")
    if not form_version:
        raise ValidationError("settings.version is required for MethodMesh-governed release forms.")
    _safe_config_token(form_id, "settings.form_id")
    _safe_config_token(form_version, "settings.version")

    mm_cfg = _read_methodmesh_config(wb)
    enabled = mm_cfg.get("enabled", "true").lower()
    if enabled not in {"true", "yes", "1"}:
        raise ValidationError("methodmesh.enabled must be true for compilation.")
    cfg_study_id = study_id or mm_cfg.get("study_id")
    if not cfg_study_id:
        raise ValidationError(
            "No study_id supplied. Add a methodmesh sheet with key/value study_id, or pass --study-id."
        )
    cfg_study_id = _safe_config_token(cfg_study_id, "study_id")
    authentication = mm_cfg.get("authentication", "nfc_credential")
    if authentication != "nfc_credential":
        raise ValidationError("compiler v0.1 supports authentication=nfc_credential only.")
    ts_policy = (timestamp_policy or mm_cfg.get("timestamp_policy") or "preferred").lower()
    if ts_policy not in {"disabled", "preferred", "required"}:
        raise ValidationError("timestamp_policy must be disabled, preferred or required.")

    ws = wb["survey"]
    headers = _headers(ws)
    for needed in ["type", "name"]:
        if needed not in headers:
            raise ValidationError(f"survey sheet is missing required column {needed!r}.")
    # Ensure columns needed by generated wrapper exist.
    for h in ["relevant", "constraint", "calculation", "appearance", "body::intent", "readonly", "required"]:
        _ensure_column(ws, headers, h)
    # Reuse localized authoring columns where present instead of creating an unnecessary
    # parallel plain-language column.
    for h in ["label", "hint", "constraint_message"]:
        if not _all_matching_headers(headers, h):
            _ensure_column(ws, headers, h)
    mm_commit_col = _ensure_column(ws, headers, "mm_commit")

    # Detect source naming problems/collisions before mutation.
    source_names: dict[str, int] = {}
    duplicates: dict[str, list[int]] = {}
    for r in range(2, ws.max_row + 1):
        name = str(_cell(ws, r, headers, "name") or "").strip()
        if not name:
            continue
        if name in source_names:
            duplicates.setdefault(name, [source_names[name]]).append(r)
        else:
            source_names[name] = r
    if duplicates:
        detail = "; ".join(f"{k}: rows {v}" for k, v in duplicates.items())
        raise ValidationError(
            "compiler v0.1 requires globally unique survey node names so generated commitment references are unambiguous. " + detail
        )
    collisions = sorted(set(source_names) & INJECTED_NAMES)
    if collisions:
        raise ValidationError(f"Source form already uses reserved MethodMesh generated node name(s): {collisions}")
    if "mm_compiler_marker" in source_names:
        raise ValidationError("Form appears already compiled; compile from the ordinary source XLSForm, not a generated release workbook.")

    members: list[Member] = []
    exclusions: list[Exclusion] = []
    warnings: list[str] = []
    repeat_stack: list[tuple[str, bool]] = []

    original_rows = ws.max_row
    for r in range(2, original_rows + 1):
        t = normalize_type(_cell(ws, r, headers, "type"))
        name = str(_cell(ws, r, headers, "name") or "").strip()
        raw_mm = str(_cell(ws, r, headers, "mm_commit") or "").strip().lower()
        if raw_mm not in MM_COMMIT_VALUES:
            raise ValidationError(f"{name or 'row '+str(r)}: invalid mm_commit {raw_mm!r}.")

        if is_begin_repeat(t):
            explicit_exclude = raw_mm == "exclude"
            if not explicit_exclude:
                raise ValidationError(
                    f"{name or 'row '+str(r)}: repeat groups are not canonicalized in compiler v0.1. "
                    "Set mm_commit=exclude on the begin_repeat row to exclude the whole repeat from the attested commitment."
                )
            repeat_stack.append((name, True))
            exclusions.append(Exclusion(name=name, xlsform_type=t, reason="repeat explicitly excluded; descendants excluded", explicit=True, source_row=r))
            continue
        if is_end_repeat(t):
            if not repeat_stack:
                raise ValidationError(f"row {r}: end_repeat without matching begin_repeat.")
            repeat_stack.pop()
            continue

        inside_excluded_repeat = bool(repeat_stack)
        if not name:
            # End-group rows are often nameless; structural rows need no member.
            continue
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_.-]*", name):
            raise ValidationError(f"{name!r} at survey row {r} is not a compiler-safe node name.")

        decision = decide_commit(t, raw_mm, name, inside_excluded_repeat=inside_excluded_repeat)
        explicit = raw_mm == "exclude"
        if decision.mode == "exclude":
            exclusions.append(Exclusion(name=name, xlsform_type=t, reason=decision.reason, explicit=explicit, source_row=r))
        else:
            choice_list = _choice_list_name(t) if (is_select_one(t) or is_select_multiple(t)) else None
            members.append(Member(
                name=name,
                xlsform_type=t,
                mode=decision.mode,
                transform=decision.transform,
                reason=decision.reason,
                source_row=r,
                choice_list=choice_list,
            ))

    if repeat_stack:
        raise ValidationError("Unclosed repeat group(s) in survey sheet.")
    if not members:
        raise ValidationError("No fields are committed. At least one source field must be committed for an attested form.")

    _validate_choice_names(wb, members)

    # Warn for explicitly excluded editable data.
    explicit_excluded = [x for x in exclusions if x.explicit and not is_begin_repeat(x.xlsform_type)]
    if explicit_excluded:
        warnings.append(
            f"{len(explicit_excluded)} field(s) are explicitly excluded from the attested commitment: "
            + ", ".join(x.name for x in explicit_excluded)
        )

    # Gate source UI on successful authentication and lock all editable source questions after finalization.
    for r in range(2, original_rows + 1):
        t = normalize_type(_cell(ws, r, headers, "type"))
        if _is_visible_or_interactive(t):
            _set(ws, r, headers, "relevant", _combine_and(_cell(ws, r, headers, "relevant"), "${mm_auth_ok} = 'true'"))
        if _is_editable_question(t):
            _set(ws, r, headers, "readonly", _combine_or(_cell(ws, r, headers, "readonly"), "${mm_finalize_for_attestation} = 'yes'"))

    # Remove compiler-only mm_commit from release workbook by clearing cells; drop column later after injected rows are added.

    recipe = _build_recipe(members)
    recipe_json = json.dumps(recipe, separators=(",", ":"), ensure_ascii=False)
    canonical_calc = _build_canonical_calc(members)

    # Header/label support after ensuring columns.
    headers = _headers(ws)

    def row(type_: str, name: str, *, label: str | None = None, hint: str | None = None, **kwargs: Any) -> list[Any]:
        values = {"type": type_, "name": name, **kwargs}
        out = _make_row(headers, **values)
        _apply_labels(out, headers, label=label, hint=hint, constraint_message=kwargs.get("constraint_message"))
        return out

    # Authentication wrapper is prepended so the original form is gated from the first user-facing row.
    auth_intent = (
        "com.example.methodmesh.EXECUTE_METHOD("
        "method_id='nfc_credential_verification',"
        "caller='odk',"
        f"form_id='{form_id}',"
        "visit_id=${mm_workflow_instance_id},"
        "input_trusted_issuer_key_ids=${mm_trusted_issuer_key_ids},"
        "input_payload_mode='FULL',"
        "return_mode='flat',"
        "methodmesh_return_namespace='mm_auth'"
        ")"
    )
    auth_ok = (
        "if(${mm_auth_methodmesh_status} = 'Succeeded' and "
        "${mm_auth_credential_verified} = 'true' and ${mm_auth_pin_verified} = 'true' and "
        "${mm_auth_issuer_signature_valid} = 'true' and "
        "(string-length(${mm_trusted_issuer_key_ids}) = 0 or ${mm_auth_issuer_trust_status} = 'trusted'), 'true', 'false')"
    )
    prefix = [
        row("calculate", "mm_compiler_marker", calculation=f"'{GENERATED_MARKER}'"),
        row("calculate", "mm_workflow_instance_id", calculation="once(uuid())"),
        row("calculate", "mm_study_id", calculation=f"'{cfg_study_id}'"),
        row("calculate", "mm_form_id", calculation=f"'{form_id}'"),
        row("calculate", "mm_form_version", calculation=f"'{form_version}'"),
        row("calculate", "mm_timestamp_policy", calculation=f"'{ts_policy}'"),
        row("text", "mm_trusted_issuer_key_ids", label="Trusted issuer key IDs (optional)",
            hint="Normally left blank until the study operates a governed issuer allow-list."),
        row("begin group", "mm_authenticate_operator", label="Authenticate operator",
            hint="Tap the NFC credential and enter its PIN in MethodMesh.", appearance="field-list", **{"body::intent": auth_intent}),
    ]
    for type_, name in AUTH_OUTPUTS:
        prefix.append(row(type_, name, appearance="hidden-answer", readonly="yes"))
    prefix.extend([
        row("end group", "mm_authenticate_operator_end"),
        row("calculate", "mm_auth_ok", calculation=auth_ok),
        row("note", "mm_auth_status_ok", label="Authentication accepted",
            hint="Protected study fields are now available.", relevant="${mm_auth_ok} = 'true'"),
        row("note", "mm_auth_status_fail", label="Authentication not accepted",
            hint="The study form remains locked until NFC credential + PIN verification succeeds.",
            relevant="${mm_auth_methodmesh_status} != '' and ${mm_auth_ok} != 'true'"),
    ])

    # Insert prefix immediately before original survey row 2.
    ws.insert_rows(2, amount=len(prefix))
    for idx, values in enumerate(prefix, start=2):
        for c, v in enumerate(values, start=1):
            ws.cell(idx, c, v)

    # Source rows moved down by prefix length. Append finalization and attestation after all original source rows.
    headers = _headers(ws)
    current_original_end = original_rows + len(prefix)

    att_intent = (
        "com.example.methodmesh.EXECUTE_METHOD("
        "method_id='attestation.create',"
        "caller='odk',"
        f"form_id='{form_id}',"
        "visit_id=${mm_workflow_instance_id},"
        "operator_id=${mm_auth_credential_subject_id},"
        f"input_study_id='{cfg_study_id}',"
        "input_event_type='odk_form_commitment',"
        "input_event_payload_hash=${mm_event_payload_hash},"
        "input_commitment_recipe=${mm_commitment_recipe},"
        "input_verification_method='NfcCredential',"
        "input_verification_execution_id=${mm_auth_methodmesh_execution_id},"
        "input_trusted_timestamp=${mm_timestamp_policy},"
        "input_payload_mode='FULL',"
        "return_mode='flat',"
        "methodmesh_return_namespace='mm_att'"
        ")"
    )

    final_rows = [
        row("calculate", "mm_credential_id_sha256", calculation="digest(string(${mm_auth_credential_id}), 'SHA-256', 'hex')"),
        row("calculate", "mm_credential_subject_id_sha256", calculation="digest(string(${mm_auth_credential_subject_id}), 'SHA-256', 'hex')"),
        row("calculate", "mm_canonical_commitment_live", calculation=canonical_calc),
        row("calculate", "mm_current_event_payload_hash", calculation="digest(${mm_canonical_commitment_live}, 'SHA-256', 'hex')"),
        row("select_one mm_yes", "mm_finalize_for_attestation", label="Finalize these data for attestation?",
            hint="Review the form first. Finalize freezes the commitment and makes source questions read-only.",
            required="yes", relevant="${mm_auth_ok} = 'true'", constraint=". = 'yes'",
            constraint_message="Select Yes to finalize the form for attestation."),
        row("calculate", "mm_frozen_canonical_commitment",
            calculation="once(if(${mm_finalize_for_attestation} = 'yes', ${mm_canonical_commitment_live}, ''))"),
        row("calculate", "mm_event_payload_hash",
            calculation="once(if(string-length(${mm_frozen_canonical_commitment}) > 0, digest(${mm_frozen_canonical_commitment}, 'SHA-256', 'hex'), ''))"),
        row("calculate", "mm_commitment_recipe", calculation="'" + recipe_json.replace("'", "&apos;") + "'"),
        row("calculate", "mm_current_data_matches_frozen_commitment",
            calculation="if(${mm_current_event_payload_hash} = ${mm_event_payload_hash}, 'true', 'false')"),
        row("note", "mm_frozen_status", label="Commitment frozen",
            hint="ODK SHA-256: ${mm_event_payload_hash}", relevant="string-length(${mm_event_payload_hash}) = 64"),
        row("note", "mm_integrity_warning", label="Integrity warning",
            hint="Current form data no longer reconstruct to the frozen commitment. Do not submit; create a controlled correction/new attestation.",
            relevant="string-length(${mm_event_payload_hash}) = 64 and ${mm_current_data_matches_frozen_commitment} != 'true'"),
        row("begin group", "mm_create_attestation", label="Create MethodMesh attestation",
            hint="MethodMesh reuses the earlier NFC+PIN execution and signs the frozen ODK commitment.",
            relevant="string-length(${mm_event_payload_hash}) = 64 and ${mm_current_data_matches_frozen_commitment} = 'true'",
            appearance="field-list", **{"body::intent": att_intent}),
    ]
    for type_, name in ATT_OUTPUTS:
        final_rows.append(row(type_, name, appearance="hidden-answer", readonly="yes"))
    final_rows.extend([
        row("end group", "mm_create_attestation_end"),
        row("calculate", "mm_attested_hash_matches_odk_hash",
            calculation="if(${mm_att_event_payload_hash} = ${mm_event_payload_hash}, 'true', 'false')"),
        row("calculate", "mm_ready_to_submit",
            calculation=(
                "if(${mm_att_methodmesh_status} = 'Succeeded' and ${mm_att_verification_method} = 'NfcCredential' and "
                "${mm_attested_hash_matches_odk_hash} = 'true' and ${mm_current_data_matches_frozen_commitment} = 'true' and "
                "string-length(${mm_att_attestation_hash}) = 64 and string-length(${mm_att_signature}) > 0, 'true', 'false')"
            )),
        row("note", "mm_status_ready", label="READY TO SUBMIT",
            hint="MethodMesh attested the exact frozen ODK commitment. Trusted timestamp status: ${mm_att_trusted_timestamp_status}.",
            relevant="${mm_ready_to_submit} = 'true'"),
        row("note", "mm_status_not_ready", label="NOT READY TO SUBMIT",
            hint="MethodMesh status: ${mm_att_methodmesh_status}. Diagnostic: ${mm_att_diagnostic_reason}.",
            relevant="${mm_finalize_for_attestation} = 'yes' and ${mm_ready_to_submit} != 'true'"),
        row("select_one mm_yes", "mm_submission_guard", label="Confirm attestation verified",
            hint="This required guard prevents form completion unless the live data, frozen ODK hash and MethodMesh attestation agree.",
            required="yes", relevant="${mm_finalize_for_attestation} = 'yes'",
            constraint="${mm_ready_to_submit} = 'true' and . = 'yes'",
            constraint_message="MethodMesh attestation is incomplete or the current data do not match the frozen attested commitment."),
    ])
    _append_rows(ws, final_rows)

    # Inject one-choice list used by finalization/guard.
    _inject_choice_yes(wb)

    # Remove compiler-only authoring column and config sheet from the release workbook.
    headers = _headers(ws)
    if "mm_commit" in headers:
        ws.delete_cols(headers["mm_commit"], 1)
    if "methodmesh" in wb.sheetnames:
        del wb["methodmesh"]

    # Style generated rows lightly for author inspection; ODK ignores workbook style.
    generated_fill = PatternFill("solid", fgColor="E8F5E9")
    generated_font = Font(color="1B5E20")
    for r in list(range(2, 2 + len(prefix))) + list(range(current_original_end + 1, ws.max_row + 1)):
        for c in range(1, ws.max_column + 1):
            ws.cell(r, c).fill = generated_fill
            ws.cell(r, c).font = copy(generated_font)

    output_xlsx = out_dir / f"{source.stem}_methodmesh.xlsx"
    manifest_json = out_dir / f"{source.stem}_commitment_manifest.json"
    report_md = out_dir / f"{source.stem}_build_report.md"
    for p in [output_xlsx, manifest_json, report_md]:
        if p.exists() and not overwrite:
            raise ValidationError(f"Output already exists: {p}. Use --overwrite to replace it.")

    manifest: dict[str, Any] = {
        "schema": MANIFEST_SCHEMA,
        "compiler": {"name": "methodmesh-xlsform", "version": __version__},
        "source": {"filename": source.name, "sha256": source_hash},
        "form": {"form_title": form_title, "form_id": form_id, "version": form_version, "study_id": cfg_study_id},
        "methodmesh": {
            "authentication": authentication,
            "timestamp_policy": ts_policy,
            "auth_method": "nfc_credential_verification",
            "attestation_method": "attestation.create",
            "return_namespaces": ["mm_auth", "mm_att"],
        },
        "commitment": {
            "recipe": recipe,
            "members": [m.manifest_dict() for m in members],
            "member_count": len(members),
        },
        "exclusions": [asdict(x) for x in exclusions],
        "warnings": warnings,
        "limitations": [
            "compiler v0.1 requires globally unique survey node names",
            "repeat groups must be explicitly excluded",
            "binary/media content hashing is not yet implemented and media fields must be explicitly excluded",
            "generated XLSX semantics are deterministic but byte-for-byte ZIP reproducibility is not claimed",
        ],
    }

    # Internal alignment invariant: recipe source-member ordering must match canonical member ordering.
    recipe_source_paths = [x["path"] for x in recipe["members"]][len(_context_members()):]
    member_names = [m.name for m in members]
    if recipe_source_paths != member_names:
        raise CompilerError("Internal error: commitment recipe/member ordering diverged from canonical commitment ordering.")

    # Save then hash the release workbook for the manifest/report.
    wb.save(output_xlsx)
    release_hash = _sha256_file(output_xlsx)
    manifest["release"] = {"filename": output_xlsx.name, "sha256": release_hash}
    manifest_json.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    explicit_excluded_names = [x.name for x in exclusions if x.explicit]
    auto_excluded_names = [x.name for x in exclusions if not x.explicit]
    report = [
        f"# MethodMesh XLSForm build report — {form_id}",
        "",
        f"- Compiler: methodmesh-xlsform {__version__}",
        f"- Source: `{source.name}`", f"- Source SHA-256: `{source_hash}`",
        f"- Release: `{output_xlsx.name}`", f"- Release SHA-256: `{release_hash}`",
        f"- Form version: `{form_version}`", f"- Study ID: `{cfg_study_id}`",
        f"- Timestamp policy: `{ts_policy}`", "",
        "## Commitment summary", "",
        f"- Committed source fields: **{len(members)}**",
        f"- Explicitly excluded fields/containers: **{len(explicit_excluded_names)}**",
        f"- Automatically excluded structural/metadata/calculated fields: **{len(auto_excluded_names)}**",
        "",
        "### Committed fields", "",
        "| Order | Field | XLSForm type | Mode | Transform |",
        "|---:|---|---|---|---|",
    ]
    for i, m in enumerate(members, 1):
        report.append(f"| {i} | `{m.name}` | `{m.xlsform_type}` | `{m.mode}` | `{m.transform}` |")
    report.extend(["", "### Explicit exclusions", ""])
    if explicit_excluded_names:
        report.extend([f"- `{n}`" for n in explicit_excluded_names])
    else:
        report.append("- None")
    report.extend(["", "## Build checks", "", "- PASS — settings form_id/version present and preserved", "- PASS — no reserved generated-name collisions", "- PASS — source node names globally unique", "- PASS — commitment recipe ordering matches generated canonical commitment", "- PASS — NFC+PIN authentication wrapper injected", "- PASS — source user-interface fields gated on authentication", "- PASS — source editable fields become read-only after finalization", "- PASS — frozen ODK commitment/hash + live integrity reconstruction injected", "- PASS — prior NFC execution reuse attestation injected", "- PASS — final required submission guard injected", ""])
    if warnings:
        report.extend(["## Warnings", ""] + [f"- {w}" for w in warnings] + [""])
    report.extend([
        "## Known v0.1 limitations", "",
        "- Repeat groups must be explicitly excluded (`mm_commit=exclude` on `begin_repeat`).",
        "- Binary/media fields cannot yet be content-hashed by this compiler; explicitly exclude them for now.",
        "- Global duplicate survey node names are rejected in v0.1.",
        "- The compiler does not replace pyxform/ODK Validate. Upload/validate the generated release form before deployment.",
        "",
        "## Release rule", "",
        "Treat the ordinary source XLSForm as the authoring source of truth. Do not hand-edit the generated MethodMesh release workbook; rebuild it from source.",
    ])
    report_md.write_text("\n".join(report) + "\n", encoding="utf-8")

    return CompileResult(output_xlsx, manifest_json, report_md, manifest, warnings)
