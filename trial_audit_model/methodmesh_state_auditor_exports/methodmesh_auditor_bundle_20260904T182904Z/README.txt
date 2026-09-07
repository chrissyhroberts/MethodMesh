METHODMESH AUDITOR-SAFE BUNDLE
================================

This ZIP is a deliberately shareable presentation/proof package generated
from the non-data MethodMesh verifier state.

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
- Project bundles exclude other projects' project lists, user lists,
  assignments and native audit events.
- Installation-wide analytics SYSTEM aggregates may be included, but the
  analytics per-project array is restricted to this selected project.

The persistent verifier state remains at:
/Users/icrucrob/AndroidStudioProjects/MethodMesh/trial_audit_model/methodmesh_state

That state is designed to contain no submission XML, photographs, attachment
bytes, CSV/XLS exports or raw submission-answer JSON. It can contain operational
metadata, UUIDs, hashes, timestamps, field names, comments/reasons, service
observations and cryptographic checkpoints.

START HERE
----------
Unzip this package and open:

    index.html

The dashboard is fully offline. It has no external JavaScript, CSS, fonts,
images, CDN dependencies or calls back to ODK Central.

The front page is intentionally a compact GUIDED AUDITOR VIEW. It does not
render every submission. Current findings link to plain-English pages under
issues/. Complete machine evidence remains in background JSON/checkpoint files
and is independently verifiable with verify_bundle.py.

SYSTEM VALIDATION
-----------------
Open system.html for aggregate server/platform validation statistics.

OPTIONAL TECHNICAL VERIFICATION
-------------------------------
The completed ZIP is created only after MethodMesh self-verification succeeds.
A routine auditor does not need to run a command.

After extracting the ZIP, run the verifier from the BUNDLE ROOT — the directory
containing index.html, system.html and verify_bundle.py:

    python3 ./verify_bundle.py

Alternatively, from any other working directory:

    python3 ./path/to/extracted-bundle/verify_bundle.py

The verifier locates CHECKSUMS.sha256, CHECKPOINT.json, MERKLE_LEAVES.json,
tsa/ and trust/ relative to its own file location. Central credentials are not
required.

Schema: methodmesh.auditor_export.v1
Generated: 2026-09-04T18:29:21.223977+00:00
