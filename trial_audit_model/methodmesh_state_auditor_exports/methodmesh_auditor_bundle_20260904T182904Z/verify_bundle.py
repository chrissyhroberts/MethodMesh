#!/usr/bin/env python3
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
