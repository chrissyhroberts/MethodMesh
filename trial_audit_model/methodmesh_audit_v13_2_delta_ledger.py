#!/usr/bin/env python3
"""
ODK Central MethodMesh verifier + timestamped receipts + auditor-safe export boundary.

This is a diagnostic precursor to the planned desktop provenance watcher.

For each logical ODK submission it:
  - enumerates every retained Central version;
  - sorts versions chronologically (oldest first);
  - labels ORIGINAL / REVISION n / CURRENT;
  - downloads and hashes the exact historical XML;
  - downloads and hashes version attachments;
  - extracts MethodMesh payload, commitment recipe and attestation fields;
  - independently reconstructs the committed payload from historical XML,
    archived attachment bytes and the signed MethodMesh recipe;
  - compares the independently reconstructed SHA-256 with both the ODK-stored
    payload hash and attestation.event_payload_hash;
  - checks whether the attestation's trusted timestamp claims to attest
    the attestation hash;
  - identifies later Central revisions as EDITED_AFTER_ATTESTATION rather
    than generic integrity failures;
  - caches immutable historical XML and attachment bytes locally so repeat
    polls do not redownload/overwrite previously observed evidence;
  - stores Central audit events but deliberately does not query /diffs;
  - archives the exact published ODK Form Version used by each submission
    version, including XForm XML, original XLSForm where available, and
    version-specific form attachments;
  - reports zero-version submissions explicitly.

Important privacy boundary:
  v12 treats the normal --output directory as a RESTRICTED verification
  archive. It can contain exact historical XML, submission attachments/images
  and detailed working JSON because those are required for reconstruction.
  That directory is never the routine auditor deliverable.

  With --daily-checkpoint, v12 additionally creates a completely separate
  auditor-safe ZIP in a sibling export directory. The ZIP is built from an
  explicit allow-list and is safety-scanned before release. It contains no
  submission XML, no attachments/photos, no old/new research values, no raw
  source_record_fields JSON, and no MethodMesh full JSON.

  The restricted verification archive still contains exact historical XML and
  attachment bytes because they are required for independent reconstruction.
  v11's DAILY AUDITOR EVIDENCE is separate and deliberately contains no old or
  new research values. Changed fields are emitted only as field names/labels.
  Central's /diffs endpoint is not called.

Important:
  The original retained MethodMesh recipe is the only reconstruction recipe
  trusted for later versions. Enketo-calculated digest fields are diagnostic
  only and are never used as authoritative inputs for edited-record hashes.

  This script checks the attestation/recipe/timestamp relationships preserved
  in ODK Central, but it does NOT yet independently verify ECDSA signature
  bytes or RFC3161 token bytes. Those remain a separate cryptographic layer.

  Server mutation is OFF by default. Use --writeback explicitly.
"""

from __future__ import annotations

import argparse
import base64
import copy
import getpass
import hashlib
import io
import json
import os
import re
import shutil
import socket
import sqlite3
import ssl
import subprocess
import sys
import tempfile
import time
import uuid
import zipfile
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import quote, urlparse
import xml.etree.ElementTree as ET

import requests


# ---------------------------------------------------------------------
# Generic helpers
# ---------------------------------------------------------------------

def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def pretty_json(obj) -> str:
    return json.dumps(obj, indent=2, ensure_ascii=False, sort_keys=True)


def compact_json(obj) -> str:
    """Deterministic UTF-8 JSON representation used for desktop receipts."""
    return json.dumps(
        obj,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def safe_name(value: str) -> str:
    return (
        str(value)
        .replace(":", "_")
        .replace("/", "_")
        .replace("\\", "_")
    )


def path_part(value) -> str:
    return quote(str(value), safe="")


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1] if "}" in tag else tag


def parse_iso(value: str | None) -> datetime:
    if not value:
        return datetime.max.replace(tzinfo=timezone.utc)
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return datetime.max.replace(tzinfo=timezone.utc)


def find_xml_value(root: ET.Element, field_name: str) -> str | None:
    for elem in root.iter():
        if local_name(elem.tag) == field_name:
            return elem.text
    return None


def find_xml_element(
    root: ET.Element,
    field_name: str,
) -> ET.Element | None:
    for elem in root.iter():
        if local_name(elem.tag) == field_name:
            return elem
    return None


def set_xml_value(
    root: ET.Element,
    field_name: str,
    value: str,
    *,
    required: bool = True,
) -> bool:
    elem = find_xml_element(root, field_name)
    if elem is None:
        if required:
            raise ValueError(
                f"Required XML field {field_name!r} was not found."
            )
        return False
    elem.text = value
    return True


def register_namespaces_from_xml(xml_bytes: bytes) -> None:
    """
    Preserve the document's namespace prefixes when ElementTree serializes
    an updated submission.
    """
    try:
        for _event, ns in ET.iterparse(
            io.BytesIO(xml_bytes),
            events=("start-ns",),
        ):
            prefix, uri = ns
            try:
                ET.register_namespace(prefix or "", uri)
            except ValueError:
                # Reserved prefixes such as ns0 cannot be registered.
                pass
    except ET.ParseError:
        pass


def xml_namespace(tag: str) -> str:
    if tag.startswith("{") and "}" in tag:
        return tag[1:].split("}", 1)[0]
    return ""


def parse_json_text(value: str | None):
    if not value:
        return None
    try:
        return json.loads(value)
    except json.JSONDecodeError:
        return None


def first_present(obj: dict | None, *keys):
    if not isinstance(obj, dict):
        return None
    for key in keys:
        if key in obj and obj[key] not in (None, ""):
            return obj[key]
    return None


def truncate_hash(value: str | None, n: int = 16) -> str:
    if not value:
        return "-"
    return value if len(value) <= n else value[:n] + "…"


# ---------------------------------------------------------------------
# ODK Central client
# ---------------------------------------------------------------------

class Central:
    def __init__(
        self,
        base_url: str,
        token: str | None = None,
        email: str | None = None,
        password: str | None = None,
    ):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        self.session.headers["Accept"] = "application/json"

        if token:
            self.session.headers["Authorization"] = f"Bearer {token}"
            return

        if not email:
            raise ValueError("Supply --token or --email.")

        if password is None:
            password = getpass.getpass(f"Password for {email}: ")

        response = self.session.post(
            f"{self.base_url}/v1/sessions",
            json={"email": email, "password": password},
            timeout=60,
        )
        response.raise_for_status()

        token = response.json()["token"]
        self.session.headers["Authorization"] = f"Bearer {token}"

    def get_json(self, path: str, extended: bool = False):
        headers = {}
        if extended:
            headers["X-Extended-Metadata"] = "true"

        response = self.session.get(
            f"{self.base_url}{path}",
            headers=headers,
            timeout=120,
        )
        response.raise_for_status()
        return response.json()

    def get_bytes(self, path: str):
        response = self.session.get(
            f"{self.base_url}{path}",
            timeout=120,
        )
        response.raise_for_status()
        return response.content, response

    def stream_sha256(self, path: str, *, chunk_size: int = 1024 * 1024):
        """Hash a response body incrementally without storing the body."""
        response = self.session.get(
            f"{self.base_url}{path}",
            timeout=120,
            stream=True,
        )
        response.raise_for_status()

        digest = hashlib.sha256()
        total = 0
        try:
            for chunk in response.iter_content(chunk_size=chunk_size):
                if not chunk:
                    continue
                digest.update(chunk)
                total += len(chunk)
        finally:
            response.close()

        return {
            "sha256": digest.hexdigest(),
            "bytes": total,
            "etag": response.headers.get("ETag"),
            "content_type": response.headers.get("Content-Type"),
        }

    def put_xml(self, path: str, xml_bytes: bytes):
        response = self.session.put(
            f"{self.base_url}{path}",
            data=xml_bytes,
            headers={"Content-Type": "application/xml"},
            timeout=120,
        )
        if response.status_code == 409:
            return None, response
        response.raise_for_status()
        payload = response.json() if response.content else {}
        return payload, response

    def patch_json(self, path: str, payload: dict):
        response = self.session.patch(
            f"{self.base_url}{path}",
            json=payload,
            timeout=120,
        )
        response.raise_for_status()
        return response.json() if response.content else {}



def central_optional_json(
    central: Central,
    path: str,
    *,
    extended: bool = False,
) -> dict:
    """Fetch a metadata endpoint without making an unavailable endpoint fatal."""
    headers = {}
    if extended:
        headers["X-Extended-Metadata"] = "true"

    started = time.monotonic()
    try:
        response = central.session.get(
            f"{central.base_url}{path}",
            headers=headers,
            timeout=60,
        )
        elapsed_ms = round((time.monotonic() - started) * 1000, 1)
        if response.status_code == 200:
            return {
                "available": True,
                "status_code": 200,
                "elapsed_ms": elapsed_ms,
                "data": response.json(),
            }

        return {
            "available": False,
            "status_code": response.status_code,
            "elapsed_ms": elapsed_ms,
            "reason": response.text[:500],
        }
    except Exception as exc:
        return {
            "available": False,
            "status_code": None,
            "elapsed_ms": round((time.monotonic() - started) * 1000, 1),
            "reason": str(exc),
        }


def basic_actor(actor) -> dict | None:
    if not isinstance(actor, dict):
        return None
    return {
        "id": actor.get("id"),
        "type": actor.get("type"),
        "displayName": actor.get("displayName"),
        "createdAt": actor.get("createdAt"),
        "updatedAt": actor.get("updatedAt"),
        "deletedAt": actor.get("deletedAt"),
    }


def sanitize_user_records(records) -> list[dict]:
    """
    Retain account/control metadata without copying account email addresses
    into the auditor-facing evidence bundle.
    """
    if not isinstance(records, list):
        return []

    result = []
    for item in records:
        if not isinstance(item, dict):
            continue
        email = item.get("email")
        result.append({
            "id": item.get("id"),
            "type": item.get("type"),
            "displayName": item.get("displayName"),
            "createdAt": item.get("createdAt"),
            "updatedAt": item.get("updatedAt"),
            "deletedAt": item.get("deletedAt"),
            "email_sha256": (
                sha256_bytes(str(email).encode("utf-8"))
                if email
                else None
            ),
        })
    return result


SYSTEM_SECRET_KEYS = {
    "password", "token", "secret", "key", "privatekey", "private_key",
    "session", "authorization", "credential", "credentials",
}


def sanitize_system_metadata(value, *, key_hint: str | None = None):
    """
    Recursively remove likely credentials and avoid copying account email
    addresses verbatim into auditor evidence. This is intentionally
    conservative; raw API responses are not stored by this daily layer.
    """
    key_lower = (key_hint or "").lower()

    if any(secret in key_lower for secret in SYSTEM_SECRET_KEYS):
        return "[REDACTED_SECRET_METADATA]"

    if key_lower in {"email", "workemail", "work_email"}:
        if value in (None, ""):
            return None
        return {
            "redacted": True,
            "sha256": sha256_bytes(str(value).encode("utf-8")),
        }

    if isinstance(value, dict):
        return {
            str(k): sanitize_system_metadata(v, key_hint=str(k))
            for k, v in value.items()
        }

    if isinstance(value, list):
        return [
            sanitize_system_metadata(v, key_hint=key_hint)
            for v in value[:1000]
        ]

    if isinstance(value, (str, int, float, bool)) or value is None:
        return value

    return str(value)



AUDIT_DETAIL_DENY_KEYS = {
    "old", "new", "value", "values", "data", "xml", "body",
    "payload", "submission", "properties", "content",
}


def sanitize_audit_details(value, *, key_hint: str | None = None):
    """
    Conservative audit-log sanitizer.

    Keep identifiers, status/configuration metadata and counts. Drop fields
    whose names indicate that they may contain research values or document
    content. The raw server audit response is deliberately not copied into the
    auditor-facing daily bundle.
    """
    if key_hint and key_hint.lower() in AUDIT_DETAIL_DENY_KEYS:
        return "[REDACTED_FROM_AUDITOR_EVIDENCE]"

    if isinstance(value, dict):
        return {
            str(k): sanitize_audit_details(v, key_hint=str(k))
            for k, v in value.items()
            if str(k).lower() not in AUDIT_DETAIL_DENY_KEYS
        }

    if isinstance(value, list):
        return [
            sanitize_audit_details(v, key_hint=key_hint)
            for v in value[:100]
        ]

    if isinstance(value, (str, int, float, bool)) or value is None:
        return value

    return str(value)


def sanitize_server_audit_events(events) -> list[dict]:
    result = []
    if not isinstance(events, list):
        return result

    for event in events:
        if not isinstance(event, dict):
            continue
        result.append({
            "id": event.get("id"),
            "action": event.get("action"),
            "loggedAt": event.get("loggedAt"),
            "actorId": event.get("actorId"),
            "actor": basic_actor(event.get("actor")),
            "acteeId": event.get("acteeId"),
            "actee": sanitize_audit_details(event.get("actee")),
            "note": event.get("note"),
            "details": sanitize_audit_details(event.get("details")),
        })
    return result


# ---------------------------------------------------------------------
# Historical form-version archival
# ---------------------------------------------------------------------

def archive_form_version(
    central: Central,
    project_id: int,
    form_id: str,
    form_version: str | None,
    output_root: Path,
) -> dict:
    """
    Archive the exact published Form Version used by a submission version.

    Central uses the special path value '___' for a blank form version.

    Always attempts to retain:
      - form-version metadata;
      - compiled XForm XML;
      - original XLSForm (.xlsx or .xls) when Central has one;
      - form-version attachments;
      - SHA-256 hashes of all archived bytes.

    The directory is shared across submissions and therefore naturally
    deduplicated by form version.
    """
    version_value = form_version if form_version not in (None, "") else ""
    version_path = "___" if version_value == "" else path_part(version_value)

    project = path_part(project_id)
    form = path_part(form_id)

    display_version = version_value if version_value != "" else "__blank__"
    form_dir = (
        output_root
        / "_form_versions"
        / safe_name(display_version)
    )
    form_dir.mkdir(parents=True, exist_ok=True)

    manifest_path = form_dir / "form_version_manifest.json"

    # If already archived successfully during this run or a prior run,
    # use the existing local copy rather than redownloading it.
    if manifest_path.exists():
        try:
            existing = json.loads(
                manifest_path.read_text(encoding="utf-8")
            )
            if existing.get("archive_complete"):
                return existing
        except Exception:
            pass

    base = (
        f"/v1/projects/{project}"
        f"/forms/{form}"
        f"/versions/{version_path}"
    )

    manifest = {
        "xml_form_id": form_id,
        "form_version": version_value,
        "central_version_path": version_path,
        "archive_complete": False,
        "metadata": None,
        "xform_xml": None,
        "xlsform": None,
        "attachments": [],
    }

    # Metadata
    metadata = central.get_json(base, extended=True)
    manifest["metadata"] = metadata
    (form_dir / "form_version_metadata.json").write_text(
        pretty_json(metadata),
        encoding="utf-8",
    )

    # Compiled XForm XML: this should be available for every published form.
    xml_bytes, xml_response = central.get_bytes(f"{base}.xml")
    xml_path = form_dir / "form.xml"
    xml_path.write_bytes(xml_bytes)
    manifest["xform_xml"] = {
        "file": xml_path.name,
        "bytes": len(xml_bytes),
        "sha256": sha256_bytes(xml_bytes),
        "etag": xml_response.headers.get("ETag"),
        "content_type": xml_response.headers.get("Content-Type"),
    }

    # Original XLSForm, where Central has retained one.
    # We try .xlsx first, then legacy .xls. A form created from raw XForm XML
    # legitimately will not have an XLSForm to retrieve.
    xlsform_info = {
        "available": False,
        "reason": None,
    }

    for extension, filename in (
        (".xlsx", "form.xlsx"),
        (".xls", "form.xls"),
    ):
        response = central.session.get(
            f"{central.base_url}{base}{extension}",
            timeout=120,
        )

        if response.status_code == 200:
            data = response.content
            destination = form_dir / filename
            destination.write_bytes(data)

            xlsform_info = {
                "available": True,
                "file": filename,
                "bytes": len(data),
                "sha256": sha256_bytes(data),
                "etag": response.headers.get("ETag"),
                "content_type": response.headers.get("Content-Type"),
            }
            break

        if response.status_code not in (404, 405):
            response.raise_for_status()

    if not xlsform_info["available"]:
        xlsform_info["reason"] = (
            "Central did not expose an XLS/XLSX source for this published "
            "form version; the compiled XForm XML has still been archived."
        )

    manifest["xlsform"] = xlsform_info

    # Form-version attachments.
    attachments = central.get_json(f"{base}/attachments")
    (form_dir / "form_attachments.json").write_text(
        pretty_json(attachments),
        encoding="utf-8",
    )

    attachment_dir = form_dir / "attachments"
    attachment_dir.mkdir(exist_ok=True)

    archived_attachments = []

    for attachment in attachments:
        filename = attachment.get("name")
        exists = bool(attachment.get("exists"))

        item = {
            "name": filename,
            "exists": exists,
        }

        if filename and exists:
            encoded_filename = path_part(filename)
            data, response = central.get_bytes(
                f"{base}/attachments/{encoded_filename}"
            )
            destination = attachment_dir / Path(filename).name
            destination.write_bytes(data)

            item.update({
                "file": str(Path("attachments") / Path(filename).name),
                "bytes": len(data),
                "sha256": sha256_bytes(data),
                "etag": response.headers.get("ETag"),
                "content_type": response.headers.get("Content-Type"),
            })

        archived_attachments.append(item)

    manifest["attachments"] = archived_attachments
    manifest["archive_complete"] = True

    manifest_path.write_text(
        pretty_json(manifest),
        encoding="utf-8",
    )

    return manifest


# ---------------------------------------------------------------------
# MethodMesh extraction / recipe-aware reconstruction
# ---------------------------------------------------------------------

def extract_methodmesh(root: ET.Element) -> dict:
    payload_hash = find_xml_value(root, "showcase_payload_sha256")
    payload_canonical = find_xml_value(root, "showcase_payload_canonical")
    image_hash = find_xml_value(root, "photo_redacted_image_sha256")
    image_name = find_xml_value(root, "photo_redacted_image_uri")

    attestation_text = find_xml_value(
        root, "attestation_methodmesh_full_json"
    )
    attestation = parse_json_text(attestation_text)

    event_payload_hash = first_present(
        attestation,
        "event_payload_hash",
        "payload_hash",
    )

    attestation_hash = first_present(
        attestation,
        "attestation_hash",
        "event_hash",
    )

    previous_hash = first_present(
        attestation,
        "previous_attestation_hash",
        "previous_hash",
    )

    tsa_status = first_present(
        attestation,
        "trusted_timestamp_status",
        "tsa_status",
    )

    tsa_time = first_present(
        attestation,
        "trusted_timestamp_time_iso",
        "trusted_timestamp_time",
        "tsa_time",
    )

    tsa_attested_hash = first_present(
        attestation,
        "trusted_timestamp_attested_hash",
        "tsa_attested_hash",
    )

    public_key_id = first_present(
        attestation,
        "public_key_id",
        "signing_key_id",
    )

    device_counter = first_present(
        attestation,
        "device_monotonic_counter",
        "monotonic_counter",
    )

    recipe = first_present(attestation, "commitment_recipe")
    if isinstance(recipe, str):
        recipe_parsed = parse_json_text(recipe)
        recipe_raw = recipe
    elif isinstance(recipe, dict):
        recipe_parsed = recipe
        recipe_raw = None
    else:
        recipe_parsed = None
        recipe_raw = None

    recipe_sha256 = first_present(
        attestation,
        "commitment_recipe_sha256",
    )

    closeout_status = find_xml_value(
        root, "attestation_methodmesh_closeout_status"
    )

    desktop_receipt_text = find_xml_value(
        root, "desktop_audit_receipt_json"
    )
    desktop_receipt = parse_json_text(desktop_receipt_text)

    payload_match = None
    if payload_hash and event_payload_hash:
        payload_match = payload_hash == event_payload_hash

    tsa_binding_match = None
    if attestation_hash and tsa_attested_hash:
        tsa_binding_match = attestation_hash == tsa_attested_hash

    recipe_hash_check = None
    if recipe_raw is not None and recipe_sha256:
        recipe_hash_check = (
            sha256_bytes(recipe_raw.encode("utf-8")) == recipe_sha256
        )

    return {
        "showcase_payload_canonical": payload_canonical,
        "showcase_payload_sha256": payload_hash,
        "photo_redacted_image_sha256": image_hash,
        "photo_attachment_field": image_name,
        "attestation_closeout_status": closeout_status,
        "attestation_present": isinstance(attestation, dict),
        "attestation_event_payload_hash": event_payload_hash,
        "attestation_hash": attestation_hash,
        "previous_attestation_hash": previous_hash,
        "public_key_id": public_key_id,
        "device_monotonic_counter": device_counter,
        "trusted_timestamp_status": tsa_status,
        "trusted_timestamp_time_iso": tsa_time,
        "trusted_timestamp_attested_hash": tsa_attested_hash,
        "payload_matches_attestation": payload_match,
        "tsa_attests_attestation_hash": tsa_binding_match,
        "commitment_recipe": recipe_parsed,
        "commitment_recipe_raw": recipe_raw,
        "commitment_recipe_sha256": recipe_sha256,
        "commitment_recipe_hash_matches_raw": recipe_hash_check,
        "desktop_audit_receipt": desktop_receipt,
        "attestation_json": attestation,
    }



def flatten_xml_leaf_values(root: ET.Element) -> dict:
    """
    Return all leaf XML values grouped by local element name.

    Most XLSForm field names are globally unique, so values normally appear
    once. Lists are retained for duplicate local names rather than silently
    discarding information.
    """
    grouped = {}

    for elem in root.iter():
        if len(list(elem)) != 0:
            continue

        name = local_name(elem.tag)
        value = "" if elem.text is None else str(elem.text)

        grouped.setdefault(name, []).append(value)

    result = {}
    for name, values in grouped.items():
        result[name] = values[0] if len(values) == 1 else values

    return result


def write_reconstruction_debug(
    version_dir: Path,
    root: ET.Element | None,
    methodmesh: dict,
    reconstruction: dict | None,
    classification: str,
    xml_sha256: str,
):
    """
    Write transparent per-version diagnostics for independent debugging.

    WARNING: these files can contain the full submitted field values,
    transcript text and MethodMesh JSON evidence. Treat the audit directory
    with the same confidentiality controls as the original ODK dataset.
    """
    fields = (
        flatten_xml_leaf_values(root)
        if root is not None
        else {}
    )

    (version_dir / "source_record_fields.json").write_text(
        pretty_json(fields),
        encoding="utf-8",
    )

    recipe = methodmesh.get("commitment_recipe")
    if recipe is not None:
        (version_dir / "commitment_recipe.json").write_text(
            pretty_json(recipe),
            encoding="utf-8",
        )

    debug = {
        "classification": classification,
        "xml_sha256": xml_sha256,
        "stored_showcase_payload_sha256":
            methodmesh.get("showcase_payload_sha256"),
        "attestation_event_payload_hash":
            methodmesh.get("attestation_event_payload_hash"),
        "commitment_recipe_sha256":
            methodmesh.get("commitment_recipe_sha256"),
        "commitment_recipe_hash_matches_raw":
            methodmesh.get("commitment_recipe_hash_matches_raw"),
        "cryptographic_verification":
            methodmesh.get("cryptographic_verification"),
        "recipe": recipe,
        "reconstruction": reconstruction,
    }

    (version_dir / "reconstruction_debug.json").write_text(
        pretty_json(debug),
        encoding="utf-8",
    )

    if reconstruction and reconstruction.get("available"):
        reconstructed_payload = reconstruction.get(
            "reconstructed_payload"
        )
        stored_member_payload = reconstruction.get(
            "stored_member_payload"
        )

        if reconstructed_payload is not None:
            (
                version_dir / "reconstructed_canonical_payload.txt"
            ).write_text(
                reconstructed_payload,
                encoding="utf-8",
            )

        if stored_member_payload is not None:
            (
                version_dir / "stored_member_payload.txt"
            ).write_text(
                stored_member_payload,
                encoding="utf-8",
            )

        lines = [
            "METHODMESH RECONSTRUCTION DEBUG",
            "=" * 78,
            f"classification: {classification}",
            f"xml_sha256: {xml_sha256}",
            (
                "stored_showcase_payload_sha256: "
                f"{methodmesh.get('showcase_payload_sha256')}"
            ),
            (
                "attestation_event_payload_hash: "
                f"{methodmesh.get('attestation_event_payload_hash')}"
            ),
            (
                "rebuilt_payload_sha256: "
                f"{reconstruction.get('reconstructed_payload_sha256')}"
            ),
            (
                "stored_member_payload_sha256: "
                f"{reconstruction.get('stored_member_payload_sha256')}"
            ),
            "",
            "MEMBER-BY-MEMBER CHECKS",
            "-" * 78,
        ]

        for i, member in enumerate(
            reconstruction.get("members") or [],
            start=1,
        ):
            lines.extend([
                f"[{i}] {member.get('path')}",
                f"  commitment: {member.get('commitment')}",
                f"  type: {member.get('type')}",
                f"  source_field: {member.get('source_field')}",
                f"  artifact_field: {member.get('artifact_field')}",
                f"  source: {member.get('source')}",
                f"  source_raw_value: {member.get('source_raw_value')}",
                f"  stored_value: {member.get('stored_value')}",
                (
                    "  source_derived_value: "
                    f"{member.get('source_derived_value')}"
                ),
                (
                    "  stored_matches_source: "
                    f"{member.get('stored_matches_source')}"
                ),
                f"  source_ok: {member.get('source_ok')}",
                f"  error: {member.get('error')}",
                "",
            ])

        lines.extend([
            "RECONSTRUCTED CANONICAL PAYLOAD",
            "-" * 78,
            reconstructed_payload or "",
            "",
            "STORED-MEMBER CANONICAL PAYLOAD",
            "-" * 78,
            stored_member_payload or "",
            "",
        ])

        (version_dir / "reconstruction_debug.txt").write_text(
            "\n".join(lines),
            encoding="utf-8",
        )


def attachment_map(attachments: list[dict]) -> dict[str, dict]:
    return {
        item.get("name"): item
        for item in attachments
        if item.get("name")
    }


def reconstruct_from_recipe(
    root: ET.Element,
    recipe: dict | None,
    attachments: list[dict],
) -> dict:
    """
    Independently reconstruct ordered-kv-v1 from source data.

    v1 rules implemented here:
      value
        -> the XML value at member.path

      artifact-bytes-sha256
        -> SHA-256 of the archived attachment named by the XML field specified
           by member.artifact_field

      text-utf8-sha256
        -> SHA-256 of UTF-8 text from member.source_field

      json-utf8-sha256
        -> SHA-256 of UTF-8 text from member.source_field

    The canonical payload is:
        path=value|path=value|...

    Member order is exactly recipe.members order.

    For indirect commitments, the source-derived digest is used in the
    reconstructed canonical payload. The stored digest field at member.path is
    separately compared with that source-derived value.
    """
    if not isinstance(recipe, dict):
        return {
            "available": False,
            "reason": "No parseable commitment_recipe in attestation.",
        }

    if recipe.get("schema") != "methodmesh.commitment_recipe.v1":
        return {
            "available": False,
            "reason": f"Unsupported recipe schema: {recipe.get('schema')!r}",
        }

    if recipe.get("canonicalization") != "ordered-kv-v1":
        return {
            "available": False,
            "reason": (
                "Unsupported canonicalization: "
                f"{recipe.get('canonicalization')!r}"
            ),
        }

    if str(recipe.get("hash_algorithm")).upper() != "SHA-256":
        return {
            "available": False,
            "reason": (
                "Unsupported hash algorithm: "
                f"{recipe.get('hash_algorithm')!r}"
            ),
        }

    members = recipe.get("members")
    if not isinstance(members, list):
        return {
            "available": False,
            "reason": "Recipe members is not an array.",
        }

    files = attachment_map(attachments)
    member_results = []
    canonical_parts = []
    stored_parts = []
    errors = []

    for member in members:
        if not isinstance(member, dict):
            errors.append("Recipe contains a non-object member.")
            continue

        path = member.get("path")
        commitment = member.get("commitment")

        if not path or not commitment:
            errors.append(f"Invalid member: {member!r}")
            continue

        stored_value = find_xml_value(root, path)
        stored_value = "" if stored_value is None else str(stored_value)

        derived_value = None
        source_description = None
        source_raw_value = None
        source_field_name = None
        artifact_field_name = None
        source_ok = True
        source_error = None

        if commitment == "value":
            derived_value = stored_value
            source_description = path
            source_raw_value = stored_value
            source_field_name = path

        elif commitment in ("text-utf8-sha256", "json-utf8-sha256"):
            source_field = member.get("source_field")
            source_field_name = source_field
            if not source_field:
                source_ok = False
                source_error = (
                    f"{path}: {commitment} requires source_field"
                )
            else:
                source_text = find_xml_value(root, source_field)
                if source_text is None:
                    source_text = ""
                source_raw_value = str(source_text)
                derived_value = sha256_bytes(
                    source_raw_value.encode("utf-8")
                )
                source_description = source_field

        elif commitment == "artifact-bytes-sha256":
            artifact_field = member.get("artifact_field")
            artifact_field_name = artifact_field
            if not artifact_field:
                source_ok = False
                source_error = (
                    f"{path}: artifact-bytes-sha256 requires artifact_field"
                )
            else:
                filename = find_xml_value(root, artifact_field)
                source_raw_value = filename
                file_info = files.get(filename)
                if not filename:
                    source_ok = False
                    source_error = (
                        f"{path}: artifact field {artifact_field!r} is blank"
                    )
                elif not file_info or not file_info.get("sha256"):
                    source_ok = False
                    source_error = (
                        f"{path}: archived attachment {filename!r} unavailable"
                    )
                else:
                    derived_value = file_info["sha256"]
                    source_description = f"{artifact_field} -> {filename}"

        else:
            source_ok = False
            source_error = (
                f"{path}: unsupported commitment type {commitment!r}"
            )

        if source_error:
            errors.append(source_error)

        stored_matches_source = None
        if source_ok and derived_value is not None:
            stored_matches_source = stored_value == derived_value

        # Use source-derived value wherever possible. If reconstruction failed,
        # retain the stored value only so the diagnostic payload is inspectable,
        # but mark overall reconstruction invalid below.
        canonical_value = (
            derived_value
            if source_ok and derived_value is not None
            else stored_value
        )

        canonical_parts.append(f"{path}={canonical_value}")
        stored_parts.append(f"{path}={stored_value}")

        member_results.append({
            "path": path,
            "type": member.get("type"),
            "commitment": commitment,
            "source": source_description,
            "source_field": source_field_name,
            "artifact_field": artifact_field_name,
            "source_raw_value": source_raw_value,
            "stored_value": stored_value,
            "source_derived_value": derived_value,
            "stored_matches_source": stored_matches_source,
            "source_ok": source_ok,
            "error": source_error,
        })

    reconstructed_payload = "|".join(canonical_parts)
    stored_member_payload = "|".join(stored_parts)

    return {
        "available": True,
        "complete": len(errors) == 0,
        "errors": errors,
        "member_count": len(member_results),
        "members": member_results,
        "reconstructed_payload": reconstructed_payload,
        "reconstructed_payload_sha256": sha256_bytes(
            reconstructed_payload.encode("utf-8")
        ),
        "stored_member_payload": stored_member_payload,
        "stored_member_payload_sha256": sha256_bytes(
            stored_member_payload.encode("utf-8")
        ),
        "all_indirect_commitments_match": all(
            x["stored_matches_source"] in (True, None)
            for x in member_results
        ) and len(errors) == 0,
    }


def classify_version(methodmesh: dict, reconstruction: dict | None) -> str:
    """
    Prefer independent source reconstruction when a v1 recipe is available.
    Fall back to the legacy stored-hash relationship for older development
    submissions that predate commitment recipes.
    """
    payload = methodmesh.get("showcase_payload_sha256")
    att_present = methodmesh.get("attestation_present")
    attested = methodmesh.get("attestation_event_payload_hash")

    if not payload:
        return "NO_METHODMESH_PAYLOAD"

    if not att_present:
        return "METHODMESH_DATA_WITHOUT_ATTESTATION"

    if reconstruction and reconstruction.get("available"):
        if not reconstruction.get("complete"):
            return "RECIPE_RECONSTRUCTION_ERROR"

        calculated = reconstruction.get("reconstructed_payload_sha256")

        if calculated and attested and calculated == attested:
            if reconstruction.get("all_indirect_commitments_match"):
                return "INDEPENDENTLY_VERIFIED_ATTESTED_VERSION"
            return "SOURCE_DIGEST_MISMATCH"

        if calculated and attested and calculated != attested:
            return "EDITED_AFTER_ATTESTATION"

        return "ATTESTATION_RELATIONSHIP_UNRESOLVED"

    # Legacy fallback.
    payload_match = methodmesh.get("payload_matches_attestation")
    if payload_match is True:
        return "VALID_ATTESTED_VERSION_LEGACY_NO_RECIPE"
    if payload_match is False:
        return "EDITED_AFTER_ATTESTATION_LEGACY_NO_RECIPE"

    return "ATTESTATION_RELATIONSHIP_UNRESOLVED"




# ---------------------------------------------------------------------
# Independent MethodMesh signature + RFC3161 verification
# ---------------------------------------------------------------------

# FreeTSA publishes these certificate-file SHA-256 values on its service page.
# The service states that its TSA certificate was updated on 2026-03-16.
#
# We deliberately pin the downloaded bytes. If FreeTSA rotates certificates,
# verification fails closed until these pins are reviewed/updated, or the
# operator supplies explicit --tsa-ca-file and --tsa-cert-file trust material.
FREETSA_CA_URL = "https://freetsa.org/files/cacert.pem"
FREETSA_TSA_CERT_URL = "https://freetsa.org/files/tsa.crt"
FREETSA_CA_FILE_SHA256 = (
    "2151b61137ffa86bf664691ba67e7da0b19f98c758e3d228d5d8ebf27e044438"
)
FREETSA_TSA_FILE_SHA256 = (
    "8bfb0305bb64e2571ca507552ef3245cb1c2fee8728e0ff8689225081ea13467"
)


