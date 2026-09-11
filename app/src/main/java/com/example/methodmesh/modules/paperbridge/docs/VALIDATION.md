# Paper Bridge validation

**Module:** `paperbridge`  
**Maturity:** Development  
**Connectivity:** Offline  
**Canonical methods:** `paper.form.transcribe`, `paper.form.design`

This file records the review state against the MethodMesh Master Book. A checked architecture item means the module code/documentation implements the contract; device/integration items remain explicitly unchecked where they require the host app, ODK Collect, Central or Kobo.

## Canonical module contract

- [x] Module is self-contained under one `paperbridge/` root and implements `MethodMeshModule`.
- [x] Both method IDs remain stable and independently exposed through `as100Methods()` and `capabilityScreens()`.
- [x] Dashboard/direct use is an additional surface, not a separate implementation.
- [x] Presets/protocols/ODK invoke the same canonical methods.
- [x] Explicit maturity/connectivity metadata is `Development` / `Offline`.
- [x] No PaperBridge-specific special case has been added to the shared shell.

## Transcription lifecycle

- [x] Working scan/review state is mutable before Commit.
- [x] Commit is blocked while review items remain unresolved.
- [x] Commit freezes stable + dynamic canonical fields.
- [x] Commit does not automatically save an archive/output copy.
- [x] Native committed state stays on the capability screen and exposes Share, Save, optional full JSON/audit, Done and Edit.
- [x] Displayed scalar/text committed results are tap-to-copy.
- [x] Dynamic image values are presented as attachments rather than raw URI text.
- [x] External/ODK Commit returns immediately to caller and suppresses native post-Commit actions.
- [x] External/ODK Commit does not write Paper Bridge recent-activity state or a MethodMesh output-folder copy.
- [x] Caller-supplied source images are not gratuitously duplicated back to the caller; acquired sources and new derivatives can return as attachments.
- [x] `paper_attachment_metadata_json` is URI-free and carries roles/hashes/registration metadata.
- [x] Committed payload is saveable/restorable across ordinary Activity recreation.

## AprilTag8 registration

- [x] New schemas default to `tag36h11` APRILTAG8 IDs 0..7 at TL/TC/TR/LC/RC/BL/BC/BR.
- [x] Detector uses tag identities and tag corners as correspondences.
- [x] Robust projective registration rectifies once into canonical page coordinates before ROI extraction.
- [x] Registration validates tag count/spatial distribution and reprojection diagnostics.
- [x] Legacy QR4/BULLSEYE4 remain supported.
- [x] Bundled prepared forms contain all eight distinct AprilTags.
- [x] ROI geometry is not repaired with per-field scan offsets.
- [ ] Repeat camera tests on representative low/mid/high-end Android devices, varied distance/angle/lighting and normal paper curl.

## Designer lifecycle

- [x] Lifecycle is SAVE SCHEMA → TEST EMPTY → TEST DATA → COMMIT.
- [x] Save schema is an explicit persistent schema action and does not masquerade as result Commit.
- [x] Tests are tied to the unchanged saved design; editing invalidates stale test completion.
- [x] Commit freezes the design return payload.
- [x] Native committed state exposes Share, Save, optional full JSON/audit, Done, Edit design and Copy template JSON.
- [x] External/ODK Commit returns directly to caller.
- [x] Destructive schema deletion requires confirmation.
- [x] Meaningful in-progress designer state is saveable: source identity, ODK schema, mappings, loose regions, controls, registration config and test state.
- [x] Large bitmap/derived visual state is reloaded/recomputed from the saved source identity after ordinary Activity recreation rather than stored in Bundle state.
- [x] Committed design payload is separately saveable/restorable.
- [ ] Exercise rotation/recreation on a target Android build during source loading, mapping, after TEST EMPTY, after TEST DATA and after Commit.

## Native Share/Save contract

- [x] Commit itself does not imply export persistence for transcription.
- [x] Share defaults to the useful human result plus relevant media and places every `content://` attachment in `EXTRA_STREAM`/`ClipData` with explicit read grants.
- [x] Save targets the shared MethodMesh Files/ArtifactStore using persistent artifacts grouped in one collection.
- [x] Full structured JSON/audit is optional rather than replacing the human result.
- [x] Design Share/Save includes generated JSON/YAML/bundle/design-image artefacts.
- [ ] On a target Android device, confirm a multi-attachment transcription Share delivers exactly one registered form image, each dynamic image field once, and the transcription once as ordinary share text (not a `.txt` attachment) to at least two receiving apps; the raw source page must not be duplicated into native Share/Save.
- [ ] On a target Android device, confirm Save immediately exposes every collection member in the top-level MethodMesh Files surface and that Files can export/share the collection bundle.

