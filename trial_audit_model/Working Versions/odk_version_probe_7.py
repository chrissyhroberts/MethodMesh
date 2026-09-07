#!/usr/bin/env python3
"""
ODK Central MethodMesh sequence verifier + desktop receipt classification/writeback.

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
import copy
import getpass
import hashlib
import io
import json
import os
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote
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
# Trusted original anchor and desktop receipt/writeback
# ---------------------------------------------------------------------

DESKTOP_RECEIPT_SCHEMA = "methodmesh.desktop_audit_receipt.v1"
DESKTOP_VERIFIER_ID = "odk_version_probe_v8_receipt_classification"


def original_anchor_status(
    methodmesh: dict,
    reconstruction: dict | None,
) -> tuple[str, dict | None]:
    """
    Establish the one recipe/hash anchor that later versions are allowed to use.

    Critically, later Enketo-calculated digest fields, payload hashes,
    canonical payload strings, and copied attestation fields are NOT trusted.

    The anchor is accepted only from the original retained version when:
      - a MethodMesh attestation is present;
      - the raw commitment recipe hashes to commitment_recipe_sha256;
      - recipe-driven source reconstruction completes;
      - all original indirect stored digests agree with source reconstruction;
      - reconstructed payload SHA-256 equals the attested event_payload_hash;
      - the attestation's timestamp binding fields are internally consistent;
      - MethodMesh reports the timestamp verification as rfc3161_verified.

    This script still does not independently verify the ECDSA signature or
    RFC3161 token bytes. That remains a separate cryptographic-verifier layer.
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

    if methodmesh.get("tsa_attests_attestation_hash") is not True:
        return "ORIGINAL_TSA_BINDING_UNRESOLVED", None

    if methodmesh.get("trusted_timestamp_status") != "rfc3161_verified":
        return "ORIGINAL_TSA_NOT_VERIFIED", None

    anchor = {
        "recipe": copy.deepcopy(recipe),
        "recipe_sha256": methodmesh.get("commitment_recipe_sha256"),
        "event_payload_hash": attested,
        "attestation_hash": methodmesh.get("attestation_hash"),
        "trusted_timestamp_status":
            methodmesh.get("trusted_timestamp_status"),
        "trusted_timestamp_time_iso":
            methodmesh.get("trusted_timestamp_time_iso"),
        "trusted_timestamp_attested_hash":
            methodmesh.get("trusted_timestamp_attested_hash"),
        "public_key_id": methodmesh.get("public_key_id"),
        "original_reconstructed_payload_sha256": rebuilt,
    }

    return "INDEPENDENTLY_VERIFIED_ATTESTED_VERSION", anchor


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



def validate_receipt_core(
    receipt: dict | None,
    *,
    recipe_sha256: str | None,
) -> tuple[bool, str]:
    """
    Validate the self-contained structure of a desktop audit receipt.

    This does not by itself establish that the receipt is a trustworthy
    checkpoint. find_latest_valid_receipt_checkpoint() additionally requires
    the receipt's writeback version to exist in the immutable Central history
    and to reconstruct to the payload hash claimed by the receipt.
    """
    if not isinstance(receipt, dict):
        return False, "NO_RECEIPT"

    if receipt.get("schema") != DESKTOP_RECEIPT_SCHEMA:
        return False, "UNSUPPORTED_RECEIPT_SCHEMA"

    supplied_hash = receipt.get("receipt_sha256")
    if not supplied_hash:
        return False, "RECEIPT_SELF_HASH_ABSENT"

    core = dict(receipt)
    core.pop("receipt_sha256", None)

    if receipt_self_hash(core) != supplied_hash:
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

    return True, "VALID_RECEIPT_CORE"


def validate_existing_receipt(
    receipt: dict | None,
    *,
    current_version_id: str,
    reconstructed_payload_sha256: str,
    recipe_sha256: str | None,
) -> tuple[bool, str]:
    """
    Validate a receipt specifically as the receipt for the current version.
    """
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

    return True, "VALID_CURRENT_RECEIPT"


