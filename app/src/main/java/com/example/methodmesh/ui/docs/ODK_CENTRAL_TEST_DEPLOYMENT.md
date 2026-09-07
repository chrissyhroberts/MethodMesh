# ODK Central rapid-test deployment

Status: UI-package implementation pass for MethodMesh v1.05.

## Purpose

The ODK Forms library can now act as a rapid capability-integration test bench as well as a design-template library.

A user configures one ODK Central server, one Project and one test App User under **Settings → ODK Central**. The Web User password is accepted only when creating a Central session and is not persisted. The resulting bearer session token is stored encrypted using Android Keystore-backed AES/GCM storage.

## Form-library semantics

The checkbox beside a module-owned XLSForm means:

> This form is deployed/published on the selected Central Project and available to the selected test App User.

Checking a form:

1. reads the packaged module XLSForm;
2. creates the form on Central when it is not already mapped;
3. otherwise creates/replaces the Draft when the local XLSForm has changed;
4. publishes the Draft using a generated `mm-<timestamp>` test version so rapid iterations do not fail simply because the module XLSForm version was not manually bumped;
5. assigns the Central `app-user` form role to the selected App User; if MethodMesh previously managed access for a different test App User, that previous MethodMesh-managed assignment is revoked first;
6. records the remote `xmlFormId` and SHA-256 of the deployed local XLSForm.

Unchecking a form **does not delete the form**. It revokes the selected App User's `app-user` role assignment for that form. This makes the form disappear from that tester's normal Collect form list while keeping the published form and submissions intact.

`Sync checked` redeploys checked forms whose local template has changed and reasserts the test App User assignment.

A separate **Remove** action moves the remote form to Central Trash. This is destructive and always requires confirmation. When available, the confirmation reports the current submission count. The local module-owned XLSForm is never deleted by this action.

## Template index

The generated template index may declare:

```json
{
  "centralFormId": "music_polyrhythm_test"
}
```

This is strongly recommended. It gives MethodMesh a stable way to rediscover a form after app reinstall or local deployment-state loss. If omitted, MethodMesh derives an XLSForm fallback form ID from the template ID. If the XLSForm itself declares a different `form_id`, Central's returned `xmlFormId` is recorded after the first successful upload.

## Authentication

This pass implements ordinary ODK Central Session Authentication:

- `POST /v1/sessions`
- `Authorization: Bearer <session token>`

The password is not stored. The current UI requires an HTTPS Central URL before accepting a password. Central servers configured exclusively for OpenID Connect/SSO do not support this login route and will need a future OAuth/OIDC-compatible connector.

## Central endpoints used

- list Projects;
- list App Users;
- list Forms with extended metadata;
- create XLSForm as Draft;
- create/replace a Draft on an existing form;
- publish Draft;
- assign/revoke `app-user` role on a Form;
- delete a remote Form to Central Trash.

## Architectural boundary

The current delivery is intentionally contained inside the supplied `ui/` package so it can be trialled with the UI glow-up. Before long-term admission, the HTTP/session/deployment classes should be promoted from `ui/odkcentral/` into an app-level shared ODK Central service package. The UI should then depend on that service interface rather than own network infrastructure.

No MethodMesh capability method IDs, inputs, outputs, ODK roundtrip transport contracts or module-owned XLSForms are changed by this feature.