## ODK/XLSForm contract

- [x] Module owns two canonical example XLSForms: one invocation for transcription and one for design.
- [x] Examples use grouped `body::intent` calls and canonical method IDs.
- [x] Examples request `input_payload_mode='FULL'` and `return_mode='flat'`.
- [x] Examples use canonical unprefixed return names and omit `methodmesh_return_namespace`.
- [x] Both examples capture `methodmesh_status` and `methodmesh_full_json`.
- [x] Attachment-capable returns use XLSForm `image`/`file` question types.
- [x] `ODK_INTEGRATION.md` documents exact inputs, intent calls, modifiers, canonical returns, attachments and closeout semantics for both capabilities.
- [x] Dynamic manifest values return under the manifest/XLSForm field names as well as the stable JSON projections.
- [x] Paper Bridge itself does not rename dynamic image fields to accommodate transport heuristics.
- [ ] Apply the generic shared transport patch described in `SHARED_FRAMEWORK_DEPENDENCY.md` in the target MethodMesh host so arbitrary dynamically named `content://` returns become real attachments.
- [ ] Validate both canonical XLSForms with the project's normal XLSForm validator / Central / Kobo pipeline.
- [ ] Execute the device round-trips in `ODK_ROUNDTRIP_TEST.md` and verify submission media.

## Shared-framework dependency

The only identified cross-module change is generic Android return transport handling for dynamically named binary outputs. The module ships the requirement/documentation only; the proposed core patch is delivered separately from the clean module root. It treats a returned `content://` value as a binary attachment regardless of its field-name spelling while preserving the normal string extra and read permission grant. This is generic transport behaviour, not Paper Bridge special casing.

## Build / integration

- [x] Module-local AprilTag/manifest fixtures and example forms are present.
- [x] Documentation reflects current Commit/Share/Save/ODK behaviour.
- [x] Handoff excludes a whole-app tree.
- [ ] Run `:app:compileDebugKotlin`/tests after inserting this module and shared transport patch into the current MethodMesh host.
- [ ] Exercise dashboard, direct capability, native preset, protocol step and ODK launch origins on the integrated app.

Paper Bridge therefore remains **Development** until the unchecked host/device validations are completed; those are test gates, not hidden implementation gaps inside this module.

### XLSForm logic / manual review

- [x] XLSForm importer retains per-field relevance, dynamic required, raw constraint and constraint message; enclosing group relevance is inherited.
- [x] Common deterministic XLSForm expressions are evaluated after extraction and after every manual edit; missing/unmapped references and unsupported functions return an explicit unknown state rather than a guessed pass.
- [x] False relevance with a populated value, required blank, and supported constraint failure are review-blocking. False relevance with a blank value is treated as not applicable.
- [x] Relevant fields expose confirmed **Set NA**, including already accepted values; image NA removes the image attachment from the committed media set and records the override instead.
- [x] Confirmed NA writes `na`, bypasses the ordinary field constraint only through an explicit override, and is auditable.
- [x] Relevant automatically accepted scalar/choice/code values can be reopened with **Edit**. Every actual manual value change is appended to standalone `paper_manual_edits_json` with before/after logic state; the same events are embedded in `paper_extraction_audit_json`.
- [x] Stable outputs expose logic checks, violation/unknown counts and NA field list.
- [ ] Device-test a form with direct relevance, inherited group relevance, dynamic requiredness, `selected()`, numeric constraint, regex constraint, an unsupported expression, a manual edit and an NA override.


## Share payload regression

- [ ] Share the built-in example with full JSON off to WhatsApp (or another receiver that previously duplicated captions): confirm beef appears once, the registered form appears once and the cropped image field appears once.
- [ ] Repeat with **Include full JSON / audit** on: confirm the same beef/media payload plus exactly one `metadata.json` attachment.
- [ ] Confirm Save to MethodMesh Files remains unchanged.


## v0.5.5 beef + pictures regression

- [ ] Share built-in example with full JSON OFF.
- [ ] Receiving app gets exactly one plain-text transcription.
- [ ] Receiving app gets the registered data-form image once.
- [ ] Receiving app gets each cropped media/image field once.
- [ ] No media item contains a second Paper Bridge text payload generated via
      ClipData.
- [ ] Save to MethodMesh Files remains unchanged.
