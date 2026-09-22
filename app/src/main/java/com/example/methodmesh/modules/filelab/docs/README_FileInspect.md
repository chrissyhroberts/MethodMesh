# Inspect file

`file.inspect` identifies a file from content before trusting its extension, computes whole-file SHA-256, performs bounded container/specialist inspection and exposes the evidence behind the identification.

**Version:** `0.7.0`
**Maturity:** Development
**Connectivity:** Offline

## Native lifecycle

The screen is the file instrument itself: choose/receive a file → live inspection → review/copy values → optional reader hand-off → **Commit inspection**. URI/name/serialized working state use `rememberSaveable` so ordinary activity recreation does not discard the operator's work.

## Detection model

Detection is deliberately evidence-weighted:

- strong magic/header signatures normally produce 95–100% confidence;
- structured textual markers (VCF, ADIF, GPX, KML, NMEA, TLE, Cabrillo, OpenRosa XForm, etc.) produce high confidence;
- extension-only specialist recognition is lower confidence and is identified as extension evidence;
- content/extension disagreements are surfaced as warnings.

A recognised format is not automatically described as deeply parsed. `filelab_inspection_depth` distinguishes `recognised`, `basic`, `structured` and `deep` inspection.


## Plain-language format explanation

After identification, `file.inspect` resolves the detected format ID through the bundled offline format knowledge catalogue. The live screen deliberately puts **What is this format?** ahead of specialist metadata. It shows a non-technical explanation first, followed by technical identity, typical uses, common producer/toolchain hints, File Lab support level, related formats and cautions.

**Often opened with** comes from the offline catalogue. **Open with** is queried from Android against the selected URI/MIME type and therefore lists only handlers installed on the current phone. Unknown/proprietary formats receive a neutral unknown-format explanation; File Lab never invents a purpose from an unfamiliar extension.

## Notable specialist coverage

The current detector covers broad families of archives/comics, ebooks/documents, image/audio/video media, scientific/research data, genomics, geospatial data, amateur-radio/SDR files, navigation logs, engineering/fabrication formats, executables, packet captures and firmware images. See `README_FileLab.md` for the grouped list.

Examples of deeper/bounded inspection include FITS header/WCS hints, ADIF counts, delimited-table structure, VCF/FASTA summaries, SigMF hints, ELF/PE basics, WAVE metadata, PCAP header facts, NumPy/NetCDF header facts and ZIP-family structure.

### CBR/RAR

RAR4 and RAR5 signatures are now detected. A `.cbr` with a RAR signature reports **CBR comic archive (RAR)** and can be handed to a compatible reader. File Lab does not currently bundle a RAR decompressor, so it does not falsely claim member enumeration or CBR→PDF conversion.

## Inputs

| Canonical input | Type | Required | Meaning |
|---|---|---:|---|
| `source_uri` | Android URI / ODK attachment projection | no | Existing caller-supplied file; native picker when absent. |
| `source_name` | text | no | Optional display-name hint. |

## Canonical returns

| Key | ODK type | Meaning |
|---|---|---|
| `filelab_inspection_summary` | text | Human summary. |
| `filelab_source_name` | text | Source display name. |
| `filelab_source_uri` | file | Acquired source only when MethodMesh picked it. |
| `filelab_format_id` | text | Stable detected format identifier. |
| `filelab_format_name` | text | Human-readable format name. |
| `filelab_mime_type` | text | MIME type when known. |
| `filelab_confidence` | integer/text | Detection confidence 0–100. |
| `filelab_size_bytes` | integer/text | Complete source size. |
| `filelab_sha256` | text | Complete-file SHA-256. |
| `filelab_inspection_depth` | text | `recognised`, `basic`, `structured` or `deep`. |
| `filelab_format_description` | text | Plain-English explanation of the detected format. |
| `filelab_format_technical_description` | text | Technical identity/container description. |
| `filelab_format_typical_uses` | text | Typical real-world uses. |
| `filelab_format_common_producers` | text | Pipe-separated common producer/toolchain hints. |
| `filelab_format_suggested_apps` | text | Pipe-separated software commonly used with the format; not an installed-app claim. |
| `filelab_related_formats` | text | Pipe-separated related format IDs. |
| `filelab_format_cautions` | text | Format-specific privacy/safety/interpretation caution when relevant. |
| `filelab_format_knowledge_json` | text/JSON | Complete offline knowledge-catalogue entry. |
| `filelab_facts_json` | text/JSON | File-specific facts. |
| `filelab_warnings_json` | text/JSON | Sampling/mismatch/safety warnings. |
| `filelab_evidence_json` | text/JSON | Why the detector selected the format. |
| `filelab_available_actions` | text | Pipe-separated native actions. |
| `filelab_inspected_time_iso` | text | Inspection time. |
| `filelab_inspect_status` | text | `succeeded` or `failed`. |
| `filelab_inspect_error` | text | Failure detail. |

The shared transport additionally returns `methodmesh_status` and `methodmesh_full_json` on handled ODK FULL roundtrips.

## ODK Integration Card

### ODK INTEGRATION

**Capability:** Inspect file
**Method ID:** `file.inspect`
**Maturity:** Development
**Connectivity:** Offline

### INTENT CALL

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='file.inspect',input_source_uri=${source_file},input_payload_mode='FULL',return_mode='flat')
```

`docs/example_odk_showcase_file_inspect.xlsx` contains exactly one invocation and unprefixed return leaves for every declared output plus `methodmesh_status` and `methodmesh_full_json`.

When MethodMesh acquired the source, `filelab_source_uri` is returned as a `file` attachment; when the caller already supplied the source, the field stays blank to avoid redundant duplication.
