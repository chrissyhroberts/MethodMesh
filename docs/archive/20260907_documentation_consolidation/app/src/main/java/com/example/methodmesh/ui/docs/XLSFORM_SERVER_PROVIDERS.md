# XLSForm server providers and collection clients

Status: MethodMesh v1.05 UI iteration 6.

## Goal

The module-owned XLSForm library is server-neutral. A single local XLSForm design template can be projected to more than one collection backend without duplicating the form inside MethodMesh.

Current rapid-test providers:

1. **ODK Central** — deploy/publish to a selected Central Project and grant a selected App User access.
2. **KoboToolbox** — import/update the XLSForm as a Kobo asset and activate/deactivate it for KoboCollect.

The local module `docs/example_odk*.xlsx` remains authoritative in both cases.

## ODK Central semantics

The **ODK** checkbox means “available to the selected Central test App User”.

- check: upload/update → publish → assign App User;
- uncheck: revoke that App User assignment only;
- permanent remote removal: separate confirmed action.

This preserves the remote form and submissions when rapidly switching test forms on and off.

## KoboToolbox semantics

The **Kobo** checkbox means “active for KoboCollect on the configured Kobo account”.

- check: import the XLSForm if new, or import into the mapped asset if changed, then activate/deploy;
- uncheck: set the remote deployment inactive;
- permanent remote removal: separate confirmed action.

Unticking Kobo is deliberately non-destructive. It keeps the remote asset and submissions so the same module form can be toggled in and out of a test device repeatedly.

Kobo API authentication uses the account API token. The current UI can retrieve that token from `/token/?format=json` using a transient username/password login; the password is never stored and the returned token is encrypted at rest with Android Keystore-backed AES/GCM.

The Kobo v2 API uses `Authorization: Token …`; current public Kobo documentation places forms under `/api/v2/assets/` and XLSForm imports under `/api/v2/imports/`.

## Provider independence

The two checkboxes are independent. A template may be:

- local only;
- ODK only;
- Kobo only;
- active on both.

Changing one provider must not mutate the deployment state of the other.

`Share` and `Save` always operate on the local XLSForm template and are independent of server deployment.

## Collection-client adapters

Do not hard-code the entire XLSForm ecosystem into the form library. Server deployment and Android invocation are separate concerns.

A later generic collection-client layer should describe installed clients by package/intent contract, for example ODK Collect-family clients, KoboCollect and other compatible collectors. Survey123 should be treated as a future provider/invocation adapter rather than special-cased into ODK transport.

The desired shape is:

```text
module docs/example_odk*.xlsx
          |
          v
   XLSForm catalogue
      /         \
 ODK Central   KoboToolbox       server deployment adapters
      |             |
 ODK Collect    KoboCollect       Android collection clients
```

Additional providers should be adapters around the same catalogue, not additional copies of the XLSForms.

## Architectural boundary

The current implementation lives under `ui/odkcentral/` and `ui/kobo/` because this handoff is constrained to the shared UI package. At repository integration, the HTTP/session/deployment code should migrate to a shared service layer while the UI retains only provider state/actions.

No capability method ID, input, output, preset/protocol contract, or ODK roundtrip contract is altered by adding Kobo deployment.
