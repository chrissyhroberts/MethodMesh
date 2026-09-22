# Handoff: Web Actions ODK Enketo public-link failure

Date: 2026-09-13
Repo: `/Users/icrucrob/AndroidStudioProjects/MethodMesh`
Branch at time of handoff: `feature/esp-mesh-transport`
Area: `app/src/main/java/com/example/methodmesh/modules/webactions/`

## User-facing goal

MethodMesh Web Actions has been split into three visible hosted-form capabilities:

1. Kobo Enketo form
2. ODK Web Forms
3. ODK Enketo form

Kobo Enketo is working. ODK Web Forms is working. ODK Enketo still fails for Central Public Access `/f/...?...st=...` links generated while the Central form renderer is set to Enketo.

The user created two Central public links for the same form:

- One with Central's Enketo renderer enabled
- One with Central's new Web Forms renderer enabled

Both links have the same `/f/<token>?st=<public-access-token>` shape. That corrected an earlier wrong assumption: `/f` does not imply Web Forms. Central uses `/f` as the public-link shell for both renderer modes; the server/form setting decides the renderer behind the shell.

## Current behavior

For the ODK Enketo public link, MethodMesh shows “loading” and then a blank screen. Earlier it sometimes showed “Could not authenticate with the provided credentials.” After paste normalization, the error text often disappears but the screen still blanks.

Latest diagnostic pattern:

- Initial shell load succeeds.
- Log shows the public credential shape is now normal after paste normalization:
  - `queryNames=st`
  - `stLength=64`
  - `rawDollar=false`
  - `bang=true`
  - `fragmentPresent=false`
- Central page body initially has real content:
  - `bodyLength=322`
  - `bodyChildren=2`
  - `documentHeight=911`
- Then it collapses:
  - `bodyLength=8`
- The blanking happens after Central JavaScript runs.

Important HTTP diagnostics found:

- Before the latest workaround, ODK Enketo logged:
  - `resource-http status=401 url=https://central.lshtm.ac.uk/v1/sessions/restore`
  - sometimes also `resource-http status=401 url=https://central.lshtm.ac.uk/v1/form-links/<id>/form?st=[redacted]` when the pasted token was corrupted or too long.
- After URL paste normalization, the `st` length dropped from `194` or `10` to `64`, indicating the earlier token was being corrupted by pasted Markdown/wrapper text. The form-link credential request was no longer the primary obvious failure in the last clean run.
- A targeted workaround was added to intercept `/v1/sessions/restore` for ODK Enketo and return `{}` with HTTP 200. The user reports it did not fix the blank screen. Another chat should inspect the latest pasted log for the post-intercept behavior.

## Changes already made in this debugging thread

Files changed include:

- `WebActionsModule.kt`
- `WebActionsMethod.kt`
- `WebActionsCapabilityScreens.kt`
- `WebActionWebViewPool.kt`
- `CentralWebFormSupport.kt`
- `EnketoClient.kt`
- `docs/README_WebActions.md` from earlier work

### Split capabilities

Added method descriptors / visible capabilities for:

- `web.odk_webforms_roundtrip` — ODK Web Forms
- `web.odk_enketo_roundtrip` — ODK Enketo form
- `web.kobo_enketo_roundtrip` — Kobo Enketo form

Kept old `web.odk_central_roundtrip` as compatibility route.

### Refresh button

Added `Refresh form` next to `Kill + clear` in the full-screen web tray.

### Hosted-form WebView behavior

Broadened hosted-form handling from the old Central-only ID to all hosted-form method IDs.

Added safe diagnostic log tag:

```bash
adb logcat -s MethodMeshWebAction
```

Diagnostics include:

- redacted launch URL shape
- top-level navigation
- page start / page finish
- SSL errors
- main-frame HTTP/load errors
- ODK Enketo page metrics without page text
- console level/line/source only, not console text
- ODK Enketo subresource HTTP errors
- credential shape metadata, not credential value

Do **not** add diagnostics that log DOM text, console message text, or token values. Auto-review rejected that once because it could expose form data or credentials.

### URL paste normalization

`requireWebUrl()` in `EnketoClient.kt` now normalizes copied URLs before parsing:

- Extracts the target from Markdown-style links like `[https://...](https://...)`
- Extracts the first `https?://` URL from pasted text
- Trims surrounding delimiters and trailing punctuation

This fixed one real issue: `stLength` dropped from corrupted values like `194` or `10` to the expected-looking `64`.

