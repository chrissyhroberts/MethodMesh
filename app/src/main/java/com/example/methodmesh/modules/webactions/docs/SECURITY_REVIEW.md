# Web Actions v0.10 security review

## Scope

This review covers `web.odk_central_roundtrip`, `web.precooked_enketo`, `web.roundtrip`, `web.open`, the embedded WebView boundary, the active-session repository, and ODK/MethodMesh payload handling.

## Secret inventory

### ODK Central

Public Central/web-form launch does not require an Enketo or Central API token. The pasted Central link is the authority for that run.

A copied Public Access Link can include `st=<secret>`. The raw pasted URL and the callback-injected launch URL are operational secrets for the lifetime of the transaction. Neither is returned as a Central canonical output.

### Enketo

The API token is an operational credential used only to create the single-submit session. It is excluded from the typed MethodSetting schema, `onSettingsChanged`, committed ExecutionRequest context, result fields, and audit JSON. The screen clears its in-memory token after successful session creation.

Prefill values may contain participant/study data. Resolved prefill values are sent to Enketo as required by the workflow but are not copied into result/audit fields.

### MethodMesh completion token

Each roundtrip receives a URL-safe token generated from 24 cryptographically random bytes. The callback endpoint is transaction-specific and token-specific.

## At-rest active-session protection

The transaction repository stores non-secret metadata in app-private SharedPreferences. Secret material (`callback_token`, `launch_url`, `original_url`) is serialized separately and encrypted with AES/GCM/NoPadding using a key generated/stored in Android Keystore.

The secret record is removed when the transaction is committed/returned, cancelled, times out, or fails preparation. This persistence exists so a legitimate web-form run can survive configuration/activity recreation; it is not intended as a history store.

## Callback acceptance

A transaction completes only when all of the following are true:

1. transaction exists;
2. transaction state is WAITING;
3. navigation is top-level in the WebView;
4. scheme is HTTPS;
5. host is the fixed MethodMesh callback host;
6. path contains the exact transaction UUID;
7. callback token is present and matches using `MessageDigest.isEqual`.

Replay after the first accepted callback is rejected by state.

A callback proves that the configured return URL was reached. It does not independently prove a server-side Central/Enketo submission receipt; audit records explicitly report `submission_receipt_verified=false` for those workflows.

## URL redaction

Safe URL output redacts fragments and query keys including:

- `st`
- `k`
- `return_url`
- `d[...]`
- `defaults[...]`
- `prefill[...]`
- common token/secret/password/auth/API-key/session/signature/callback names

The Central method goes further: it does not output the source URL at all, only host, classified link kind and SHA-256 of the source URI in audit metadata.

## Network/WebView boundary

- HTTPS required by default.
- HTTP requires explicit user/configuration opt-in.
- WebView mixed content is `MIXED_CONTENT_NEVER_ALLOW`.
- SSL errors are cancelled.
- `file://` access and file-URL cross-origin access are disabled.
- Android `content://` access is enabled only so platform file chooser results can be read by the web form.
- external schemes are restricted to `mailto`, `tel`, `sms`, and `geo`.
- geolocation requests are delegated to Android permission handling and are accepted only when the requesting origin host equals the active transaction launch host.
- third-party cookies are disabled where Android supports that control.

The Enketo API client does not follow redirects, preventing its Basic Authorization header from being implicitly forwarded to a redirect target.

## Preset caveat

The canonical result path never leaks a Central `st` token. However, if a user deliberately fixes a token-bearing Central URL in a MethodMesh preset, that preset must necessarily persist the URL in order to run it later. The capability UI/docs recommend keeping such a URL as a runtime input unless stored-link access is intentional.

## Residual risks requiring integration testing

- Central/Enketo behavior can vary by deployed version and authentication configuration.
- WebView file/camera chooser behavior varies by Android/WebView version and installed picker apps.
- geolocation requires the host MethodMesh app to declare appropriate Android location permissions.
- authenticated Central web-form sessions depend on normal web cookies/login state inside the MethodMesh WebView.
- a malicious or compromised remote form origin can see values intentionally sent to that origin; MethodMesh cannot make a remote web form trustworthy.

- Single-submit normalization changes only the transient encrypted launch URL; the original public link remains the canonical setting. Access tokens such as `st` are preserved operationally but remain excluded from outputs.

## v0.10 disposable Central browser state

The Central kiosk intentionally trades offline persistence for a cleaner online-only collection boundary. WebView cache/form storage is cleared at start and teardown and browser-managed WebStorage is deleted. This reduces the chance that provider autosave state survives into a later MethodMesh run. Cookies are not globally deleted because authenticated Central Data Collector links may rely on their login session.
