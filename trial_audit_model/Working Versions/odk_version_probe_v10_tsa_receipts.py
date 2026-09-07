#!/usr/bin/env python3
"""
ODK Central MethodMesh cryptographic verifier + RFC3161 desktop receipt writeback.

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
  - stores Central diffs and audit events;
  - archives the exact published ODK Form Version used by each submission
    version, including XForm XML, original XLSForm where available, and
    version-specific form attachments;
  - reports zero-version submissions explicitly.

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
import subprocess
import sys
import tempfile
import uuid
from datetime import datetime, timezone
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
DESKTOP_VERIFIER_ID = "odk_version_probe_v10_tsa_receipts"
DEFAULT_DESKTOP_TSA_URL = "https://freetsa.org/tsr"


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
        data, response = central.get_bytes(
            f"{base}/versions/{encoded_version}"
            f"/attachments/{encoded_filename}"
        )

        destination = output_dir / Path(filename).name
        destination.write_bytes(data)

        observed_hash = sha256_bytes(data)

        item.update({
            "bytes": len(data),
            "sha256": observed_hash,
            "etag": response.headers.get("ETag"),
            "content_type": response.headers.get("Content-Type"),
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

    # Diffs and audits are properties of the logical submission.
    diffs = central.get_json(f"{base}/diffs")
    (submission_dir / "diffs.json").write_text(
        pretty_json(diffs),
        encoding="utf-8",
    )

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
            "diff_count": len(diffs),
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
        "diff_count": len(diffs),
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

        if diffs:
            print(f"  PASS: Central returned {len(diffs)} diff record(s)")
        else:
            print("  NOTE: Central returned no diff records")

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
# Main
# ---------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description=(
            "Cryptographically verify the original MethodMesh ECDSA/RFC3161 "
            "attestation, verify every unseen ODK Central version, and RFC3161-"
            "timestamp each new desktop audit receipt using "
            "the signed recipe plus retained source values. Optionally write "
            "desktop audit receipts back as new versions and set Central "
            "reviewState=approved."
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
        required=True,
        help="ODK XML form ID",
    )
    parser.add_argument(
        "--submission",
        help=(
            "Optional logical/original submission instanceId. "
            "If omitted, probe all current logical submissions."
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
        "--tsa-ca-file",
        help=(
            "Trusted TSA CA certificate bundle. Must be supplied together "
            "with --tsa-cert-file. If omitted for FreeTSA, the verifier "
            "uses pinned official certificate downloads/cache."
        ),
    )
    parser.add_argument(
        "--tsa-cert-file",
        help=(
            "Trusted TSA signer/intermediate certificate. Must be supplied "
            "together with --tsa-ca-file."
        ),
    )
    parser.add_argument(
        "--no-tsa-download",
        action="store_true",
        help=(
            "Disable automatic pinned FreeTSA certificate download. "
            "Useful for fully offline verification when explicit trust "
            "files are supplied."
        ),
    )
    parser.add_argument(
        "--desktop-tsa-url",
        default=DEFAULT_DESKTOP_TSA_URL,
        help=(
            "RFC3161 TSA endpoint used to timestamp new desktop receipt "
            "hashes. Default: https://freetsa.org/tsr"
        ),
    )
    parser.add_argument(
        "--writeback",
        action="store_true",
        help=(
            "Enable server mutation: write desktop audit receipts as new "
            "submission versions and update Central review state. Without "
            "this flag the script is a dry-run."
        ),
    )
    parser.add_argument(
        "--no-review-state",
        action="store_true",
        help=(
            "With --writeback, skip PATCHing Central reviewState. "
            "Receipt writeback still occurs."
        ),
    )
    parser.add_argument(
        "--output",
        default="odk_version_probe_v7_sequence_writeback",
        help=(
            "Audit/cache directory. The default intentionally remains the "
            "v7 directory so v10 reuses previously archived immutable XML, "
            "attachments and form versions instead of downloading them again."
        ),
    )

    args = parser.parse_args()

    central = Central(
        base_url=args.central,
        token=args.token,
        email=args.email,
        password=args.password,
    )

    output_root = Path(args.output)
    output_root.mkdir(parents=True, exist_ok=True)

    project = path_part(args.project)
    form = path_part(args.form)

    if args.submission:
        submission_ids = [args.submission]
    else:
        submissions = central.get_json(
            f"/v1/projects/{project}/forms/{form}/submissions"
        )

        submission_ids = [
            item["instanceId"]
            for item in submissions
        ]

        (output_root / "submissions.json").write_text(
            pretty_json(submissions),
            encoding="utf-8",
        )

    results = []

    for submission_id in submission_ids:
        try:
            results.append(
                probe_submission(
                    central=central,
                    project_id=args.project,
                    form_id=args.form,
                    submission_id=submission_id,
                    output_root=output_root,
                    writeback=args.writeback,
                    update_review_state=not args.no_review_state,
                    tsa_ca_file=args.tsa_ca_file,
                    tsa_cert_file=args.tsa_cert_file,
                    allow_tsa_download=not args.no_tsa_download,
                    desktop_tsa_url=args.desktop_tsa_url,
                )
            )
        except Exception as exc:
            print(
                f"\nERROR probing {submission_id}: {exc}",
                file=sys.stderr,
            )
            results.append({
                "logical_submission_id": submission_id,
                "error": str(exc),
            })

    summary = {
        "central": args.central,
        "project_id": args.project,
        "form_id": args.form,
        "submission_count": len(results),
        "generated_at_utc": utc_now_iso(),
        "writeback_enabled": args.writeback,
        "review_state_updates_enabled": not args.no_review_state,
        "cryptographic_verification_required": True,
        "tsa_auto_download_enabled": not args.no_tsa_download,
        "tsa_ca_file": args.tsa_ca_file,
        "tsa_cert_file": args.tsa_cert_file,
        "desktop_tsa_url": args.desktop_tsa_url,
        "desktop_receipts_require_rfc3161": True,
        "results": results,
    }

    (output_root / "summary.json").write_text(
        pretty_json(summary),
        encoding="utf-8",
    )

    print()
    print("=" * 78)
    print("DONE")
    print("=" * 78)
    print(f"Output: {output_root.resolve()}")


if __name__ == "__main__":
    main()