def find_latest_valid_receipt_checkpoint(
    version_summary: list[dict],
    *,
    trusted_anchor: dict | None,
) -> dict | None:
    """
    Find the latest trustworthy desktop receipt checkpoint in retained history.

    A copied receipt in a later human edit is not treated as a new checkpoint.
    The checkpoint is the historical version named by writeback_version_id.

    To qualify:
      - receipt self-hash/schema/recipe binding must validate;
      - writeback_version_id must exist in retained Central history;
      - that exact historical version must itself contain the same receipt;
      - that writeback version must independently reconstruct from the trusted
        original recipe;
      - reconstructed payload SHA-256 must equal the receipt claim.

    This means a checkpoint is grounded in immutable historical source data,
    not merely in a copied JSON receipt field.
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
        receipt = (
            (carrying_item.get("methodmesh") or {})
            .get("desktop_audit_receipt")
        )

        ok, reason = validate_receipt_core(
            receipt,
            recipe_sha256=trusted_anchor.get("recipe_sha256"),
        )
        if not ok:
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
    Create one receipt covering every previously unseen retained version.

    The current/source version is the final item in verified_sequence. The
    receipt is written as a new Central version and cryptographically
    self-hashed. The prior receipt hash is linked when a previous desktop
    checkpoint exists.
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
            # Stored calculated digest fields are diagnostic only and are
            # deliberately not a prerequisite for verification.
            "stored_matches_source": member.get("stored_matches_source"),
        })

    prior_receipt = (
        prior_checkpoint.get("receipt")
        if prior_checkpoint is not None
        else None
    )

    core = {
        "schema": DESKTOP_RECEIPT_SCHEMA,
        "verifier": DESKTOP_VERIFIER_ID,
        "verification_result": verification_result,
        "verification_basis": "trusted_recipe_plus_retained_sources",
        "observed_at_utc": utc_now_iso(),
        "logical_submission_id": logical_submission_id,

        # The source/current version whose data are copied into the new
        # desktop writeback version.
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
        },

        "note": (
            "Desktop verification reconstructs every previously unseen "
            "retained version from the original bound commitment recipe plus "
            "actual retained source values and attachment bytes. "
            "Enketo-calculated digest fields are never trusted."
        ),
    }

    receipt = dict(core)
    receipt["receipt_sha256"] = receipt_self_hash(core)
    return receipt


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
    version_summary: list[dict],
    current_runtime: dict,
    current_metadata: dict,
    writeback: bool,
    update_review_state: bool,
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
            action["review_state_action"] = "WOULD_SET_APPROVED"
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
            action["review_state_action"] = "WOULD_SET_APPROVED"
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

    action["receipt_action"] = "WOULD_CREATE_SEQUENCE_RECEIPT"
    action["planned_writeback_version_id"] = new_version_id
    action["planned_receipt"] = receipt

    if not writeback:
        action["review_state_action"] = "WOULD_SET_APPROVED_AFTER_WRITEBACK"
        return action

    if find_xml_element(
        current_runtime["root"],
        "desktop_audit_receipt_json",
    ) is None:
        action["receipt_action"] = "ABORT_NO_DESKTOP_RECEIPT_FIELD"
        if update_review_state:
            set_review_state(central, base, "hasIssues")
            action["review_state_action"] = "SET_HAS_ISSUES"
        return action

    # Immediately before mutation, prove that the server's current XML and
    # attachment bytes are still the same bytes that were verified.
    state_ok, state_reason = server_current_matches_archive(
        central=central,
        base=base,
        expected_version_id=current_id,
        archived_xml_bytes=current_runtime["xml_bytes"],
        archived_attachments=current_runtime["attachments"],
    )
    action["pre_writeback_server_check"] = state_reason

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

    action["receipt_action"] = "SEQUENCE_RECEIPT_WRITTEN"
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
            "  TSA status:     "
            f"{methodmesh.get('trusted_timestamp_status') or '-'}"
        )
        print(
            "  TSA binding:    "
            f"{methodmesh.get('tsa_attests_attestation_hash')}"
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
        == "INDEPENDENTLY_VERIFIED_ATTESTED_VERSION"
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
            version_summary=version_summary,
            current_runtime=current_runtime,
            current_metadata=metadata,
            writeback=writeback,
            update_review_state=update_review_state,
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
        if writeback_action.get("pre_writeback_server_check"):
            print(
                "  Race check:     "
                f"{writeback_action.get('pre_writeback_server_check')}"
            )

    return result


# ---------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description=(
            "Verify every unseen ODK Central version using the original MethodMesh "
            "commitment recipe plus retained source values. Optionally write "
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
            "v7 directory so v8 reuses previously archived immutable XML, "
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
