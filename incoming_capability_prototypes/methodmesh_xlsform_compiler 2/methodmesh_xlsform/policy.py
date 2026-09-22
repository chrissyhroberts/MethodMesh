from __future__ import annotations

from dataclasses import dataclass
import re

from .errors import ValidationError

ALLOWED_MM_COMMIT = {"", "auto", "value", "sha256", "exclude"}

STRUCTURAL_TYPES = {
    "begin_group", "begin group", "end_group", "end group",
    "begin_repeat", "begin repeat", "end_repeat", "end repeat",
    "note",
}
METADATA_TYPES = {
    "start", "end", "today", "deviceid", "username", "phonenumber",
    "start-geopoint", "audit",
}
MEDIA_TYPES = {"image", "audio", "video", "file"}
DIRECT_VALUE_TYPES = {"integer", "decimal", "date", "range"}
HASH_LEXICAL_TYPES = {
    "text", "datetime", "dateTime", "time", "geopoint", "geotrace", "geoshape",
    "barcode", "acknowledge", "rank", "hidden",
}

SAFE_TOKEN = re.compile(r"^[A-Za-z0-9_.-]+$")
SAFE_NAME = re.compile(r"^[A-Za-z_][A-Za-z0-9_.-]*$")


@dataclass(frozen=True)
class CommitDecision:
    mode: str  # value | sha256 | exclude
    transform: str
    reason: str


def normalize_type(type_value: object) -> str:
    return str(type_value or "").strip()


def type_head(type_value: object) -> str:
    t = normalize_type(type_value)
    if not t:
        return ""
    return t.split()[0]


def is_select_one(type_value: object) -> bool:
    return normalize_type(type_value).lower().startswith("select_one ") or normalize_type(type_value).lower().startswith("select one ")


def is_select_multiple(type_value: object) -> bool:
    return normalize_type(type_value).lower().startswith("select_multiple ") or normalize_type(type_value).lower().startswith("select multiple ")


def is_select_one_from_file(type_value: object) -> bool:
    return normalize_type(type_value).lower().startswith("select_one_from_file ")


def is_select_multiple_from_file(type_value: object) -> bool:
    return normalize_type(type_value).lower().startswith("select_multiple_from_file ")


def is_calculate(type_value: object) -> bool:
    return normalize_type(type_value).lower() == "calculate"


def is_begin_repeat(type_value: object) -> bool:
    return normalize_type(type_value).lower() in {"begin_repeat", "begin repeat"}


def is_end_repeat(type_value: object) -> bool:
    return normalize_type(type_value).lower() in {"end_repeat", "end repeat"}


def is_begin_group(type_value: object) -> bool:
    return normalize_type(type_value).lower() in {"begin_group", "begin group"}


def is_end_group(type_value: object) -> bool:
    return normalize_type(type_value).lower() in {"end_group", "end group"}


def _validated_override(raw: object, field_name: str) -> str:
    value = str(raw or "").strip().lower()
    if value not in ALLOWED_MM_COMMIT:
        raise ValidationError(
            f"{field_name}: mm_commit must be one of auto, value, sha256, exclude; got {value!r}."
        )
    return value or "auto"


def decide_commit(type_value: object, mm_commit: object, field_name: str, *, inside_excluded_repeat: bool = False) -> CommitDecision:
    t = normalize_type(type_value)
    tl = t.lower()
    override = _validated_override(mm_commit, field_name)

    if inside_excluded_repeat:
        if override not in {"auto", "exclude"}:
            raise ValidationError(
                f"{field_name}: field is inside a repeat excluded from the commitment but mm_commit={override!r}."
            )
        return CommitDecision("exclude", "none", "inside explicitly excluded repeat")

    if tl in STRUCTURAL_TYPES or tl in METADATA_TYPES:
        if override in {"auto", "exclude"}:
            return CommitDecision("exclude", "none", "structural/metadata field")
        if tl in METADATA_TYPES and override == "sha256":
            return CommitDecision("sha256", "odk-lexical-utf8-sha256", "explicit metadata commitment")
        raise ValidationError(f"{field_name}: type {t!r} cannot use mm_commit={override!r}.")

    if is_begin_repeat(t):
        # handled by compiler repeat stack
        return CommitDecision("exclude", "none", "repeat container")

    if is_calculate(t):
        if override in {"auto", "exclude"}:
            return CommitDecision("exclude", "none", "calculate excluded by default")
        if override == "sha256":
            return CommitDecision("sha256", "odk-lexical-utf8-sha256", "explicit calculate commitment")
        raise ValidationError(
            f"{field_name}: calculate fields may be explicitly sha256 or exclude in compiler v0.1; value is ambiguous."
        )

    head = type_head(t)
    head_lower = head.lower()

    if head_lower in MEDIA_TYPES:
        if override == "exclude":
            return CommitDecision("exclude", "none", "binary/media explicitly excluded")
        raise ValidationError(
            f"{field_name}: binary/media field {t!r} cannot be safely content-hashed by ODK digest() in compiler v0.1. "
            "Set mm_commit=exclude for now. Binary artefact commitment is a planned compiler extension."
        )

    if override == "exclude":
        return CommitDecision("exclude", "none", "explicitly excluded by author")

    if is_select_one(t) or is_select_one_from_file(t):
        if override in {"auto", "value"}:
            return CommitDecision("value", "stored-choice-name", "single select commits stable stored choice name")
        return CommitDecision("sha256", "odk-lexical-utf8-sha256", "explicit sha256")

    if is_select_multiple(t) or is_select_multiple_from_file(t):
        if override in {"auto", "sha256"}:
            return CommitDecision(
                "sha256", "odk-selection-order-lexical-utf8-sha256",
                "multi-select commits the exact stored space-separated selection-order representation",
            )
        raise ValidationError(
            f"{field_name}: select_multiple cannot use mm_commit=value in v0.1; use auto/sha256 or exclude."
        )

    if head_lower in DIRECT_VALUE_TYPES:
        if override in {"auto", "value"}:
            transform = "yyyy-mm-dd" if head_lower == "date" else "odk-canonical-scalar"
            return CommitDecision("value", transform, "deterministic scalar value")
        return CommitDecision("sha256", "odk-lexical-utf8-sha256", "explicit sha256")

    if head_lower in {x.lower() for x in HASH_LEXICAL_TYPES}:
        if override in {"auto", "sha256"}:
            return CommitDecision("sha256", "odk-lexical-utf8-sha256", "text/complex lexical value hashed")
        raise ValidationError(
            f"{field_name}: type {t!r} is not safe for raw ordered-kv insertion; use auto/sha256 or exclude."
        )

    # Unknown question types must be explicit; fail rather than guess.
    if override == "sha256":
        return CommitDecision("sha256", "odk-lexical-utf8-sha256", "explicit sha256 for otherwise unknown type")
    raise ValidationError(
        f"{field_name}: unsupported or ambiguous XLSForm type {t!r}. Set mm_commit=sha256/exclude if appropriate, "
        "or extend the compiler policy deliberately."
    )
