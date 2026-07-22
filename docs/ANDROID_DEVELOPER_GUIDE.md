# ResearchOS Android Developer Guide

This guide describes the current canonical Android implementation. The specification documents remain authoritative when this guide and a specification disagree.

## Project structure

The Android application lives under `app/src/main/java/com/example/researchos/`.

- `core/researchos/` contains canonical architecture objects such as `Entity`, `Observation`, `Signal`, `Transformation`, `Relationship`, `ExecutionRequest`, and `ExecutionResult`.
- `core/researchos/runtime/` contains the canonical method contract and execution engine.
- `modules/` contains self-contained capability modules.
- `platform/` contains Android and device-service boundaries.
- `transport/` contains RIL parsing, Android intent routing, graph selection, and return formatting.
- `ui/` contains the Compose dashboard and shared presentation components.

There is one execution model. The former flat `Method`, `MethodRequest`, `MethodResult`, and adapter runtime no longer exist.

## Capability modules

Each capability implements `ResearchOSModule` and may expose:

- canonical `As100Method` implementations;
- RIL phrase bindings;
- focused capability screens;
- declared module dependencies;
- module-owned examples.

Modules are registered explicitly in `ResearchOSModuleManifest`. Explicit registration makes the installed capability set deterministic and auditable.

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
com.example.researchos.EXECUTE_METHOD
```

The method selector is:

```text
method_id='<canonical method ID>'
```

ODK multi-field calls use the action in the `body::intent` cell and `field-list` in the group appearance. Function-style parameters are parsed from the action, while group fields are delivered as string extras. Blank return fields never override non-blank explicit parameters.

Example:

```text
com.example.researchos.EXECUTE_METHOD(method_id='attestation.create',event_payload_hash=${event_payload_hash},verification_method='Fingerprint',trusted_timestamp='preferred',return_mode='flat')
```

Old method aliases and old transport keys are not accepted. Callers must use canonical IDs and the current parameter names.

## RIL transport

RIL is the canonical internal request representation. Direct transport uses:

- `ril` for a complete RIL request; or
- `method_id` plus method-specific settings;
- `returns` for graph selectors;
- `return_mode` for the requested shape.

RIL phrases are owned by modules. Phrase resolution returns the module's canonical method ID. Unknown actions remain unresolved and must not be silently redirected.

## Returns

The default caller return is intentionally compact:

- execution ID, method ID, and status;
- relevant invocation identifiers when supplied;
- the scientific or operational result;
- the minimum provenance required to interpret or verify it;
- failure diagnostics only when execution fails.

Canonical graph objects and full provenance remain in the ResearchOS graph. Explicit graph selectors may request particular canonical fields. The return formatter does not reproduce deleted flat-model aliases.

## Attestation contract

`attestation.create` requires:

- `event_payload_hash`: exactly 64 hexadecimal SHA-256 characters;
- `verification_method`: one of `Fingerprint`, `Pin`, `Qr`, `Nfc`, or `Password`.

`trusted_timestamp` is optional and accepts only:

- `disabled`;
- `preferred`;
- `required`.

ResearchOS signs the supplied form hash directly. Raw `event_payload` input and automatic double-hash detection are not supported.

Attestation schema version 3 returns the public key, signature, chain link, verification evidence, and optional full RFC 3161 evidence. Fingerprint, QR, and NFC verification use their respective capability or device-service boundaries.

## Adding a capability

1. Create a folder under `modules/<capability>/`.
2. Implement one or more canonical `As100Method` objects.
3. Define method descriptors and contracts, including required context and produced graph outputs.
4. Create a focused `CapabilityScreenSpec` when interaction is required.
5. Expose the methods, screens, RIL bindings, and dependencies from a `ResearchOSModule` object.
6. Add that module object to `ResearchOSModuleManifest`.
7. Add unit tests for method output, RIL resolution, return formatting, and invalid requests.
8. Verify dashboard and external invocation modes separately.

Do not add an adapter, alias, second result model, direct hardware call from a method, or module-specific transport parser.

## Verification

Run:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Device-dependent behavior such as camera, NFC, biometric prompts, location, and ODK round trips must also be tested on an Android device.
