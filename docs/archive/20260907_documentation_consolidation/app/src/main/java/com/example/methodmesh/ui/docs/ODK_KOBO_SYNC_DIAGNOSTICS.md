# ODK / Kobo form-sync diagnostics

Iteration 8 hardens the rapid XLSForm test library in two places.

## Kobo untick semantics

The Kobo checkbox means whether the mapped Kobo project is actively deployed for KoboCollect.

- Tick: import/update the module-owned XLSForm and activate its deployment.
- Untick: PATCH the existing Kobo v2 deployment to `active=false` and verify the server reports it inactive.
- Remove from Kobo: permanently delete the remote project after destructive confirmation.

Unticking therefore preserves the remote project and submissions.

## Form upload diagnostics

Upload/sync errors are attached to the exact form and provider that failed. The Forms page shows:

- provider;
- HTTP status;
- server/API error code when supplied;
- human-readable server message;
- request method and path;
- structured `details` / conversion warnings / validation errors returned by the provider;
- raw response as a fallback;
- `Copy report` for pasting a complete diagnostic into a development chat or issue.

ODK Central XLSForm conversion warnings are deliberately not suppressed. Central normally rejects conversion warnings unless `ignoreWarnings=true`; MethodMesh keeps the strict default and surfaces the warning/error instead of silently forcing deployment.

ODK Central does not itself provide comprehensive ODK Validate validation of every XForm rule. A future repository-level integration may add ODK Validate as a preflight validator; this UI package currently reports the complete Central conversion/API response and Kobo import/API response without adding a new app dependency.
