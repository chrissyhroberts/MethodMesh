# Digital signing

Capability: `document.sign_pdf`  
Status: **Development**

Digital Signing opens an existing PDF as a full-screen document surface, lets the operator pan/zoom and add freehand coloured ink, then commits a new PDF without overwriting the source. The document opens in a true full-screen dialog above the dashboard/preset host, so its size is independent of the capability card measurement. It uses a cover-style viewport: drag to pan, pinch to zoom, tap the page counter to jump directly to a page, and toggle a compact corner Ink palette without giving up document real estate. The committed file is SHA-256 hashed and can optionally be sent to an RFC 3161 Time Stamp Authority (TSA).

The visible mark is an **electronic handwritten ink/markup layer**. It is not a certificate-backed/PAdES signer-identity signature. An RFC 3161 timestamp attests the committed file hash/time, not the identity of the person who drew the ink.

## Native workflow

1. Open `Digital signing`.
2. Choose a PDF with the Android document picker, or continue a recovered draft.
3. The PDF occupies the full capability surface.
4. With **Ink off**, hold and drag with one finger to pan and pinch to zoom.
5. Toggle **Ink** from the floating corner control to write. Toggle it off again to navigate.
6. Use the floating colour palette, width control, Undo and Erase tools for generic freehand markup.
7. Press **Done** to reveal the small finish overlay.
8. Choose Standard or Finalised output and optionally request a trusted timestamp.
9. Press **Commit**. MethodMesh creates **Deliverable A — Signed PDF** and **Deliverable B — Provenance ZIP**, then immediately returns both to the normal MethodMesh result handler for Share/Save/close-out. There is no second capability result card.

The source SHA-256 can be tapped in the document overlay to copy it.

## PDF input

The PDF can come from either route:

- **Direct/native:** Android `OpenDocument` picker (`application/pdf`).
- **ODK/external/protocol:** `input_pdf_uri` / `pdf_uri` supplied as an Android URI. MethodMesh immediately copies the readable input into its own files area so the working draft does not depend on temporary caller URI permissions.

When a PDF URI is supplied by an ODK launch, the document opens directly without a redundant picker step.

## Draft persistence

The active PDF, current page and completed ink strokes are stored under the module's private app files area. A process restart can recover an unfinished draft. Commit state is persisted before optional TSA network work begins, so timestamp failure or interruption cannot discard the exact committed PDF.

If a committed result is recovered after interruption, MethodMesh completes any pending timestamp/bundle work and returns the result to the caller/handler.

## Standard vs Finalised

### Standard

The source page appearance is recreated into a new PDF and handwritten ink is drawn into the committed page content.

### Finalised

The complete committed appearance, including ink, is raster-flattened page by page. This removes editable form/annotation structure and makes accidental modification of existing elements difficult.

Finalisation is **not DRM**. A capable PDF editor can still rewrite the document or add new content. Integrity is verified by comparing the committed SHA-256 and, where used, the RFC 3161 timestamp token.

ODK and other automatic external-return launches force Finalised mode.

## Trusted timestamp

The TSA endpoint is preconfigured exactly as in MethodMesh's attestation capability pattern. The built-in default is:

```text
https://freetsa.org/tsr
```

`input_tsa_url` remains an advanced/preset override, but a normal user does not need to enter a URL before timestamping works.

If `input_request_tsa=true`, MethodMesh:

1. commits/finalises the PDF;
2. calculates SHA-256 over the exact committed output bytes;
3. persists the committed result locally;
4. sends an RFC 3161 `application/timestamp-query` request to the configured/default TSA;
5. validates the response against the request;
6. checks the token message imprint against the signed PDF SHA-256;
7. locates the included signer certificate and validates the timestamp token signature;
8. reports whether the signer certificate was valid at the token generation time;
9. creates Deliverable B, a portable provenance ZIP containing the PDF copy and timestamp evidence.

If TSA is unavailable, the PDF commit still succeeds. Deliverable B records the failure and omits the raw token.

## Deliverables

- **Deliverable A — Signed PDF:** the primary signed/marked-up document. This is always the first media result.
- **Deliverable B — Provenance ZIP:** companion evidence containing the committed PDF copy, hashes, TSA material and independent verification instructions.

The provenance ZIP is supporting evidence and should not replace or visually compete with Deliverable A in the normal result flow.

## Provenance ZIP

Every successful Commit creates `digital_signing_verification_bundle_uri`, the **Deliverable B provenance ZIP** companion to the Deliverable A signed PDF. It contains:

- the exact committed signed PDF;
- `manifest.json` with document/source hashes and TSA metadata;
- `tsa.json` with the complete timestamp result;
- `timestamp-token.tst` when an RFC 3161 token was obtained;
- `SHA256SUMS.txt`;
- `VERIFY.txt` with step-by-step SHA-256 and OpenSSL verification commands and an explanation of the TSA trust-chain requirement.

The ZIP itself is SHA-256 hashed and returned as `digital_signing_verification_bundle_sha256`.

The raw timestamp token is the DER-encoded RFC 3161 CMS token. `VERIFY.txt` explains how to inspect it with `openssl ts -reply -token_in` and verify it against the bundled PDF with `openssl ts -verify -token_in` using an independently trusted TSA certificate chain.

## Inputs

- `input_pdf_uri` — input PDF URI. ODK/external callers normally supply this; direct native use can use the file picker.
- `input_finalise_pdf` — request Finalised mode. ODK forces this true regardless of a false input.
- `input_request_tsa` — `true` to request RFC 3161 attestation after Commit.
- `input_tsa_url` — optional RFC 3161 endpoint override. Blank/missing values fall back to the baked-in default TSA.
- `input_pen_width_pt` — ink width, 0.8–12 pt.
- `input_pen_color` — `black`, `blue`, `red`, `green`, `purple`, `orange`, `teal`, or `magenta`.

## Main outputs

- `digital_signing_signed_pdf_uri` — **Deliverable A:** FileProvider URI of the new committed signed PDF.
- `digital_signing_signed_sha256` — SHA-256 of the exact committed PDF.
- `digital_signing_verification_bundle_uri` — **Deliverable B:** provenance ZIP containing the signed PDF copy, TSA evidence and verification instructions.
- `digital_signing_verification_bundle_sha256` — SHA-256 of the ZIP.
- `digital_signing_result_json` — structured signing/provenance record, including TSA and bundle objects.
- `digital_signing_tsa_json` — TSA-only JSON object, always present (`requested=false`/`not_requested` when unused).

Additional audit fields:

- `digital_signing_status`
- `digital_signing_signed_pdf_name`
- `digital_signing_verification_bundle_name`
- `digital_signing_source_sha256`
- `digital_signing_source_origin`
- `digital_signing_page_count`
- `digital_signing_ink_present`
- `digital_signing_ink_stroke_count`
- `digital_signing_finalised`
- `digital_signing_finalisation_mode`
- `digital_signing_committed_time_iso`
- `digital_signing_tsa_status`
- `digital_signing_tsa_time_iso`
- `digital_signing_tsa_authority`
- `digital_signing_error`

## ODK/XLSForm workflow

Use an XLSForm `begin_group` Android intent. The PDF question supplies its Android URI as `input_pdf_uri`. The TSA URL does not need to be supplied unless the study deliberately overrides the MethodMesh default.

Example intent:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='document.sign_pdf',caller='odk_collect',input_pdf_uri=${source_pdf},input_finalise_pdf='true',input_request_tsa=${request_tsa},input_payload_mode='FULL',return_mode='flat')
```

The module includes `docs/example_odk_document.sign_pdf.xlsx`.

For ODK, both `digital_signing_signed_pdf_uri` and `digital_signing_verification_bundle_uri` are media results. `digital_signing_result_json` contains the complete signing record and `digital_signing_tsa_json` contains the complete TSA record. The generic MethodMesh return layer grants returned `_uri` attachments to the caller.

## Presets and protocols

The capability remains the same canonical `document.sign_pdf` method when launched directly, from a preset, or as a protocol step. Fixed preset configuration is honoured by the shared runtime; the document itself remains the interactive operator surface because inking is inherently interactive. A prior protocol step may pipe a `pdf_uri` into this capability.

## Dependencies

The PDF viewer/commit engine uses Android platform APIs (`PdfRenderer` and `PdfDocument`) only. It does **not** require PDFBox or Material icon libraries.

RFC 3161 support uses the Bouncy Castle dependencies already present in the MethodMesh app build (`bcprov-jdk18on` and `bcpkix-jdk18on`).

## Known limitations

- No certificate/PAdES signer identity signature.
- No multi-signer workflow.
- Password-protected/encrypted PDFs may not open through Android `PdfRenderer`.
- Page recreation is appearance-preserving, not a byte-preserving incremental PDF edit; interactive source forms/annotations are flattened.
- Finalisation does not prevent a capable editor adding new markup; subsequent byte changes are detected through SHA-256/TSA verification.
- TSA requires network connectivity and a compatible RFC 3161 endpoint.
- Independent cryptographic trust of a TSA requires a trust chain obtained/validated according to the verifier's own policy; an embedded signer certificate is not by itself a trust anchor.