### Public `/f` handling

`buildCentralLaunchUrl()` now preserves `CentralLinkKind.PUBLIC_ACCESS` URLs exactly. It returns the normalized original URL unchanged rather than parsing/rebuilding it or adding `return_url` / cache-buster values.

Reason: Central's `st` token is opaque and punctuation-sensitive.

### Chrome user agent toggle

Added `chrome_user_agent` setting and made it default on for ODK Enketo. The WebView user agent removes Android WebView markers (`; wv`, `Version/...`) when enabled.

Log showed `chromeUserAgent=true`, but this did **not** fix the blank screen.

### ODK Enketo light bootstrap

ODK Enketo gets a lighter bootstrap script than other hosted forms. It watches for submit/success and repairs SVG sizing but no longer performs the aggressive live-page cleanup that unregisters service workers or deletes Cache Storage inside the page.

This did **not** fix the blank screen.

### Session restore interception

Added ODK-Enketo-only WebView interception for:

```text
/v1/sessions/restore
```

It returns HTTP 200 with `{}` to prevent Central's unauthenticated-session check from collapsing the public flow. User reports this did not work.

Another chat should inspect the latest log after this change. Look for:

```text
intercept-session-restore
resource-http
console level=ERROR
odk-enketo-page-metrics
```

## Current hypothesis

The `/f` public-link shell loads, but Central's JavaScript path for Enketo public forms is failing inside Android WebView after the shell loads.

Evidence:

- Main frame load succeeds.
- No SSL or blocked navigation error.
- The token is now shape-valid (`stLength=64`).
- Chrome-style user agent does not fix it.
- Avoiding the aggressive cleanup script does not fix it.
- Intercepting `/v1/sessions/restore` alone does not fix it.

Possibilities:

1. Another Central API endpoint is failing after `/v1/sessions/restore`. Inspect latest post-intercept logs.
2. Central's public-link shell expects browser APIs/storage/cookies that WebView does not provide or that MethodMesh settings block.
3. The Enketo renderer launch URL is available in the `/v1/form-links/<id>/form?st=...` JSON response, and MethodMesh should bypass the Central SPA entirely by calling that endpoint itself and opening the returned Enketo URL directly.
4. The `/v1/form-links/.../form` JSON response may contain fields needed to construct/directly launch the Enketo URL. Add a safe MethodMesh-side resolver that calls this endpoint outside WebView, parses only URL-like fields, and never logs/stores the `st` token or response body.

## Suggested next steps

1. Read the latest post-intercept log pasted by the user:
   - `/Users/icrucrob/.codex/attachments/3dffcef6-6d1c-4111-a7bf-1d2e55059583/pasted-text.txt`
   - Check whether `intercept-session-restore` appears.
   - Check whether another `resource-http status=401/403/404/5xx` appears.

2. If session-restore is intercepted but the page still blanks, remove or gate that workaround if it is not useful.

3. Implement a proper **ODK Enketo public-link resolver**:
   - For ODK Enketo with `CentralLinkKind.PUBLIC_ACCESS`, call:
     ```text
     https://<host>/v1/form-links/<id>/form?st=<token>
     ```
     from MethodMesh using `HttpURLConnection`.
   - Do not log the token or response body.
   - Parse the JSON and identify the field containing the Enketo/webform launch URL.
   - Use that returned URL as the WebView launch URL.
   - Keep the original pasted URL as secret operational state only.
   - Redact all URL outputs as existing helpers do.

4. If the endpoint returns no direct URL, inspect the JSON structure locally in a safe one-off debug path. Do **not** commit logging of the full response if it may contain secrets.

5. Consider adding a user-visible warning on ODK Enketo if resolver fails:
   - “Central public link loaded, but MethodMesh could not resolve the Enketo launch URL. Try ODK Web Forms or open in external browser.”

6. Keep Kobo and ODK Web Forms paths unchanged. They are known working.

## Validation used so far

Repeated after each code change:

```bash
./gradlew :app:compileDebugKotlin
```

It passed after the latest changes before this handoff.

## Important security/privacy notes

- Do not log the `st` token.
- Do not log DOM text or console message text from forms.
- Do not log raw Central JSON response bodies unless the user explicitly approves and there is no safer path.
- Existing diagnostics redact URL query keys including `st`, `k`, and `return_url`.
- Credential-shape logging is acceptable because it logs only length and punctuation presence, not value.
