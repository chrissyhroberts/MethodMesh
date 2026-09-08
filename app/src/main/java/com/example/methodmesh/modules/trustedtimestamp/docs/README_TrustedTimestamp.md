# Trusted timestamp

## Capability

`integrity.trusted_timestamp`

**Maturity:** Development

**Connectivity:** Online only

Creates an RFC 3161 proof that exact bytes existed no later than a Timestamp Authority-certified time.

SHA-256 is computed locally. The source content itself is not sent to the TSA. The RFC 3161 request contains the message imprint and protocol metadata.

The proof establishes existence of the exact bytes **no later than** the trusted timestamp. It does not establish authorship, original creation time, truth, photographic authenticity or legal validity.

## Canonical contract

### ODK inputs

ODK may provide either source directly:

- `input_text` — exact UTF-8 text from a form field; or
- `input_file` — a file attachment from an earlier ODK step.

If both source inputs are omitted, MethodMesh opens the polished native capability surface in external-roundtrip mode and lets the operator either:

- choose a file with the MethodMesh/Android document picker; or
- enter exact text in MethodMesh.

Optional modifiers:

- `input_tsa_url` — RFC 3161 endpoint override; blank/missing uses `https://freetsa.org/tsr`;
- `input_timeout_ms` — network timeout, clamped to 1000–30000 ms; default `10000`.

Exactly one source is timestamped. Supplying both `input_text` and `input_file` is an error.

### ODK outputs

On a handled successful roundtrip ODK receives:

- `trusted_timestamp_proof_uri` — **the proof ZIP as a real ODK attachment**. The `_uri` suffix is a historical stable contract name; ODK must not be left with the raw MethodMesh cache URI;
- `methodmesh_status` — shared MethodMesh transport status;
- `methodmesh_full_json` — shared complete metadata/audit JSON.

If **ODK supplied** `input_text` or `input_file`, MethodMesh does **not** return a duplicate source.

If **MethodMesh acquired the source on ODK's behalf**, it additionally returns exactly one of:

- `trusted_timestamp_source_uri` — the picked source as a real ODK attachment; or
- `trusted_timestamp_source_text` — text entered in MethodMesh.

All canonical supporting outputs remain addressable through the MethodMesh capability contract even when an example XLSForm captures only the beef and shared metadata envelope.

### Runtime inputs

- file selected in the capability UI or supplied through the runtime/protocol context;
- exact text entered in the capability UI or supplied by preset/runtime context;
- TSA URL;
- timeout.

### Runtime outputs

The runtime screen is one coherent toolkit dashboard, not a second generic result page:

1. **Source** — selected file or exact text;
2. **Trusted timestamp proof** — proof ZIP;
3. **Technical details** — timestamp/hash/TSA/trust data, available without displacing the two primary artefacts;
4. **metadata JSON** — optional runtime salad when requested.

Every displayed scalar/text result is tap-to-copy. File results expose meaningful Share/Save/Export actions rather than raw URI text.

## ODK Integration Card

### Capability

Trusted timestamp

`integrity.trusted_timestamp`

**Tags:** Development · Online only

### ODK inputs

| Canonical input | ODK type | Required | Meaning |
|---|---|---:|---|
| `input_text` | text | source A | Exact text to timestamp. |
| `input_file` | file | source B | File attachment from an earlier ODK step. |
| `input_tsa_url` | text | no | Optional TSA endpoint override. |
| `input_timeout_ms` | integer | no | Optional timeout, 1000–30000 ms. |

`input_text` and `input_file` are mutually exclusive. Leave both blank to use MethodMesh interactive acquisition.

### Copyable intent calls

Direct text:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='integrity.trusted_timestamp',input_text=${source_text},input_tsa_url=${tsa_url},input_timeout_ms=${timeout_ms},return_mode='flat',input_payload_mode='FULL')
```

Direct file:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='integrity.trusted_timestamp',input_file=${source_file},input_tsa_url=${tsa_url},input_timeout_ms=${timeout_ms},return_mode='flat',input_payload_mode='FULL')
```

