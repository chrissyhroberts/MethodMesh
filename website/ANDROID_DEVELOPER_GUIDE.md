# MethodMesh Android Developer Guide

This guide describes the current canonical Android implementation. The specification documents remain authoritative when this guide and a specification disagree.

## Project structure

The Android application lives under `app/src/main/java/com/example/methodmesh/`.

- `core/methodmesh/` contains canonical architecture objects such as `Entity`, `Observation`, `Signal`, `Transformation`, `Relationship`, `ExecutionRequest`, and `ExecutionResult`.
- `core/methodmesh/runtime/` contains the canonical method contract and execution engine.
- `modules/` contains self-contained capability modules.
- `platform/` contains Android and device-service boundaries.
- `transport/` contains RIL parsing, Android intent routing, graph selection, and return formatting.
- `ui/` contains the Compose dashboard and shared presentation components.

There is one execution model. The former flat `Method`, `MethodRequest`, `MethodResult`, and adapter runtime no longer exist.

## Capability modules

Each capability implements `MethodMeshModule` and may expose:

- canonical `As100Method` implementations;
- RIL phrase bindings;
- focused capability screens;
- declared module dependencies;
- module-owned examples.

Modules are discovered from standalone `*Module` objects under the modules package. The runtime does not maintain a capability-specific central registry: adding a module, its screen, or its documentation should not require edits to core infrastructure.

Capabilities may invoke other capabilities through their public invocation boundary. They must consume canonical results and must not copy a dependency's implementation. For example, QR-backed attestation invokes `qr.scan` and consumes its returned evidence.

## Canonical execution

A method receives an `ExecutionRequest` and returns an `ExecutionResult`. Results contain canonical graph objects and provenance. Successful results are recorded through `ResearchRuntime`.

The external execution sequence is:

```text
Android intent or RIL request
  -> canonical method ID resolution
  -> focused capability screen
  -> required device-service or dependency interaction
  -> ExecutionResult
  -> compact selected return
  -> calling application
```

An external invocation starts immediately. A capability may pause only for required permission, authentication, capture, consent, or method input. Dashboard launches remain manual and may expose configuration controls.

## Android intent contract

The exported action is:

```text
com.example.methodmesh.EXECUTE_METHOD
```

The method selector is:

```text
method_id='<canonical method ID>'
```

ODK multi-field calls use the action in the `body::intent` cell and `field-list`
in the group appearance. Capability inputs use the generic `input_` namespace;
MethodMesh removes that prefix before presenting the settings to the module.
Unprefixed child fields are outputs and are never interpreted as capability
configuration.

Example:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='attestation.create',input_event_payload_hash=${form_payload_hash},input_verification_method='Fingerprint',input_trusted_timestamp='preferred',return_mode='flat')
```

Static structured strings that are unsafe or unwieldy in XLSForm intent syntax
may use `input64_<name>`, containing URL-safe Base64 without padding. MethodMesh
decodes the UTF-8 value and exposes it to the module as `<name>`.

Transport keys such as `method_id`, `return_mode`, `returns`, and core invocation
context remain unprefixed. Old method aliases, unprefixed capability settings,
and old transport keys are not accepted.

## RIL transport

RIL is the canonical internal request representation. Direct transport uses:

- `ril` for a complete RIL request; or
- `method_id` plus `input_`-prefixed method settings;
- `returns` for graph selectors;
- `return_mode` for the requested shape.

RIL phrases are owned by modules. Phrase resolution returns the module's canonical method ID. Unknown actions remain unresolved and must not be silently redirected.

## Android app interoperability

The `android_app_inspector` module provides a safe discovery and test harness for public Android integration points. It can:

- list installed launchable applications;
- inspect exported activities, services, receivers, and providers;
- probe common public actions, categories, and URI schemes;
- target an exported activity explicitly;
- send an action, optional URI, and simple string extras;
- capture result code, returned URI, and returned extras;
- save a tested package/component/action/URI/extras combination locally as an integration definition.

The Android public package APIs do not expose every raw manifest intent-filter or undocumented extra. The inspector therefore reports safe resolution probes and supports explicit testing of component/action combinations discovered from application documentation, source, or other authorised references. It does not bypass non-exported components, permissions, authentication, or private implementation details.

The `bluetooth_device_inspector` module provides the corresponding hardware discovery path. It scans nearby BLE devices, connects to a selected device, enumerates GATT endpoints, reads readable characteristics, listens for notifications where supported, reports paired classic-Bluetooth serial candidates, and can save a profile to the central device registry. Transport-specific details remain behind the device-service boundary so later capabilities consume normalized device signals rather than owning Bluetooth code.

## Direct-run output export

When a capability is run from the MethodMesh dashboard, the shared result scaffold provides an **Export** action. Export writes one timestamped JSON record containing the selected return fields and a manifest of any linked attachments. Image, SVG, and other files are copied alongside the JSON using a common base name, so an exported record can be moved as a self-contained bundle. The default location is the app's public `Documents/MethodMesh/outputs` folder; the global Output storage panel can select a different Storage Access Framework folder. Callers such as ODK/Kobo continue to receive attachments through the normal return contract rather than relying on dashboard exports.

## Returns

The default caller return is intentionally compact:

- execution ID, method ID, and status;
- relevant invocation identifiers when supplied;
- the scientific or operational result;
- the minimum provenance required to interpret or verify it;
- failure diagnostics only when execution fails.

Canonical graph objects and full provenance remain in the MethodMesh graph. Explicit graph selectors may request particular canonical fields. The return formatter does not reproduce deleted flat-model aliases.

## Dashboard organization

The capability dashboard is a manual test and inspection surface, not a second capability registry. Capabilities remain standalone modules, but the dashboard groups them by owning module and provides a search filter so a large installed set can be navigated without scrolling through every method. This presentation layer does not add dependencies between modules or change external intent behavior.

## Attestation contract

`attestation.create` requires:

- `event_payload_hash`: exactly 64 hexadecimal SHA-256 characters;
- `verification_method`: one of `Fingerprint`, `Pin`, `Qr`, `Nfc`, or `Password`.

`trusted_timestamp` is optional and accepts only:

- `disabled`;
- `preferred`;
- `required`.

MethodMesh signs the supplied form hash directly. Raw `event_payload` input and automatic double-hash detection are not supported.

Attestation schema version 4 returns the public key, signature, chain link,
hash-only verification evidence, and optional full RFC 3161 evidence.
Fingerprint, QR, and NFC verification use their respective capability or
device-service boundaries. Raw QR, NFC, and study-token credentials are consumed
transiently and are not placed in the attestation record or caller return.

## Adding a capability

1. Create a folder under `modules/<capability>/`.
2. Implement one or more canonical `As100Method` objects.
3. Define method descriptors and contracts, including required context and produced graph outputs.
4. Create a focused `CapabilityScreenSpec` when interaction is required.
5. Expose the methods, screens, RIL bindings, and dependencies from a `MethodMeshModule` object.
6. Add unit tests for method output, RIL resolution, return formatting, and invalid requests.
7. Verify dashboard and external invocation modes separately.

Do not add an adapter, alias, second result model, direct hardware call from a method, or module-specific transport parser.

## Verification

Run:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Device-dependent behavior such as camera, NFC, biometric prompts, location, and ODK round trips must also be tested on an Android device.