def parse_iso_strict(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return parsed.astimezone(timezone.utc)
    except (ValueError, TypeError):
        return None


def normalize_hex_integer(value: str | None) -> str | None:
    if value is None:
        return None
    raw = str(value).strip().lower()
    if raw.startswith("0x"):
        raw = raw[2:]
    raw = raw.lstrip("0")
    return raw or "0"


def methodmesh_attestation_canonical_payload(
    attestation: dict,
) -> str:
    """
    Reconstruct MethodMesh attestation schema v4's exact signed UTF-8 payload.

    This contract was verified against a real MethodMesh attestation:
    commitment_recipe_sha256 is signed immediately after event_payload_mode.

    Fail closed for any other schema rather than guessing a new canonical form.
    """
    schema = str(attestation.get("attestation_schema_version", ""))
    if schema != "4":
        raise ValueError(
            "Unsupported MethodMesh attestation schema for independent "
            f"signature verification: {schema!r}"
        )

    def value(key: str, *fallbacks: str) -> str:
        for candidate in (key, *fallbacks):
            if candidate in attestation:
                raw = attestation.get(candidate)
                return "" if raw is None else str(raw)
        return ""

    recipe_sha = value("commitment_recipe_sha256")
    if not re.fullmatch(r"[0-9a-fA-F]{64}", recipe_sha):
        raise ValueError(
            "Schema v4 cryptographic verification requires "
            "commitment_recipe_sha256."
        )

    return "\n".join([
        "attestation_schema_version=4",
        f"attestation_id={value('attestation_id')}",
        f"study_id={value('study_id')}",
        f"operator_id={value('operator_id')}",
        f"subject_id={value('subject_id', 'subject_ref')}",
        f"event_type={value('event_type')}",
        f"event_payload_hash={value('event_payload_hash', 'payload_hash')}",
        f"event_payload_mode={value('event_payload_mode')}",
        f"commitment_recipe_sha256={recipe_sha.lower()}",
        f"verification_method={value('verification_method')}",
        (
            "verification_evidence_format="
            f"{value('verification_evidence_format')}"
        ),
        (
            "verification_evidence_hash="
            f"{value('verification_evidence_hash')}"
        ),
        f"device_event_time_iso={value('device_event_time_iso')}",
        (
            "device_monotonic_counter="
            f"{value('device_monotonic_counter', 'monotonic_counter')}"
        ),
        (
            "previous_attestation_hash="
            f"{value('previous_attestation_hash', 'previous_hash')}"
        ),
        f"public_key_id={value('public_key_id', 'signing_key_id')}",
        "hash_algorithm=SHA-256",
        "signature_algorithm=SHA256withECDSA",
    ])


def run_command(
    args: list[str],
    *,
    input_bytes: bytes | None = None,
) -> dict:
    try:
        completed = subprocess.run(
            args,
            input=input_bytes,
            capture_output=True,
            timeout=60,
        )
        return {
            "available": True,
            "returncode": completed.returncode,
            "stdout": completed.stdout.decode(
                "utf-8", errors="replace"
            ),
            "stderr": completed.stderr.decode(
                "utf-8", errors="replace"
            ),
            "command": args,
        }
    except FileNotFoundError:
        return {
            "available": False,
            "returncode": None,
            "stdout": "",
            "stderr": f"Executable not found: {args[0]}",
            "command": args,
        }
    except subprocess.TimeoutExpired as exc:
        return {
            "available": True,
            "returncode": None,
            "stdout": (
                (exc.stdout or b"").decode("utf-8", errors="replace")
                if isinstance(exc.stdout, bytes)
                else (exc.stdout or "")
            ),
            "stderr": "Command timed out",
            "command": args,
        }


def pinned_download(
    *,
    url: str,
    destination: Path,
    expected_sha256: str,
) -> dict:
    destination.parent.mkdir(parents=True, exist_ok=True)

    if destination.exists():
        data = destination.read_bytes()
        actual = sha256_bytes(data)
        if actual == expected_sha256:
            return {
                "path": str(destination),
                "sha256": actual,
                "source": "PINNED_CACHE",
                "url": url,
            }

    # IMPORTANT: use a fresh requests call, never the Central session, so the
    # ODK bearer token cannot leak to an external timestamp authority.
    response = requests.get(
        url,
        timeout=30,
        headers={
            "User-Agent": "MethodMesh-Desktop-Verifier/1.0",
            "Accept": "*/*",
        },
    )
    response.raise_for_status()
    data = response.content
    actual = sha256_bytes(data)

    if actual != expected_sha256:
        raise ValueError(
            "Downloaded TSA trust material failed its pinned SHA-256: "
            f"{url}; expected={expected_sha256}; actual={actual}"
        )

    temp_path = destination.with_suffix(destination.suffix + ".tmp")
    temp_path.write_bytes(data)
    temp_path.replace(destination)

    return {
        "path": str(destination),
        "sha256": actual,
        "source": "PINNED_DOWNLOAD",
        "url": url,
    }


def resolve_tsa_trust_material(
    *,
    attestation: dict,
    output_root: Path,
    tsa_ca_file: str | None,
    tsa_cert_file: str | None,
    allow_tsa_download: bool,
) -> dict:
    """
    Resolve independently trusted TSA certificate material.

    Explicit operator-supplied files take precedence. Otherwise FreeTSA is
    supported through pinned HTTPS downloads and a persistent local cache.
    """
    if bool(tsa_ca_file) != bool(tsa_cert_file):
        return {
            "available": False,
            "reason": (
                "Provide both --tsa-ca-file and --tsa-cert-file, or neither."
            ),
        }

    if tsa_ca_file and tsa_cert_file:
        ca_path = Path(tsa_ca_file).expanduser().resolve()
        cert_path = Path(tsa_cert_file).expanduser().resolve()

        if not ca_path.exists() or not cert_path.exists():
            return {
                "available": False,
                "reason": "Explicit TSA trust file does not exist.",
                "ca_file": str(ca_path),
                "tsa_cert_file": str(cert_path),
            }

        return {
            "available": True,
            "source": "OPERATOR_SUPPLIED",
            "ca_file": str(ca_path),
            "tsa_cert_file": str(cert_path),
            "ca_file_sha256": sha256_bytes(ca_path.read_bytes()),
            "tsa_cert_file_sha256": sha256_bytes(cert_path.read_bytes()),
        }

    authority = str(
        attestation.get("trusted_timestamp_authority") or ""
    ).strip()
    parsed = urlparse(authority)
    hostname = (parsed.hostname or "").lower()

    if hostname not in {"freetsa.org", "www.freetsa.org"}:
        return {
            "available": False,
            "reason": (
                "No built-in trusted certificate policy exists for TSA "
                f"authority {authority!r}. Supply --tsa-ca-file and "
                "--tsa-cert-file."
            ),
            "authority": authority,
        }

    if not allow_tsa_download:
        return {
            "available": False,
            "reason": (
                "FreeTSA trust material is not operator-supplied and "
                "automatic pinned download is disabled."
            ),
            "authority": authority,
        }

    trust_dir = output_root / "_tsa_trust" / "freetsa"
    ca_path = trust_dir / "cacert.pem"
    tsa_path = trust_dir / "tsa.crt"

    try:
        ca_info = pinned_download(
            url=FREETSA_CA_URL,
            destination=ca_path,
            expected_sha256=FREETSA_CA_FILE_SHA256,
        )
        tsa_info = pinned_download(
            url=FREETSA_TSA_CERT_URL,
            destination=tsa_path,
            expected_sha256=FREETSA_TSA_FILE_SHA256,
        )
    except Exception as exc:
        return {
            "available": False,
            "reason": f"Could not obtain pinned FreeTSA trust material: {exc}",
            "authority": authority,
        }

    return {
        "available": True,
        "source": "FREETSA_PINNED",
        "authority": authority,
        "ca_file": str(ca_path),
        "tsa_cert_file": str(tsa_path),
        "ca_file_sha256": ca_info["sha256"],
        "tsa_cert_file_sha256": tsa_info["sha256"],
        "ca_cache_source": ca_info["source"],
        "tsa_cache_source": tsa_info["source"],
    }


def parse_openssl_timestamp_text(text: str) -> dict:
    serial = None
    timestamp = None
    hash_algorithm = None

    match = re.search(
        r"^Serial number:\s*(0x[0-9A-Fa-f]+|[0-9A-Fa-f]+)\s*$",
        text,
        flags=re.MULTILINE,
    )
    if match:
        serial = normalize_hex_integer(match.group(1))

    match = re.search(
        r"^Hash Algorithm:\s*([A-Za-z0-9_-]+)\s*$",
        text,
        flags=re.MULTILINE,
    )
    if match:
        hash_algorithm = match.group(1).lower()

    match = re.search(
        r"^Time stamp:\s*(.+?)\s*$",
        text,
        flags=re.MULTILINE,
    )
    if match:
        raw_time = " ".join(match.group(1).split())
        try:
            timestamp = datetime.strptime(
                raw_time,
                "%b %d %H:%M:%S %Y GMT",
            ).replace(tzinfo=timezone.utc).isoformat()
        except ValueError:
            timestamp = None

    return {
        "serial_hex": serial,
        "time_iso": timestamp,
        "hash_algorithm": hash_algorithm,
    }


def crypto_receipt_summary(result: dict | None) -> dict | None:
    if not isinstance(result, dict):
        return None
    keys = [
        "overall_valid",
        "verification_status",
        "verification_complete",
        "canonical_payload_sha256",
        "public_key_id_valid",
        "ecdsa_signature_valid",
        "attestation_hash_valid",
        "timestamp_token_sha256_valid",
        "timestamp_attested_hash_valid",
        "rfc3161_signature_and_chain_valid",
        "timestamp_serial_matches_json",
        "timestamp_time_matches_json",
        "timestamp_precedes_central_creation",
        "tsa_trust_source",
        "openssl_version",
    ]
    return {key: result.get(key) for key in keys}


def verify_methodmesh_attestation_crypto(
    *,
    methodmesh: dict,
    version_dir: Path,
    output_root: Path,
    central_created_at: str | None,
    tsa_ca_file: str | None,
    tsa_cert_file: str | None,
    allow_tsa_download: bool,
) -> dict:
    """
    Independently verify the original MethodMesh attestation.

    Required checks:
      1. exact recipe string hashes to commitment_recipe_sha256;
      2. public key ID matches MethodMesh's key-ID derivation;
      3. SHA256withECDSA verifies over schema-v4 canonical UTF-8 bytes;
      4. attestation_hash is recomputed from canonical + signature;
      5. RFC3161 token bytes hash to trusted_timestamp_token_sha256;
      6. RFC3161 message imprint / CMS signature / certificate chain verify
         with independently trusted TSA certificate material;
      7. token serial and timestamp agree with the exported JSON fields;
      8. TSA time is not later than Central's creation time.

    No attestation field that merely says "verified" is accepted as proof.
    """
    evidence_dir = version_dir / "_crypto_verification"
    evidence_dir.mkdir(parents=True, exist_ok=True)

    result = {
        "overall_valid": False,
        "errors": [],
        "warnings": [],
        "verification_basis": (
            "independent_ecdsa_plus_rfc3161_certificate_chain"
        ),
    }

    attestation = methodmesh.get("attestation_json")
    if not isinstance(attestation, dict):
        result["errors"].append("ATTESTATION_JSON_ABSENT")
        return result

    # Fail closed on algorithm/schema changes.
    declared_checks = {
        "attestation_schema_version_is_4":
            str(attestation.get("attestation_schema_version")) == "4",
        "hash_algorithm_is_sha256":
            attestation.get("hash_algorithm") == "SHA-256",
        "signature_algorithm_is_sha256_ecdsa":
            attestation.get("signature_algorithm") == "SHA256withECDSA",
        "public_key_algorithm_is_ec":
            attestation.get("public_key_algorithm") == "EC",
        "public_key_format_is_x509":
            attestation.get("public_key_format") == "X.509",
    }
    result.update(declared_checks)

    recipe_raw = methodmesh.get("commitment_recipe_raw")
    recipe_sha = methodmesh.get("commitment_recipe_sha256")
    recipe_hash_valid = bool(
        isinstance(recipe_raw, str)
        and isinstance(recipe_sha, str)
        and sha256_bytes(recipe_raw.encode("utf-8"))
        == recipe_sha.lower()
    )
    result["commitment_recipe_hash_valid"] = recipe_hash_valid

    try:
        canonical = methodmesh_attestation_canonical_payload(attestation)
        canonical_bytes = canonical.encode("utf-8")
        result["canonical_payload_sha256"] = sha256_bytes(canonical_bytes)
        (evidence_dir / "attestation_canonical_payload.txt").write_text(
            canonical,
            encoding="utf-8",
        )
    except Exception as exc:
        result["errors"].append(
            f"CANONICAL_PAYLOAD_RECONSTRUCTION_FAILED:{exc}"
        )
        (evidence_dir / "crypto_verification.json").write_text(
            pretty_json(result),
            encoding="utf-8",
        )
        return result

    # Decode key/signature strictly.
    try:
        public_key_b64 = str(attestation["public_key_base64"])
        public_key_der = base64.b64decode(
            public_key_b64,
            validate=True,
        )
        signature_der = base64.b64decode(
            str(attestation["signature"]),
            validate=True,
        )
    except Exception as exc:
        result["errors"].append(f"BASE64_DECODE_FAILED:{exc}")
        (evidence_dir / "crypto_verification.json").write_text(
            pretty_json(result),
            encoding="utf-8",
        )
        return result

    public_key_id_calculated = sha256_bytes(
        public_key_b64.encode("utf-8")
    )[:16]
    public_key_id_stored = str(attestation.get("public_key_id") or "")
    result["public_key_id_calculated"] = public_key_id_calculated
    result["public_key_id_stored"] = public_key_id_stored
    result["public_key_id_valid"] = (
        public_key_id_calculated == public_key_id_stored
    )

    key_der_path = evidence_dir / "methodmesh_public_key.der"
    key_pem_path = evidence_dir / "methodmesh_public_key.pem"
    signature_path = evidence_dir / "methodmesh_signature.der"
    canonical_path = evidence_dir / "attestation_canonical_payload.txt"

    key_der_path.write_bytes(public_key_der)
    signature_path.write_bytes(signature_der)

    key_convert = run_command([
        "openssl", "pkey",
        "-pubin",
        "-inform", "DER",
        "-in", str(key_der_path),
        "-out", str(key_pem_path),
    ])
    result["openssl_public_key_parse"] = key_convert

    signature_verify = {
        "available": False,
        "returncode": None,
        "stdout": "",
        "stderr": "Public key conversion failed.",
    }

    if (
        key_convert.get("available")
        and key_convert.get("returncode") == 0
    ):
        signature_verify = run_command([
            "openssl", "dgst",
            "-sha256",
            "-verify", str(key_pem_path),
            "-signature", str(signature_path),
            str(canonical_path),
        ])

    result["openssl_ecdsa_verify"] = signature_verify
    result["ecdsa_signature_valid"] = (
        signature_verify.get("available")
        and signature_verify.get("returncode") == 0
        and "Verified OK" in signature_verify.get("stdout", "")
    )

    signature_b64 = str(attestation.get("signature") or "")
    calculated_attestation_hash = sha256_bytes(
        (
            canonical
            + "\nsignature="
            + signature_b64
        ).encode("utf-8")
    )
    stored_attestation_hash = str(
        attestation.get("attestation_hash") or ""
    ).lower()

    result["attestation_hash_calculated"] = calculated_attestation_hash
    result["attestation_hash_stored"] = stored_attestation_hash
    result["attestation_hash_valid"] = (
        calculated_attestation_hash == stored_attestation_hash
    )

    declared_attested_hash = str(
        attestation.get("trusted_timestamp_attested_hash") or ""
    ).lower()
    result["timestamp_attested_hash_valid"] = (
        declared_attested_hash == calculated_attestation_hash
    )

    # RFC3161 token bytes.
    try:
        token_bytes = base64.b64decode(
            str(attestation["trusted_timestamp_token_base64"]),
            validate=True,
        )
    except Exception as exc:
        result["errors"].append(
            f"TIMESTAMP_TOKEN_BASE64_DECODE_FAILED:{exc}"
        )
        token_bytes = b""

    token_path = evidence_dir / "timestamp_token.tsr"
    token_path.write_bytes(token_bytes)

    token_sha_calculated = sha256_bytes(token_bytes) if token_bytes else None
    token_sha_stored = str(
        attestation.get("trusted_timestamp_token_sha256") or ""
    ).lower()

    result["timestamp_token_sha256_calculated"] = token_sha_calculated
    result["timestamp_token_sha256_stored"] = token_sha_stored
    result["timestamp_token_sha256_valid"] = bool(
        token_sha_calculated
        and token_sha_calculated == token_sha_stored
    )

    openssl_version = run_command(["openssl", "version"])
    result["openssl_version"] = (
        openssl_version.get("stdout", "").strip()
        if openssl_version.get("returncode") == 0
        else None
    )

    token_parse = run_command([
        "openssl", "ts",
        "-reply",
        "-token_in",
        "-in", str(token_path),
        "-text",
    ])
    result["openssl_timestamp_parse"] = token_parse

    parsed_token = (
        parse_openssl_timestamp_text(token_parse.get("stdout", ""))
        if token_parse.get("returncode") == 0
        else {}
    )
    result["timestamp_token_fields"] = parsed_token

    serial_json = normalize_hex_integer(
        attestation.get("trusted_timestamp_serial")
    )
    result["timestamp_serial_matches_json"] = bool(
        serial_json
        and parsed_token.get("serial_hex")
        and serial_json == parsed_token.get("serial_hex")
    )

    json_tsa_time = parse_iso_strict(
        attestation.get("trusted_timestamp_time_iso")
    )
    token_tsa_time = parse_iso_strict(parsed_token.get("time_iso"))

    result["timestamp_time_matches_json"] = bool(
        json_tsa_time
        and token_tsa_time
        and abs((json_tsa_time - token_tsa_time).total_seconds()) < 1
    )

    central_time = parse_iso_strict(central_created_at)
    result["timestamp_precedes_central_creation"] = (
        None
        if central_time is None or token_tsa_time is None
        else token_tsa_time <= central_time
    )

    device_time = parse_iso_strict(
        attestation.get("device_event_time_iso")
    )
    result["device_to_tsa_offset_seconds"] = (
        None
        if device_time is None or token_tsa_time is None
        else (device_time - token_tsa_time).total_seconds()
    )

    trust = resolve_tsa_trust_material(
        attestation=attestation,
        output_root=output_root,
        tsa_ca_file=tsa_ca_file,
        tsa_cert_file=tsa_cert_file,
        allow_tsa_download=allow_tsa_download,
    )
    result["tsa_trust"] = trust
    result["tsa_trust_source"] = trust.get("source")

    timestamp_verify = {
        "available": False,
        "returncode": None,
        "stdout": "",
        "stderr": trust.get("reason", "TSA trust unavailable"),
    }

    if trust.get("available") and token_bytes:
        timestamp_verify = run_command([
            "openssl", "ts",
            "-verify",
            "-token_in",
            "-in", str(token_path),
            "-digest", calculated_attestation_hash,
            "-CAfile", trust["ca_file"],
            "-untrusted", trust["tsa_cert_file"],
        ])

    result["openssl_rfc3161_verify"] = timestamp_verify
    result["rfc3161_signature_and_chain_valid"] = (
        timestamp_verify.get("available")
        and timestamp_verify.get("returncode") == 0
        and "Verification: OK" in timestamp_verify.get("stdout", "")
    )

    declared_status = attestation.get("trusted_timestamp_status")
    result["declared_timestamp_status_consistent"] = (
        declared_status == "rfc3161_verified"
    )

    required_boolean_checks = [
        *declared_checks.values(),
        recipe_hash_valid,
        result.get("public_key_id_valid") is True,
        result.get("ecdsa_signature_valid") is True,
        result.get("attestation_hash_valid") is True,
        result.get("timestamp_attested_hash_valid") is True,
        result.get("timestamp_token_sha256_valid") is True,
        result.get("rfc3161_signature_and_chain_valid") is True,
        result.get("timestamp_serial_matches_json") is True,
        result.get("timestamp_time_matches_json") is True,
        result.get("declared_timestamp_status_consistent") is True,
    ]

    if result.get("timestamp_precedes_central_creation") is not None:
        required_boolean_checks.append(
            result.get("timestamp_precedes_central_creation") is True
        )

    result["overall_valid"] = all(required_boolean_checks)

    # Distinguish positive evidence failure from verifier/infrastructure
    # unavailability. A temporary inability to fetch TSA trust material or
    # invoke OpenSSL must never downgrade an otherwise approved Central record.
    openssl_operational = bool(result.get("openssl_version"))
    trust_operational = bool((result.get("tsa_trust") or {}).get("available"))
    token_parse_operational = (
        (result.get("openssl_timestamp_parse") or {}).get("returncode") == 0
    )

    definitive_failures = []

    for key in [
        "attestation_schema_version_is_4",
        "hash_algorithm_is_sha256",
        "signature_algorithm_is_sha256_ecdsa",
        "public_key_algorithm_is_ec",
        "public_key_format_is_x509",
        "commitment_recipe_hash_valid",
        "public_key_id_valid",
        "attestation_hash_valid",
        "timestamp_attested_hash_valid",
        "timestamp_token_sha256_valid",
        "declared_timestamp_status_consistent",
    ]:
        if result.get(key) is False:
            definitive_failures.append(key)

    # ECDSA failure is definitive only when OpenSSL actually ran.
    if (
        openssl_operational
        and (result.get("openssl_ecdsa_verify") or {}).get("returncode")
        is not None
        and result.get("ecdsa_signature_valid") is False
    ):
        definitive_failures.append("ecdsa_signature_valid")

    # Token metadata mismatch is definitive only after the token parsed.
    if token_parse_operational:
        for key in [
            "timestamp_serial_matches_json",
            "timestamp_time_matches_json",
        ]:
            if result.get(key) is False:
                definitive_failures.append(key)

    # RFC3161 failure is definitive only with independent trust material.
    if (
        openssl_operational
        and trust_operational
        and (result.get("openssl_rfc3161_verify") or {}).get("returncode")
        is not None
        and result.get("rfc3161_signature_and_chain_valid") is False
    ):
        definitive_failures.append(
            "rfc3161_signature_and_chain_valid"
        )

    if result.get("timestamp_precedes_central_creation") is False:
        definitive_failures.append(
            "timestamp_precedes_central_creation"
        )

    result["definitive_failures"] = sorted(set(definitive_failures))
    result["verification_complete"] = bool(
        openssl_operational
        and trust_operational
        and token_parse_operational
        and (result.get("openssl_ecdsa_verify") or {}).get("returncode")
        is not None
        and (result.get("openssl_rfc3161_verify") or {}).get("returncode")
        is not None
    )

    if result["overall_valid"]:
        result["verification_status"] = "VERIFIED"
    elif result["definitive_failures"]:
        result["verification_status"] = "INVALID"
    else:
        result["verification_status"] = "UNAVAILABLE"

    if not result["overall_valid"]:
        for key, value in {
            **declared_checks,
            "commitment_recipe_hash_valid": recipe_hash_valid,
            "public_key_id_valid": result.get("public_key_id_valid"),
            "ecdsa_signature_valid": result.get("ecdsa_signature_valid"),
            "attestation_hash_valid": result.get("attestation_hash_valid"),
            "timestamp_attested_hash_valid":
                result.get("timestamp_attested_hash_valid"),
            "timestamp_token_sha256_valid":
                result.get("timestamp_token_sha256_valid"),
            "rfc3161_signature_and_chain_valid":
                result.get("rfc3161_signature_and_chain_valid"),
            "timestamp_serial_matches_json":
                result.get("timestamp_serial_matches_json"),
            "timestamp_time_matches_json":
                result.get("timestamp_time_matches_json"),
            "declared_timestamp_status_consistent":
                result.get("declared_timestamp_status_consistent"),
            "timestamp_precedes_central_creation":
                result.get("timestamp_precedes_central_creation"),
        }.items():
            if value is False:
                result["errors"].append(f"FAILED_CHECK:{key}")

    (evidence_dir / "crypto_verification.json").write_text(
        pretty_json(result),
        encoding="utf-8",
    )

    return result


# ---------------------------------------------------------------------
# Trusted original anchor and desktop receipt/writeback
# ---------------------------------------------------------------------

DESKTOP_RECEIPT_SCHEMA_V1 = "methodmesh.desktop_audit_receipt.v1"
DESKTOP_RECEIPT_SCHEMA_V2 = "methodmesh.desktop_audit_receipt.v2"
DESKTOP_RECEIPT_SCHEMA = DESKTOP_RECEIPT_SCHEMA_V2
DESKTOP_VERIFIER_ID = "methodmesh_audit_v13_2_delta_ledger"
DEFAULT_DESKTOP_TSA_URL = "https://freetsa.org/tsr"

# v13 production mode: attachment bytes are streamed through SHA-256 and
# are not persisted even inside the ephemeral verifier workspace.
STREAM_ATTACHMENTS_WITHOUT_PERSISTING = False


def original_anchor_status(
    methodmesh: dict,
    reconstruction: dict | None,
) -> tuple[str, dict | None]:
    """
    Establish the original trust anchor.

    The anchor is accepted only if BOTH data reconstruction and independent
    cryptographic verification succeed. Self-reported MethodMesh status fields
    are not sufficient.
    """
    if not methodmesh.get("attestation_present"):
        return "ORIGINAL_ATTESTATION_ABSENT", None

    recipe = methodmesh.get("commitment_recipe")
    if not isinstance(recipe, dict):
        return "ORIGINAL_RECIPE_ABSENT", None

    if methodmesh.get("commitment_recipe_hash_matches_raw") is not True:
        return "ORIGINAL_RECIPE_HASH_UNVERIFIED", None

    if not reconstruction or not reconstruction.get("available"):
        return "ORIGINAL_RECIPE_RECONSTRUCTION_UNAVAILABLE", None

    if not reconstruction.get("complete"):
        return "ORIGINAL_RECIPE_RECONSTRUCTION_ERROR", None

    if not reconstruction.get("all_indirect_commitments_match"):
        return "ORIGINAL_SOURCE_DIGEST_MISMATCH", None

    rebuilt = reconstruction.get("reconstructed_payload_sha256")
    attested = methodmesh.get("attestation_event_payload_hash")
    if not rebuilt or not attested or rebuilt != attested:
        return "ORIGINAL_PAYLOAD_DOES_NOT_MATCH_ATTESTATION", None

    crypto = methodmesh.get("cryptographic_verification")
    if not isinstance(crypto, dict):
        return "ORIGINAL_CRYPTOGRAPHIC_VERIFICATION_ABSENT", None

    if crypto.get("overall_valid") is not True:
        if crypto.get("verification_status") == "UNAVAILABLE":
            return "ORIGINAL_CRYPTOGRAPHIC_VERIFIER_UNAVAILABLE", None
        return "ORIGINAL_CRYPTOGRAPHIC_VERIFICATION_FAILED", None

    anchor = {
        "recipe": copy.deepcopy(recipe),
        "recipe_sha256": methodmesh.get("commitment_recipe_sha256"),
        "event_payload_hash": attested,
        "attestation_hash": methodmesh.get("attestation_hash"),
        "public_key_id": methodmesh.get("public_key_id"),
        "original_reconstructed_payload_sha256": rebuilt,
        "cryptographic_verification": crypto_receipt_summary(crypto),
        # Retain these mirror fields for convenient reporting only. Trust
        # comes from the independently verified token, not these strings.
        "trusted_timestamp_status":
            methodmesh.get("trusted_timestamp_status"),
        "trusted_timestamp_time_iso":
            methodmesh.get("trusted_timestamp_time_iso"),
        "trusted_timestamp_attested_hash":
            methodmesh.get("trusted_timestamp_attested_hash"),
    }

    return "CRYPTOGRAPHICALLY_VERIFIED_ATTESTED_VERSION", anchor


def classify_against_anchor(
    anchor: dict | None,
    reconstruction: dict | None,
) -> str:
    """
    Classify a later retained version using ONLY the trusted original recipe
    and independently reconstructed source values/attachment bytes.
    """
    if anchor is None:
        return "NO_TRUSTED_ORIGINAL_RECIPE_ANCHOR"

    if not reconstruction or not reconstruction.get("available"):
        return "RECIPE_RECONSTRUCTION_UNAVAILABLE"

    if not reconstruction.get("complete"):
        return "RECIPE_RECONSTRUCTION_ERROR"

    rebuilt = reconstruction.get("reconstructed_payload_sha256")
    if not rebuilt:
        return "RECONSTRUCTED_PAYLOAD_HASH_ABSENT"

    if rebuilt == anchor.get("event_payload_hash"):
        return "RECONSTRUCTED_DATA_MATCHES_ORIGINAL_ATTESTATION"

    return "EDITED_AFTER_ATTESTATION"


def receipt_self_hash(receipt_without_hash: dict) -> str:
    return sha256_bytes(
        compact_json(receipt_without_hash).encode("utf-8")
    )


def desktop_receipt_body(receipt: dict) -> dict:
    """
    Return the deterministic v2 receipt body that is actually TSA-stamped.

    Timestamp evidence is an envelope around the receipt body, so it must not
    participate in receipt_sha256 or the timestamp would be self-referential.
    """
    body = dict(receipt)
    body.pop("receipt_sha256", None)
    body.pop("receipt_envelope_sha256", None)

    for key in list(body):
        if key.startswith("trusted_timestamp_"):
            body.pop(key, None)

    return body


def desktop_receipt_envelope_hash(receipt: dict) -> str:
    envelope = dict(receipt)
    envelope.pop("receipt_envelope_sha256", None)
    return receipt_self_hash(envelope)




def create_rfc3161_timestamp_for_hash(
    *,
    digest_hex: str,
    tsa_url: str,
    trust_material: dict,
    evidence_dir: Path,
) -> dict:
    """
    Request and independently verify an RFC3161 timestamp for a SHA-256 digest.

    The request includes a nonce and asks the TSA to include its certificate.
    The HTTP call uses a fresh requests client so Central credentials can never
    be sent to the TSA.
    """
    evidence_dir.mkdir(parents=True, exist_ok=True)

    result = {
        "overall_valid": False,
        "verification_status": "UNAVAILABLE",
        "authority": tsa_url,
        "attested_hash": digest_hex,
        "errors": [],
    }

    if not re.fullmatch(r"[0-9a-fA-F]{64}", str(digest_hex or "")):
        result["verification_status"] = "INVALID"
        result["errors"].append("INVALID_SHA256_DIGEST")
        return result

    if not trust_material.get("available"):
        result["errors"].append(
            f"TSA_TRUST_UNAVAILABLE:{trust_material.get('reason')}"
        )
        return result

    request_path = evidence_dir / "desktop_receipt.tsq"
    response_path = evidence_dir / "desktop_receipt.tsr"
    token_path = evidence_dir / "desktop_receipt_token.der"

    query = run_command([
        "openssl", "ts", "-query",
        "-digest", digest_hex,
        "-sha256",
        "-cert",
        "-out", str(request_path),
    ])

    result["openssl_timestamp_query"] = query
    if not query.get("available") or query.get("returncode") != 0:
        result["errors"].append("OPENSSL_TIMESTAMP_QUERY_FAILED")
        return result

    request_bytes = request_path.read_bytes()
    result["request_sha256"] = sha256_bytes(request_bytes)

    try:
        response = requests.post(
            tsa_url,
            data=request_bytes,
            headers={
                "Content-Type": "application/timestamp-query",
                "Accept": "application/timestamp-reply",
                "User-Agent": "MethodMesh-Desktop-Verifier/1.0",
            },
            timeout=60,
        )
        response.raise_for_status()
        response_bytes = response.content
    except Exception as exc:
        result["errors"].append(f"TSA_HTTP_REQUEST_FAILED:{exc}")
        return result

    response_path.write_bytes(response_bytes)
    result["response_sha256"] = sha256_bytes(response_bytes)

    verify = run_command([
        "openssl", "ts", "-verify",
        "-in", str(response_path),
        "-queryfile", str(request_path),
        "-CAfile", trust_material["ca_file"],
        "-untrusted", trust_material["tsa_cert_file"],
    ])
    result["openssl_timestamp_verify_query"] = verify

    verify_digest = run_command([
        "openssl", "ts", "-verify",
        "-in", str(response_path),
        "-digest", digest_hex,
        "-CAfile", trust_material["ca_file"],
        "-untrusted", trust_material["tsa_cert_file"],
    ])
    result["openssl_timestamp_verify_digest"] = verify_digest

    query_ok = (
        verify.get("available")
        and verify.get("returncode") == 0
        and "Verification: OK" in verify.get("stdout", "")
    )
    digest_ok = (
        verify_digest.get("available")
        and verify_digest.get("returncode") == 0
        and "Verification: OK" in verify_digest.get("stdout", "")
    )

    if not (query_ok and digest_ok):
        result["verification_status"] = "INVALID"
        result["errors"].append("RFC3161_VERIFICATION_FAILED")
        return result

    token_extract = run_command([
        "openssl", "ts", "-reply",
        "-in", str(response_path),
        "-token_out",
        "-out", str(token_path),
    ])
    result["openssl_token_extract"] = token_extract

    if token_extract.get("returncode") != 0 or not token_path.exists():
        result["verification_status"] = "INVALID"
        result["errors"].append("RFC3161_TOKEN_EXTRACTION_FAILED")
        return result

    token_bytes = token_path.read_bytes()

    token_parse = run_command([
        "openssl", "ts", "-reply",
        "-token_in",
        "-in", str(token_path),
        "-text",
    ])
    result["openssl_token_parse"] = token_parse

    parsed = (
        parse_openssl_timestamp_text(token_parse.get("stdout", ""))
        if token_parse.get("returncode") == 0
        else {}
    )

    result.update({
        "overall_valid": True,
        "verification_status": "VERIFIED",
        "time_iso": parsed.get("time_iso"),
        "serial_hex": parsed.get("serial_hex"),
        "hash_algorithm": parsed.get("hash_algorithm"),
        "request_base64":
            base64.b64encode(request_bytes).decode("ascii"),
        "response_base64":
            base64.b64encode(response_bytes).decode("ascii"),
        "token_base64":
            base64.b64encode(token_bytes).decode("ascii"),
        "token_sha256": sha256_bytes(token_bytes),
        "trust_source": trust_material.get("source"),
    })

    return result


def verify_desktop_receipt_timestamp(
    *,
    receipt: dict,
    output_root: Path,
    version_dir: Path,
    tsa_ca_file: str | None,
    tsa_cert_file: str | None,
    allow_tsa_download: bool,
    central_created_at: str | None = None,
) -> dict:
    """
    Independently verify a stored v2 desktop receipt timestamp.

    v1 receipts are explicitly treated as valid legacy checkpoints but are
    reported as untimestamped.
    """
    schema = receipt.get("schema")

    if schema == DESKTOP_RECEIPT_SCHEMA_V1:
        return {
            "overall_valid": True,
            "verification_status": "LEGACY_UNTIMESTAMPED",
            "timestamp_required": False,
        }

    if schema != DESKTOP_RECEIPT_SCHEMA_V2:
        return {
            "overall_valid": False,
            "verification_status": "INVALID",
            "timestamp_required": True,
            "errors": ["UNSUPPORTED_RECEIPT_SCHEMA"],
        }

    body = desktop_receipt_body(receipt)
    body_hash = receipt_self_hash(body)
    supplied_hash = receipt.get("receipt_sha256")

    result = {
        "overall_valid": False,
        "verification_status": "INVALID",
        "timestamp_required": True,
        "body_hash_calculated": body_hash,
        "body_hash_stored": supplied_hash,
        "errors": [],
    }

    if body_hash != supplied_hash:
        result["errors"].append("RECEIPT_BODY_HASH_MISMATCH")
        return result

    if receipt.get("trusted_timestamp_attested_hash") != supplied_hash:
        result["errors"].append("TSA_ATTESTED_HASH_MISMATCH")
        return result

    response_b64 = receipt.get("trusted_timestamp_response_base64")
    request_b64 = receipt.get("trusted_timestamp_request_base64")

    if not response_b64 or not request_b64:
        result["errors"].append("TIMESTAMP_REQUEST_OR_RESPONSE_ABSENT")
        return result

    try:
        request_bytes = base64.b64decode(request_b64, validate=True)
        response_bytes = base64.b64decode(response_b64, validate=True)
    except Exception as exc:
        result["errors"].append(f"TIMESTAMP_BASE64_DECODE_FAILED:{exc}")
        return result

    expected_request_sha = receipt.get("trusted_timestamp_request_sha256")
    expected_response_sha = receipt.get("trusted_timestamp_response_sha256")

    if sha256_bytes(request_bytes) != expected_request_sha:
        result["errors"].append("TIMESTAMP_REQUEST_SHA256_MISMATCH")
        return result

    if sha256_bytes(response_bytes) != expected_response_sha:
        result["errors"].append("TIMESTAMP_RESPONSE_SHA256_MISMATCH")
        return result

    authority = str(
        receipt.get("trusted_timestamp_authority")
        or DEFAULT_DESKTOP_TSA_URL
    )

    trust = resolve_tsa_trust_material(
        attestation={"trusted_timestamp_authority": authority},
        output_root=output_root,
        tsa_ca_file=tsa_ca_file,
        tsa_cert_file=tsa_cert_file,
        allow_tsa_download=allow_tsa_download,
    )
    result["tsa_trust"] = trust
    result["tsa_trust_source"] = trust.get("source")

    if not trust.get("available"):
        result["verification_status"] = "UNAVAILABLE"
        result["errors"].append(
            f"TSA_TRUST_UNAVAILABLE:{trust.get('reason')}"
        )
        return result

    evidence_dir = version_dir / "_desktop_receipt_tsa"
    evidence_dir.mkdir(parents=True, exist_ok=True)
    request_path = evidence_dir / "stored_receipt.tsq"
    response_path = evidence_dir / "stored_receipt.tsr"
    request_path.write_bytes(request_bytes)
    response_path.write_bytes(response_bytes)

    verify = run_command([
        "openssl", "ts", "-verify",
        "-in", str(response_path),
        "-queryfile", str(request_path),
        "-CAfile", trust["ca_file"],
        "-untrusted", trust["tsa_cert_file"],
    ])
    result["openssl_timestamp_verify_query"] = verify

    verify_digest = run_command([
        "openssl", "ts", "-verify",
        "-in", str(response_path),
        "-digest", supplied_hash,
        "-CAfile", trust["ca_file"],
        "-untrusted", trust["tsa_cert_file"],
    ])
    result["openssl_timestamp_verify_digest"] = verify_digest

    query_ok = (
        verify.get("available")
        and verify.get("returncode") == 0
        and "Verification: OK" in verify.get("stdout", "")
    )
    digest_ok = (
        verify_digest.get("available")
        and verify_digest.get("returncode") == 0
        and "Verification: OK" in verify_digest.get("stdout", "")
    )

    if not (query_ok and digest_ok):
        result["errors"].append("RFC3161_VERIFICATION_FAILED")
        return result

    # The token stored separately in the receipt is diagnostic/convenient,
    # but it must be byte-identical to the token extracted from the signed
    # TimeStampResp.
    extracted_token_path = evidence_dir / "stored_receipt_token.der"
    token_extract = run_command([
        "openssl", "ts", "-reply",
        "-in", str(response_path),
        "-token_out",
        "-out", str(extracted_token_path),
    ])
    result["openssl_token_extract"] = token_extract

    if (
        token_extract.get("returncode") != 0
        or not extracted_token_path.exists()
    ):
        result["errors"].append("RFC3161_TOKEN_EXTRACTION_FAILED")
        return result

    extracted_token = extracted_token_path.read_bytes()
    stored_token_b64 = receipt.get("trusted_timestamp_token_base64")
    stored_token_sha = receipt.get("trusted_timestamp_token_sha256")

    if stored_token_b64:
        try:
            stored_token = base64.b64decode(
                stored_token_b64,
                validate=True,
            )
        except Exception as exc:
            result["errors"].append(
                f"TIMESTAMP_TOKEN_BASE64_DECODE_FAILED:{exc}"
            )
            return result

        if stored_token != extracted_token:
            result["errors"].append("TIMESTAMP_TOKEN_BYTES_MISMATCH")
            return result

    if (
        stored_token_sha
        and sha256_bytes(extracted_token) != stored_token_sha
    ):
        result["errors"].append("TIMESTAMP_TOKEN_SHA256_MISMATCH")
        return result

    parse = run_command([
        "openssl", "ts", "-reply",
        "-in", str(response_path),
        "-text",
    ])
    result["openssl_timestamp_parse"] = parse
    parsed = (
        parse_openssl_timestamp_text(parse.get("stdout", ""))
        if parse.get("returncode") == 0
        else {}
    )

    stored_serial = normalize_hex_integer(
        receipt.get("trusted_timestamp_serial")
    )
    parsed_serial = normalize_hex_integer(parsed.get("serial_hex"))

    if stored_serial and parsed_serial and stored_serial != parsed_serial:
        result["errors"].append("TIMESTAMP_SERIAL_MISMATCH")
        return result

    stored_time = parse_iso_strict(
        receipt.get("trusted_timestamp_time_iso")
    )
    parsed_time = parse_iso_strict(parsed.get("time_iso"))

    if (
        stored_time is not None
        and parsed_time is not None
        and abs((stored_time - parsed_time).total_seconds()) >= 1
    ):
        result["errors"].append("TIMESTAMP_TIME_MISMATCH")
        return result

    source_created = parse_iso_strict(
        receipt.get("source_created_at")
    )
    if source_created is not None and parsed_time is not None:
        result["timestamp_after_source_version"] = (
            parsed_time >= source_created
        )
        if result["timestamp_after_source_version"] is not True:
            result["errors"].append(
                "TIMESTAMP_PRECEDES_SOURCE_VERSION"
            )
            return result

    writeback_created = parse_iso_strict(central_created_at)
    if writeback_created is not None and parsed_time is not None:
        result["timestamp_before_writeback_version"] = (
            parsed_time <= writeback_created
        )
        if result["timestamp_before_writeback_version"] is not True:
            result["errors"].append(
                "TIMESTAMP_FOLLOWS_WRITEBACK_VERSION"
            )
            return result

    envelope_hash = receipt.get("receipt_envelope_sha256")
    if envelope_hash:
        calculated_envelope_hash = desktop_receipt_envelope_hash(receipt)
        result["receipt_envelope_sha256_calculated"] = (
            calculated_envelope_hash
        )
        result["receipt_envelope_sha256_stored"] = envelope_hash
        if calculated_envelope_hash != envelope_hash:
            result["errors"].append("RECEIPT_ENVELOPE_HASH_MISMATCH")
            return result

    result.update({
        "overall_valid": True,
        "verification_status": "VERIFIED",
        "parsed_time_iso": parsed.get("time_iso"),
        "parsed_serial_hex": parsed.get("serial_hex"),
    })
    return result


def validate_receipt_core(
    receipt: dict | None,
    *,
    recipe_sha256: str | None,
) -> tuple[bool, str]:
    """
    Validate deterministic receipt structure.

    v1 is accepted as a legacy untimestamped checkpoint.
    v2 additionally requires a timestamp envelope to be present; independent
    RFC3161 verification is performed separately and must pass before v2 is
    accepted as a trusted checkpoint.
    """
    if not isinstance(receipt, dict):
        return False, "NO_RECEIPT"

    schema = receipt.get("schema")
    if schema not in {
        DESKTOP_RECEIPT_SCHEMA_V1,
        DESKTOP_RECEIPT_SCHEMA_V2,
    }:
        return False, "UNSUPPORTED_RECEIPT_SCHEMA"

    supplied_hash = receipt.get("receipt_sha256")
    if not supplied_hash:
        return False, "RECEIPT_SELF_HASH_ABSENT"

    if schema == DESKTOP_RECEIPT_SCHEMA_V1:
        core = dict(receipt)
        core.pop("receipt_sha256", None)
        expected = receipt_self_hash(core)
    else:
        expected = receipt_self_hash(desktop_receipt_body(receipt))

    if expected != supplied_hash:
        return False, "RECEIPT_SELF_HASH_MISMATCH"

    if receipt.get("commitment_recipe_sha256") != recipe_sha256:
        return False, "RECEIPT_RECIPE_HASH_MISMATCH"

    if receipt.get("verification_result") not in {
        "VERIFIED_EDIT",
        "VERIFIED_EDIT_SEQUENCE",
        "VERIFIED_NO_COMMITTED_DATA_CHANGE",
        "VERIFIED_SEQUENCE",
    }:
        return False, "RECEIPT_NOT_A_VALIDATING_RESULT"

    if not receipt.get("writeback_version_id"):
        return False, "RECEIPT_WRITEBACK_VERSION_ABSENT"

    if not receipt.get("reconstructed_payload_sha256"):
        return False, "RECEIPT_PAYLOAD_HASH_ABSENT"

    if schema == DESKTOP_RECEIPT_SCHEMA_V2:
        if (
            receipt.get("trusted_timestamp_status")
            != "rfc3161_verified"
        ):
            return False, "RECEIPT_TIMESTAMP_STATUS_INVALID"
        if not receipt.get("trusted_timestamp_response_base64"):
            return False, "RECEIPT_TIMESTAMP_RESPONSE_ABSENT"

    return True, (
        "VALID_LEGACY_RECEIPT_CORE"
        if schema == DESKTOP_RECEIPT_SCHEMA_V1
        else "VALID_TIMESTAMPED_RECEIPT_CORE"
    )


def validate_existing_receipt(
    receipt: dict | None,
    *,
    current_version_id: str,
    reconstructed_payload_sha256: str,
    recipe_sha256: str | None,
    timestamp_verification: dict | None = None,
) -> tuple[bool, str]:
    ok, reason = validate_receipt_core(
        receipt,
        recipe_sha256=recipe_sha256,
    )
    if not ok:
        return False, reason

    if receipt.get("writeback_version_id") != current_version_id:
        return False, "RECEIPT_BELONGS_TO_PRIOR_VERSION"

    if (
        receipt.get("reconstructed_payload_sha256")
        != reconstructed_payload_sha256
    ):
        return False, "RECEIPT_PAYLOAD_HASH_MISMATCH"

    if receipt.get("schema") == DESKTOP_RECEIPT_SCHEMA_V2:
        if not isinstance(timestamp_verification, dict):
            return False, "RECEIPT_TIMESTAMP_NOT_VERIFIED"
        if timestamp_verification.get("overall_valid") is not True:
            if timestamp_verification.get("verification_status") == "UNAVAILABLE":
                return False, "RECEIPT_TIMESTAMP_VERIFIER_UNAVAILABLE"
            return False, "RECEIPT_TIMESTAMP_INVALID"
        return True, "VALID_TIMESTAMPED_CURRENT_RECEIPT"

    return True, "VALID_LEGACY_CURRENT_RECEIPT_UNTIMESTAMPED"


def find_latest_valid_receipt_checkpoint(
    version_summary: list[dict],
    *,
    trusted_anchor: dict | None,
) -> dict | None:
    """
    Find the latest trustworthy desktop receipt checkpoint.

    Legacy v1 checkpoints are retained for migration continuity.
    v2 checkpoints are trusted only if their RFC3161 timestamp has been
    independently verified during this run.
    """
    if trusted_anchor is None:
        return None

    id_to_index = {
        item["version_id"]: i
        for i, item in enumerate(version_summary)
    }
    id_to_item = {
        item["version_id"]: item
        for item in version_summary
    }

    candidates = {}

    for carrying_item in version_summary:
        methodmesh = carrying_item.get("methodmesh") or {}
        receipt = methodmesh.get("desktop_audit_receipt")

        ok, reason = validate_receipt_core(
            receipt,
            recipe_sha256=trusted_anchor.get("recipe_sha256"),
        )
        if not ok:
            continue

        schema = receipt.get("schema")
        timestamp_verification = (
            methodmesh.get("desktop_receipt_timestamp_verification")
            or {}
        )

        if (
            schema == DESKTOP_RECEIPT_SCHEMA_V2
            and timestamp_verification.get("overall_valid") is not True
        ):
            continue

        writeback_id = receipt.get("writeback_version_id")
        if writeback_id not in id_to_item:
            continue

        writeback_item = id_to_item[writeback_id]
        writeback_receipt = (
            (writeback_item.get("methodmesh") or {})
            .get("desktop_audit_receipt")
        )

        if not isinstance(writeback_receipt, dict):
            continue

        if (
            writeback_receipt.get("receipt_sha256")
            != receipt.get("receipt_sha256")
        ):
            continue

        reconstruction = writeback_item.get("reconstruction") or {}
        if (
            not reconstruction.get("available")
            or not reconstruction.get("complete")
        ):
            continue

        rebuilt = reconstruction.get("reconstructed_payload_sha256")
        if rebuilt != receipt.get("reconstructed_payload_sha256"):
            continue

        candidates[writeback_id] = {
            "checkpoint_version_id": writeback_id,
            "checkpoint_index": id_to_index[writeback_id],
            "receipt": receipt,
            "receipt_sha256": receipt.get("receipt_sha256"),
            "receipt_schema": schema,
            "timestamp_verification": timestamp_verification,
            "audited_through_source_version_id":
                receipt.get("audited_through_source_version_id")
                or receipt.get("source_version_id"),
            "reconstructed_payload_sha256": rebuilt,
        }

    if not candidates:
        return None

    return max(
        candidates.values(),
        key=lambda x: x["checkpoint_index"],
    )


def summarize_version_for_receipt(
    item: dict,
    *,
    previous_version_id: str,
    previous_payload_sha256: str | None,
) -> dict:
    reconstruction = item.get("reconstruction") or {}
    rebuilt = reconstruction.get("reconstructed_payload_sha256")

    source_issues = [
        member.get("path")
        for member in (reconstruction.get("members") or [])
        if (
            member.get("stored_matches_source") is False
            or member.get("source_ok") is False
        )
    ]

    if previous_payload_sha256 is not None and rebuilt == previous_payload_sha256:
        transition = "NO_COMMITTED_DATA_CHANGE"
    else:
        transition = "COMMITTED_DATA_CHANGED"

    return {
        "version_id": item.get("version_id"),
        "previous_version_id": previous_version_id,
        "created_at": item.get("created_at"),
        "actor": item.get("actor"),
        "form_version": item.get("form_version"),
        "xml_sha256": item.get("xml_sha256"),
        "reconstructed_payload_sha256": rebuilt,
        "transition": transition,
        "reconstruction_complete": reconstruction.get("complete"),
        "stored_calculated_digest_fields_trusted": False,
        "stored_digest_mismatches": source_issues,
    }


def verify_unseen_sequence(
    version_summary: list[dict],
    *,
    trusted_anchor: dict | None,
    checkpoint: dict | None,
) -> dict:
    """
    Verify every retained version not already covered by a prior desktop
    checkpoint.

    The first run starts immediately after the original MethodMesh-attested
    version. Later runs start immediately after the last trustworthy desktop
    writeback checkpoint.

    A single failed/unavailable reconstruction makes the interval incomplete
    and prevents approval/writeback.
    """
    if trusted_anchor is None:
        return {
            "complete": False,
            "reason": "NO_TRUSTED_ORIGINAL_RECIPE_ANCHOR",
            "versions": [],
        }

    if not version_summary:
        return {
            "complete": False,
            "reason": "NO_RETAINED_VERSIONS",
            "versions": [],
        }

    if checkpoint is None:
        start_index = 1
        previous_version_id = version_summary[0]["version_id"]
        previous_payload_sha256 = (
            (version_summary[0].get("reconstruction") or {})
            .get("reconstructed_payload_sha256")
        )
    else:
        start_index = checkpoint["checkpoint_index"] + 1
        previous_version_id = checkpoint["checkpoint_version_id"]
        previous_payload_sha256 = checkpoint.get(
            "reconstructed_payload_sha256"
        )

    unseen = version_summary[start_index:]
    verified = []
    failures = []

    for item in unseen:
        reconstruction = item.get("reconstruction") or {}

        ok = (
            reconstruction.get("available")
            and reconstruction.get("complete")
            and reconstruction.get("reconstructed_payload_sha256")
        )

        if not ok:
            failures.append({
                "version_id": item.get("version_id"),
                "classification": item.get("classification"),
                "reason": (
                    reconstruction.get("reason")
                    or reconstruction.get("errors")
                    or "RECONSTRUCTION_INCOMPLETE"
                ),
            })
            # Still include a diagnostic sequence entry.
            verified.append({
                "version_id": item.get("version_id"),
                "previous_version_id": previous_version_id,
                "created_at": item.get("created_at"),
                "actor": item.get("actor"),
                "form_version": item.get("form_version"),
                "xml_sha256": item.get("xml_sha256"),
                "reconstructed_payload_sha256":
                    reconstruction.get("reconstructed_payload_sha256"),
                "transition": "UNVERIFIED",
                "reconstruction_complete":
                    reconstruction.get("complete"),
                "stored_calculated_digest_fields_trusted": False,
            })
        else:
            entry = summarize_version_for_receipt(
                item,
                previous_version_id=previous_version_id,
                previous_payload_sha256=previous_payload_sha256,
            )
            verified.append(entry)
            previous_payload_sha256 = (
                reconstruction.get("reconstructed_payload_sha256")
            )

        previous_version_id = item.get("version_id")

    return {
        "complete": len(failures) == 0,
        "reason": (
            "ALL_UNSEEN_VERSIONS_RECONSTRUCTED"
            if len(failures) == 0
            else "UNVERIFIED_VERSION_IN_SEQUENCE"
        ),
        "start_index": start_index,
        "unseen_count": len(unseen),
        "versions": verified,
        "failures": failures,
        "prior_checkpoint": checkpoint,
    }


def make_desktop_receipt(
    *,
    logical_submission_id: str,
    source_version_id: str,
    writeback_version_id: str,
    source_xml_sha256: str,
    source_form_version: str | None,
    source_created_at: str | None,
    source_actor,
    reconstruction: dict,
    anchor: dict,
    verified_sequence: list[dict],
    prior_checkpoint: dict | None,
) -> dict:
    """
    Create the deterministic v2 receipt body.

    receipt_sha256 is the permanent receipt identity and the value sent to the
    TSA. Timestamp fields are attached afterwards and therefore do not alter
    receipt_sha256.
    """
    rebuilt = reconstruction["reconstructed_payload_sha256"]

    changed_count = sum(
        1
        for item in verified_sequence
        if item.get("transition") == "COMMITTED_DATA_CHANGED"
    )

    verification_result = (
        "VERIFIED_NO_COMMITTED_DATA_CHANGE"
        if changed_count == 0
        else (
            "VERIFIED_EDIT"
            if len(verified_sequence) == 1
            else "VERIFIED_EDIT_SEQUENCE"
        )
    )

    member_checks = []
    for member in reconstruction.get("members") or []:
        member_checks.append({
            "path": member.get("path"),
            "commitment": member.get("commitment"),
            "source_field": member.get("source_field"),
            "artifact_field": member.get("artifact_field"),
            "source_ok": member.get("source_ok"),
            "stored_matches_source": member.get("stored_matches_source"),
        })

    prior_receipt = (
        prior_checkpoint.get("receipt")
        if prior_checkpoint is not None
        else None
    )

    core = {
        "schema": DESKTOP_RECEIPT_SCHEMA_V2,
        "verifier": DESKTOP_VERIFIER_ID,
        "verification_result": verification_result,
        "verification_basis": (
            "trusted_recipe_plus_retained_sources_plus_rfc3161"
        ),
        "observed_at_utc": utc_now_iso(),
        "logical_submission_id": logical_submission_id,
        "source_version_id": source_version_id,
        "audited_through_source_version_id": source_version_id,
        "writeback_version_id": writeback_version_id,
        "source_xml_sha256": source_xml_sha256,
        "source_form_version": source_form_version,
        "source_created_at": source_created_at,
        "source_actor": source_actor,
        "reconstructed_payload_sha256": rebuilt,
        "commitment_recipe_schema":
            (anchor.get("recipe") or {}).get("schema"),
        "commitment_recipe_sha256": anchor.get("recipe_sha256"),
        "canonicalization":
            (anchor.get("recipe") or {}).get("canonicalization"),
        "hash_algorithm":
            (anchor.get("recipe") or {}).get("hash_algorithm"),
        "sequence_count": len(verified_sequence),
        "verified_sequence": verified_sequence,
        "previous_desktop_checkpoint_version_id": (
            prior_checkpoint.get("checkpoint_version_id")
            if prior_checkpoint is not None
            else None
        ),
        "previous_desktop_receipt_sha256": (
            prior_receipt.get("receipt_sha256")
            if isinstance(prior_receipt, dict)
            else None
        ),
        "member_count": reconstruction.get("member_count"),
        "reconstruction_complete": reconstruction.get("complete"),
        "stored_calculated_digest_fields_trusted": False,
        "current_member_checks": member_checks,
        "original_attestation": {
            "event_payload_hash": anchor.get("event_payload_hash"),
            "attestation_hash": anchor.get("attestation_hash"),
            "public_key_id": anchor.get("public_key_id"),
            "trusted_timestamp_status":
                anchor.get("trusted_timestamp_status"),
            "trusted_timestamp_time_iso":
                anchor.get("trusted_timestamp_time_iso"),
            "trusted_timestamp_attested_hash":
                anchor.get("trusted_timestamp_attested_hash"),
            "cryptographic_verification":
                anchor.get("cryptographic_verification"),
        },
        "note": (
            "Desktop verification reconstructs every previously unseen "
            "retained version from the original bound commitment recipe plus "
            "actual retained source values and attachment bytes. The "
            "deterministic receipt body hash is independently RFC3161 "
            "timestamped before Central approval."
        ),
    }

    receipt = dict(core)
    receipt["receipt_sha256"] = receipt_self_hash(core)
    return receipt


def attach_desktop_receipt_timestamp(
    *,
    receipt: dict,
    timestamp_result: dict,
) -> dict:
    if timestamp_result.get("overall_valid") is not True:
        raise ValueError(
            "Cannot attach an unverified RFC3161 timestamp to receipt."
        )

    stamped = dict(receipt)
    stamped.update({
        "trusted_timestamp_status": "rfc3161_verified",
        "trusted_timestamp_authority":
            timestamp_result.get("authority"),
        "trusted_timestamp_attested_hash":
            receipt.get("receipt_sha256"),
        "trusted_timestamp_time_iso":
            timestamp_result.get("time_iso"),
        "trusted_timestamp_serial":
            timestamp_result.get("serial_hex"),
        "trusted_timestamp_request_base64":
            timestamp_result.get("request_base64"),
        "trusted_timestamp_request_sha256":
            timestamp_result.get("request_sha256"),
        "trusted_timestamp_response_base64":
            timestamp_result.get("response_base64"),
        "trusted_timestamp_response_sha256":
            timestamp_result.get("response_sha256"),
        "trusted_timestamp_token_base64":
            timestamp_result.get("token_base64"),
        "trusted_timestamp_token_sha256":
            timestamp_result.get("token_sha256"),
        "trusted_timestamp_trust_source":
            timestamp_result.get("trust_source"),
    })
    stamped["receipt_envelope_sha256"] = (
        desktop_receipt_envelope_hash(stamped)
    )
    return stamped


def locate_meta(root: ET.Element) -> ET.Element:
    meta = find_xml_element(root, "meta")
    if meta is None:
        raise ValueError("Submission XML has no meta element.")
    return meta


def build_writeback_xml(
    *,
    original_xml_bytes: bytes,
    reconstruction: dict,
    receipt: dict,
    source_version_id: str,
    new_version_id: str,
) -> bytes:
    """
    Create a complete replacement XML version.

    Source/research fields are left untouched. Only provenance/calculated
    technical fields are normalized:
      - indirect recipe-member digest fields;
      - showcase_payload_canonical;
      - showcase_payload_sha256;
      - desktop_audit_receipt_json;
      - meta/instanceID and meta/deprecatedID.
    """
    register_namespaces_from_xml(original_xml_bytes)
    root = ET.fromstring(original_xml_bytes)

    # Normalize only derived digest fields from source reconstruction.
    for member in reconstruction.get("members") or []:
        if member.get("commitment") == "value":
            continue
        if not member.get("source_ok"):
            continue
        derived = member.get("source_derived_value")
        path = member.get("path")
        if derived is not None and path:
            set_xml_value(root, path, str(derived), required=True)

    set_xml_value(
        root,
        "showcase_payload_canonical",
        reconstruction["reconstructed_payload"],
        required=True,
    )
    set_xml_value(
        root,
        "showcase_payload_sha256",
        reconstruction["reconstructed_payload_sha256"],
        required=True,
    )
    set_xml_value(
        root,
        "desktop_audit_receipt_json",
        compact_json(receipt),
        required=True,
    )

    meta = locate_meta(root)
    instance_elem = find_xml_element(meta, "instanceID")
    if instance_elem is None:
        raise ValueError("Submission XML has no meta/instanceID.")

    instance_elem.text = new_version_id

    deprecated_elem = find_xml_element(meta, "deprecatedID")
    if deprecated_elem is None:
        ns = xml_namespace(instance_elem.tag)
        tag = f"{{{ns}}}deprecatedID" if ns else "deprecatedID"
        deprecated_elem = ET.SubElement(meta, tag)

    deprecated_elem.text = source_version_id

    return ET.tostring(
        root,
        encoding="utf-8",
        xml_declaration=True,
    )


def server_current_matches_archive(
    *,
    central: Central,
    base: str,
    expected_version_id: str,
    archived_xml_bytes: bytes,
    archived_attachments: list[dict],
) -> tuple[bool, str]:
    """
    Race/integrity guard immediately before writeback.

    Re-fetch current metadata, XML and attachment bytes. If anything differs
    from the version we actually verified, refuse to approve/write a receipt.
    """
    metadata = central.get_json(base)
    actual_current = (
        (metadata.get("currentVersion") or {}).get("instanceId")
    )

    if actual_current != expected_version_id:
        return False, (
            "NEWER_CURRENT_VERSION:"
            f"{actual_current!r}!={expected_version_id!r}"
        )

    fresh_xml, _ = central.get_bytes(f"{base}.xml")
    if sha256_bytes(fresh_xml) != sha256_bytes(archived_xml_bytes):
        return False, "CURRENT_XML_CHANGED_SINCE_VERIFICATION"

    fresh_list = central.get_json(f"{base}/attachments")
    fresh_by_name = {
        item.get("name"): item
        for item in fresh_list
        if item.get("name")
    }

    archived_existing_names = {
        item.get("name")
        for item in archived_attachments
        if item.get("name") and item.get("exists")
    }
    fresh_existing_names = {
        item.get("name")
        for item in fresh_list
        if item.get("name") and item.get("exists")
    }

    if fresh_existing_names != archived_existing_names:
        return False, (
            "CURRENT_ATTACHMENT_SET_CHANGED:"
            f"archived={sorted(archived_existing_names)!r};"
            f"fresh={sorted(fresh_existing_names)!r}"
        )

    for archived in archived_attachments:
        name = archived.get("name")
        exists = bool(archived.get("exists"))
        fresh = fresh_by_name.get(name)

        if exists and (not fresh or not fresh.get("exists")):
            return False, f"CURRENT_ATTACHMENT_MISSING:{name}"

        if exists and name:
            data, _ = central.get_bytes(
                f"{base}/attachments/{path_part(name)}"
            )
            if sha256_bytes(data) != archived.get("sha256"):
                return False, f"CURRENT_ATTACHMENT_CHANGED:{name}"

    return True, "CURRENT_SERVER_STATE_MATCHES_VERIFIED_ARCHIVE"


def set_review_state(
    central: Central,
    base: str,
    state: str,
) -> dict:
    if state not in {"approved", "hasIssues", "rejected", "edited"}:
        raise ValueError(f"Unsupported Central review state: {state}")
    return central.patch_json(base, {"reviewState": state})



def apply_current_validation_writeback(
    *,
    central: Central,
    base: str,
    logical_submission_id: str,
    trusted_anchor: dict | None,
    original_version_id: str,
    original_version_classification: str,
    version_summary: list[dict],
    current_runtime: dict,
    current_metadata: dict,
    writeback: bool,
    update_review_state: bool,
    output_root: Path,
    tsa_ca_file: str | None,
    tsa_cert_file: str | None,
    allow_tsa_download: bool,
    desktop_tsa_url: str,
) -> dict:
    """
    Strict sequence-aware validation/writeback.

    Approval requires an unbroken recipe-driven reconstruction of every
    previously unseen retained version since:
      - the original MethodMesh-attested version, on the first audit; or
      - the latest trustworthy desktop receipt checkpoint, on later audits.

    If any unseen historical version cannot be reconstructed, no validating
    receipt is written and reviewState becomes hasIssues (when mutation is
    enabled).
    """
    current_id = current_runtime["version_id"]
    current_reconstruction = current_runtime.get("reconstruction") or {}

    checkpoint = find_latest_valid_receipt_checkpoint(
        version_summary,
        trusted_anchor=trusted_anchor,
    )

    sequence = verify_unseen_sequence(
        version_summary,
        trusted_anchor=trusted_anchor,
        checkpoint=checkpoint,
    )

    action = {
        "mode": "WRITEBACK" if writeback else "DRY_RUN",
        "current_version_id": current_id,
        "classification": current_runtime.get("classification"),
        "server_review_state_before": current_metadata.get("reviewState"),
        "receipt_action": None,
        "review_state_action": None,
        "prior_checkpoint": checkpoint,
        "sequence_verification": sequence,
    }

    if trusted_anchor is None:
        if (
            original_version_classification
            == "ORIGINAL_CRYPTOGRAPHIC_VERIFIER_UNAVAILABLE"
        ):
            action["receipt_action"] = (
                "CRYPTO_VERIFIER_UNAVAILABLE_NO_RECEIPT"
            )
            action["review_state_action"] = (
                "UNCHANGED_VERIFIER_UNAVAILABLE"
            )
            return action

        action["receipt_action"] = "NO_TRUSTED_ORIGINAL_ANCHOR_NO_RECEIPT"
        if writeback and update_review_state:
            set_review_state(central, base, "hasIssues")
            action["review_state_action"] = "SET_HAS_ISSUES"
        else:
            action["review_state_action"] = "WOULD_SET_HAS_ISSUES"
        return action

    if not sequence.get("complete"):
        action["receipt_action"] = "UNVERIFIED_SEQUENCE_NO_RECEIPT"
        if writeback and update_review_state:
            set_review_state(central, base, "hasIssues")
            action["review_state_action"] = "SET_HAS_ISSUES"
        else:
            action["review_state_action"] = "WOULD_SET_HAS_ISSUES"
        return action

    # If the latest trustworthy receipt checkpoint is already the current
    # version, there is nothing new to audit or write.
    if (
        checkpoint is not None
        and checkpoint.get("checkpoint_version_id") == current_id
        and sequence.get("unseen_count") == 0
    ):
        action["receipt_action"] = "VALID_CURRENT_RECEIPT_ALREADY_PRESENT"
        if writeback and update_review_state:
            if current_metadata.get("reviewState") != "approved":
                set_review_state(central, base, "approved")
                action["review_state_action"] = "SET_APPROVED"
            else:
                action["review_state_action"] = "ALREADY_APPROVED"
        else:
            action["review_state_action"] = (
                "ALREADY_APPROVED"
                if current_metadata.get("reviewState") == "approved"
                else "WOULD_SET_APPROVED"
            )
        return action

    # An untouched original is validated directly by the MethodMesh anchor.
    if (
        current_id == original_version_id
        and sequence.get("unseen_count") == 0
    ):
        action["receipt_action"] = "ORIGINAL_ATTESTATION_IS_CURRENT"
        if writeback and update_review_state:
            if current_metadata.get("reviewState") != "approved":
                set_review_state(central, base, "approved")
                action["review_state_action"] = "SET_APPROVED"
            else:
                action["review_state_action"] = "ALREADY_APPROVED"
        else:
            action["review_state_action"] = (
                "ALREADY_APPROVED"
                if current_metadata.get("reviewState") == "approved"
                else "WOULD_SET_APPROVED"
            )
        return action

    # There are unseen retained versions and every one reconstructed.
    if sequence.get("unseen_count", 0) <= 0:
        action["receipt_action"] = "NO_UNSEEN_VERSION_BUT_NO_CURRENT_CHECKPOINT"
        if writeback and update_review_state:
            set_review_state(central, base, "hasIssues")
            action["review_state_action"] = "SET_HAS_ISSUES"
        else:
            action["review_state_action"] = "WOULD_SET_HAS_ISSUES"
        return action

    valid_current = (
        current_reconstruction.get("available")
        and current_reconstruction.get("complete")
        and current_reconstruction.get("reconstructed_payload_sha256")
    )
    if not valid_current:
        action["receipt_action"] = "CURRENT_RECONSTRUCTION_INVALID_NO_RECEIPT"
        if writeback and update_review_state:
            set_review_state(central, base, "hasIssues")
            action["review_state_action"] = "SET_HAS_ISSUES"
        else:
            action["review_state_action"] = "WOULD_SET_HAS_ISSUES"
        return action

    new_version_id = f"uuid:{uuid.uuid4()}"

    receipt = make_desktop_receipt(
        logical_submission_id=logical_submission_id,
        source_version_id=current_id,
        writeback_version_id=new_version_id,
        source_xml_sha256=current_runtime["xml_sha256"],
        source_form_version=current_runtime.get("form_version"),
        source_created_at=current_runtime.get("created_at"),
        source_actor=current_runtime.get("actor"),
        reconstruction=current_reconstruction,
        anchor=trusted_anchor,
        verified_sequence=sequence.get("versions") or [],
        prior_checkpoint=checkpoint,
    )

    if not writeback:
        action["receipt_action"] = (
            "WOULD_REQUEST_RFC3161_AND_CREATE_TIMESTAMPED_SEQUENCE_RECEIPT"
        )
        action["planned_writeback_version_id"] = new_version_id
        action["planned_receipt_body"] = receipt
        action["planned_receipt_sha256"] = receipt["receipt_sha256"]
        action["planned_tsa_authority"] = desktop_tsa_url
        action["review_state_action"] = (
            "WOULD_SET_APPROVED_AFTER_VERIFIED_TIMESTAMP_AND_WRITEBACK"
        )
        return action

    # Do not create an external timestamp for a version that cannot be
    # written, or for a version that has already lost the race.
    if find_xml_element(
        current_runtime["root"],
        "desktop_audit_receipt_json",
    ) is None:
        action["receipt_action"] = "ABORT_NO_DESKTOP_RECEIPT_FIELD"
        if update_review_state:
            set_review_state(central, base, "hasIssues")
            action["review_state_action"] = "SET_HAS_ISSUES"
        return action

    pre_tsa_ok, pre_tsa_reason = server_current_matches_archive(
        central=central,
        base=base,
        expected_version_id=current_id,
        archived_xml_bytes=current_runtime["xml_bytes"],
        archived_attachments=current_runtime["attachments"],
    )
    action["pre_tsa_server_check"] = pre_tsa_reason

    if not pre_tsa_ok:
        action["receipt_action"] = "ABORT_SERVER_STATE_CHANGED_BEFORE_TSA"
        action["review_state_action"] = "UNCHANGED_DUE_TO_RACE"
        return action

    # Resolve TSA trust material and request a timestamp for the deterministic
    # receipt body hash. A reviewed edit is never approved without a verified
    # external RFC3161 timestamp.
    trust = resolve_tsa_trust_material(
        attestation={
            "trusted_timestamp_authority": desktop_tsa_url
        },
        output_root=output_root,
        tsa_ca_file=tsa_ca_file,
        tsa_cert_file=tsa_cert_file,
        allow_tsa_download=allow_tsa_download,
    )

    tsa_dir = (
        current_runtime["version_dir"]
        / "_desktop_writeback"
        / "tsa"
    )

    timestamp_result = create_rfc3161_timestamp_for_hash(
        digest_hex=receipt["receipt_sha256"],
        tsa_url=desktop_tsa_url,
        trust_material=trust,
        evidence_dir=tsa_dir,
    )

    action["desktop_receipt_timestamp"] = {
        "verification_status":
            timestamp_result.get("verification_status"),
        "overall_valid":
            timestamp_result.get("overall_valid"),
        "authority":
            timestamp_result.get("authority"),
        "time_iso":
            timestamp_result.get("time_iso"),
        "serial_hex":
            timestamp_result.get("serial_hex"),
        "attested_hash":
            timestamp_result.get("attested_hash"),
        "trust_source":
            timestamp_result.get("trust_source"),
        "errors":
            timestamp_result.get("errors"),
    }

    if timestamp_result.get("overall_valid") is not True:
        action["receipt_action"] = (
            "TSA_TIMESTAMP_UNAVAILABLE_NO_RECEIPT"
            if timestamp_result.get("verification_status") == "UNAVAILABLE"
            else "TSA_TIMESTAMP_INVALID_NO_RECEIPT"
        )
        action["review_state_action"] = (
            "UNCHANGED_TSA_UNAVAILABLE"
            if timestamp_result.get("verification_status") == "UNAVAILABLE"
            else "UNCHANGED_TSA_INVALID"
        )
        return action

    receipt = attach_desktop_receipt_timestamp(
        receipt=receipt,
        timestamp_result=timestamp_result,
    )

    # Re-verify the exact timestamped receipt before it is allowed into
    # Central. This catches envelope construction mistakes immediately.
    local_timestamp_verification = verify_desktop_receipt_timestamp(
        receipt=receipt,
        output_root=output_root,
        version_dir=current_runtime["version_dir"],
        tsa_ca_file=tsa_ca_file,
        tsa_cert_file=tsa_cert_file,
        allow_tsa_download=allow_tsa_download,
        central_created_at=None,
    )

    action["desktop_receipt_timestamp_recheck"] = {
        "verification_status":
            local_timestamp_verification.get("verification_status"),
        "overall_valid":
            local_timestamp_verification.get("overall_valid"),
        "errors":
            local_timestamp_verification.get("errors"),
    }

    if local_timestamp_verification.get("overall_valid") is not True:
        action["receipt_action"] = (
            "LOCAL_TSA_RECHECK_FAILED_NO_RECEIPT"
        )
        action["review_state_action"] = "UNCHANGED_TSA_INVALID"
        return action

    action["receipt_action"] = "WOULD_CREATE_TIMESTAMPED_SEQUENCE_RECEIPT"
    action["planned_writeback_version_id"] = new_version_id
    action["planned_receipt"] = receipt

    # Immediately before mutation, prove that the server's current XML and
    # attachment bytes are still the same bytes that were verified.
    state_ok, state_reason = server_current_matches_archive(
        central=central,
        base=base,
        expected_version_id=current_id,
        archived_xml_bytes=current_runtime["xml_bytes"],
        archived_attachments=current_runtime["attachments"],
    )
    action["post_tsa_server_check"] = state_reason

    if not state_ok:
        action["receipt_action"] = "ABORT_SERVER_STATE_CHANGED"
        action["review_state_action"] = "UNCHANGED_DUE_TO_RACE"
        return action

    writeback_xml = build_writeback_xml(
        original_xml_bytes=current_runtime["xml_bytes"],
        reconstruction=current_reconstruction,
        receipt=receipt,
        source_version_id=current_id,
        new_version_id=new_version_id,
    )

    writeback_dir = current_runtime["version_dir"] / "_desktop_writeback"
    writeback_dir.mkdir(exist_ok=True)

    (writeback_dir / "planned_receipt.json").write_text(
        pretty_json(receipt),
        encoding="utf-8",
    )
    (writeback_dir / "planned_submission.xml").write_bytes(writeback_xml)

    payload, response = central.put_xml(base, writeback_xml)

    if response.status_code == 409:
        action["receipt_action"] = "CONFLICT_409_NEWER_VERSION_WON_RACE"
        action["review_state_action"] = "UNCHANGED_DUE_TO_RACE"
        return action

    action["receipt_action"] = "TIMESTAMPED_SEQUENCE_RECEIPT_WRITTEN"
    action["writeback_response"] = payload

    if update_review_state:
        review_response = set_review_state(central, base, "approved")
        action["review_state_action"] = "SET_APPROVED"
        action["review_response"] = review_response
    else:
        action["review_state_action"] = "SKIPPED_BY_OPTION"

    return action


# ---------------------------------------------------------------------
# Attachment handling with immutable local cache
# ---------------------------------------------------------------------

def load_cached_attachments(
    version_dir: Path,
) -> list[dict] | None:
    summary_path = version_dir / "version_summary.json"
    if not summary_path.exists():
        return None

    try:
        prior = json.loads(summary_path.read_text(encoding="utf-8"))
        attachments = prior.get("attachments")
        if not isinstance(attachments, list):
            return None

        output_dir = version_dir / "attachments"

        # Re-hash the local bytes rather than trusting yesterday's manifest.
        refreshed = []
        for item in attachments:
            copied = dict(item)
            filename = copied.get("name")
            if copied.get("exists") and filename:
                local_path = output_dir / Path(filename).name
                if not local_path.exists():
                    return None
                data = local_path.read_bytes()
                copied["bytes"] = len(data)
                copied["sha256"] = sha256_bytes(data)
                copied["archive_source"] = "local_cache"
            refreshed.append(copied)

        return refreshed
    except Exception:
        return None


def fetch_version_attachments(
    central: Central,
    base: str,
    encoded_version: str,
    version_dir: Path,
    methodmesh: dict,
) -> tuple[list[dict], str]:
    """
    For a previously observed immutable submission version, prefer locally
    archived attachment bytes. This avoids both unnecessary downloads and the
    documented risk that a later server-side attachment overwrite could alter
    what a historical attachment endpoint returns.
    """
    cached = load_cached_attachments(version_dir)
    if cached is not None:
        return cached, "SEEN_BEFORE"

    attachments = central.get_json(
        f"{base}/versions/{encoded_version}/attachments"
    )

    (version_dir / "attachments.json").write_text(
        pretty_json(attachments),
        encoding="utf-8",
    )

    output_dir = version_dir / "attachments"
    output_dir.mkdir(exist_ok=True)

    result = []
    expected_image_hash = methodmesh.get(
        "photo_redacted_image_sha256"
    )

    for attachment in attachments:
        filename = attachment.get("name")
        exists = bool(attachment.get("exists"))

        item = {
            "name": filename,
            "exists": exists,
            "archive_source": "central_first_observation",
        }

        if not filename or not exists:
            result.append(item)
            continue

        encoded_filename = path_part(filename)
        attachment_path = (
            f"{base}/versions/{encoded_version}"
            f"/attachments/{encoded_filename}"
        )

        if STREAM_ATTACHMENTS_WITHOUT_PERSISTING:
            streamed = central.stream_sha256(attachment_path)
            observed_hash = streamed["sha256"]
            item.update({
                "bytes": streamed["bytes"],
                "sha256": observed_hash,
                "etag": streamed.get("etag"),
                "content_type": streamed.get("content_type"),
                "storage": "STREAMED_AND_DISCARDED",
            })
        else:
            data, response = central.get_bytes(attachment_path)

            destination = output_dir / Path(filename).name
            destination.write_bytes(data)

            observed_hash = sha256_bytes(data)

            item.update({
                "bytes": len(data),
                "sha256": observed_hash,
                "etag": response.headers.get("ETag"),
                "content_type": response.headers.get("Content-Type"),
                "storage": "LOCAL_ARCHIVE",
            })

        if expected_image_hash and len(attachments) == 1:
            item["matches_methodmesh_image_sha256"] = (
                observed_hash == expected_image_hash
            )

        result.append(item)

    return result, "NEWLY_ARCHIVED"


# ---------------------------------------------------------------------
# Probe one logical submission
# ---------------------------------------------------------------------

def probe_submission(
    central: Central,
    project_id: int,
    form_id: str,
    submission_id: str,
    output_root: Path,
    *,
    writeback: bool = False,
    update_review_state: bool = True,
    tsa_ca_file: str | None = None,
    tsa_cert_file: str | None = None,
    allow_tsa_download: bool = True,
    desktop_tsa_url: str = DEFAULT_DESKTOP_TSA_URL,
):
    project = path_part(project_id)
    form = path_part(form_id)
    logical = path_part(submission_id)

    base = (
        f"/v1/projects/{project}"
        f"/forms/{form}"
        f"/submissions/{logical}"
    )

    submission_dir = output_root / safe_name(submission_id)
    submission_dir.mkdir(parents=True, exist_ok=True)

    print()
    print("=" * 78)
    print(f"SUBMISSION {submission_id}")
    print("=" * 78)

    metadata = central.get_json(base, extended=True)
    (submission_dir / "submission_metadata.json").write_text(
        pretty_json(metadata),
        encoding="utf-8",
    )

    versions_raw = central.get_json(f"{base}/versions")
    (submission_dir / "versions_raw.json").write_text(
        pretty_json(versions_raw),
        encoding="utf-8",
    )

    # Central does not guarantee that the desired historical presentation
    # order is oldest -> newest. Sort explicitly.
    versions = sorted(
        versions_raw,
        key=lambda x: parse_iso(x.get("createdAt")),
    )

    print(f"Versions found: {len(versions)}")

    # Privacy rule: v11 deliberately does NOT call Central's /diffs endpoint.
    # That endpoint returns old/new research values. Changed field names are
    # derived locally from the already-required restricted XML archive instead.
    diffs = []

    audits = central.get_json(f"{base}/audits", extended=True)
    (submission_dir / "audits.json").write_text(
        pretty_json(audits),
        encoding="utf-8",
    )

    if len(versions) == 0:
        result = {
            "logical_submission_id": submission_id,
            "version_count": 0,
            "status": "NO_RETAINED_VERSIONS_RETURNED",
            "submission_metadata": metadata,
            "central_diff_endpoint_queried": False,
            "audit_event_count": len(audits),
            "versions": [],
        }

        (submission_dir / "probe_summary.json").write_text(
            pretty_json(result),
            encoding="utf-8",
        )

        print()
        print("NO RETAINED VERSIONS RETURNED")
        print(f"Central audit events: {len(audits)}")
        return result

    version_summary = []
    trusted_anchor = None
    current_runtime = None

    for index, version_stub in enumerate(versions):
        version_id = version_stub["instanceId"]
        encoded_version = path_part(version_id)

        details = central.get_json(
            f"{base}/versions/{encoded_version}",
            extended=True,
        )

        # The specific submission-version endpoint gives us the exact
        # published Form Version against which this submission version
        # was created.
        form_version = details.get("formVersion")

        form_archive = archive_form_version(
            central=central,
            project_id=project_id,
            form_id=form_id,
            form_version=form_version,
            output_root=output_root,
        )

        # Prefer the detailed response's timestamp.
        created_at = details.get("createdAt") or version_stub.get("createdAt")
        current = bool(
            details.get("current")
            if details.get("current") is not None
            else version_stub.get("current")
        )

        is_original = index == 0
        revision_number = index

        if is_original and current:
            label = "ORIGINAL / CURRENT"
        elif is_original:
            label = "ORIGINAL"
        elif current:
            label = f"REVISION {revision_number} / CURRENT"
        else:
            label = f"REVISION {revision_number}"

        version_dir = (
            submission_dir
            / f"{index + 1:03d}_{safe_name(version_id)}"
        )
        version_dir.mkdir(parents=True, exist_ok=True)

        (version_dir / "metadata.json").write_text(
            pretty_json(details),
            encoding="utf-8",
        )

        # Historical XML is immutable once this version exists. Preserve the
        # first bytes we observed locally and do not overwrite them on later
        # polls.
        xml_path = version_dir / "submission.xml"
        if xml_path.exists():
            xml_bytes = xml_path.read_bytes()
            xml_archive_status = "SEEN_BEFORE"
        else:
            xml_bytes, _ = central.get_bytes(
                f"{base}/versions/{encoded_version}.xml"
            )
            xml_path.write_bytes(xml_bytes)
            xml_archive_status = "NEWLY_ARCHIVED"

        xml_hash = sha256_bytes(xml_bytes)

        try:
            root = ET.fromstring(xml_bytes)
            methodmesh = extract_methodmesh(root)
        except ET.ParseError as exc:
            root = None
            methodmesh = {
                "xml_parse_error": str(exc),
                "attestation_present": False,
            }

        attachments, attachment_archive_status = fetch_version_attachments(
            central=central,
            base=base,
            encoded_version=encoded_version,
            version_dir=version_dir,
            methodmesh=methodmesh,
        )

        # Only the original retained version establishes the mobile
        # cryptographic trust anchor. Later versions carry copied attestation
        # JSON but are evaluated against the already verified original anchor.
        if index == 0 and root is not None:
            crypto_verification = verify_methodmesh_attestation_crypto(
                methodmesh=methodmesh,
                version_dir=version_dir,
                output_root=output_root,
                central_created_at=created_at,
                tsa_ca_file=tsa_ca_file,
                tsa_cert_file=tsa_cert_file,
                allow_tsa_download=allow_tsa_download,
            )
            methodmesh["cryptographic_verification"] = crypto_verification

        reconstruction = None

        if root is not None:
            # The original retained version is the only place from which we
            # accept a recipe. Every later version is reconstructed using the
            # trusted original recipe, never Enketo's copied/recalculated
            # technical fields.
            recipe_for_reconstruction = (
                methodmesh.get("commitment_recipe")
                if index == 0
                else (
                    trusted_anchor.get("recipe")
                    if trusted_anchor is not None
                    else None
                )
            )

            reconstruction = reconstruct_from_recipe(
                root=root,
                recipe=recipe_for_reconstruction,
                attachments=attachments,
            )

        if index == 0:
            classification, trusted_anchor = original_anchor_status(
                methodmesh=methodmesh,
                reconstruction=reconstruction,
            )
            if trusted_anchor is not None:
                trusted_anchor["original_version_id"] = version_id
                trusted_anchor["original_xml_sha256"] = xml_hash
                trusted_anchor["original_form_version"] = form_version
        else:
            classification = classify_against_anchor(
                anchor=trusted_anchor,
                reconstruction=reconstruction,
            )

            desktop_receipt = methodmesh.get("desktop_audit_receipt")
            if isinstance(desktop_receipt, dict):
                methodmesh["desktop_receipt_timestamp_verification"] = (
                    verify_desktop_receipt_timestamp(
                        receipt=desktop_receipt,
                        output_root=output_root,
                        version_dir=version_dir,
                        tsa_ca_file=tsa_ca_file,
                        tsa_cert_file=tsa_cert_file,
                        allow_tsa_download=allow_tsa_download,
                        central_created_at=created_at,
                    )
                )

            # A desktop writeback version is not expected to match the
            # original mobile event payload hash. If this exact retained
            # version carries a valid receipt naming itself as the writeback
            # version and its independently reconstructed payload matches that
            # receipt, classify it as desktop-verified instead of merely
            # EDITED_AFTER_ATTESTATION.
            if (
                trusted_anchor is not None
                and reconstruction
                and reconstruction.get("available")
                and reconstruction.get("complete")
                and reconstruction.get("reconstructed_payload_sha256")
            ):
                desktop_receipt_valid, desktop_receipt_reason = (
                    validate_existing_receipt(
                        methodmesh.get("desktop_audit_receipt"),
                        current_version_id=version_id,
                        reconstructed_payload_sha256=(
                            reconstruction.get(
                                "reconstructed_payload_sha256"
                            )
                        ),
                        recipe_sha256=trusted_anchor.get("recipe_sha256"),
                        timestamp_verification=methodmesh.get(
                            "desktop_receipt_timestamp_verification"
                        ),
                    )
                )
                if desktop_receipt_valid:
                    classification = "VERIFIED_BY_DESKTOP_RECEIPT"
                methodmesh["desktop_receipt_validation"] = (
                    desktop_receipt_reason
                )
            else:
                methodmesh["desktop_receipt_validation"] = (
                    "NOT_APPLICABLE_OR_UNVERIFIABLE"
                )

        write_reconstruction_debug(
            version_dir=version_dir,
            root=root,
            methodmesh=methodmesh,
            reconstruction=reconstruction,
            classification=classification,
            xml_sha256=xml_hash,
        )

        submitter = details.get("submitter")
        actor = None
        if isinstance(submitter, dict):
            actor = (
                submitter.get("displayName")
                or submitter.get("email")
                or submitter.get("id")
            )

        if current:
            current_runtime = {
                "version_id": version_id,
                "created_at": created_at,
                "form_version": form_version,
                "actor": actor,
                "xml_sha256": xml_hash,
                "xml_bytes": xml_bytes,
                "root": root,
                "methodmesh": methodmesh,
                "reconstruction": reconstruction,
                "classification": classification,
                "attachments": attachments,
                "version_dir": version_dir,
            }

        summary_item = {
            "sequence": index + 1,
            "label": label,
            "version_id": version_id,
            "created_at": created_at,
            "current": current,
            "form_version": form_version,
            "form_archive": {
                "directory": str(
                    Path("_form_versions")
                    / safe_name(
                        form_version
                        if form_version not in (None, "")
                        else "__blank__"
                    )
                ),
                "xform_xml_sha256": (
                    form_archive.get("xform_xml") or {}
                ).get("sha256"),
                "xlsform_available": (
                    form_archive.get("xlsform") or {}
                ).get("available"),
                "xlsform_sha256": (
                    form_archive.get("xlsform") or {}
                ).get("sha256"),
            },
            "actor": actor,
            "submitter_id": details.get("submitterId"),
            "device_id": details.get("deviceId"),
            "user_agent": details.get("userAgent"),
            "xml_sha256": xml_hash,
            "xml_bytes": len(xml_bytes),
            "archive_status": (
                "SEEN_BEFORE"
                if xml_archive_status == "SEEN_BEFORE"
                and attachment_archive_status == "SEEN_BEFORE"
                else "NEW_OR_INCOMPLETE_ARCHIVE"
            ),
            "classification": classification,
            "methodmesh": methodmesh,
            "reconstruction": reconstruction,
            "attachments": attachments,
        }

        version_summary.append(summary_item)

        (version_dir / "version_summary.json").write_text(
            pretty_json(summary_item),
            encoding="utf-8",
        )

        print()
        print(label)
        print(f"  ID:             {version_id}")
        print(f"  Created:        {created_at}")
        print(f"  Current:        {current}")
        print(f"  Form version:   {form_version!r}")
        print(f"  Actor:          {actor or '-'}")
        print(f"  XML SHA-256:    {xml_hash}")
        print(
            "  Payload:        "
            f"{methodmesh.get('showcase_payload_sha256') or '-'}"
        )
        print(
            "  Attested hash:  "
            f"{methodmesh.get('attestation_event_payload_hash') or '-'}"
        )
        print(
            "  Stored check:   "
            f"{methodmesh.get('payload_matches_attestation')} "
            "(diagnostic only)"
        )
        print(
            "  Recipe:         "
            + (
                "v1 / ORIGINAL ANCHOR"
                if trusted_anchor is not None
                else (
                    "v1 / UNTRUSTED"
                    if isinstance(
                        methodmesh.get("commitment_recipe"),
                        dict,
                    )
                    else "legacy/absent"
                )
            )
        )
        if reconstruction and reconstruction.get("available"):
            print(
                "  Rebuilt SHA:    "
                f"{reconstruction.get('reconstructed_payload_sha256') or '-'}"
            )
            print(
                "  Stored digests: "
                + (
                    "MATCH"
                    if reconstruction.get("all_indirect_commitments_match")
                    else "MISMATCH (ignored for reconstruction)"
                )
            )
            mismatches = [
                item.get("path")
                for item in (reconstruction.get("members") or [])
                if item.get("stored_matches_source") is False
                or item.get("source_ok") is False
            ]
            if mismatches:
                print(
                    "  Source issues:  "
                    + ", ".join(str(x) for x in mismatches)
                )
            stored_canonical = methodmesh.get("showcase_payload_canonical")
            if stored_canonical is not None:
                print(
                    "  Canonical:      "
                    + (
                        "MATCH"
                        if stored_canonical
                        == reconstruction.get("reconstructed_payload")
                        else "DIFFERENT"
                    )
                )
        print(
            "  Archive:        "
            f"{xml_archive_status}/{attachment_archive_status}"
        )
        print(
            "  TSA declared:   "
            f"{methodmesh.get('trusted_timestamp_status') or '-'}"
        )
        if index == 0:
            crypto = methodmesh.get("cryptographic_verification") or {}
            print(
                "  ECDSA signature:"
                + (
                    " PASS"
                    if crypto.get("ecdsa_signature_valid") is True
                    else " FAIL"
                )
            )
            print(
                "  Attest. hash:   "
                + (
                    "PASS"
                    if crypto.get("attestation_hash_valid") is True
                    else "FAIL"
                )
            )
            print(
                "  RFC3161 token:  "
                + (
                    "PASS"
                    if crypto.get(
                        "rfc3161_signature_and_chain_valid"
                    ) is True
                    else "FAIL"
                )
            )
            print(
                "  TSA trust:      "
                f"{crypto.get('tsa_trust_source') or '-'}"
            )
            print(
                "  Crypto overall: "
                f"{crypto.get('verification_status') or 'UNKNOWN'}"
            )
        else:
            print(
                "  TSA binding:    "
                f"{methodmesh.get('tsa_attests_attestation_hash')} "
                "(copied field; not independently re-trusted here)"
            )
        print(f"  Files:          {len(attachments)}")
        print(f"  RESULT:         {classification}")
        if index > 0:
            receipt_state = methodmesh.get("desktop_receipt_validation")
            if receipt_state and receipt_state != "NO_RECEIPT":
                print(
                    "  Desktop receipt:"
                    f" {receipt_state}"
                )
            receipt_tsa = (
                methodmesh.get(
                    "desktop_receipt_timestamp_verification"
                )
                or {}
            )
            if receipt_tsa:
                print(
                    "  Desktop TSA:    "
                    f"{receipt_tsa.get('verification_status')}"
                )

        for attachment in attachments:
            if "matches_methodmesh_image_sha256" in attachment:
                print(
                    "  Image hash:     "
                    + (
                        "MATCH"
                        if attachment["matches_methodmesh_image_sha256"]
                        else "MISMATCH"
                    )
                )

    # -----------------------------------------------------------------
    # Submission-level interpretation
    # -----------------------------------------------------------------

    edit_history = len(version_summary) > 1

    original = version_summary[0]
    current_versions = [v for v in version_summary if v["current"]]
    current = current_versions[-1] if current_versions else version_summary[-1]

    original_attestation_valid = (
        trusted_anchor is not None
        and original["classification"]
        == "CRYPTOGRAPHICALLY_VERIFIED_ATTESTED_VERSION"
    )

    if not edit_history:
        overall_status = (
            "CURRENT_VERSION_ATTESTED"
            if original_attestation_valid
            else original["classification"]
        )
    else:
        if (
            original_attestation_valid
            and current.get("classification")
            == "VERIFIED_BY_DESKTOP_RECEIPT"
        ):
            overall_status = (
                "EDIT_HISTORY_PRESENT_CURRENT_VERSION_DESKTOP_VERIFIED"
            )
        elif original_attestation_valid:
            overall_status = (
                "EDIT_HISTORY_PRESENT_ORIGINAL_ATTESTATION_INTACT"
            )
        else:
            overall_status = (
                "EDIT_HISTORY_PRESENT_ORIGINAL_ATTESTATION_UNRESOLVED"
            )

    writeback_action = None
    if current_runtime is not None:
        writeback_action = apply_current_validation_writeback(
            central=central,
            base=base,
            logical_submission_id=submission_id,
            trusted_anchor=trusted_anchor,
            original_version_id=original["version_id"],
            original_version_classification=original["classification"],
            version_summary=version_summary,
            current_runtime=current_runtime,
            current_metadata=metadata,
            writeback=writeback,
            update_review_state=update_review_state,
            output_root=output_root,
            tsa_ca_file=tsa_ca_file,
            tsa_cert_file=tsa_cert_file,
            allow_tsa_download=allow_tsa_download,
            desktop_tsa_url=desktop_tsa_url,
        )

        (submission_dir / "writeback_action.json").write_text(
            pretty_json(writeback_action),
            encoding="utf-8",
        )

    result = {
        "logical_submission_id": submission_id,
        "version_count": len(version_summary),
        "edit_history_detected": edit_history,
        "original_version_retrievable": True,
        "original_attestation_valid_for_original_version":
            original_attestation_valid,
        "current_version_id": current["version_id"],
        "current_version_classification": current["classification"],
        "central_diff_endpoint_queried": False,
        "audit_event_count": len(audits),
        "overall_status": overall_status,
        "writeback_action": writeback_action,
        "versions": version_summary,
    }

    (submission_dir / "probe_summary.json").write_text(
        pretty_json(result),
        encoding="utf-8",
    )

    print()
    if edit_history:
        print("EDIT HISTORY DETECTED")
        print("  PASS: original XML is still retrievable")
        if original_attestation_valid:
            print("  PASS: original MethodMesh payload still matches its attestation")
        else:
            print("  WARN: original MethodMesh attestation relationship unresolved")

        print(
            "  Privacy: Central /diffs not queried; changed field names "
            "are derived locally without exporting old/new values"
        )

        print(
            "  Current revision classification: "
            f"{current['classification']}"
        )
    else:
        print("No edit history: one retained version.")

    print(f"Central audit events: {len(audits)}")
    print(f"OVERALL: {overall_status}")

    if writeback_action is not None:
        print("DESKTOP VALIDATION:")
        print(
            "  Mode:           "
            f"{writeback_action.get('mode')}"
        )
        print(
            "  Receipt:        "
            f"{writeback_action.get('receipt_action')}"
        )
        print(
            "  Review state:   "
            f"{writeback_action.get('review_state_action')}"
        )
        sequence_info = (
            writeback_action.get("sequence_verification") or {}
        )
        print(
            "  Unseen versions:"
            f" {sequence_info.get('unseen_count', 0)}"
        )
        print(
            "  Sequence:       "
            f"{sequence_info.get('reason')}"
        )
        prior = writeback_action.get("prior_checkpoint")
        if prior:
            print(
                "  Prior receipt:  "
                f"{prior.get('checkpoint_version_id')}"
            )
        if writeback_action.get("pre_tsa_server_check"):
            print(
                "  Pre-TSA check:  "
                f"{writeback_action.get('pre_tsa_server_check')}"
            )
        if writeback_action.get("post_tsa_server_check"):
            print(
                "  Post-TSA check: "
                f"{writeback_action.get('post_tsa_server_check')}"
            )
        receipt_tsa = writeback_action.get("desktop_receipt_timestamp") or {}
        if receipt_tsa:
            print(
                "  Receipt TSA:    "
                f"{receipt_tsa.get('verification_status')}"
            )
            if receipt_tsa.get("time_iso"):
                print(
                    "  TSA time:       "
                    f"{receipt_tsa.get('time_iso')}"
                )

    return result



# ---------------------------------------------------------------------
# Privacy-safe daily trial/system evidence
# ---------------------------------------------------------------------

DAILY_EVIDENCE_SCHEMA = "methodmesh.daily_evidence.v1"
DAILY_CHECKPOINT_SCHEMA = "methodmesh.daily_checkpoint.v1"
SERVICE_PROBE_SCHEMA = "methodmesh.service_probe.v1"

PROVENANCE_TECHNICAL_FIELDS = {
    "instanceID",
    "deprecatedID",
    "desktop_audit_receipt_json",
    "showcase_payload_sha256",
    "showcase_payload_canonical",
    "attestation_methodmesh_full_json",
    "attestation_methodmesh_closeout_status",
    "photo_redacted_image_sha256",
}


def canonical_leaf_map(root: ET.Element) -> dict[str, list[str]]:
    """
    Return local XML leaf values grouped by local name.

    This is used only in process memory to determine WHICH committed source
    fields changed. Values are never written to the daily evidence bundle.
    """
    grouped: dict[str, list[str]] = {}
    for elem in root.iter():
        if len(list(elem)) != 0:
            continue
        name = local_name(elem.tag)
        grouped.setdefault(name, []).append(
            "" if elem.text is None else str(elem.text)
        )
    return grouped


def recipe_source_field_names(recipe: dict | None) -> list[str]:
    names = []
    if not isinstance(recipe, dict):
        return names

    for member in recipe.get("members") or []:
        if not isinstance(member, dict):
            continue

        source = member.get("source_field")
        artifact = member.get("artifact_field")
        direct_path = (
            member.get("path")
            if member.get("commitment") == "value"
            else None
        )

        for candidate in (direct_path, source, artifact):
            if not candidate:
                continue
            name = str(candidate).strip()
            if not name:
                continue
            if "/" in name:
                name = name.rstrip("/").split("/")[-1]
            if name in PROVENANCE_TECHNICAL_FIELDS:
                continue
            if name.endswith("_sha256"):
                continue
            if name not in names:
                names.append(name)

    return names


def best_effort_xform_labels(form_xml_path: Path) -> dict[str, str]:
    """
    Best-effort map from XML field name to human label.

    XForms with complex itext/translations may not yield a simple literal
    label. In those cases the stable field name is retained.
    """
    if not form_xml_path.exists():
        return {}

    try:
        root = ET.fromstring(form_xml_path.read_bytes())
    except Exception:
        return {}

    labels: dict[str, str] = {}

    for elem in root.iter():
        ref = (
            elem.attrib.get("ref")
            or elem.attrib.get("nodeset")
        )
        if not ref:
            continue

        field_name = ref.rstrip("/").split("/")[-1]
        if not field_name:
            continue

        label_text = None
        for child in list(elem):
            if local_name(child.tag) != "label":
                continue
            direct = "".join(child.itertext()).strip()
            if direct:
                label_text = " ".join(direct.split())
                break

        if label_text and "${" not in label_text:
            labels.setdefault(field_name, label_text)

    return labels


def field_display_name(field_name: str, labels: dict[str, str]) -> str:
    label = labels.get(field_name)
    if label:
        return label
    return field_name.replace("_", " ").strip().title()


def changed_committed_fields_for_versions(
    *,
    previous_xml: Path,
    current_xml: Path,
    recipe: dict | None,
    labels: dict[str, str],
    previous_attachments: list[dict] | None = None,
    current_attachments: list[dict] | None = None,
) -> list[dict]:
    """
    Compare only recipe-referenced source/artifact fields.

    No old or new values are returned or persisted.
    """
    try:
        prev_root = ET.fromstring(previous_xml.read_bytes())
        curr_root = ET.fromstring(current_xml.read_bytes())
    except Exception:
        return []

    prev = canonical_leaf_map(prev_root)
    curr = canonical_leaf_map(curr_root)
    source_names = recipe_source_field_names(recipe)

    changed = []

    for name in source_names:
        if prev.get(name, []) != curr.get(name, []):
            changed.append({
                "field_name": name,
                "display_name": field_display_name(name, labels),
                "change_type": "FIELD_VALUE_CHANGED",
            })

    # Artifact bytes can change even if their filename/reference does not.
    prev_att = {
        item.get("name"): item.get("sha256")
        for item in (previous_attachments or [])
        if item.get("name")
    }
    curr_att = {
        item.get("name"): item.get("sha256")
        for item in (current_attachments or [])
        if item.get("name")
    }

    if prev_att != curr_att:
        artifact_names = []
        if isinstance(recipe, dict):
            for member in recipe.get("members") or []:
                if not isinstance(member, dict):
                    continue
                field = member.get("artifact_field")
                if field:
                    name = str(field).rstrip("/").split("/")[-1]
                    if name not in artifact_names:
                        artifact_names.append(name)
        for name in artifact_names:
            if not any(x["field_name"] == name for x in changed):
                changed.append({
                    "field_name": name,
                    "display_name": field_display_name(name, labels),
                    "change_type": "ARTIFACT_BYTES_CHANGED",
                })

    return sorted(
        changed,
        key=lambda x: (x["display_name"], x["field_name"]),
    )


def comment_fingerprint(comment: dict) -> str:
    actor_id = comment.get("actorId")
    body = str(comment.get("body") or "")
    return sha256_bytes(
        compact_json({
            "actorId": actor_id,
            "body": body,
        }).encode("utf-8")
    )


def privacy_safe_comment(comment: dict) -> dict:
    actor = comment.get("actor")
    return {
        "actorId": comment.get("actorId"),
        "actor": basic_actor(actor),
        "body": str(comment.get("body") or ""),
        "body_sha256": sha256_bytes(
            str(comment.get("body") or "").encode("utf-8")
        ),
        "fingerprint": comment_fingerprint(comment),
    }


def latest_daily_evidence_file(output_root: Path) -> Path | None:
    daily_root = output_root / "_daily_evidence"
    if not daily_root.exists():
        return None

    candidates = sorted(
        daily_root.glob("*/daily_evidence.json"),
        key=lambda p: p.parent.name,
    )
    return candidates[-1] if candidates else None


def load_previous_daily_evidence(output_root: Path) -> dict | None:
    path = latest_daily_evidence_file(output_root)
    if path is None:
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None


def extract_endpoint_hostname(central_url: str) -> tuple[str, int]:
    parsed = urlparse(central_url)
    if not parsed.hostname:
        raise ValueError(f"Cannot determine hostname from {central_url!r}")
    port = parsed.port or (443 if parsed.scheme == "https" else 80)
    return parsed.hostname, port


def tcp_port_probe(host: str, port: int, timeout: float = 2.0) -> dict:
    started = time.monotonic()
    try:
        sock = socket.create_connection((host, port), timeout=timeout)
        sock.close()
        return {
            "port": port,
            "state": "OPEN",
            "elapsed_ms": round((time.monotonic() - started) * 1000, 1),
        }
    except ConnectionRefusedError:
        return {
            "port": port,
            "state": "CLOSED_REFUSED",
            "elapsed_ms": round((time.monotonic() - started) * 1000, 1),
        }
    except socket.timeout:
        return {
            "port": port,
            "state": "TIMEOUT_FILTERED_OR_UNREACHABLE",
            "elapsed_ms": round((time.monotonic() - started) * 1000, 1),
        }
    except OSError as exc:
        return {
            "port": port,
            "state": "ERROR",
            "elapsed_ms": round((time.monotonic() - started) * 1000, 1),
            "error": str(exc),
        }


def tls_probe(host: str, port: int = 443) -> dict:
    started = time.monotonic()
    try:
        context = ssl.create_default_context()
        with socket.create_connection((host, port), timeout=5) as raw:
            with context.wrap_socket(raw, server_hostname=host) as tls:
                cert_der = tls.getpeercert(binary_form=True)
                cert = tls.getpeercert()
                cipher = tls.cipher()
                return {
                    "status": "PASS",
                    "elapsed_ms": round(
                        (time.monotonic() - started) * 1000, 1
                    ),
                    "tls_version": tls.version(),
                    "cipher": cipher[0] if cipher else None,
                    "certificate_sha256": sha256_bytes(cert_der),
                    "subject": cert.get("subject"),
                    "issuer": cert.get("issuer"),
                    "serialNumber": cert.get("serialNumber"),
                    "notBefore": cert.get("notBefore"),
                    "notAfter": cert.get("notAfter"),
                    "subjectAltName": cert.get("subjectAltName"),
                }
    except Exception as exc:
        return {
            "status": "FAIL",
            "elapsed_ms": round((time.monotonic() - started) * 1000, 1),
            "error": str(exc),
        }


def icmp_ping_probe(host: str) -> dict:
    started = time.monotonic()
    try:
        completed = subprocess.run(
            ["ping", "-c", "1", host],
            capture_output=True,
            timeout=5,
        )
        return {
            "status": "PASS" if completed.returncode == 0 else "NO_REPLY",
            "returncode": completed.returncode,
            "elapsed_ms": round(
                (time.monotonic() - started) * 1000, 1
            ),
        }
    except FileNotFoundError:
        return {
            "status": "UNAVAILABLE",
            "reason": "ping executable not found",
        }
    except subprocess.TimeoutExpired:
        return {
            "status": "TIMEOUT",
            "elapsed_ms": round(
                (time.monotonic() - started) * 1000, 1
            ),
        }


def service_probe(
    *,
    central: Central,
    project_id: int,
    ports: list[int],
    expected_public_ports: list[int],
) -> dict:
    """
    Point-in-time external service observation.

    This is evidence of reachability at the observation time, not a claim that
    the firewall is generally secure or that the service was continuously up.
    """
    host, central_port = extract_endpoint_hostname(central.base_url)

    started = time.monotonic()
    try:
        addresses = sorted({
            item[4][0]
            for item in socket.getaddrinfo(
                host,
                None,
                proto=socket.IPPROTO_TCP,
            )
        })
        dns = {
            "status": "PASS",
            "addresses": addresses,
            "elapsed_ms": round(
                (time.monotonic() - started) * 1000, 1
            ),
        }
    except Exception as exc:
        dns = {
            "status": "FAIL",
            "addresses": [],
            "elapsed_ms": round(
                (time.monotonic() - started) * 1000, 1
            ),
            "error": str(exc),
        }

    http_started = time.monotonic()
    try:
        response = requests.get(
            f"{central.base_url}/version.txt",
            timeout=10,
            allow_redirects=True,
            headers={"User-Agent": "MethodMesh-Service-Probe/1.0"},
        )
        version_text = response.text.strip()
        http = {
            "status": "PASS" if response.ok else "FAIL",
            "status_code": response.status_code,
            "elapsed_ms": round(
                (time.monotonic() - http_started) * 1000, 1
            ),
            "final_url": response.url,
            "content_type": response.headers.get("Content-Type"),
            "server_header": response.headers.get("Server"),
            "version_text": version_text,
            "version_text_sha256": sha256_bytes(
                version_text.encode("utf-8")
            ),
        }
    except Exception as exc:
        http = {
            "status": "FAIL",
            "status_code": None,
            "elapsed_ms": round(
                (time.monotonic() - http_started) * 1000, 1
            ),
            "error": str(exc),
        }

    api_started = time.monotonic()
    try:
        response = central.session.get(
            f"{central.base_url}/v1/projects/{path_part(project_id)}",
            headers={"X-Extended-Metadata": "true"},
            timeout=10,
        )
        api = {
            "status": "PASS" if response.status_code == 200 else "FAIL",
            "status_code": response.status_code,
            "elapsed_ms": round(
                (time.monotonic() - api_started) * 1000, 1
            ),
        }
    except Exception as exc:
        api = {
            "status": "FAIL",
            "status_code": None,
            "elapsed_ms": round(
                (time.monotonic() - api_started) * 1000, 1
            ),
            "error": str(exc),
        }

    all_ports = sorted(set(ports + [central_port]))
    port_results = [tcp_port_probe(host, port) for port in all_ports]
    open_ports = sorted(
        p["port"] for p in port_results if p["state"] == "OPEN"
    )

    expected = sorted(set(expected_public_ports))
    unexpected_open = sorted(set(open_ports) - set(expected))
    expected_not_open = sorted(set(expected) - set(open_ports))

    return {
        "schema": SERVICE_PROBE_SCHEMA,
        "observed_at_utc": utc_now_iso(),
        "central_url": central.base_url,
        "hostname": host,
        "dns": dns,
        "icmp": icmp_ping_probe(host),
        "tls": tls_probe(host, 443),
        "http_version_endpoint": http,
        "authenticated_api": api,
        "tcp_ports": port_results,
        "expected_public_ports": expected,
        "unexpected_open_ports": unexpected_open,
        "expected_public_ports_not_open": expected_not_open,
        "interpretation_note": (
            "Port observations describe reachability from this verifier host "
            "at this instant only. CLOSED/TIMEOUT is not a complete firewall "
            "assessment."
        ),
    }


def append_service_probe(
    *,
    output_root: Path,
    probe: dict,
) -> Path:
    path = output_root / "_service_probes.jsonl"
    with path.open("a", encoding="utf-8") as handle:
        handle.write(compact_json(probe) + "\n")
    return path


def load_service_probes(
    *,
    output_root: Path,
    start: datetime,
    end: datetime,
) -> list[dict]:
    path = output_root / "_service_probes.jsonl"
    if not path.exists():
        return []

    result = []
    for line in path.read_text(encoding="utf-8").splitlines():
        try:
            item = json.loads(line)
            observed = parse_iso_strict(item.get("observed_at_utc"))
            if observed is not None and start <= observed <= end:
                result.append(item)
        except Exception:
            continue
    return result


def service_probe_summary(probes: list[dict]) -> dict:
    if not probes:
        return {
            "observation_count": 0,
            "http_success_count": 0,
            "api_success_count": 0,
            "observed_http_success_fraction": None,
            "observed_api_success_fraction": None,
            "unexpected_open_ports_seen": [],
        }

    http_success = sum(
        1 for p in probes
        if (p.get("http_version_endpoint") or {}).get("status") == "PASS"
    )
    api_success = sum(
        1 for p in probes
        if (p.get("authenticated_api") or {}).get("status") == "PASS"
    )
    unexpected = sorted({
        port
        for p in probes
        for port in (p.get("unexpected_open_ports") or [])
    })

    return {
        "observation_count": len(probes),
        "http_success_count": http_success,
        "api_success_count": api_success,
        "observed_http_success_fraction": http_success / len(probes),
        "observed_api_success_fraction": api_success / len(probes),
        "unexpected_open_ports_seen": unexpected,
        "interpretation_note": (
            "These are observed probe success fractions, not continuous "
            "availability unless probes are scheduled at the declared cadence."
        ),
    }



def collect_server_audits_paged(
    *,
    central: Central,
    start: datetime,
    end: datetime,
    page_size: int = 1000,
    max_pages: int = 100,
) -> dict:
    start_q = quote(start.isoformat().replace("+00:00", "Z"), safe="")
    end_q = quote(end.isoformat().replace("+00:00", "Z"), safe="")

    all_events = []
    page_status = []
    complete = True

    for page in range(max_pages):
        offset = page * page_size
        path = (
            f"/v1/audits?start={start_q}&end={end_q}"
            f"&limit={page_size}&offset={offset}"
        )
        response = central_optional_json(
            central,
            path,
            extended=True,
        )
        page_status.append({
            "offset": offset,
            "status_code": response.get("status_code"),
            "elapsed_ms": response.get("elapsed_ms"),
            "available": response.get("available"),
        })

        if not response.get("available"):
            complete = False
            return {
                "available": bool(all_events),
                "status_code": response.get("status_code"),
                "complete": False,
                "pages_read": page + 1,
                "page_status": page_status,
                "reason": response.get("reason"),
                "data": sanitize_server_audit_events(all_events),
            }

        data = response.get("data")
        if not isinstance(data, list):
            complete = False
            break

        all_events.extend(data)
        if len(data) < page_size:
            break
    else:
        complete = False

    return {
        "available": True,
        "status_code": 200,
        "complete": complete,
        "pages_read": len(page_status),
        "page_status": page_status,
        "event_count": len(all_events),
        "data": sanitize_server_audit_events(all_events),
    }


def fetch_post_run_submission_state(
    *,
    central: Central,
    project_id: int,
    form_id: str,
    submission_id: str,
) -> dict:
    base = (
        f"/v1/projects/{path_part(project_id)}"
        f"/forms/{path_part(form_id)}"
        f"/submissions/{path_part(submission_id)}"
    )

    metadata = central_optional_json(
        central,
        base,
        extended=True,
    )
    versions = central_optional_json(
        central,
        f"{base}/versions",
        extended=True,
    )

    version_data = (
        versions.get("data")
        if versions.get("available")
        and isinstance(versions.get("data"), list)
        else []
    )

    current = None
    for item in version_data:
        if isinstance(item, dict) and item.get("current"):
            current = item
            break

    return {
        "metadata_available": metadata.get("available"),
        "versions_available": versions.get("available"),
        "review_state": (
            (metadata.get("data") or {}).get("reviewState")
            if metadata.get("available")
            and isinstance(metadata.get("data"), dict)
            else None
        ),
        "current_version_id": (
            current.get("instanceId")
            if isinstance(current, dict)
            else None
        ),
        "version_ids": [
            item.get("instanceId")
            for item in version_data
            if isinstance(item, dict) and item.get("instanceId")
        ],
        "version_count": len(version_data),
    }


def collect_central_system_metadata(
    *,
    central: Central,
    project_id: int,
    form_id: str,
    start: datetime,
    end: datetime,
) -> dict:
    project = path_part(project_id)
    form = path_part(form_id)

    endpoint_results = {}

    endpoint_results["projects"] = central_optional_json(
        central,
        "/v1/projects",
        extended=True,
    )
    endpoint_results["forms"] = central_optional_json(
        central,
        f"/v1/projects/{project}/forms",
        extended=True,
    )
    endpoint_results["project"] = central_optional_json(
        central,
        f"/v1/projects/{project}",
        extended=True,
    )
    endpoint_results["form"] = central_optional_json(
        central,
        f"/v1/projects/{project}/forms/{form}",
        extended=True,
    )
    endpoint_results["form_versions"] = central_optional_json(
        central,
        f"/v1/projects/{project}/forms/{form}/versions",
        extended=True,
    )
    endpoint_results["submitters"] = central_optional_json(
        central,
        f"/v1/projects/{project}/forms/{form}/submissions/submitters",
        extended=True,
    )
    endpoint_results["roles"] = central_optional_json(
        central,
        "/v1/roles",
    )
    endpoint_results["app_users"] = central_optional_json(
        central,
        f"/v1/projects/{project}/app-users",
        extended=True,
    )
    endpoint_results["actor_properties"] = central_optional_json(
        central,
        f"/v1/projects/{project}/actor-properties",
        extended=True,
    )
    endpoint_results["project_form_assignments"] = central_optional_json(
        central,
        f"/v1/projects/{project}/assignments/forms",
        extended=True,
    )
    endpoint_results["form_assignments"] = central_optional_json(
        central,
        f"/v1/projects/{project}/forms/{form}/assignments",
        extended=True,
    )
    endpoint_results["project_assignments"] = central_optional_json(
        central,
        f"/v1/projects/{project}/assignments",
        extended=True,
    )
    endpoint_results["system_assignments"] = central_optional_json(
        central,
        "/v1/assignments",
        extended=True,
    )
    endpoint_results["users"] = central_optional_json(
        central,
        "/v1/users",
        extended=True,
    )
    endpoint_results["analytics_config"] = central_optional_json(
        central,
        "/v1/config/analytics",
    )
    endpoint_results["analytics_preview"] = central_optional_json(
        central,
        "/v1/analytics/preview",
    )

    endpoint_results["server_audits"] = collect_server_audits_paged(
        central=central,
        start=start,
        end=end,
    )

    # Sanitize user/account and audit content before the auditor bundle.
    users = endpoint_results.get("users") or {}
    if users.get("available"):
        users["data"] = sanitize_user_records(users.get("data"))

    for endpoint in endpoint_results.values():
        if isinstance(endpoint, dict) and endpoint.get("available"):
            endpoint["data"] = sanitize_system_metadata(
                endpoint.get("data")
            )

    return {
        "window_start_utc": start.isoformat(),
        "window_end_utc": end.isoformat(),
        "endpoints": endpoint_results,
    }


def fetch_submission_comments(
    *,
    central: Central,
    project_id: int,
    form_id: str,
    submission_id: str,
) -> list[dict]:
    path = (
        f"/v1/projects/{path_part(project_id)}"
        f"/forms/{path_part(form_id)}"
        f"/submissions/{path_part(submission_id)}/comments"
    )
    response = central_optional_json(
        central,
        path,
        extended=True,
    )
    if not response.get("available"):
        return []
    data = response.get("data")
    if not isinstance(data, list):
        return []
    return [privacy_safe_comment(item) for item in data]


def submission_comment_counter(
    prior_submission: dict | None,
) -> Counter:
    if not isinstance(prior_submission, dict):
        return Counter()
    return Counter(
        item.get("fingerprint")
        for item in (prior_submission.get("comments") or [])
        if item.get("fingerprint")
    )


def new_comments_since_previous(
    *,
    current_comments: list[dict],
    prior_submission: dict | None,
) -> list[dict]:
    prior_counts = submission_comment_counter(prior_submission)
    seen = Counter()
    result = []

    for item in current_comments:
        fp = item.get("fingerprint")
        if not fp:
            continue
        seen[fp] += 1
        if seen[fp] > prior_counts.get(fp, 0):
            result.append(item)
    return result


def submission_privacy_evidence(
    *,
    result: dict,
    output_root: Path,
    form_labels: dict[str, str],
    comments: list[dict],
    prior_submission: dict | None,
    post_run_state: dict | None = None,
) -> dict:
    submission_id = result["logical_submission_id"]
    versions = result.get("versions") or []

    prior_known_versions = set(
        (prior_submission or {}).get("known_version_ids") or []
    )
    pre_run_version_ids = [
        item.get("version_id")
        for item in versions
        if item.get("version_id")
    ]
    post_run_version_ids = (
        (post_run_state or {}).get("version_ids") or []
    )
    current_known_versions = (
        post_run_version_ids
        if post_run_version_ids
        else pre_run_version_ids
    )

    original_recipe = None
    if versions:
        original_recipe = (
            (versions[0].get("methodmesh") or {})
            .get("commitment_recipe")
        )

    edit_events = []
    for index in range(1, len(versions)):
        previous = versions[index - 1]
        current = versions[index]
        current_id = current.get("version_id")

        # Desktop receipt writebacks are review/checkpoint events, not human
        # research-data edits.
        if current.get("classification") == "VERIFIED_BY_DESKTOP_RECEIPT":
            continue

        previous_xml = (
            output_root
            / safe_name(submission_id)
            / f"{index:03d}_{safe_name(previous.get('version_id'))}"
            / "submission.xml"
        )
        current_xml = (
            output_root
            / safe_name(submission_id)
            / f"{index + 1:03d}_{safe_name(current_id)}"
            / "submission.xml"
        )

        changed_fields = changed_committed_fields_for_versions(
            previous_xml=previous_xml,
            current_xml=current_xml,
            recipe=original_recipe,
            labels=form_labels,
            previous_attachments=previous.get("attachments"),
            current_attachments=current.get("attachments"),
        )

        edit_events.append({
            "logical_submission_id": submission_id,
            "previous_version_id": previous.get("version_id"),
            "version_id": current_id,
            "created_at": current.get("created_at"),
            "actor": current.get("actor"),
            "classification": current.get("classification"),
            "changed_fields": changed_fields,
            "new_since_previous_daily_checkpoint": (
                current_id not in prior_known_versions
            ),
            "research_values_included": False,
        })

    new_comments = new_comments_since_previous(
        current_comments=comments,
        prior_submission=prior_submission,
    )
    new_edits = [
        event for event in edit_events
        if event.get("new_since_previous_daily_checkpoint")
    ]

    # Central does not provide a comment ID/timestamp/version FK. Only create
    # an edit association when the checkpoint-to-checkpoint evidence is
    # unambiguous.
    if len(new_comments) == 1 and len(new_edits) == 1:
        new_edits[0]["reason"] = {
            **new_comments[0],
            "association_status": "ASSOCIATED",
            "association_basis": (
                "ONE_NEW_COMMENT_AND_ONE_NEW_HUMAN_EDIT_BETWEEN_DAILY_CHECKPOINTS"
            ),
            "central_native_version_link": False,
        }
    else:
        for event in new_edits:
            event["reason"] = {
                "association_status": "UNRESOLVED",
                "association_basis": (
                    "CENTRAL_COMMENTS_HAVE_NO_NATIVE_VERSION_ID_OR_TIMESTAMP"
                ),
                "central_native_version_link": False,
            }

    current_version = versions[-1] if versions else None

    writeback_action = result.get("writeback_action") or {}
    receipt_action = writeback_action.get("receipt_action")
    planned_receipt = (
        writeback_action.get("planned_receipt")
        or writeback_action.get("planned_receipt_body")
        or {}
    )

    desktop_writeback_event = None
    if receipt_action in {
        "TIMESTAMPED_SEQUENCE_RECEIPT_WRITTEN",
        "SEQUENCE_RECEIPT_WRITTEN",
    }:
        desktop_writeback_event = {
            "action": receipt_action,
            "source_version_id": (
                (writeback_action.get("sequence_verification") or {})
                .get("versions", [{}])[-1]
                .get("version_id")
                if (writeback_action.get("sequence_verification") or {})
                .get("versions")
                else result.get("current_version_id")
            ),
            "writeback_version_id": (
                planned_receipt.get("writeback_version_id")
                or writeback_action.get("planned_writeback_version_id")
            ),
            "receipt_sha256": planned_receipt.get("receipt_sha256"),
            "receipt_schema": planned_receipt.get("schema"),
            "receipt_tsa_status": (
                (writeback_action.get("desktop_receipt_timestamp") or {})
                .get("verification_status")
            ),
            "receipt_tsa_time": (
                (writeback_action.get("desktop_receipt_timestamp") or {})
                .get("time_iso")
            ),
            "review_state_action":
                writeback_action.get("review_state_action"),
            "pre_tsa_server_check":
                writeback_action.get("pre_tsa_server_check"),
            "post_tsa_server_check":
                writeback_action.get("post_tsa_server_check"),
            "research_values_included": False,
        }

    return {
        "logical_submission_id": submission_id,
        "overall_status": result.get("overall_status"),
        "current_version_id_pre_run": result.get("current_version_id"),
        "current_version_id_post_run": (
            (post_run_state or {}).get("current_version_id")
            or result.get("current_version_id")
        ),
        "current_version_classification_pre_run":
            result.get("current_version_classification"),
        "version_count_pre_run": result.get("version_count"),
        "version_count_post_run": (
            (post_run_state or {}).get("version_count")
            if post_run_state
            else result.get("version_count")
        ),
        "known_version_ids": current_known_versions,
        "post_run_review_state":
            (post_run_state or {}).get("review_state"),
        "desktop_writeback_event": desktop_writeback_event,
        "original_attestation_verified": (
            result.get(
                "original_attestation_valid_for_original_version"
            )
            is True
        ),
        "current_review_action": (
            (result.get("writeback_action") or {})
            .get("review_state_action")
        ),
        "edit_events": edit_events,
        "comments": comments,
        "new_comment_count": len(new_comments),
        "new_edit_count": len(new_edits),
        "current_payload_commitment_sha256": (
            (current_version.get("reconstruction") or {})
            .get("reconstructed_payload_sha256")
            if current_version
            else None
        ),
        "research_values_included": False,
    }


def evidence_leaf(
    *,
    leaf_type: str,
    leaf_id: str,
    payload,
) -> dict:
    payload_hash = sha256_bytes(
        compact_json(payload).encode("utf-8")
    )
    leaf_hash = sha256_bytes(
        (
            "methodmesh-leaf-v1\n"
            + leaf_type
            + "\n"
            + leaf_id
            + "\n"
            + payload_hash
        ).encode("utf-8")
    )
    return {
        "type": leaf_type,
        "id": leaf_id,
        "payload_sha256": payload_hash,
        "leaf_sha256": leaf_hash,
    }


def merkle_root_hex(leaves: list[dict]) -> str:
    if not leaves:
        return sha256_bytes(b"methodmesh-empty-merkle-v1")

    level = [
        bytes.fromhex(item["leaf_sha256"])
        for item in sorted(
            leaves,
            key=lambda x: (
                x["type"],
                x["id"],
                x["leaf_sha256"],
            ),
        )
    ]

    while len(level) > 1:
        next_level = []
        for i in range(0, len(level), 2):
            left = level[i]
            right = level[i + 1] if i + 1 < len(level) else left
            next_level.append(
                hashlib.sha256(
                    b"methodmesh-node-v1\n" + left + right
                ).digest()
            )
        level = next_level

    return level[0].hex()


def one_page_auditor_summary(
    evidence: dict,
    checkpoint: dict | None,
) -> str:
    submissions = evidence.get("submissions") or []
    edit_events = [
        e
        for submission in submissions
        for e in (submission.get("edit_events") or [])
    ]

    unresolved_reasons = sum(
        1
        for event in edit_events
        if (event.get("reason") or {}).get("association_status")
        == "UNRESOLVED"
        and event.get("new_since_previous_daily_checkpoint")
    )
    associated_reasons = sum(
        1
        for event in edit_events
        if (event.get("reason") or {}).get("association_status")
        == "ASSOCIATED"
    )
    auditable_submissions = [
        item for item in submissions
        if (item.get("version_count_post_run") or 0) > 0
    ]
    zero_version_submissions = len(submissions) - len(auditable_submissions)

    verified_originals = sum(
        1
        for item in auditable_submissions
        if item.get("original_attestation_verified")
    )
    unresolved_integrity = sum(
        1
        for item in submissions
        if "UNRESOLVED" in str(item.get("overall_status"))
        or "FAIL" in str(item.get("overall_status"))
    )

    service = evidence.get("service_summary") or {}
    central = evidence.get("central_system") or {}
    endpoints = central.get("endpoints") or {}
    available_endpoints = sum(
        1 for item in endpoints.values()
        if isinstance(item, dict) and item.get("available")
    )

    lines = [
        "METHODMESH DAILY TRIAL EVIDENCE",
        "=" * 72,
        f"Central:                 {evidence.get('central_url')}",
        f"Project / Form:          {evidence.get('project_id')} / {evidence.get('form_id')}",
        f"Evidence window:         {evidence.get('window_start_utc')} -> {evidence.get('window_end_utc')}",
        "",
        "PROVENANCE",
        f"Logical submissions:     {len(submissions)}",
        f"Retained-version records:{len(auditable_submissions):>6}",
        f"Zero-version anomalies:  {zero_version_submissions:>6}",
        f"Original crypto verified:{verified_originals:>6} / {len(auditable_submissions)}",
        f"Human edit events:       {len(edit_events):>6}",
        f"Reasons associated:      {associated_reasons:>6}",
        f"New reasons unresolved:  {unresolved_reasons:>6}",
        f"Integrity unresolved:    {unresolved_integrity:>6}",
        "Research values copied into this report: NO",
        "",
        "CENTRAL / SYSTEM",
        f"Metadata endpoints read: {available_endpoints:>6} / {len(endpoints)}",
        f"Server audit events:     {len(((endpoints.get('server_audits') or {}).get('data') or [])):>6}",
        "",
        "SERVICE OBSERVATIONS",
        f"Probe observations:      {service.get('observation_count', 0):>6}",
        f"HTTP successes:          {service.get('http_success_count', 0):>6}",
        f"API successes:           {service.get('api_success_count', 0):>6}",
        f"Unexpected open ports:   {service.get('unexpected_open_ports_seen') or 'none'}",
        "",
        "DAILY CRYPTOGRAPHIC CHECKPOINT",
        f"Evidence Merkle root:    {evidence.get('evidence_merkle_root')}",
    ]

    if checkpoint:
        lines.extend([
            f"Checkpoint SHA-256:      {checkpoint.get('daily_checkpoint_sha256')}",
            f"Previous checkpoint:     {checkpoint.get('previous_daily_checkpoint_sha256') or 'GENESIS'}",
            f"RFC3161 status:          {checkpoint.get('trusted_timestamp_status') or checkpoint.get('timestamp_status')}",
            f"RFC3161 time:            {checkpoint.get('trusted_timestamp_time_iso') or '-'}",
        ])
    else:
        lines.extend([
            "Checkpoint SHA-256:      not requested",
            "RFC3161 status:          not requested",
        ])

    lines.extend([
        "",
        "AUDIT INTERPRETATION",
        (
            "PASS - privacy-safe evidence snapshot completed."
            if unresolved_integrity == 0
            else "ATTENTION - unresolved integrity evidence exists."
        ),
        "",
        "NOTE",
        "Changed-field evidence contains field names/labels only. Old and new",
        "research values are deliberately excluded. Raw XML/attachments remain",
        "inside the restricted verification archive for issue-specific review.",
    ])
    return "\n".join(lines) + "\n"


def create_daily_evidence_snapshot(
    *,
    central: Central,
    project_id: int,
    form_id: str,
    output_root: Path,
    results: list[dict],
    current_probe: dict,
    tsa_ca_file: str | None,
    tsa_cert_file: str | None,
    allow_tsa_download: bool,
    tsa_url: str,
    create_timestamp: bool,
) -> dict:
    now = datetime.now(timezone.utc)
    previous = load_previous_daily_evidence(output_root)

    if previous:
        previous_end = parse_iso_strict(previous.get("window_end_utc"))
    else:
        previous_end = None

    window_start = previous_end or (now - timedelta(hours=24))
    window_end = now

    # Find a label map from the currently archived form version.
    labels = {}
    form_versions_dir = output_root / "_form_versions"
    if form_versions_dir.exists():
        candidates = sorted(form_versions_dir.glob("*/form.xml"))
        if candidates:
            labels = best_effort_xform_labels(candidates[-1])

    prior_submission_map = {
        item.get("logical_submission_id"): item
        for item in ((previous or {}).get("submissions") or [])
        if item.get("logical_submission_id")
    }

    submission_evidence = []
    for result in results:
        submission_id = result.get("logical_submission_id")
        if not submission_id:
            continue

        comments = fetch_submission_comments(
            central=central,
            project_id=project_id,
            form_id=form_id,
            submission_id=submission_id,
        )
        post_run_state = fetch_post_run_submission_state(
            central=central,
            project_id=project_id,
            form_id=form_id,
            submission_id=submission_id,
        )

        submission_evidence.append(
            submission_privacy_evidence(
                result=result,
                output_root=output_root,
                form_labels=labels,
                comments=comments,
                prior_submission=prior_submission_map.get(submission_id),
                post_run_state=post_run_state,
            )
        )

    central_system = collect_central_system_metadata(
        central=central,
        project_id=project_id,
        form_id=form_id,
        start=window_start,
        end=window_end,
    )

    probes = load_service_probes(
        output_root=output_root,
        start=window_start,
        end=window_end,
    )
    if current_probe and not any(
        p.get("observed_at_utc") == current_probe.get("observed_at_utc")
        for p in probes
    ):
        probes.append(current_probe)

    probe_summary = service_probe_summary(probes)

    evidence = {
        "schema": DAILY_EVIDENCE_SCHEMA,
        "generated_at_utc": now.isoformat(),
        "window_start_utc": window_start.isoformat(),
        "window_end_utc": window_end.isoformat(),
        "central_url": central.base_url,
        "project_id": project_id,
        "form_id": form_id,
        "privacy_policy": {
            "research_values_included": False,
            "central_diffs_endpoint_queried": False,
            "changed_fields_are_names_only": True,
            "raw_xml_location": "restricted verifier archive only",
            "comment_body_included_as_reason_evidence": True,
        },
        "submissions": submission_evidence,
        "central_system": central_system,
        "service_summary": probe_summary,
        "service_probe_hashes": [
            sha256_bytes(compact_json(p).encode("utf-8"))
            for p in probes
        ],
    }

    leaves = []

    for submission in submission_evidence:
        sid = submission["logical_submission_id"]
        leaves.append(
            evidence_leaf(
                leaf_type="submission",
                leaf_id=sid,
                payload=submission,
            )
        )

    for name, endpoint in sorted(
        (central_system.get("endpoints") or {}).items()
    ):
        leaves.append(
            evidence_leaf(
                leaf_type="central_endpoint",
                leaf_id=name,
                payload=endpoint,
            )
        )

    for index, probe in enumerate(probes):
        leaves.append(
            evidence_leaf(
                leaf_type="service_probe",
                leaf_id=(
                    probe.get("observed_at_utc")
                    or f"probe-{index:06d}"
                ),
                payload=probe,
            )
        )

    evidence["merkle_leaves"] = leaves
    evidence["evidence_merkle_root"] = merkle_root_hex(leaves)

    previous_checkpoint_sha = (
        (previous or {})
        .get("daily_checkpoint", {})
        .get("daily_checkpoint_sha256")
    )
    previous_merkle_root = (previous or {}).get("evidence_merkle_root")

    checkpoint_body = {
        "schema": DAILY_CHECKPOINT_SCHEMA,
        "generated_at_utc": now.isoformat(),
        "central_url": central.base_url,
        "project_id": project_id,
        "form_id": form_id,
        "window_start_utc": window_start.isoformat(),
        "window_end_utc": window_end.isoformat(),
        "evidence_merkle_root": evidence["evidence_merkle_root"],
        "leaf_count": len(leaves),
        "previous_daily_checkpoint_sha256": previous_checkpoint_sha,
        "previous_evidence_merkle_root": previous_merkle_root,
        "research_values_included": False,
    }
    checkpoint_sha = receipt_self_hash(checkpoint_body)

    checkpoint = {
        **checkpoint_body,
        "daily_checkpoint_sha256": checkpoint_sha,
        "timestamp_status": "NOT_REQUESTED",
    }

    timestamp_result = None
    if create_timestamp:
        trust = resolve_tsa_trust_material(
            attestation={"trusted_timestamp_authority": tsa_url},
            output_root=output_root,
            tsa_ca_file=tsa_ca_file,
            tsa_cert_file=tsa_cert_file,
            allow_tsa_download=allow_tsa_download,
        )

        stamp_dir_name = now.strftime("%Y%m%dT%H%M%SZ")
        timestamp_result = create_rfc3161_timestamp_for_hash(
            digest_hex=checkpoint_sha,
            tsa_url=tsa_url,
            trust_material=trust,
            evidence_dir=(
                output_root
                / "_daily_evidence"
                / stamp_dir_name
                / "tsa"
            ),
        )

        if timestamp_result.get("overall_valid") is True:
            checkpoint.update({
                "timestamp_status": "VERIFIED",
                "trusted_timestamp_status": "rfc3161_verified",
                "trusted_timestamp_authority":
                    timestamp_result.get("authority"),
                "trusted_timestamp_attested_hash": checkpoint_sha,
                "trusted_timestamp_time_iso":
                    timestamp_result.get("time_iso"),
                "trusted_timestamp_serial":
                    timestamp_result.get("serial_hex"),
                "trusted_timestamp_request_base64":
                    timestamp_result.get("request_base64"),
                "trusted_timestamp_request_sha256":
                    timestamp_result.get("request_sha256"),
                "trusted_timestamp_response_base64":
                    timestamp_result.get("response_base64"),
                "trusted_timestamp_response_sha256":
                    timestamp_result.get("response_sha256"),
                "trusted_timestamp_token_base64":
                    timestamp_result.get("token_base64"),
                "trusted_timestamp_token_sha256":
                    timestamp_result.get("token_sha256"),
                "trusted_timestamp_trust_source":
                    timestamp_result.get("trust_source"),
            })
        else:
            checkpoint["timestamp_status"] = (
                timestamp_result.get("verification_status")
                or "FAILED"
            )
            checkpoint["timestamp_errors"] = (
                timestamp_result.get("errors") or []
            )

    evidence["daily_checkpoint"] = checkpoint

    stamp_dir_name = now.strftime("%Y%m%dT%H%M%SZ")
    daily_dir = output_root / "_daily_evidence" / stamp_dir_name
    daily_dir.mkdir(parents=True, exist_ok=True)

    (daily_dir / "daily_evidence.json").write_text(
        pretty_json(evidence),
        encoding="utf-8",
    )
    (daily_dir / "merkle_leaves.json").write_text(
        pretty_json(leaves),
        encoding="utf-8",
    )
    (daily_dir / "daily_checkpoint.json").write_text(
        pretty_json(checkpoint),
        encoding="utf-8",
    )
    (daily_dir / "AUDITOR_SUMMARY.txt").write_text(
        one_page_auditor_summary(evidence, checkpoint),
        encoding="utf-8",
    )

    return {
        "directory": str(daily_dir),
        "evidence_merkle_root": evidence["evidence_merkle_root"],
        "daily_checkpoint_sha256": checkpoint_sha,
        "timestamp_status": checkpoint.get("timestamp_status"),
        "timestamp_time_iso":
            checkpoint.get("trusted_timestamp_time_iso"),
        "leaf_count": len(leaves),
        "service_observation_count": probe_summary.get(
            "observation_count"
        ),
        "_service_probes_for_safe_export": probes,
        "_daily_evidence_object_for_safe_export": evidence,
    }



# ---------------------------------------------------------------------
# Auditor-safe export boundary
# ---------------------------------------------------------------------

AUDITOR_EXPORT_SCHEMA = "methodmesh.auditor_export.v1"

AUDITOR_FORBIDDEN_SUFFIXES = {
    ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".tif", ".tiff",
    ".dcm", ".xml", ".csv", ".xlsx", ".xls", ".ods",
}

AUDITOR_FORBIDDEN_BASENAMES = {
    "source_record_fields.json",
    "submission_metadata.json",
    "versions_raw.json",
    "version_summary.json",
    "probe_summary.json",
    "audits.json",
    "submissions.json",
    "summary.json",
    "submission.xml",
    "form.xml",
}

AUDITOR_FORBIDDEN_JSON_KEYS = {
    "old",
    "new",
    "value",
    "values",
    "xml",
    "submission_xml",
    "source_record_fields",
    "showcase_payload_canonical",
    "attestation_methodmesh_full_json",
    "methodmesh_full_json",
    "attachment_bytes",
    "file_bytes",
    "binary",
    "base64_image",
}


def auditor_safe_submission(item: dict) -> dict:
    edit_events = []
    for event in item.get("edit_events") or []:
        changed_fields = [
            {
                "field_name": field.get("field_name"),
                "display_name": field.get("display_name"),
                "change_type": field.get("change_type"),
            }
            for field in (event.get("changed_fields") or [])
        ]

        reason = event.get("reason")
        if isinstance(reason, dict):
            safe_reason = {
                "actorId": reason.get("actorId"),
                "actor": reason.get("actor"),
                "body": reason.get("body"),
                "body_sha256": reason.get("body_sha256"),
                "fingerprint": reason.get("fingerprint"),
                "association_status":
                    reason.get("association_status"),
                "association_basis":
                    reason.get("association_basis"),
                "central_native_version_link":
                    reason.get("central_native_version_link"),
            }
        else:
            safe_reason = None

        edit_events.append({
            "logical_submission_id":
                event.get("logical_submission_id"),
            "previous_version_id":
                event.get("previous_version_id"),
            "version_id": event.get("version_id"),
            "created_at": event.get("created_at"),
            "actor": event.get("actor"),
            "classification": event.get("classification"),
            "changed_fields": changed_fields,
            "reason": safe_reason,
            "new_since_previous_daily_checkpoint":
                event.get("new_since_previous_daily_checkpoint"),
            "research_values_included": False,
        })

    comments = []
    for comment in item.get("comments") or []:
        comments.append({
            "actorId": comment.get("actorId"),
            "actor": comment.get("actor"),
            "body": comment.get("body"),
            "body_sha256": comment.get("body_sha256"),
            "fingerprint": comment.get("fingerprint"),
        })

    writeback = item.get("desktop_writeback_event")
    safe_writeback = None
    if isinstance(writeback, dict):
        safe_writeback = {
            key: writeback.get(key)
            for key in [
                "action",
                "source_version_id",
                "writeback_version_id",
                "receipt_sha256",
                "receipt_schema",
                "receipt_tsa_status",
                "receipt_tsa_time",
                "review_state_action",
                "pre_tsa_server_check",
                "post_tsa_server_check",
                "research_values_included",
            ]
        }

    return {
        "logical_submission_id": item.get("logical_submission_id"),
        "overall_status": item.get("overall_status"),
        "current_version_id_pre_run":
            item.get("current_version_id_pre_run"),
        "current_version_id_post_run":
            item.get("current_version_id_post_run"),
        "current_version_classification_pre_run":
            item.get("current_version_classification_pre_run"),
        "version_count_pre_run": item.get("version_count_pre_run"),
        "version_count_post_run": item.get("version_count_post_run"),
        "known_version_ids": item.get("known_version_ids") or [],
        "post_run_review_state": item.get("post_run_review_state"),
        "original_attestation_verified":
            item.get("original_attestation_verified"),
        "current_review_action": item.get("current_review_action"),
        "edit_events": edit_events,
        "comments": comments,
        "new_comment_count": item.get("new_comment_count"),
        "new_edit_count": item.get("new_edit_count"),
        "desktop_writeback_event": safe_writeback,
        "current_payload_commitment_sha256":
            item.get("current_payload_commitment_sha256"),
        "research_values_included": False,
    }


def build_auditor_safe_evidence(
    *,
    evidence: dict,
    service_probes: list[dict],
) -> dict:
    """
    Build a shareable representation while preserving EXACT Merkle leaf
    payloads from the daily evidence checkpoint.

    v11 already constructs submission evidence without research values and
    sanitizes Central metadata before hashing it. We therefore preserve those
    exact sanitized objects here, rather than transforming them again and
    breaking the timestamped Merkle proof.
    """
    central_system = evidence.get("central_system") or {}

    return {
        "schema": AUDITOR_EXPORT_SCHEMA,
        "source_daily_evidence_schema": evidence.get("schema"),
        "generated_at_utc": evidence.get("generated_at_utc"),
        "window_start_utc": evidence.get("window_start_utc"),
        "window_end_utc": evidence.get("window_end_utc"),
        "central_url": evidence.get("central_url"),
        "project_id": evidence.get("project_id"),
        "form_id": evidence.get("form_id"),
        "privacy_policy": {
            "research_values_included": False,
            "raw_submission_xml_included": False,
            "attachments_included": False,
            "images_included": False,
            "central_diffs_endpoint_queried": False,
            "changed_fields_are_names_only": True,
            "raw_methodmesh_full_json_included": False,
            "comment_body_included_as_reason_evidence": True,
            "comment_body_is_operator_entered_free_text": True,
        },
        # These are the exact privacy-safe payloads used as Merkle leaves.
        "submissions": evidence.get("submissions") or [],
        "central_system": {
            "window_start_utc":
                central_system.get("window_start_utc"),
            "window_end_utc":
                central_system.get("window_end_utc"),
            "endpoints":
                central_system.get("endpoints") or {},
        },
        "service_summary": evidence.get("service_summary") or {},
        # Exact service probe payloads used as Merkle leaves.
        "service_probes": service_probes,
        "evidence_merkle_root": evidence.get("evidence_merkle_root"),
        "daily_checkpoint": evidence.get("daily_checkpoint"),
    }


def json_forbidden_key_paths(obj, *, path: str = "$") -> list[str]:
    findings = []
    if isinstance(obj, dict):
        for key, value in obj.items():
            key_text = str(key)
            child = f"{path}.{key_text}"
            if key_text.lower() in AUDITOR_FORBIDDEN_JSON_KEYS:
                findings.append(child)
            findings.extend(
                json_forbidden_key_paths(value, path=child)
            )
    elif isinstance(obj, list):
        for index, value in enumerate(obj):
            findings.extend(
                json_forbidden_key_paths(
                    value,
                    path=f"{path}[{index}]",
                )
            )
    return findings


def safety_scan_auditor_directory(bundle_dir: Path) -> dict:
    files = sorted(
        p for p in bundle_dir.rglob("*")
        if p.is_file()
    )

    allowed_relative = {
        "AUDITOR_REPORT.txt",
        "AUDIT_EVIDENCE.json",
        "CHECKPOINT.json",
        "MERKLE_LEAVES.json",
        "SERVICE_PROBES.json",
        "CHECKSUMS.sha256",
        "README.txt",
        "verify_bundle.py",
        "tsa/daily_checkpoint.tsq",
        "tsa/daily_checkpoint.tsr",
        "tsa/daily_checkpoint_token.der",
        "trust/cacert.pem",
        "trust/tsa.crt",
    }

    findings = []

    for path in files:
        rel = path.relative_to(bundle_dir).as_posix()
        suffix = path.suffix.lower()

        if rel not in allowed_relative:
            findings.append(
                f"UNEXPECTED_FILE_NOT_ALLOWLISTED:{rel}"
            )

        if suffix in AUDITOR_FORBIDDEN_SUFFIXES:
            findings.append(f"FORBIDDEN_FILE_TYPE:{rel}")

        if path.name.lower() in AUDITOR_FORBIDDEN_BASENAMES:
            findings.append(f"FORBIDDEN_RAW_FILENAME:{rel}")

        if suffix == ".json":
            try:
                obj = json.loads(path.read_text(encoding="utf-8"))
            except Exception as exc:
                findings.append(f"INVALID_JSON:{rel}:{exc}")
                continue

            for key_path in json_forbidden_key_paths(obj):
                findings.append(
                    f"FORBIDDEN_JSON_KEY:{rel}:{key_path}"
                )

    return {
        "passed": len(findings) == 0,
        "file_count": len(files),
        "findings": findings,
        "checked_at_utc": utc_now_iso(),
    }


def write_checksum_manifest(bundle_dir: Path) -> Path:
    manifest_path = bundle_dir / "CHECKSUMS.sha256"
    lines = []

    for path in sorted(
        p for p in bundle_dir.rglob("*")
        if p.is_file() and p != manifest_path
    ):
        rel = path.relative_to(bundle_dir).as_posix()
        digest = sha256_bytes(path.read_bytes())
        lines.append(f"{digest}  {rel}")

    manifest_path.write_text(
        "\n".join(lines) + "\n",
        encoding="utf-8",
    )
    return manifest_path


AUDITOR_VERIFY_SCRIPT = r'''#!/usr/bin/env python3
import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def compact_json(obj):
    return json.dumps(
        obj,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )


def receipt_self_hash(obj):
    return sha256_bytes(compact_json(obj).encode("utf-8"))


def evidence_leaf(leaf_type, leaf_id, payload):
    payload_hash = sha256_bytes(
        compact_json(payload).encode("utf-8")
    )
    leaf_hash = sha256_bytes(
        (
            "methodmesh-leaf-v1\n"
            + leaf_type
            + "\n"
            + leaf_id
            + "\n"
            + payload_hash
        ).encode("utf-8")
    )
    return {
        "type": leaf_type,
        "id": leaf_id,
        "payload_sha256": payload_hash,
        "leaf_sha256": leaf_hash,
    }


def merkle_root_hex(leaves):
    if not leaves:
        return sha256_bytes(b"methodmesh-empty-merkle-v1")

    level = [
        bytes.fromhex(item["leaf_sha256"])
        for item in sorted(
            leaves,
            key=lambda x: (
                x["type"],
                x["id"],
                x["leaf_sha256"],
            ),
        )
    ]

    while len(level) > 1:
        next_level = []
        for i in range(0, len(level), 2):
            left = level[i]
            right = level[i + 1] if i + 1 < len(level) else left
            next_level.append(
                hashlib.sha256(
                    b"methodmesh-node-v1\n" + left + right
                ).digest()
            )
        level = next_level

    return level[0].hex()


def verify_checksums():
    problems = []
    for line in (ROOT / "CHECKSUMS.sha256").read_text(
        encoding="utf-8"
    ).splitlines():
        if not line.strip():
            continue
        digest, rel = line.split("  ", 1)
        path = ROOT / rel
        if not path.exists():
            problems.append(f"missing {rel}")
            continue
        actual = sha256_bytes(path.read_bytes())
        if actual != digest:
            problems.append(f"checksum mismatch {rel}")
    return problems


def build_expected_leaves(evidence):
    leaves = []

    for submission in evidence.get("submissions") or []:
        leaves.append(
            evidence_leaf(
                "submission",
                (
                    f"{submission.get('form_id')}::"
                    f"{submission['logical_submission_id']}"
                    if submission.get("form_id")
                    else submission["logical_submission_id"]
                ),
                submission,
            )
        )

    endpoints = (
        (evidence.get("central_system") or {})
        .get("endpoints") or {}
    )
    for name, endpoint in sorted(endpoints.items()):
        leaves.append(
            evidence_leaf("central_endpoint", name, endpoint)
        )

    for index, probe in enumerate(evidence.get("service_probes") or []):
        leaves.append(
            evidence_leaf(
                "service_probe",
                probe.get("observed_at_utc")
                or f"probe-{index:06d}",
                probe,
            )
        )

    return leaves


def checkpoint_body(checkpoint):
    keys = [
        "schema",
        "generated_at_utc",
        "central_url",
        "project_id",
        "form_id",
        "window_start_utc",
        "window_end_utc",
        "evidence_merkle_root",
        "leaf_count",
        "previous_daily_checkpoint_sha256",
        "previous_evidence_merkle_root",
        "research_values_included",
    ]
    return {key: checkpoint.get(key) for key in keys}


def openssl_verify(checkpoint):
    digest = checkpoint["daily_checkpoint_sha256"]
    ca = ROOT / "trust" / "cacert.pem"
    tsa = ROOT / "trust" / "tsa.crt"
    tsr = ROOT / "tsa" / "daily_checkpoint.tsr"
    tsq = ROOT / "tsa" / "daily_checkpoint.tsq"

    commands = [
        [
            "openssl", "ts", "-verify",
            "-in", str(tsr),
            "-queryfile", str(tsq),
            "-CAfile", str(ca),
            "-untrusted", str(tsa),
        ],
        [
            "openssl", "ts", "-verify",
            "-in", str(tsr),
            "-digest", digest,
            "-CAfile", str(ca),
            "-untrusted", str(tsa),
        ],
    ]

    for command in commands:
        try:
            completed = subprocess.run(
                command,
                capture_output=True,
                text=True,
                timeout=30,
            )
        except FileNotFoundError:
            return False, "OpenSSL not installed"
        except subprocess.TimeoutExpired:
            return False, "OpenSSL verification timed out"

        combined = (
            (completed.stdout or "")
            + (completed.stderr or "")
        )
        if (
            completed.returncode != 0
            or "Verification: OK" not in combined
        ):
            return False, combined.strip()

    return True, "Verification: OK"


def main():
    evidence = json.loads(
        (ROOT / "AUDIT_EVIDENCE.json").read_text(encoding="utf-8")
    )
    checkpoint = json.loads(
        (ROOT / "CHECKPOINT.json").read_text(encoding="utf-8")
    )
    stored_leaves = json.loads(
        (ROOT / "MERKLE_LEAVES.json").read_text(encoding="utf-8")
    )

    problems = verify_checksums()

    expected_leaves = build_expected_leaves(evidence)
    if expected_leaves != stored_leaves:
        problems.append(
            "Merkle leaf set does not match AUDIT_EVIDENCE.json"
        )

    root = merkle_root_hex(expected_leaves)
    if root != evidence.get("evidence_merkle_root"):
        problems.append(
            "recalculated Merkle root differs from evidence"
        )

    if root != checkpoint.get("evidence_merkle_root"):
        problems.append(
            "checkpoint Merkle root differs from evidence"
        )

    body_hash = receipt_self_hash(checkpoint_body(checkpoint))
    if body_hash != checkpoint.get("daily_checkpoint_sha256"):
        problems.append(
            "daily checkpoint SHA-256 does not verify"
        )

    if checkpoint.get("research_values_included") is not False:
        problems.append(
            "checkpoint does not declare research_values_included=false"
        )

    tsa_ok, tsa_message = openssl_verify(checkpoint)
    if not tsa_ok:
        problems.append(
            f"RFC3161 verification failed: {tsa_message}"
        )

    print("METHODMESH AUDITOR BUNDLE VERIFICATION")
    print("=" * 44)

    if problems:
        print("RESULT: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print("RESULT: PASS")
    print(f"Merkle root:     {root}")
    print(
        "Checkpoint SHA:  "
        f"{checkpoint.get('daily_checkpoint_sha256')}"
    )
    print(
        "RFC3161 time:    "
        f"{checkpoint.get('trusted_timestamp_time_iso')}"
    )
    print(
        "Research values: NOT INCLUDED IN AUDITOR BUNDLE"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
'''


def create_auditor_safe_export(
    *,
    evidence: dict,
    service_probes: list[dict],
    daily_dir: Path,
    output_root: Path,
    export_root: Path,
) -> dict:
    stamp = daily_dir.name
    export_root.mkdir(parents=True, exist_ok=True)

    staging = export_root / f".staging_{stamp}_{uuid.uuid4().hex[:8]}"
    staging.mkdir(parents=True, exist_ok=False)

    try:
        safe_evidence = build_auditor_safe_evidence(
            evidence=evidence,
            service_probes=service_probes,
        )
        checkpoint = evidence.get("daily_checkpoint") or {}
        leaves = evidence.get("merkle_leaves") or []

        (staging / "AUDIT_EVIDENCE.json").write_text(
            pretty_json(safe_evidence),
            encoding="utf-8",
        )
        (staging / "CHECKPOINT.json").write_text(
            pretty_json(checkpoint),
            encoding="utf-8",
        )
        (staging / "MERKLE_LEAVES.json").write_text(
            pretty_json(leaves),
            encoding="utf-8",
        )
        (staging / "SERVICE_PROBES.json").write_text(
            pretty_json(service_probes),
            encoding="utf-8",
        )
        report_text = (
            delta_auditor_summary(
                evidence,
                checkpoint,
                evidence.get("run_stats") or {},
            )
            if evidence.get("schema") == STATE_SNAPSHOT_SCHEMA
            else one_page_auditor_summary(evidence, checkpoint)
        )
        (staging / "AUDITOR_REPORT.txt").write_text(
            report_text,
            encoding="utf-8",
        )

        readme = f'''METHODMESH AUDITOR-SAFE BUNDLE
================================

This ZIP is intentionally separate from the restricted verifier archive.

SAFE EXPORT POLICY
------------------
- No submission XML.
- No photographs or other submission attachments.
- No source_record_fields JSON.
- No old/new research values.
- No canonical research payload text.
- No MethodMesh full JSON copied from submissions.
- Changed research fields are represented by FIELD NAME / LABEL only.
- Operator-entered Central comment text is included intentionally as
  reason-for-change evidence.

The restricted archive remains at:
{output_root.resolve()}

Do not share the restricted archive unless an issue-specific inspection has
been authorised.

VERIFY
------
Unzip this package and run:

    python3 verify_bundle.py

The verifier checks file checksums, the evidence-derived Merkle tree, the daily
checkpoint hash, and the RFC3161 timestamp using bundled public TSA trust
material. It does not require ODK Central credentials.

Schema: {AUDITOR_EXPORT_SCHEMA}
Generated: {utc_now_iso()}
'''
        (staging / "README.txt").write_text(
            readme,
            encoding="utf-8",
        )

        verify_path = staging / "verify_bundle.py"
        verify_path.write_text(
            AUDITOR_VERIFY_SCRIPT,
            encoding="utf-8",
        )

        tsa_src = daily_dir / "tsa"
        tsa_dst = staging / "tsa"
        tsa_dst.mkdir()

        tsa_name_map = {
            "desktop_receipt.tsq": "daily_checkpoint.tsq",
            "desktop_receipt.tsr": "daily_checkpoint.tsr",
            "desktop_receipt_token.der":
                "daily_checkpoint_token.der",
        }

        for source_name, dest_name in tsa_name_map.items():
            source = tsa_src / source_name
            if not source.exists():
                raise RuntimeError(
                    f"Missing daily checkpoint TSA evidence: {source}"
                )
            (tsa_dst / dest_name).write_bytes(source.read_bytes())

        trust_src = output_root / "_tsa_trust" / "freetsa"
        trust_dst = staging / "trust"
        trust_dst.mkdir()

        for name in ["cacert.pem", "tsa.crt"]:
            source = trust_src / name
            if not source.exists():
                raise RuntimeError(
                    f"Missing pinned public TSA trust material: {source}"
                )
            (trust_dst / name).write_bytes(source.read_bytes())

        scan = safety_scan_auditor_directory(staging)
        if not scan["passed"]:
            raise RuntimeError(
                "AUDITOR EXPORT SAFETY SCAN FAILED: "
                + "; ".join(scan["findings"])
            )

        write_checksum_manifest(staging)

        scan = safety_scan_auditor_directory(staging)
        if not scan["passed"]:
            raise RuntimeError(
                "AUDITOR EXPORT SAFETY SCAN FAILED AFTER MANIFEST: "
                + "; ".join(scan["findings"])
            )

        verify_run = run_command([
            sys.executable,
            str(staging / "verify_bundle.py"),
        ])
        if verify_run.get("returncode") != 0:
            raise RuntimeError(
                "AUDITOR BUNDLE SELF-VERIFICATION FAILED: "
                + verify_run.get("stdout", "")
                + verify_run.get("stderr", "")
            )

        zip_path = (
            export_root
            / f"methodmesh_auditor_bundle_{stamp}.zip"
        )
        if zip_path.exists():
            zip_path.unlink()

        with zipfile.ZipFile(
            zip_path,
            mode="w",
            compression=zipfile.ZIP_DEFLATED,
        ) as archive:
            for path in sorted(
                p for p in staging.rglob("*")
                if p.is_file()
            ):
                archive.write(
                    path,
                    arcname=path.relative_to(staging).as_posix(),
                )

        with zipfile.ZipFile(zip_path) as archive:
            forbidden = []
            for member in archive.namelist():
                if member.endswith("/"):
                    continue
                member_path = Path(member)
                if (
                    member_path.suffix.lower()
                    in AUDITOR_FORBIDDEN_SUFFIXES
                ):
                    forbidden.append(member)
                if (
                    member_path.name.lower()
                    in AUDITOR_FORBIDDEN_BASENAMES
                ):
                    forbidden.append(member)

            if forbidden:
                zip_path.unlink(missing_ok=True)
                raise RuntimeError(
                    "AUDITOR ZIP CONTAINS FORBIDDEN MEMBERS: "
                    + ", ".join(sorted(set(forbidden)))
                )

        return {
            "schema": AUDITOR_EXPORT_SCHEMA,
            "zip_path": str(zip_path.resolve()),
            "zip_sha256": sha256_bytes(zip_path.read_bytes()),
            "safety_scan_passed": True,
            "file_count": scan["file_count"],
            "self_verifier_result":
                verify_run.get("stdout", "").strip(),
            "research_values_included": False,
            "attachments_included": False,
            "raw_xml_included": False,
        }

    finally:
        if staging.exists():
            for path in sorted(
                staging.rglob("*"),
                key=lambda p: len(p.parts),
                reverse=True,
            ):
                if path.is_file() or path.is_symlink():
                    path.unlink(missing_ok=True)
                elif path.is_dir():
                    try:
                        path.rmdir()
                    except OSError:
                        pass
            try:
                staging.rmdir()
            except OSError:
                pass



# ---------------------------------------------------------------------
# v13 persistent NON-DATA ledger + delta controller
# ---------------------------------------------------------------------

LEDGER_SCHEMA_VERSION = "methodmesh.delta_ledger.v1"
STATE_SNAPSHOT_SCHEMA = "methodmesh.delta_state_snapshot.v1"

SAFE_STATE_FORBIDDEN_SUFFIXES = {
    ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".tif", ".tiff",
    ".dcm", ".xml", ".csv", ".xlsx", ".xls", ".ods",
}

SAFE_STATE_FORBIDDEN_BASENAMES = {
    "submission.xml",
    "source_record_fields.json",
    "versions_raw.json",
    "submission_metadata.json",
    "version_summary.json",
    "probe_summary.json",
    "form.xml",
}


def sqlite_connect(path: Path) -> sqlite3.Connection:
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=FULL")
    conn.execute("PRAGMA foreign_keys=ON")
    try:
        path.chmod(0o600)
    except OSError:
        pass
    return conn


def init_delta_ledger(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS meta (
            key TEXT PRIMARY KEY,
            value TEXT
        );

        CREATE TABLE IF NOT EXISTS forms (
            project_id INTEGER NOT NULL,
            form_id TEXT NOT NULL,
            form_numeric_id INTEGER,
            name TEXT,
            version TEXT,
            state TEXT,
            deleted_at TEXT,
            last_seen_at TEXT NOT NULL,
            PRIMARY KEY (project_id, form_id)
        );

        CREATE TABLE IF NOT EXISTS form_instances (
            project_id INTEGER NOT NULL,
            form_numeric_id INTEGER NOT NULL,
            form_id TEXT NOT NULL,
            name TEXT,
            version TEXT,
            state TEXT,
            published_at TEXT,
            created_at TEXT,
            updated_at TEXT,
            deleted_at TEXT,
            last_seen_at TEXT NOT NULL,
            PRIMARY KEY (project_id, form_numeric_id)
        );

        CREATE TABLE IF NOT EXISTS submissions (
            project_id INTEGER NOT NULL,
            form_id TEXT NOT NULL,
            logical_id TEXT NOT NULL,
            current_version_id TEXT,
            current_payload_sha256 TEXT,
            original_attestation_hash TEXT,
            desktop_receipt_sha256 TEXT,
            overall_status TEXT,
            status TEXT NOT NULL,
            review_state TEXT,
            created_at TEXT,
            updated_at TEXT,
            deleted_at TEXT,
            last_verified_at TEXT,
            last_seen_at TEXT NOT NULL,
            version_count INTEGER,
            verification_note TEXT,
            PRIMARY KEY (project_id, form_id, logical_id)
        );

        CREATE TABLE IF NOT EXISTS versions (
            project_id INTEGER NOT NULL,
            form_id TEXT NOT NULL,
            logical_id TEXT NOT NULL,
            version_id TEXT NOT NULL,
            sequence INTEGER,
            created_at TEXT,
            form_version TEXT,
            classification TEXT,
            xml_sha256 TEXT,
            reconstructed_payload_sha256 TEXT,
            desktop_receipt_sha256 TEXT,
            desktop_receipt_schema TEXT,
            tsa_status TEXT,
            tsa_time TEXT,
            attachment_count INTEGER,
            verified_at TEXT NOT NULL,
            PRIMARY KEY (
                project_id, form_id, logical_id, version_id
            )
        );

        CREATE TABLE IF NOT EXISTS edits (
            project_id INTEGER NOT NULL,
            form_id TEXT NOT NULL,
            logical_id TEXT NOT NULL,
            version_id TEXT NOT NULL,
            previous_version_id TEXT,
            created_at TEXT,
            actor TEXT,
            changed_fields_json TEXT NOT NULL,
            reason_body TEXT,
            reason_body_sha256 TEXT,
            reason_association_status TEXT NOT NULL,
            reason_association_basis TEXT,
            first_seen_at TEXT NOT NULL,
            PRIMARY KEY (
                project_id, form_id, logical_id, version_id
            )
        );

        CREATE TABLE IF NOT EXISTS comments (
            project_id INTEGER NOT NULL,
            form_id TEXT NOT NULL,
            logical_id TEXT NOT NULL,
            fingerprint TEXT NOT NULL,
            actor_id TEXT,
            actor_json TEXT,
            body TEXT,
            body_sha256 TEXT,
            first_seen_at TEXT NOT NULL,
            PRIMARY KEY (
                project_id, form_id, logical_id, fingerprint
            )
        );

        CREATE TABLE IF NOT EXISTS events (
            event_fingerprint TEXT PRIMARY KEY,
            project_id INTEGER,
            logged_at TEXT,
            action TEXT,
            actor_id TEXT,
            actee_id TEXT,
            event_json TEXT NOT NULL,
            first_seen_at TEXT NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_submissions_status
            ON submissions(project_id, form_id, status);

        CREATE INDEX IF NOT EXISTS idx_edits_reason
            ON edits(project_id, form_id, reason_association_status);

        CREATE INDEX IF NOT EXISTS idx_events_time
            ON events(project_id, logged_at);
        """
    )
    set_ledger_meta(conn, "schema", LEDGER_SCHEMA_VERSION)
    conn.commit()


def get_ledger_meta(
    conn: sqlite3.Connection,
    key: str,
    default=None,
):
    row = conn.execute(
        "SELECT value FROM meta WHERE key = ?",
        (key,),
    ).fetchone()
    return row["value"] if row is not None else default


def set_ledger_meta(
    conn: sqlite3.Connection,
    key: str,
    value,
) -> None:
    conn.execute(
        """
        INSERT INTO meta(key, value)
        VALUES(?, ?)
        ON CONFLICT(key) DO UPDATE SET value = excluded.value
        """,
        (key, None if value is None else str(value)),
    )


def ledger_submission_row(
    conn: sqlite3.Connection,
    project_id: int,
    form_id: str,
    logical_id: str,
):
    return conn.execute(
        """
        SELECT *
        FROM submissions
        WHERE project_id = ?
          AND form_id = ?
          AND logical_id = ?
        """,
        (project_id, form_id, logical_id),
    ).fetchone()


def ledger_version_ids(
    conn: sqlite3.Connection,
    project_id: int,
    form_id: str,
    logical_id: str,
) -> set[str]:
    rows = conn.execute(
        """
        SELECT version_id
        FROM versions
        WHERE project_id = ?
          AND form_id = ?
          AND logical_id = ?
        """,
        (project_id, form_id, logical_id),
    ).fetchall()
    return {row["version_id"] for row in rows}


def ledger_comment_fingerprints(
    conn: sqlite3.Connection,
    project_id: int,
    form_id: str,
    logical_id: str,
) -> set[str]:
    rows = conn.execute(
        """
        SELECT fingerprint
        FROM comments
        WHERE project_id = ?
          AND form_id = ?
          AND logical_id = ?
        """,
        (project_id, form_id, logical_id),
    ).fetchall()
    return {row["fingerprint"] for row in rows}


def ledger_unresolved_edit_ids(
    conn: sqlite3.Connection,
    project_id: int,
    form_id: str,
) -> list[str]:
    rows = conn.execute(
        """
        SELECT DISTINCT logical_id
        FROM edits
        WHERE project_id = ?
          AND form_id = ?
          AND reason_association_status = 'UNRESOLVED'
        """,
        (project_id, form_id),
    ).fetchall()
    return [row["logical_id"] for row in rows]


def paged_submission_metadata(
    *,
    central: Central,
    project_id: int,
    form_id: str,
    deleted: bool = False,
    page_size: int = 1000,
) -> list[dict]:
    project = path_part(project_id)
    form = path_part(form_id)
    result = []
    offset = 0

    while True:
        deleted_q = "&deleted=true" if deleted else ""
        path = (
            f"/v1/projects/{project}/forms/{form}/submissions"
            f"?limit={page_size}&offset={offset}{deleted_q}"
        )
        page = central.get_json(path, extended=True)
        if not isinstance(page, list):
            raise RuntimeError(
                f"Unexpected submissions response for {form_id!r}"
            )
        result.extend(page)
        if len(page) < page_size:
            break
        offset += page_size

    return result


def project_forms_metadata(
    *,
    central: Central,
    project_id: int,
    target_form: str | None = None,
) -> tuple[list[dict], list[dict]]:
    project = path_part(project_id)
    active = central.get_json(
        f"/v1/projects/{project}/forms",
        extended=True,
    )
    deleted = central_optional_json(
        central,
        f"/v1/projects/{project}/forms?deleted=true",
        extended=True,
    )
    deleted_data = (
        deleted.get("data")
        if deleted.get("available")
        and isinstance(deleted.get("data"), list)
        else []
    )

    if target_form:
        active = [
            item
            for item in active
            if item.get("xmlFormId") == target_form
        ]

    return active, deleted_data


def insert_observation_event(
    conn: sqlite3.Connection,
    *,
    project_id: int,
    action: str,
    observed_at: str,
    actee_id: str | None,
    details: dict,
) -> bool:
    """
    Persist a MethodMesh-derived observation event when Central's server-wide
    audit endpoint is unavailable or when a snapshot comparison adds useful
    provenance.

    These events describe what MethodMesh OBSERVED, not necessarily the exact
    time or actor of the underlying Central action.
    """
    payload = {
        "source": "methodmesh.snapshot_observation",
        "loggedAt": observed_at,
        "action": action,
        "actorId": None,
        "acteeId": actee_id,
        "details": details,
    }
    fp = sha256_bytes(
        compact_json({
            "project_id": project_id,
            "action": action,
            "actee_id": actee_id,
            "details": details,
        }).encode("utf-8")
    )

    before = conn.total_changes
    conn.execute(
        """
        INSERT OR IGNORE INTO events(
            event_fingerprint, project_id, logged_at,
            action, actor_id, actee_id, event_json, first_seen_at
        )
        VALUES(?, ?, ?, ?, NULL, ?, ?, ?)
        """,
        (
            fp,
            project_id,
            observed_at,
            action,
            actee_id,
            compact_json(payload),
            observed_at,
        ),
    )
    return conn.total_changes > before


def persist_form_metadata(
    conn: sqlite3.Connection,
    *,
    project_id: int,
    forms: list[dict],
    deleted_forms: list[dict],
    observed_at: str,
) -> dict:
    """
    Maintain current logical-form state plus all identifiable Central form
    instances, and emit MethodMesh observation events for lifecycle changes.

    If /v1/audits is unavailable, these observation events still provide a
    timestamped record that MethodMesh observed a form appearing, disappearing,
    being restored, or changing published version. They do not claim an actor
    or exact server-side action time.
    """
    active_ids = {
        item.get("xmlFormId")
        for item in forms
        if item.get("xmlFormId")
    }

    counters = {
        "form_discovered": 0,
        "form_deleted_observed": 0,
        "form_restored_observed": 0,
        "form_version_changed": 0,
    }

    def previous_instance(numeric_id):
        if numeric_id is None:
            return None
        return conn.execute(
            """
            SELECT *
            FROM form_instances
            WHERE project_id = ?
              AND form_numeric_id = ?
            """,
            (project_id, numeric_id),
        ).fetchone()

    def upsert_instance(item: dict, *, is_deleted: bool) -> None:
        numeric_id = item.get("id")
        form_id = item.get("xmlFormId")
        if numeric_id is None or not form_id:
            return

        prior = previous_instance(numeric_id)
        central_deleted_at = item.get("deletedAt")
        effective_deleted_at = (
            central_deleted_at
            if is_deleted
            else None
        )

        if prior is None:
            action = (
                "methodmesh.form.deleted_first_observed"
                if is_deleted
                else "methodmesh.form.discovered"
            )
            if insert_observation_event(
                conn,
                project_id=project_id,
                action=action,
                observed_at=observed_at,
                actee_id=str(numeric_id),
                details={
                    "form_id": form_id,
                    "form_numeric_id": numeric_id,
                    "version": item.get("version"),
                    "state": item.get("state"),
                    "central_deleted_at": central_deleted_at,
                    "interpretation": (
                        "FIRST_OBSERVED_ALREADY_DELETED"
                        if is_deleted
                        else "FIRST_OBSERVED_ACTIVE"
                    ),
                },
            ):
                if is_deleted:
                    counters["form_deleted_observed"] += 1
                else:
                    counters["form_discovered"] += 1
        else:
            prior_deleted = prior["deleted_at"] is not None

            if prior_deleted and not is_deleted:
                if insert_observation_event(
                    conn,
                    project_id=project_id,
                    action="methodmesh.form.restore_observed",
                    observed_at=observed_at,
                    actee_id=str(numeric_id),
                    details={
                        "form_id": form_id,
                        "form_numeric_id": numeric_id,
                        "previous_deleted_at": prior["deleted_at"],
                        "version": item.get("version"),
                    },
                ):
                    counters["form_restored_observed"] += 1

            if (not prior_deleted) and is_deleted:
                if insert_observation_event(
                    conn,
                    project_id=project_id,
                    action="methodmesh.form.delete_observed",
                    observed_at=observed_at,
                    actee_id=str(numeric_id),
                    details={
                        "form_id": form_id,
                        "form_numeric_id": numeric_id,
                        "central_deleted_at": central_deleted_at,
                        "last_observed_version": prior["version"],
                    },
                ):
                    counters["form_deleted_observed"] += 1

            if (
                not is_deleted
                and prior["version"] != item.get("version")
            ):
                if insert_observation_event(
                    conn,
                    project_id=project_id,
                    action="methodmesh.form.version_changed_observed",
                    observed_at=observed_at,
                    actee_id=str(numeric_id),
                    details={
                        "form_id": form_id,
                        "form_numeric_id": numeric_id,
                        "previous_version": prior["version"],
                        "current_version": item.get("version"),
                    },
                ):
                    counters["form_version_changed"] += 1

        conn.execute(
            """
            INSERT INTO form_instances(
                project_id, form_numeric_id, form_id, name, version,
                state, published_at, created_at, updated_at, deleted_at,
                last_seen_at
            )
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(project_id, form_numeric_id) DO UPDATE SET
                form_id = excluded.form_id,
                name = excluded.name,
                version = excluded.version,
                state = excluded.state,
                published_at = excluded.published_at,
                created_at = excluded.created_at,
                updated_at = excluded.updated_at,
                deleted_at = excluded.deleted_at,
                last_seen_at = excluded.last_seen_at
            """,
            (
                project_id,
                numeric_id,
                form_id,
                item.get("name"),
                item.get("version"),
                item.get("state"),
                item.get("publishedAt"),
                item.get("createdAt"),
                item.get("updatedAt"),
                effective_deleted_at,
                observed_at,
            ),
        )

    for item in forms:
        form_id = item.get("xmlFormId")
        if not form_id:
            continue

        upsert_instance(item, is_deleted=False)

        conn.execute(
            """
            INSERT INTO forms(
                project_id, form_id, form_numeric_id, name,
                version, state, deleted_at, last_seen_at
            )
            VALUES(?, ?, ?, ?, ?, ?, NULL, ?)
            ON CONFLICT(project_id, form_id) DO UPDATE SET
                form_numeric_id = excluded.form_numeric_id,
                name = excluded.name,
                version = excluded.version,
                state = excluded.state,
                deleted_at = NULL,
                last_seen_at = excluded.last_seen_at
            """,
            (
                project_id,
                form_id,
                item.get("id"),
                item.get("name"),
                item.get("version"),
                item.get("state"),
                observed_at,
            ),
        )

    for item in deleted_forms:
        form_id = item.get("xmlFormId")
        if not form_id:
            continue

        upsert_instance(item, is_deleted=True)

        # Never let a deleted historical form overwrite an active logical form
        # that happens to reuse the same xmlFormId.
        if form_id in active_ids:
            continue

        conn.execute(
            """
            INSERT INTO forms(
                project_id, form_id, form_numeric_id, name,
                version, state, deleted_at, last_seen_at
            )
            VALUES(?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(project_id, form_id) DO UPDATE SET
                form_numeric_id = excluded.form_numeric_id,
                name = excluded.name,
                version = excluded.version,
                state = excluded.state,
                deleted_at = excluded.deleted_at,
                last_seen_at = excluded.last_seen_at
            """,
            (
                project_id,
                form_id,
                item.get("id"),
                item.get("name"),
                item.get("version"),
                item.get("state"),
                item.get("deletedAt"),
                observed_at,
            ),
        )

    conn.commit()
    return counters



def metadata_current_version_id(item: dict) -> str | None:
    current = item.get("currentVersion")
    if isinstance(current, dict):
        return current.get("instanceId")
    return None


def needs_content_verification(
    prior,
    current_metadata: dict,
) -> tuple[bool, str]:
    current_version_id = metadata_current_version_id(current_metadata)

    if prior is None:
        return True, "NEW_SUBMISSION"

    if prior["status"] in {
        "DELETED",
        "PURGED",
        "DELETED_BEFORE_CONTENT_VERIFICATION",
    }:
        return True, "RESTORED_OR_REAPPEARED"

    if prior["current_version_id"] != current_version_id:
        return True, "CURRENT_VERSION_CHANGED"

    # This catches attachment changes on an otherwise unchanged XML version.
    # Review-state-only updates may cause an unnecessary verification, but
    # never cause data to be missed.
    if prior["updated_at"] != current_metadata.get("updatedAt"):
        return True, "SUBMISSION_UPDATED_AT_CHANGED"

    return False, "UNCHANGED_METADATA_AND_VERSION"


def cleanup_orphan_ephemeral_dirs(parent: Path) -> int:
    """
    Remove MethodMesh-owned transient workspaces left by an interrupted prior
    run. Only directories with the v13-specific prefix are touched.
    """
    removed = 0
    if not parent.exists():
        return removed

    for path in parent.glob("methodmesh_ephemeral_*"):
        if not path.is_dir():
            continue
        try:
            shutil.rmtree(path)
            removed += 1
        except OSError:
            pass

    return removed


def choose_ephemeral_parent(
    *,
    requested: str | None,
    allow_disk_temp: bool,
) -> Path:
    if requested:
        parent = Path(requested).expanduser().resolve()
        parent.mkdir(parents=True, exist_ok=True)
        return parent

    shm = Path("/dev/shm")
    if shm.exists() and shm.is_dir() and os.access(shm, os.W_OK):
        return shm

    if allow_disk_temp:
        return Path(tempfile.gettempdir()).resolve()

    raise RuntimeError(
        "No RAM-backed ephemeral directory was found. Production v13 "
        "will not place submission XML on an ordinary disk. On Linux use "
        "/dev/shm (automatic), or supply --ephemeral-dir pointing to a "
        "tmpfs/RAM disk. For local development only, explicitly add "
        "--allow-disk-temp."
    )


def assert_safe_persistent_state(state_root: Path) -> None:
    """
    Fail closed if a raw-research-data-like artifact is found in the durable
    v13 state directory.
    """
    findings = []
    for path in state_root.rglob("*"):
        if not path.is_file():
            continue
        rel = path.relative_to(state_root).as_posix()
        if path.suffix.lower() in SAFE_STATE_FORBIDDEN_SUFFIXES:
            findings.append(f"forbidden file type: {rel}")
        if path.name.lower() in SAFE_STATE_FORBIDDEN_BASENAMES:
            findings.append(f"forbidden raw filename: {rel}")

    if findings:
        raise RuntimeError(
            "PERSISTENT STATE PRIVACY CHECK FAILED: "
            + "; ".join(findings[:20])
        )


def safe_version_record(version: dict) -> dict:
    methodmesh = version.get("methodmesh") or {}
    reconstruction = version.get("reconstruction") or {}
    receipt = methodmesh.get("desktop_audit_receipt")
    receipt_tsa = (
        methodmesh.get("desktop_receipt_timestamp_verification")
        or {}
    )

    return {
        "sequence": version.get("sequence"),
        "version_id": version.get("version_id"),
        "created_at": version.get("created_at"),
        "form_version": version.get("form_version"),
        "classification": version.get("classification"),
        "xml_sha256": version.get("xml_sha256"),
        "reconstructed_payload_sha256":
            reconstruction.get("reconstructed_payload_sha256"),
        "desktop_receipt_sha256": (
            receipt.get("receipt_sha256")
            if isinstance(receipt, dict)
            else None
        ),
        "desktop_receipt_schema": (
            receipt.get("schema")
            if isinstance(receipt, dict)
            else None
        ),
        "tsa_status":
            receipt_tsa.get("verification_status"),
        "tsa_time":
            receipt.get("trusted_timestamp_time_iso")
            if isinstance(receipt, dict)
            else None,
        "attachment_count": len(version.get("attachments") or []),
    }


def label_map_from_ephemeral_root(ephemeral_root: Path) -> dict[str, str]:
    form_root = ephemeral_root / "_form_versions"
    if not form_root.exists():
        return {}
    candidates = sorted(form_root.glob("*/form.xml"))
    if not candidates:
        return {}
    return best_effort_xform_labels(candidates[-1])


def version_xml_path(
    ephemeral_root: Path,
    logical_id: str,
    index: int,
    version_id: str,
) -> Path:
    return (
        ephemeral_root
        / safe_name(logical_id)
        / f"{index + 1:03d}_{safe_name(version_id)}"
        / "submission.xml"
    )


def derive_new_edit_events(
    *,
    result: dict,
    ephemeral_root: Path,
    existing_version_ids: set[str],
) -> list[dict]:
    versions = result.get("versions") or []
    if len(versions) < 2:
        return []

    original_recipe = (
        ((versions[0].get("methodmesh") or {}).get("commitment_recipe"))
    )
    labels = label_map_from_ephemeral_root(ephemeral_root)

    events = []
    for index in range(1, len(versions)):
        previous = versions[index - 1]
        current = versions[index]
        version_id = current.get("version_id")

        if not version_id or version_id in existing_version_ids:
            continue

        # Desktop checkpoint versions do not represent human research-data
        # changes.
        if current.get("classification") == "VERIFIED_BY_DESKTOP_RECEIPT":
            continue

        prev_path = version_xml_path(
            ephemeral_root,
            result["logical_submission_id"],
            index - 1,
            previous.get("version_id"),
        )
        curr_path = version_xml_path(
            ephemeral_root,
            result["logical_submission_id"],
            index,
            version_id,
        )

        changed_fields = changed_committed_fields_for_versions(
            previous_xml=prev_path,
            current_xml=curr_path,
            recipe=original_recipe,
            labels=labels,
            previous_attachments=previous.get("attachments"),
            current_attachments=current.get("attachments"),
        )

        events.append({
            "version_id": version_id,
            "previous_version_id": previous.get("version_id"),
            "created_at": current.get("created_at"),
            "actor": current.get("actor"),
            "changed_fields": changed_fields,
        })

    return events


def persist_comments_and_reasons(
    conn: sqlite3.Connection,
    *,
    project_id: int,
    form_id: str,
    logical_id: str,
    comments: list[dict],
    new_edit_events: list[dict],
    observed_at: str,
) -> None:
    known = ledger_comment_fingerprints(
        conn,
        project_id,
        form_id,
        logical_id,
    )
    new_comments = []

    for comment in comments:
        fp = comment.get("fingerprint")
        if not fp:
            continue

        if fp not in known:
            new_comments.append(comment)

        conn.execute(
            """
            INSERT OR IGNORE INTO comments(
                project_id, form_id, logical_id, fingerprint,
                actor_id, actor_json, body, body_sha256, first_seen_at
            )
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                project_id,
                form_id,
                logical_id,
                fp,
                str(comment.get("actorId"))
                if comment.get("actorId") is not None
                else None,
                compact_json(comment.get("actor"))
                if comment.get("actor") is not None
                else None,
                comment.get("body"),
                comment.get("body_sha256"),
                observed_at,
            ),
        )

    # Only link reasons when the evidence is unambiguous.
    if len(new_comments) == 1 and len(new_edit_events) == 1:
        comment = new_comments[0]
        edit = new_edit_events[0]
        conn.execute(
            """
            UPDATE edits
            SET reason_body = ?,
                reason_body_sha256 = ?,
                reason_association_status = 'ASSOCIATED',
                reason_association_basis = ?
            WHERE project_id = ?
              AND form_id = ?
              AND logical_id = ?
              AND version_id = ?
            """,
            (
                comment.get("body"),
                comment.get("body_sha256"),
                (
                    "ONE_NEW_COMMENT_AND_ONE_NEW_HUMAN_EDIT_"
                    "DURING_VERIFICATION_INTERVAL"
                ),
                project_id,
                form_id,
                logical_id,
                edit["version_id"],
            ),
        )
    else:
        # A later comment can resolve a previously unresolved single edit.
        unresolved = conn.execute(
            """
            SELECT version_id
            FROM edits
            WHERE project_id = ?
              AND form_id = ?
              AND logical_id = ?
              AND reason_association_status = 'UNRESOLVED'
            ORDER BY created_at
            """,
            (project_id, form_id, logical_id),
        ).fetchall()

        if len(new_comments) == 1 and len(unresolved) == 1:
            comment = new_comments[0]
            conn.execute(
                """
                UPDATE edits
                SET reason_body = ?,
                    reason_body_sha256 = ?,
                    reason_association_status = 'ASSOCIATED',
                    reason_association_basis = ?
                WHERE project_id = ?
                  AND form_id = ?
                  AND logical_id = ?
                  AND version_id = ?
                """,
                (
                    comment.get("body"),
                    comment.get("body_sha256"),
                    "ONE_NEW_COMMENT_AND_ONE_UNRESOLVED_EDIT",
                    project_id,
                    form_id,
                    logical_id,
                    unresolved[0]["version_id"],
                ),
            )

    conn.commit()


def persist_zero_version_anomaly(
    conn: sqlite3.Connection,
    *,
    project_id: int,
    form_id: str,
    metadata: dict,
    observed_at: str,
) -> dict:
    logical_id = metadata.get("instanceId")

    conn.execute(
        """
        INSERT INTO submissions(
            project_id, form_id, logical_id,
            current_version_id, current_payload_sha256,
            original_attestation_hash, desktop_receipt_sha256,
            overall_status, status, review_state,
            created_at, updated_at, deleted_at,
            last_verified_at, last_seen_at, version_count,
            verification_note
        )
        VALUES(?, ?, ?, ?, NULL, NULL, NULL,
               'ZERO_VERSION_ANOMALY', 'ZERO_VERSION_ANOMALY', ?,
               ?, ?, NULL, NULL, ?, 0, ?)
        ON CONFLICT(project_id, form_id, logical_id) DO UPDATE SET
            current_version_id = excluded.current_version_id,
            current_payload_sha256 = NULL,
            original_attestation_hash = NULL,
            desktop_receipt_sha256 = NULL,
            overall_status = 'ZERO_VERSION_ANOMALY',
            status = 'ZERO_VERSION_ANOMALY',
            review_state = excluded.review_state,
            created_at = excluded.created_at,
            updated_at = excluded.updated_at,
            deleted_at = NULL,
            last_verified_at = NULL,
            last_seen_at = excluded.last_seen_at,
            version_count = 0,
            verification_note = excluded.verification_note
        """,
        (
            project_id,
            form_id,
            logical_id,
            metadata_current_version_id(metadata),
            metadata.get("reviewState"),
            metadata.get("createdAt"),
            metadata.get("updatedAt"),
            observed_at,
            (
                "Central lists this logical submission but returns zero "
                "retained versions. MethodMesh has NOT content-verified it."
            ),
        ),
    )
    conn.commit()

    return {
        "logical_submission_id": logical_id,
        "status": "ZERO_VERSION_ANOMALY",
        "content_verified": False,
    }


def persist_verified_submission(
    conn: sqlite3.Connection,
    *,
    central: Central,
    project_id: int,
    form_id: str,
    metadata_before: dict,
    result: dict,
    ephemeral_root: Path,
    observed_at: str,
) -> dict:
    logical_id = result["logical_submission_id"]
    existing_version_ids = ledger_version_ids(
        conn,
        project_id,
        form_id,
        logical_id,
    )

    new_edit_events = derive_new_edit_events(
        result=result,
        ephemeral_root=ephemeral_root,
        existing_version_ids=existing_version_ids,
    )

    for edit in new_edit_events:
        conn.execute(
            """
            INSERT OR IGNORE INTO edits(
                project_id, form_id, logical_id, version_id,
                previous_version_id, created_at, actor,
                changed_fields_json,
                reason_association_status,
                reason_association_basis,
                first_seen_at
            )
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, 'UNRESOLVED', ?, ?)
            """,
            (
                project_id,
                form_id,
                logical_id,
                edit["version_id"],
                edit.get("previous_version_id"),
                edit.get("created_at"),
                edit.get("actor"),
                compact_json(edit.get("changed_fields") or []),
                (
                    "CENTRAL_COMMENTS_HAVE_NO_NATIVE_VERSION_"
                    "FOREIGN_KEY"
                ),
                observed_at,
            ),
        )

    for version in result.get("versions") or []:
        safe = safe_version_record(version)
        conn.execute(
            """
            INSERT INTO versions(
                project_id, form_id, logical_id, version_id,
                sequence, created_at, form_version, classification,
                xml_sha256, reconstructed_payload_sha256,
                desktop_receipt_sha256, desktop_receipt_schema,
                tsa_status, tsa_time, attachment_count, verified_at
            )
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(project_id, form_id, logical_id, version_id)
            DO UPDATE SET
                classification = excluded.classification,
                xml_sha256 = excluded.xml_sha256,
                reconstructed_payload_sha256 =
                    excluded.reconstructed_payload_sha256,
                desktop_receipt_sha256 =
                    excluded.desktop_receipt_sha256,
                desktop_receipt_schema =
                    excluded.desktop_receipt_schema,
                tsa_status = excluded.tsa_status,
                tsa_time = excluded.tsa_time,
                attachment_count = excluded.attachment_count,
                verified_at = excluded.verified_at
            """,
            (
                project_id,
                form_id,
                logical_id,
                safe["version_id"],
                safe["sequence"],
                safe["created_at"],
                safe["form_version"],
                safe["classification"],
                safe["xml_sha256"],
                safe["reconstructed_payload_sha256"],
                safe["desktop_receipt_sha256"],
                safe["desktop_receipt_schema"],
                safe["tsa_status"],
                safe["tsa_time"],
                safe["attachment_count"],
                observed_at,
            ),
        )

    comments = fetch_submission_comments(
        central=central,
        project_id=project_id,
        form_id=form_id,
        submission_id=logical_id,
    )

    persist_comments_and_reasons(
        conn,
        project_id=project_id,
        form_id=form_id,
        logical_id=logical_id,
        comments=comments,
        new_edit_events=new_edit_events,
        observed_at=observed_at,
    )

    # Re-fetch metadata after possible receipt writeback.
    project = path_part(project_id)
    form = path_part(form_id)
    logical = path_part(logical_id)
    metadata_after = central.get_json(
        (
            f"/v1/projects/{project}/forms/{form}/submissions/{logical}"
        ),
        extended=True,
    )
    versions_after = central.get_json(
        (
            f"/v1/projects/{project}/forms/{form}/submissions/{logical}"
            f"/versions"
        )
    )

    current_version_id = metadata_current_version_id(metadata_after)

    current_rebuilt = None
    current_receipt_sha = None
    original_attestation_hash = None

    versions = result.get("versions") or []
    if versions:
        original_attestation_hash = (
            (versions[0].get("methodmesh") or {}).get("attestation_hash")
        )

        current_result_version = versions[-1]
        current_rebuilt = (
            (current_result_version.get("reconstruction") or {})
            .get("reconstructed_payload_sha256")
        )
        current_receipt = (
            (current_result_version.get("methodmesh") or {})
            .get("desktop_audit_receipt")
        )
        if isinstance(current_receipt, dict):
            current_receipt_sha = current_receipt.get("receipt_sha256")

    writeback = result.get("writeback_action") or {}
    planned = (
        writeback.get("planned_receipt")
        or writeback.get("planned_receipt_body")
        or {}
    )
    if isinstance(planned, dict) and planned.get("receipt_sha256"):
        current_receipt_sha = planned.get("receipt_sha256")
        current_rebuilt = (
            planned.get("reconstructed_payload_sha256")
            or current_rebuilt
        )

    overall_status = result.get("overall_status")
    if (
        writeback.get("receipt_action")
        == "TIMESTAMPED_SEQUENCE_RECEIPT_WRITTEN"
    ):
        overall_status = (
            "EDIT_HISTORY_PRESENT_CURRENT_VERSION_DESKTOP_VERIFIED"
        )

    conn.execute(
        """
        INSERT INTO submissions(
            project_id, form_id, logical_id,
            current_version_id, current_payload_sha256,
            original_attestation_hash, desktop_receipt_sha256,
            overall_status, status, review_state,
            created_at, updated_at, deleted_at,
            last_verified_at, last_seen_at, version_count,
            verification_note
        )
        VALUES(?, ?, ?, ?, ?, ?, ?, ?, 'VERIFIED_PRESENT', ?, ?, ?, NULL,
               ?, ?, ?, ?)
        ON CONFLICT(project_id, form_id, logical_id) DO UPDATE SET
            current_version_id = excluded.current_version_id,
            current_payload_sha256 = excluded.current_payload_sha256,
            original_attestation_hash = excluded.original_attestation_hash,
            desktop_receipt_sha256 = excluded.desktop_receipt_sha256,
            overall_status = excluded.overall_status,
            status = 'VERIFIED_PRESENT',
            review_state = excluded.review_state,
            created_at = excluded.created_at,
            updated_at = excluded.updated_at,
            deleted_at = NULL,
            last_verified_at = excluded.last_verified_at,
            last_seen_at = excluded.last_seen_at,
            version_count = excluded.version_count,
            verification_note = excluded.verification_note
        """,
        (
            project_id,
            form_id,
            logical_id,
            current_version_id,
            current_rebuilt,
            original_attestation_hash,
            current_receipt_sha,
            overall_status,
            metadata_after.get("reviewState"),
            metadata_after.get("createdAt"),
            metadata_after.get("updatedAt"),
            observed_at,
            observed_at,
            len(versions_after),
            "RAW_CONTENT_PROCESSED_EPHEMERALLY_AND_DISCARDED",
        ),
    )
    conn.commit()

    return {
        "logical_submission_id": logical_id,
        "status": "VERIFIED_PRESENT",
        "current_version_id": current_version_id,
        "overall_status": overall_status,
        "new_edit_count": len(new_edit_events),
        "comments_seen": len(comments),
    }


def mark_deleted_submission(
    conn: sqlite3.Connection,
    *,
    project_id: int,
    form_id: str,
    metadata: dict,
    observed_at: str,
) -> dict:
    logical_id = metadata.get("instanceId")
    prior = ledger_submission_row(
        conn,
        project_id,
        form_id,
        logical_id,
    )

    if prior is None:
        status = "DELETED_BEFORE_CONTENT_VERIFICATION"
        note = (
            "Central reports a deleted submission that MethodMesh had not "
            "previously content-verified."
        )
        lifecycle_action = (
            "methodmesh.submission.deleted_first_observed"
        )
    else:
        status = "DELETED"
        note = (
            "Previously observed submission is now soft-deleted in Central."
        )
        lifecycle_action = (
            "methodmesh.submission.delete_observed"
            if prior["status"] not in {
                "DELETED",
                "PURGED",
                "DELETED_BEFORE_CONTENT_VERIFICATION",
            }
            else None
        )

    if lifecycle_action:
        insert_observation_event(
            conn,
            project_id=project_id,
            action=lifecycle_action,
            observed_at=observed_at,
            actee_id=logical_id,
            details={
                "form_id": form_id,
                "logical_submission_id": logical_id,
                "central_deleted_at": metadata.get("deletedAt"),
                "prior_status": (
                    prior["status"] if prior is not None else None
                ),
                "prior_current_version_id": (
                    prior["current_version_id"]
                    if prior is not None
                    else None
                ),
                "prior_payload_commitment_sha256": (
                    prior["current_payload_sha256"]
                    if prior is not None
                    else None
                ),
                "content_verified_before_deletion": (
                    prior is not None
                    and prior["last_verified_at"] is not None
                ),
            },
        )

    conn.execute(
        """
        INSERT INTO submissions(
            project_id, form_id, logical_id,
            current_version_id, overall_status, status,
            review_state, created_at, updated_at, deleted_at,
            last_seen_at, version_count, verification_note
        )
        VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(project_id, form_id, logical_id) DO UPDATE SET
            status = excluded.status,
            review_state = excluded.review_state,
            updated_at = excluded.updated_at,
            deleted_at = excluded.deleted_at,
            last_seen_at = excluded.last_seen_at,
            verification_note = excluded.verification_note
        """,
        (
            project_id,
            form_id,
            logical_id,
            metadata_current_version_id(metadata),
            prior["overall_status"] if prior is not None else None,
            status,
            metadata.get("reviewState"),
            metadata.get("createdAt"),
            metadata.get("updatedAt"),
            metadata.get("deletedAt"),
            observed_at,
            prior["version_count"] if prior is not None else None,
            note,
        ),
    )
    conn.commit()

    return {
        "logical_submission_id": logical_id,
        "status": status,
        "deleted_at": metadata.get("deletedAt"),
    }



def event_fingerprint(event: dict) -> str:
    return sha256_bytes(
        compact_json({
            "id": event.get("id"),
            "loggedAt": event.get("loggedAt"),
            "action": event.get("action"),
            "actorId": event.get("actorId"),
            "acteeId": event.get("acteeId"),
            "details": event.get("details"),
        }).encode("utf-8")
    )


def extract_uuid_like(value) -> str | None:
    if isinstance(value, str):
        if value.startswith("uuid:"):
            return value
        return None
    if isinstance(value, dict):
        for key in (
            "instanceId",
            "submissionId",
            "submission",
            "id",
            "uuid",
        ):
            candidate = extract_uuid_like(value.get(key))
            if candidate:
                return candidate
        for nested in value.values():
            candidate = extract_uuid_like(nested)
            if candidate:
                return candidate
    if isinstance(value, list):
        for nested in value:
            candidate = extract_uuid_like(nested)
            if candidate:
                return candidate
    return None


def ingest_server_audit_delta(
    conn: sqlite3.Connection,
    *,
    central: Central,
    project_id: int,
    start: datetime,
    end: datetime,
) -> dict:
    response = collect_server_audits_paged(
        central=central,
        start=start,
        end=end,
    )
    events = response.get("data") or []
    inserted = 0

    for event in events:
        fp = event_fingerprint(event)
        before = conn.total_changes
        conn.execute(
            """
            INSERT OR IGNORE INTO events(
                event_fingerprint, project_id, logged_at,
                action, actor_id, actee_id, event_json, first_seen_at
            )
            VALUES(?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                fp,
                project_id,
                event.get("loggedAt"),
                event.get("action"),
                str(event.get("actorId"))
                if event.get("actorId") is not None
                else None,
                str(event.get("acteeId"))
                if event.get("acteeId") is not None
                else None,
                compact_json(event),
                utc_now_iso(),
            ),
        )
        if conn.total_changes > before:
            inserted += 1

        action = event.get("action")
        if action in {"submission.delete", "submission.purge"}:
            logical_id = (
                extract_uuid_like(event.get("actee"))
                or extract_uuid_like(event.get("details"))
                or (
                    event.get("acteeId")
                    if isinstance(event.get("acteeId"), str)
                    and event.get("acteeId").startswith("uuid:")
                    else None
                )
            )
            if logical_id:
                status = (
                    "PURGED"
                    if action == "submission.purge"
                    else "DELETED"
                )
                conn.execute(
                    """
                    UPDATE submissions
                    SET status = ?,
                        verification_note = ?,
                        last_seen_at = ?
                    WHERE project_id = ?
                      AND logical_id = ?
                    """,
                    (
                        status,
                        f"Status observed from Central audit action {action}",
                        event.get("loggedAt") or utc_now_iso(),
                        project_id,
                        logical_id,
                    ),
                )

    conn.commit()
    return {
        "available": response.get("available"),
        "complete": response.get("complete"),
        "events_read": len(events),
        "events_inserted": inserted,
    }


def poll_unresolved_comments(
    conn: sqlite3.Connection,
    *,
    central: Central,
    project_id: int,
    form_id: str,
    observed_at: str,
) -> int:
    logical_ids = ledger_unresolved_edit_ids(
        conn,
        project_id,
        form_id,
    )
    resolved = 0

    for logical_id in logical_ids:
        comments = fetch_submission_comments(
            central=central,
            project_id=project_id,
            form_id=form_id,
            submission_id=logical_id,
        )
        known = ledger_comment_fingerprints(
            conn,
            project_id,
            form_id,
            logical_id,
        )
        new_comments = [
            item
            for item in comments
            if item.get("fingerprint") not in known
        ]

        before = conn.execute(
            """
            SELECT COUNT(*) AS n
            FROM edits
            WHERE project_id = ?
              AND form_id = ?
              AND logical_id = ?
              AND reason_association_status = 'ASSOCIATED'
            """,
            (project_id, form_id, logical_id),
        ).fetchone()["n"]

        persist_comments_and_reasons(
            conn,
            project_id=project_id,
            form_id=form_id,
            logical_id=logical_id,
            comments=comments,
            new_edit_events=[],
            observed_at=observed_at,
        )

        after = conn.execute(
            """
            SELECT COUNT(*) AS n
            FROM edits
            WHERE project_id = ?
              AND form_id = ?
              AND logical_id = ?
              AND reason_association_status = 'ASSOCIATED'
            """,
            (project_id, form_id, logical_id),
        ).fetchone()["n"]

        resolved += max(0, after - before)

    return resolved


def ledger_submission_snapshot(
    conn: sqlite3.Connection,
    project_id: int,
) -> list[dict]:
    rows = conn.execute(
        """
        SELECT *
        FROM submissions
        WHERE project_id = ?
        ORDER BY form_id, logical_id
        """,
        (project_id,),
    ).fetchall()

    result = []
    for row in rows:
        edits = conn.execute(
            """
            SELECT *
            FROM edits
            WHERE project_id = ?
              AND form_id = ?
              AND logical_id = ?
            ORDER BY created_at, version_id
            """,
            (project_id, row["form_id"], row["logical_id"]),
        ).fetchall()

        edit_events = []
        for edit in edits:
            edit_events.append({
                "version_id": edit["version_id"],
                "previous_version_id": edit["previous_version_id"],
                "created_at": edit["created_at"],
                "actor": edit["actor"],
                "changed_fields": json.loads(
                    edit["changed_fields_json"] or "[]"
                ),
                "reason": {
                    "body": edit["reason_body"],
                    "body_sha256": edit["reason_body_sha256"],
                    "association_status":
                        edit["reason_association_status"],
                    "association_basis":
                        edit["reason_association_basis"],
                    "central_native_version_link": False,
                },
                "research_values_included": False,
            })

        result.append({
            "logical_submission_id": row["logical_id"],
            "form_id": row["form_id"],
            "status": row["status"],
            "overall_status": row["overall_status"],
            "current_version_id_post_run":
                row["current_version_id"],
            "current_payload_commitment_sha256":
                row["current_payload_sha256"],
            "original_attestation_hash":
                row["original_attestation_hash"],
            "desktop_receipt_sha256":
                row["desktop_receipt_sha256"],
            "post_run_review_state": row["review_state"],
            "created_at": row["created_at"],
            "updated_at": row["updated_at"],
            "deleted_at": row["deleted_at"],
            "last_verified_at": row["last_verified_at"],
            "version_count_post_run": row["version_count"],
            "edit_events": edit_events,
            "research_values_included": False,
        })

    return result


def ledger_forms_snapshot(
    conn: sqlite3.Connection,
    project_id: int,
) -> list[dict]:
    rows = conn.execute(
        """
        SELECT *
        FROM forms
        WHERE project_id = ?
        ORDER BY form_id
        """,
        (project_id,),
    ).fetchall()
    return [dict(row) for row in rows]


def ledger_events_snapshot(
    conn: sqlite3.Connection,
    project_id: int,
    start: datetime,
    end: datetime,
) -> list[dict]:
    rows = conn.execute(
        """
        SELECT event_json
        FROM events
        WHERE project_id = ?
          AND logged_at >= ?
          AND logged_at <= ?
        ORDER BY logged_at, event_fingerprint
        """,
        (
            project_id,
            start.isoformat(),
            end.isoformat(),
        ),
    ).fetchall()
    return [json.loads(row["event_json"]) for row in rows]


def delta_auditor_summary(
    evidence: dict,
    checkpoint: dict,
    run_stats: dict,
) -> str:
    submissions = evidence.get("submissions") or []
    active = sum(
        1 for x in submissions
        if x.get("status") == "VERIFIED_PRESENT"
    )
    deleted = sum(
        1 for x in submissions
        if x.get("status") == "DELETED"
    )
    purged = sum(
        1 for x in submissions
        if x.get("status") == "PURGED"
    )
    unverified_deleted = sum(
        1 for x in submissions
        if x.get("status")
        == "DELETED_BEFORE_CONTENT_VERIFICATION"
    )
    zero_version = sum(
        1 for x in submissions
        if x.get("status") == "ZERO_VERSION_ANOMALY"
    )
    unresolved_reasons = sum(
        1
        for sub in submissions
        for edit in (sub.get("edit_events") or [])
        if (edit.get("reason") or {}).get("association_status")
        == "UNRESOLVED"
    )

    service = evidence.get("service_summary") or {}

    lines = [
        "METHODMESH PROJECT AUDIT — DELTA LEDGER",
        "=" * 72,
        f"Central:                  {evidence.get('central_url')}",
        f"Project:                  {evidence.get('project_id')}",
        f"Evidence window:          {evidence.get('window_start_utc')} -> {evidence.get('window_end_utc')}",
        "",
        "PRIVACY / PROCESSING",
        "Central remains the research-data store: YES",
        "Research values retained by MethodMesh: NO",
        "Submission XML retained by MethodMesh: NO",
        "Attachment bytes retained by MethodMesh: NO",
        "Attachment verification: streamed SHA-256 and discarded",
        "",
        "THIS RUN",
        f"Forms examined:           {run_stats.get('forms_examined', 0)}",
        f"Metadata rows examined:   {run_stats.get('metadata_rows_examined', 0)}",
        f"Submissions content-read: {run_stats.get('content_verified', 0)}",
        f"Unchanged content skipped:{run_stats.get('content_skipped', 0):>6}",
        f"Deleted rows observed:    {run_stats.get('deleted_observed', 0)}",
        "",
        "LEDGER STATE",
        f"Verified present:         {active}",
        f"Deleted:                  {deleted}",
        f"Purged:                   {purged}",
        f"Deleted before verify:    {unverified_deleted}",
        f"Zero-version anomalies:   {zero_version}",
        f"Unresolved edit reasons:  {unresolved_reasons}",
        "",
        "SERVICE OBSERVATIONS",
        f"Probe observations:       {service.get('observation_count', 0)}",
        f"HTTP successes:           {service.get('http_success_count', 0)}",
        f"API successes:            {service.get('api_success_count', 0)}",
        f"Unexpected open ports:    {service.get('unexpected_open_ports_seen') or 'none'}",
        "",
        "DAILY CRYPTOGRAPHIC CHECKPOINT",
        f"Evidence Merkle root:     {evidence.get('evidence_merkle_root')}",
        f"Checkpoint SHA-256:       {checkpoint.get('daily_checkpoint_sha256')}",
        f"Previous checkpoint:      {checkpoint.get('previous_daily_checkpoint_sha256') or 'GENESIS'}",
        f"RFC3161 status:           {checkpoint.get('timestamp_status')}",
        f"RFC3161 time:             {checkpoint.get('trusted_timestamp_time_iso') or '-'}",
        "",
        "WHAT THIS MEANS",
        "MethodMesh has independently verified the currently changed/new",
        "records it needed to inspect, retained only non-data cryptographic",
        "state, and cryptographically committed the observed project/system",
        "state. Unchanged submissions were not redownloaded.",
    ]
    return "\n".join(lines) + "\n"


def create_delta_daily_checkpoint(
    *,
    conn: sqlite3.Connection,
    central: Central,
    project_id: int,
    state_root: Path,
    current_probe: dict,
    tsa_ca_file: str | None,
    tsa_cert_file: str | None,
    allow_tsa_download: bool,
    tsa_url: str,
    run_stats: dict,
    auditor_export_root: Path,
) -> dict:
    now = datetime.now(timezone.utc)
    previous_end_raw = get_ledger_meta(
        conn,
        "last_checkpoint_end_utc",
    )
    previous_end = parse_iso_strict(previous_end_raw)
    window_start = previous_end or (now - timedelta(hours=24))
    window_end = now

    central_system = collect_central_system_metadata(
        central=central,
        project_id=project_id,
        form_id=(
            get_ledger_meta(conn, "representative_form_id", "")
            or ""
        ),
        start=window_start,
        end=window_end,
    )

    # Include the local non-data event/form ledger as synthetic metadata
    # endpoints so the existing offline verifier can reproduce the exact
    # Merkle tree without learning any research values.
    endpoints = central_system.setdefault("endpoints", {})
    endpoints["methodmesh_forms_state"] = {
        "available": True,
        "status_code": 200,
        "data": ledger_forms_snapshot(conn, project_id),
    }
    endpoints["methodmesh_event_ledger"] = {
        "available": True,
        "status_code": 200,
        "data": ledger_events_snapshot(
            conn,
            project_id,
            window_start,
            window_end,
        ),
    }

    probes = load_service_probes(
        output_root=state_root,
        start=window_start,
        end=window_end,
    )
    if current_probe and not any(
        p.get("observed_at_utc") == current_probe.get("observed_at_utc")
        for p in probes
    ):
        probes.append(current_probe)

    submissions = ledger_submission_snapshot(
        conn,
        project_id,
    )

    evidence = {
        "schema": STATE_SNAPSHOT_SCHEMA,
        "generated_at_utc": now.isoformat(),
        "window_start_utc": window_start.isoformat(),
        "window_end_utc": window_end.isoformat(),
        "central_url": central.base_url,
        "project_id": project_id,
        "form_id": "__PROJECT_LEVEL__",
        "privacy_policy": {
            "research_values_included": False,
            "raw_submission_xml_included": False,
            "attachments_included": False,
            "changed_fields_are_names_only": True,
            "persistent_state_is_non_data_ledger": True,
            "central_is_authoritative_data_store": True,
        },
        "submissions": submissions,
        "central_system": central_system,
        "service_summary": service_probe_summary(probes),
        "service_probe_hashes": [
            sha256_bytes(compact_json(p).encode("utf-8"))
            for p in probes
        ],
        "run_stats": run_stats,
    }

    leaves = []
    for submission in submissions:
        leaf_id = (
            f"{submission.get('form_id')}::"
            f"{submission.get('logical_submission_id')}"
        )
        leaves.append(
            evidence_leaf(
                leaf_type="submission",
                leaf_id=leaf_id,
                payload=submission,
            )
        )

    for name, endpoint in sorted(
        (central_system.get("endpoints") or {}).items()
    ):
        leaves.append(
            evidence_leaf(
                leaf_type="central_endpoint",
                leaf_id=name,
                payload=endpoint,
            )
        )

    for index, probe in enumerate(probes):
        leaves.append(
            evidence_leaf(
                leaf_type="service_probe",
                leaf_id=(
                    probe.get("observed_at_utc")
                    or f"probe-{index:06d}"
                ),
                payload=probe,
            )
        )

    root_hash = merkle_root_hex(leaves)
    evidence["merkle_leaves"] = leaves
    evidence["evidence_merkle_root"] = root_hash

    previous_checkpoint_sha = get_ledger_meta(
        conn,
        "last_daily_checkpoint_sha256",
    )
    previous_merkle_root = get_ledger_meta(
        conn,
        "last_evidence_merkle_root",
    )

    checkpoint_body = {
        "schema": DAILY_CHECKPOINT_SCHEMA,
        "generated_at_utc": now.isoformat(),
        "central_url": central.base_url,
        "project_id": project_id,
        "form_id": "__PROJECT_LEVEL__",
        "window_start_utc": window_start.isoformat(),
        "window_end_utc": window_end.isoformat(),
        "evidence_merkle_root": root_hash,
        "leaf_count": len(leaves),
        "previous_daily_checkpoint_sha256": previous_checkpoint_sha,
        "previous_evidence_merkle_root": previous_merkle_root,
        "research_values_included": False,
    }
    checkpoint_sha = receipt_self_hash(checkpoint_body)

    stamp = now.strftime("%Y%m%dT%H%M%SZ")
    daily_dir = state_root / "_daily_evidence" / stamp
    daily_dir.mkdir(parents=True, exist_ok=True)

    trust = resolve_tsa_trust_material(
        attestation={"trusted_timestamp_authority": tsa_url},
        output_root=state_root,
        tsa_ca_file=tsa_ca_file,
        tsa_cert_file=tsa_cert_file,
        allow_tsa_download=allow_tsa_download,
    )

    timestamp_result = create_rfc3161_timestamp_for_hash(
        digest_hex=checkpoint_sha,
        tsa_url=tsa_url,
        trust_material=trust,
        evidence_dir=daily_dir / "tsa",
    )

    checkpoint = {
        **checkpoint_body,
        "daily_checkpoint_sha256": checkpoint_sha,
        "timestamp_status": (
            "VERIFIED"
            if timestamp_result.get("overall_valid") is True
            else (
                timestamp_result.get("verification_status")
                or "FAILED"
            )
        ),
    }

    if timestamp_result.get("overall_valid") is True:
        checkpoint.update({
            "trusted_timestamp_status": "rfc3161_verified",
            "trusted_timestamp_authority":
                timestamp_result.get("authority"),
            "trusted_timestamp_attested_hash": checkpoint_sha,
            "trusted_timestamp_time_iso":
                timestamp_result.get("time_iso"),
            "trusted_timestamp_serial":
                timestamp_result.get("serial_hex"),
            "trusted_timestamp_request_base64":
                timestamp_result.get("request_base64"),
            "trusted_timestamp_request_sha256":
                timestamp_result.get("request_sha256"),
            "trusted_timestamp_response_base64":
                timestamp_result.get("response_base64"),
            "trusted_timestamp_response_sha256":
                timestamp_result.get("response_sha256"),
            "trusted_timestamp_token_base64":
                timestamp_result.get("token_base64"),
            "trusted_timestamp_token_sha256":
                timestamp_result.get("token_sha256"),
            "trusted_timestamp_trust_source":
                timestamp_result.get("trust_source"),
        })

    evidence["daily_checkpoint"] = checkpoint

    (daily_dir / "daily_evidence.json").write_text(
        pretty_json(evidence),
        encoding="utf-8",
    )
    (daily_dir / "merkle_leaves.json").write_text(
        pretty_json(leaves),
        encoding="utf-8",
    )
    (daily_dir / "daily_checkpoint.json").write_text(
        pretty_json(checkpoint),
        encoding="utf-8",
    )
    (daily_dir / "AUDITOR_SUMMARY.txt").write_text(
        delta_auditor_summary(
            evidence,
            checkpoint,
            run_stats,
        ),
        encoding="utf-8",
    )

    export = create_auditor_safe_export(
        evidence=evidence,
        service_probes=probes,
        daily_dir=daily_dir,
        output_root=state_root,
        export_root=auditor_export_root,
    )

    set_ledger_meta(
        conn,
        "last_checkpoint_end_utc",
        window_end.isoformat(),
    )
    set_ledger_meta(
        conn,
        "last_daily_checkpoint_sha256",
        checkpoint_sha,
    )
    set_ledger_meta(
        conn,
        "last_evidence_merkle_root",
        root_hash,
    )
    conn.commit()

    return {
        "directory": str(daily_dir.resolve()),
        "evidence_merkle_root": root_hash,
        "daily_checkpoint_sha256": checkpoint_sha,
        "timestamp_status": checkpoint.get("timestamp_status"),
        "timestamp_time_iso":
            checkpoint.get("trusted_timestamp_time_iso"),
        "auditor_export": export,
    }


def process_form_delta(
    *,
    conn: sqlite3.Connection,
    central: Central,
    project_id: int,
    form_id: str,
    ephemeral_parent: Path,
    state_root: Path,
    writeback: bool,
    update_review_state: bool,
    tsa_ca_file: str | None,
    tsa_cert_file: str | None,
    allow_tsa_download: bool,
    desktop_tsa_url: str,
) -> dict:
    observed_at = utc_now_iso()
    active = paged_submission_metadata(
        central=central,
        project_id=project_id,
        form_id=form_id,
        deleted=False,
    )
    deleted = paged_submission_metadata(
        central=central,
        project_id=project_id,
        form_id=form_id,
        deleted=True,
    )

    stats = {
        "form_id": form_id,
        "active_metadata_rows": len(active),
        "deleted_metadata_rows": len(deleted),
        "content_verified": 0,
        "content_skipped": 0,
        "verification_errors": 0,
        "zero_version_anomalies": 0,
        "deleted_observed": 0,
        "reasons_resolved_later": 0,
    }

    for metadata in active:
        logical_id = metadata.get("instanceId")
        if not logical_id:
            continue

        prior = ledger_submission_row(
            conn,
            project_id,
            form_id,
            logical_id,
        )

        # Migration/steady-state handling for Central's zero-version anomaly:
        # classify it explicitly from metadata without downloading XML again.
        if (
            metadata_current_version_id(metadata) is None
            and prior is not None
            and (
                prior["version_count"] == 0
                or prior["current_version_id"] is None
            )
        ):
            persist_zero_version_anomaly(
                conn,
                project_id=project_id,
                form_id=form_id,
                metadata=metadata,
                observed_at=observed_at,
            )
            stats["zero_version_anomalies"] += 1
            continue

        needs_verify, reason = needs_content_verification(
            prior,
            metadata,
        )

        if not needs_verify:
            conn.execute(
                """
                UPDATE submissions
                SET review_state = ?,
                    updated_at = ?,
                    deleted_at = NULL,
                    last_seen_at = ?,
                    status = 'VERIFIED_PRESENT'
                WHERE project_id = ?
                  AND form_id = ?
                  AND logical_id = ?
                """,
                (
                    metadata.get("reviewState"),
                    metadata.get("updatedAt"),
                    observed_at,
                    project_id,
                    form_id,
                    logical_id,
                ),
            )
            conn.commit()
            stats["content_skipped"] += 1
            continue

        print()
        print(
            f"[DELTA] {form_id} / {logical_id}: {reason}; "
            "content verification required"
        )

        if reason == "RESTORED_OR_REAPPEARED":
            insert_observation_event(
                conn,
                project_id=project_id,
                action="methodmesh.submission.restore_observed",
                observed_at=observed_at,
                actee_id=logical_id,
                details={
                    "form_id": form_id,
                    "logical_submission_id": logical_id,
                    "prior_status": (
                        prior["status"] if prior is not None else None
                    ),
                    "current_version_id":
                        metadata_current_version_id(metadata),
                },
            )
            conn.commit()

        try:
            with tempfile.TemporaryDirectory(
                prefix="methodmesh_ephemeral_",
                dir=str(ephemeral_parent),
            ) as temp_dir:
                temp_root = Path(temp_dir)

                result = probe_submission(
                    central=central,
                    project_id=project_id,
                    form_id=form_id,
                    submission_id=logical_id,
                    output_root=temp_root,
                    writeback=writeback,
                    update_review_state=update_review_state,
                    tsa_ca_file=tsa_ca_file,
                    tsa_cert_file=tsa_cert_file,
                    allow_tsa_download=allow_tsa_download,
                    desktop_tsa_url=desktop_tsa_url,
                )

                if not (result.get("versions") or []):
                    persist_zero_version_anomaly(
                        conn,
                        project_id=project_id,
                        form_id=form_id,
                        metadata=metadata,
                        observed_at=observed_at,
                    )
                    stats["zero_version_anomalies"] += 1
                else:
                    persist_verified_submission(
                        conn,
                        central=central,
                        project_id=project_id,
                        form_id=form_id,
                        metadata_before=metadata,
                        result=result,
                        ephemeral_root=temp_root,
                        observed_at=observed_at,
                    )
                    stats["content_verified"] += 1
        except Exception as exc:
            stats["verification_errors"] += 1
            conn.execute(
                """
                INSERT INTO submissions(
                    project_id, form_id, logical_id,
                    current_version_id, overall_status,
                    status, review_state, created_at, updated_at,
                    deleted_at, last_seen_at, verification_note
                )
                VALUES(?, ?, ?, ?, 'VERIFICATION_ERROR',
                       'ERROR', ?, ?, ?, NULL, ?, ?)
                ON CONFLICT(project_id, form_id, logical_id) DO UPDATE SET
                    overall_status = 'VERIFICATION_ERROR',
                    status = 'ERROR',
                    review_state = excluded.review_state,
                    updated_at = excluded.updated_at,
                    last_seen_at = excluded.last_seen_at,
                    verification_note = excluded.verification_note
                """,
                (
                    project_id,
                    form_id,
                    logical_id,
                    metadata_current_version_id(metadata),
                    metadata.get("reviewState"),
                    metadata.get("createdAt"),
                    metadata.get("updatedAt"),
                    observed_at,
                    str(exc),
                ),
            )
            conn.commit()
            print(
                f"ERROR verifying {form_id}/{logical_id}: {exc}",
                file=sys.stderr,
            )

    for metadata in deleted:
        if not metadata.get("instanceId"):
            continue
        mark_deleted_submission(
            conn,
            project_id=project_id,
            form_id=form_id,
            metadata=metadata,
            observed_at=observed_at,
        )
        stats["deleted_observed"] += 1

    stats["reasons_resolved_later"] = poll_unresolved_comments(
        conn,
        central=central,
        project_id=project_id,
        form_id=form_id,
        observed_at=observed_at,
    )

    return stats


# ---------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------

def main():
    global STREAM_ATTACHMENTS_WITHOUT_PERSISTING

    parser = argparse.ArgumentParser(
        description=(
            "Project-level MethodMesh delta verifier. ODK Central remains "
            "the research-data store. v13 persists only a non-data SQLite "
            "ledger and cryptographic/audit evidence; submission XML is "
            "processed in an ephemeral workspace and attachment bytes are "
            "streamed through SHA-256 and discarded."
        )
    )

    parser.add_argument(
        "--central",
        required=True,
        help="Central root URL, e.g. https://central.example.org",
    )
    parser.add_argument(
        "--project",
        required=True,
        type=int,
        help="Central project ID",
    )
    parser.add_argument(
        "--form",
        help=(
            "Optional XML form ID. If omitted, process every active form "
            "in the project."
        ),
    )
    parser.add_argument(
        "--token",
        default=os.getenv("ODK_TOKEN"),
        help="Central bearer token or ODK_TOKEN",
    )
    parser.add_argument(
        "--email",
        default=os.getenv("ODK_EMAIL"),
        help="Central user email or ODK_EMAIL",
    )
    parser.add_argument(
        "--password",
        default=os.getenv("ODK_PASSWORD"),
        help="Central password or ODK_PASSWORD",
    )
    parser.add_argument(
        "--writeback",
        action="store_true",
        help=(
            "Write verified desktop receipts back to Central and update "
            "reviewState where appropriate."
        ),
    )
    parser.add_argument(
        "--no-review-state",
        action="store_true",
        help="Skip Central reviewState updates even with --writeback.",
    )
    parser.add_argument(
        "--daily-checkpoint",
        action="store_true",
        help=(
            "Create a project-level non-data Merkle checkpoint, RFC3161 "
            "timestamp and auditor-safe ZIP."
        ),
    )
    parser.add_argument(
        "--probe-only",
        action="store_true",
        help=(
            "Run only DNS/TLS/HTTP/API/targeted-port service probes and "
            "append the observation to the safe local probe ledger."
        ),
    )
    parser.add_argument(
        "--probe-ports",
        default="80,443,22,5432,3000,8080,8443",
        help="Comma-separated explicit TCP ports to observe.",
    )
    parser.add_argument(
        "--expected-public-ports",
        default="80,443",
        help="Comma-separated ports expected to be publicly reachable.",
    )
    parser.add_argument(
        "--tsa-ca-file",
        help=(
            "Trusted TSA CA certificate bundle. Supply together with "
            "--tsa-cert-file, or omit both for pinned FreeTSA trust."
        ),
    )
    parser.add_argument(
        "--tsa-cert-file",
        help="Trusted TSA signer/intermediate certificate.",
    )
    parser.add_argument(
        "--no-tsa-download",
        action="store_true",
        help="Disable automatic pinned FreeTSA public-certificate download.",
    )
    parser.add_argument(
        "--desktop-tsa-url",
        default=DEFAULT_DESKTOP_TSA_URL,
        help="RFC3161 TSA endpoint for desktop receipts and daily roots.",
    )
    parser.add_argument(
        "--output",
        default="methodmesh_state",
        help=(
            "Persistent NON-DATA state directory. Contains SQLite ledger, "
            "public TSA trust material, service probes and checkpoints only."
        ),
    )
    parser.add_argument(
        "--auditor-export-dir",
        help=(
            "Directory for auditor-safe ZIPs. Defaults to sibling "
            "'<output>_auditor_exports'."
        ),
    )
    parser.add_argument(
        "--ephemeral-dir",
        help=(
            "RAM-backed directory used transiently for submission XML. "
            "On Linux /dev/shm is used automatically."
        ),
    )
    parser.add_argument(
        "--allow-disk-temp",
        action="store_true",
        help=(
            "DEVELOPMENT ONLY: allow ordinary OS temp storage if no RAM "
            "filesystem is available. Production default is fail-closed."
        ),
    )
    parser.add_argument(
        "--audit-overlap-minutes",
        type=int,
        default=5,
        help=(
            "Overlap when reading Central server audit events. Duplicate "
            "events are deduplicated by deterministic fingerprint."
        ),
    )

    args = parser.parse_args()

    central = Central(
        base_url=args.central,
        token=args.token,
        email=args.email,
        password=args.password,
    )

    state_root = Path(args.output).expanduser().resolve()
    state_root.mkdir(parents=True, exist_ok=True)

    # A v13 state directory must never be an old v12 raw archive.
    assert_safe_persistent_state(state_root)

    marker = state_root / "NON_DATA_STATE_ONLY.txt"
    marker.write_text(
        "METHODMESH v13 NON-DATA STATE\n"
        "=============================\n"
        "ODK Central is the authoritative research-data store.\n"
        "This directory must contain no submission XML, photos, attachments,\n"
        "CSV/XLS exports, or raw submission-answer JSON.\n"
        "Persistent contents are restricted to UUIDs, timestamps, field names,\n"
        "comments/reasons, cryptographic hashes, verification states, service\n"
        "metadata, public TSA certificates and cryptographic checkpoints.\n",
        encoding="utf-8",
    )

    ledger_path = state_root / "methodmesh_state.sqlite"
    conn = sqlite_connect(ledger_path)
    init_delta_ledger(conn)

    set_ledger_meta(conn, "central_url", args.central)
    set_ledger_meta(conn, "project_id", args.project)
    conn.commit()

    auditor_export_root = (
        Path(args.auditor_export_dir).expanduser().resolve()
        if args.auditor_export_dir
        else state_root.parent / f"{state_root.name}_auditor_exports"
    )

    def parse_port_list(raw: str) -> list[int]:
        result = []
        for part in str(raw).split(","):
            part = part.strip()
            if not part:
                continue
            port = int(part)
            if not 1 <= port <= 65535:
                raise ValueError(f"Invalid TCP port: {port}")
            if port not in result:
                result.append(port)
        return result

    probe_ports = parse_port_list(args.probe_ports)
    expected_public_ports = parse_port_list(
        args.expected_public_ports
    )

    current_probe = service_probe(
        central=central,
        project_id=args.project,
        ports=probe_ports,
        expected_public_ports=expected_public_ports,
    )
    append_service_probe(
        output_root=state_root,
        probe=current_probe,
    )

    if args.probe_only:
        print(pretty_json(current_probe))
        print(
            "Probe appended to: "
            f"{(state_root / '_service_probes.jsonl').resolve()}"
        )
        assert_safe_persistent_state(state_root)
        return

    ephemeral_parent = choose_ephemeral_parent(
        requested=args.ephemeral_dir,
        allow_disk_temp=args.allow_disk_temp,
    )

    orphan_count = cleanup_orphan_ephemeral_dirs(
        ephemeral_parent
    )
    if orphan_count:
        print(
            f"Removed {orphan_count} orphaned MethodMesh ephemeral "
            "workspace(s) from a previous interrupted run."
        )

    if (
        args.allow_disk_temp
        and ephemeral_parent == Path(tempfile.gettempdir()).resolve()
    ):
        print(
            "WARNING: --allow-disk-temp is active. This is intended only "
            "for development/testing; transient XML may touch ordinary "
            "temporary storage.",
            file=sys.stderr,
        )
    else:
        print(
            f"Ephemeral verifier workspace: {ephemeral_parent} "
            "(raw submission material is removed after each submission)"
        )

    # Persist pinned PUBLIC trust material outside the ephemeral workspace.
    trust = resolve_tsa_trust_material(
        attestation={
            "trusted_timestamp_authority": args.desktop_tsa_url
        },
        output_root=state_root,
        tsa_ca_file=args.tsa_ca_file,
        tsa_cert_file=args.tsa_cert_file,
        allow_tsa_download=not args.no_tsa_download,
    )
    if not trust.get("available"):
        raise RuntimeError(
            "TSA trust material unavailable: "
            f"{trust.get('reason')}"
        )

    effective_ca = trust["ca_file"]
    effective_tsa = trust["tsa_cert_file"]

    # The auditor-safe bundle expects public TSA trust material in a stable
    # local public-cert cache. If the operator supplied certificates, copy only
    # those public certificate files into that safe cache.
    export_trust_dir = state_root / "_tsa_trust" / "freetsa"
    export_trust_dir.mkdir(parents=True, exist_ok=True)
    ca_cache = export_trust_dir / "cacert.pem"
    tsa_cache = export_trust_dir / "tsa.crt"
    if Path(effective_ca).resolve() != ca_cache.resolve():
        shutil.copyfile(effective_ca, ca_cache)
    if Path(effective_tsa).resolve() != tsa_cache.resolve():
        shutil.copyfile(effective_tsa, tsa_cache)

    # In v13 attachment bytes are never persisted, even inside the transient
    # verifier workspace.
    STREAM_ATTACHMENTS_WITHOUT_PERSISTING = True

    now = datetime.now(timezone.utc)
    last_audit_raw = get_ledger_meta(
        conn,
        "last_audit_end_utc",
    )
    last_audit = parse_iso_strict(last_audit_raw)
    audit_start = (
        last_audit
        - timedelta(minutes=max(0, args.audit_overlap_minutes))
        if last_audit
        else now - timedelta(hours=24)
    )

    audit_delta = ingest_server_audit_delta(
        conn,
        central=central,
        project_id=args.project,
        start=audit_start,
        end=now,
    )
    pending_audit_end_utc = now.isoformat()

    set_ledger_meta(
        conn,
        "last_server_audit_available",
        audit_delta.get("available"),
    )
    set_ledger_meta(
        conn,
        "last_server_audit_complete",
        audit_delta.get("complete"),
    )
    set_ledger_meta(
        conn,
        "last_server_audit_events_read",
        audit_delta.get("events_read"),
    )
    conn.commit()

    if not audit_delta.get("available"):
        print(
            "WARNING: Central server-wide audit endpoint is unavailable "
            "to this account. System/form lifecycle audit provenance is "
            "INCOMPLETE.",
            file=sys.stderr,
        )
    elif audit_delta.get("complete") is not True:
        print(
            "WARNING: Central server-wide audit retrieval was incomplete. "
            "System/form lifecycle audit provenance is INCOMPLETE.",
            file=sys.stderr,
        )
    elif audit_delta.get("events_read", 0) == 0:
        print(
            "NOTICE: Central server-wide audit query returned zero events "
            "for this interval. This is recorded explicitly in the ledger."
        )

    active_forms, deleted_forms = project_forms_metadata(
        central=central,
        project_id=args.project,
        target_form=args.form,
    )

    observed_at = utc_now_iso()
    form_lifecycle_stats = persist_form_metadata(
        conn,
        project_id=args.project,
        forms=active_forms,
        deleted_forms=deleted_forms,
        observed_at=observed_at,
    )

    if active_forms:
        set_ledger_meta(
            conn,
            "representative_form_id",
            active_forms[0].get("xmlFormId") or "",
        )
        conn.commit()

    form_stats = []
    for form_meta in active_forms:
        form_id = form_meta.get("xmlFormId")
        if not form_id:
            continue

        print()
        print("#" * 78)
        print(f"FORM {form_id}")
        print("#" * 78)

        stats = process_form_delta(
            conn=conn,
            central=central,
            project_id=args.project,
            form_id=form_id,
            ephemeral_parent=ephemeral_parent,
            state_root=state_root,
            writeback=args.writeback,
            update_review_state=not args.no_review_state,
            tsa_ca_file=effective_ca,
            tsa_cert_file=effective_tsa,
            allow_tsa_download=False,
            desktop_tsa_url=args.desktop_tsa_url,
        )
        form_stats.append(stats)

        print(
            f"[FORM SUMMARY] metadata={stats['active_metadata_rows']} active, "
            f"{stats['deleted_metadata_rows']} deleted; "
            f"content verified={stats['content_verified']}; "
            f"unchanged skipped={stats['content_skipped']}; "
            f"zero-version={stats['zero_version_anomalies']}; "
            f"errors={stats['verification_errors']}"
        )

    run_stats = {
        "forms_examined": len(form_stats),
        "metadata_rows_examined": sum(
            x["active_metadata_rows"] + x["deleted_metadata_rows"]
            for x in form_stats
        ),
        "content_verified": sum(
            x["content_verified"] for x in form_stats
        ),
        "content_skipped": sum(
            x["content_skipped"] for x in form_stats
        ),
        "verification_errors": sum(
            x["verification_errors"] for x in form_stats
        ),
        "zero_version_anomalies": sum(
            x["zero_version_anomalies"] for x in form_stats
        ),
        "deleted_observed": sum(
            x["deleted_observed"] for x in form_stats
        ),
        "reasons_resolved_later": sum(
            x["reasons_resolved_later"] for x in form_stats
        ),
        "server_audit_events_read":
            audit_delta.get("events_read"),
        "server_audit_events_new":
            audit_delta.get("events_inserted"),
        "form_lifecycle_observations": form_lifecycle_stats,
    }

    print()
    print("=" * 78)
    print("DELTA RUN SUMMARY")
    print("=" * 78)
    print(f"Forms examined:           {run_stats['forms_examined']}")
    print(
        f"Submission metadata rows: {run_stats['metadata_rows_examined']}"
    )
    print(
        f"Content verified:         {run_stats['content_verified']}"
    )
    print(
        f"Unchanged content skipped:{run_stats['content_skipped']:>9}"
    )
    print(
        f"Zero-version anomalies:   {run_stats['zero_version_anomalies']}"
    )
    print(
        f"Deleted rows observed:    {run_stats['deleted_observed']}"
    )
    print(
        f"Verification errors:      {run_stats['verification_errors']}"
    )
    print(
        f"Server audit events read: {run_stats['server_audit_events_read']}"
    )
    lifecycle = run_stats.get("form_lifecycle_observations") or {}
    print(
        "Form lifecycle observations: "
        f"discovered={lifecycle.get('form_discovered', 0)}, "
        f"deleted={lifecycle.get('form_deleted_observed', 0)}, "
        f"restored={lifecycle.get('form_restored_observed', 0)}, "
        f"version_changed={lifecycle.get('form_version_changed', 0)}"
    )
    print(
        "Persistent research data: NONE "
        "(ledger contains metadata/hashes/reasons only)"
    )

    checkpoint = None
    if args.daily_checkpoint:
        checkpoint = create_delta_daily_checkpoint(
            conn=conn,
            central=central,
            project_id=args.project,
            state_root=state_root,
            current_probe=current_probe,
            tsa_ca_file=effective_ca,
            tsa_cert_file=effective_tsa,
            allow_tsa_download=False,
            tsa_url=args.desktop_tsa_url,
            run_stats=run_stats,
            auditor_export_root=auditor_export_root,
        )

        print()
        print("=" * 78)
        print("PROJECT DAILY CHECKPOINT")
        print("=" * 78)
        print(
            "Merkle root:      "
            f"{checkpoint.get('evidence_merkle_root')}"
        )
        print(
            "Checkpoint SHA:   "
            f"{checkpoint.get('daily_checkpoint_sha256')}"
        )
        print(
            "RFC3161:          "
            f"{checkpoint.get('timestamp_status')}"
        )
        if checkpoint.get("timestamp_time_iso"):
            print(
                "TSA time:         "
                f"{checkpoint.get('timestamp_time_iso')}"
            )
        export = checkpoint.get("auditor_export") or {}
        if export:
            print(
                "SAFE AUDITOR ZIP: "
                f"{export.get('zip_path')}"
            )

    # Normal runs should already have cleaned each TemporaryDirectory, but
    # perform a final prefix-limited scrub as a second line of defence.
    cleanup_orphan_ephemeral_dirs(ephemeral_parent)

    assert_safe_persistent_state(state_root)

    set_ledger_meta(
        conn,
        "last_audit_end_utc",
        pending_audit_end_utc,
    )
    set_ledger_meta(
        conn,
        "last_successful_run_utc",
        utc_now_iso(),
    )
    conn.commit()
    conn.close()

    print()
    print("Persistent state privacy check: PASS")
    print(f"Ledger: {ledger_path}")
    print("DONE")


if __name__ == "__main__":
    main()