Interactive MethodMesh acquisition:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='integrity.trusted_timestamp',input_tsa_url=${tsa_url},input_timeout_ms=${timeout_ms},return_mode='flat',input_payload_mode='FULL')
```

### Modifiers

| Modifier | Type | Meaning |
|---|---|---|
| `input_tsa_url` | text | TSA endpoint override. |
| `input_timeout_ms` | integer | Request timeout. |
| `input_payload_mode='FULL'` | shared transport | Request the complete shared execution/audit envelope. |
| `methodmesh_return_namespace` | shared transport | Optional prefix when a flat return map would otherwise collide. Not required merely because the same leaf names are used in separate XLSForm groups. |

### Canonical returns

| Canonical key | ODK type | Returned | Meaning |
|---|---|---|---|
| `trusted_timestamp_proof_uri` | file | successful call | Proof ZIP attachment. Historical `_uri` key retained for compatibility. |
| `trusted_timestamp_source_uri` | file | interactive picker only | Source attachment acquired by MethodMesh. |
| `trusted_timestamp_source_text` | text | interactive text entry only | Source text acquired by MethodMesh. |
| `trusted_timestamp_status` | text | handled call | Capability status. |
| `trusted_timestamp_original_name` | text | success | Source display name. |
| `trusted_timestamp_size_bytes` | integer/text | success | Exact byte count. |
| `trusted_timestamp_sha256` | text | success | SHA-256 of source bytes. |
| `trusted_timestamp_authority` | text | success | TSA identity. |
| `trusted_timestamp_time_iso` | text | success | TSA-certified timestamp. |
| `trusted_timestamp_serial` | text | success | RFC 3161 serial. |
| `trusted_timestamp_policy_oid` | text | success | TSA policy OID. |
| `trusted_timestamp_token_sha256` | text | success | SHA-256 of timestamp token. |
| `trusted_timestamp_trust_status` | text | success | Registry/trust evaluation. |
| `trusted_timestamp_proof_filename` | text | success | Human-facing proof ZIP filename. |
| `trusted_timestamp_full_json` | text/JSON | runtime optional | Capability-owned metadata JSON; contains no private cache URI/path. |
| `trusted_timestamp_error` | text | failure | Failure explanation. |
| `methodmesh_status` | text | ODK handled call | Shared transport status. |
| `methodmesh_full_json` | text/JSON | ODK handled call | Shared complete metadata/audit envelope. |

### XLSForm field placement

Return leaves may reuse the exact canonical names inside different intent groups. ODK disambiguates them by full group path. For example:

```text
direct_text_result/
    trusted_timestamp_proof_uri
    methodmesh_status
    methodmesh_full_json

direct_file_result/
    trusted_timestamp_proof_uri
    methodmesh_status
    methodmesh_full_json
```

`methodmesh_return_namespace` remains available for flat projections, but is not required for the grouped pattern above.

### File return semantics

`trusted_timestamp_proof_uri` and `trusted_timestamp_source_uri` are transport-bearing canonical keys. The shared MethodMesh return layer uses `ClipData` and read grants so ODK imports the bytes into attachment-compatible fields. The ODK-visible answer is the file attachment, not an obscure MethodMesh cache URI or private filesystem path.

## Proof ZIP

The portable evidence is one ordinary ZIP:

```text
<source_name>_proof_of_existence.zip
```

Every proof ZIP includes:

```text
README.txt
proof.json
timestamp.tsq
timestamp.tsr
tsa-signing-cert.pem    # when available
tsa-root.pem            # when available
verify.sh
verify.ps1
```

The source is not duplicated inside the proof ZIP, whether it was file or text. The source remains Part 1 of the result; the proof/TSA ZIP is Part 2.

The ZIP is kept in app cache only as a transient working copy. Native Save/Share/Export acts on the ZIP bytes themselves. ODK external-roundtrip mode suppresses native persistence actions and returns the attachment to ODK on Commit.

## Native workflow

1. Choose **file** or **text**.
2. Optionally adjust TSA URL and timeout.
3. Create the proof.
4. Review **1 · Source** and **2 · Trusted timestamp proof** on the same capability dashboard.
5. Tap any scalar/text detail to copy it.
6. Use Share/Save/Export for file artefacts.
7. Commit the captured result.

Changing source/TSA/timeout after a proof is created invalidates the working proof so the next Commit cannot silently describe stale inputs.

## Presets and protocols

The canonical method remains `integrity.trusted_timestamp` for dashboard, direct, preset, protocol and ODK use.

Preset/runtime text may be supplied through `input_text`. File URIs/paths may be piped through `input_file` where the caller/runtime can represent them. Normal direct native use should use the in-tool picker rather than asking the user to type a URI.

## Portable metadata

`trusted_timestamp_full_json`, when requested for runtime use, is a compact capability-owned metadata record. It deliberately identifies the proof attachment by filename rather than leaking a private cache URI/path.

The ZIP's `proof.json` is the richer portable evidence manifest.

ODK always captures the shared `methodmesh_full_json` envelope in the reviewed XLSForm patterns.

## Permissions and services

Network access is required to obtain a new timestamp and configured certificate material.

File selection and explicit durable saving use Android Storage Access Framework. The generated proof ZIP uses the app's existing `FileProvider` only as inter-app transport. No broad storage permission is required.

Default TSA: FreeTSA (`https://freetsa.org/tsr`).

## Online / offline behaviour

**Online only** for creation of a new trusted timestamp. Hashing is local, but the core operation requires an independent TSA response. Existing proof ZIPs are standards-based and can be inspected or verified later without MethodMesh.

There is no fabricated offline timestamp fallback.

## Known limitations

- Maturity remains **Development** until explicitly promoted after build/device/ODK validation.
- A dedicated in-app proof-verification method is not yet exposed.
- Custom TSA endpoints can be cryptographically consistent without being independently trusted.
- TSA certificate rotation requires registry maintenance.
- The proof concerns exact bytes and certified time, not broader truth or legal meaning.
