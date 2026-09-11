# Shared MethodMesh transport dependency

Paper Bridge's canonical dynamic return contract uses the original XLSForm variable name for every mapped field. An `image` variable may therefore be called `site_sketch`, `wound_photo`, or any other valid XLSForm name rather than ending in `_uri` or containing the word `image`.

The current shared `ReturnIntentProjector` classifies `content://` values as binary attachments partly from field-name heuristics. That is insufficient for dynamic capability contracts: a valid Paper Bridge image result such as `site_sketch=content://...` can be returned as a string extra without being placed in `ClipData`, so ODK cannot reliably take ownership of the bytes.

This is a shared transport issue, not a Paper Bridge naming issue. Paper Bridge must not rename user XLSForm fields or add module-specific logic to the shared shell.

Apply the companion patch `METHODMESH_shared_content_uri_attachment.patch` to the MethodMesh host. The generic change treats every returned `content://` value as a caller-readable attachment while retaining the ordinary flat string extra. It adds the URI to `ClipData` and therefore activates the existing `FLAG_GRANT_READ_URI_PERMISSION` path for arbitrary canonical field names.

This matches the Master Book rule that binary return plumbing is central and capability-agnostic. The patch also adds a transport regression test using the deliberately non-media-looking field name `site_sketch`.
