# MethodMesh v2.8.0

MethodMesh v2.8.0 is a field-network and transport-foundation release. It
packages the current ESP-NOW/BLE work, the ESP32-C3 installation path, native
AprilTag support, Paper Bridge improvements and the shared transport cleanup.
It remains intended for controlled field and bench validation while the broader
Field Network roadmap is implemented.

## Highlights

- Added the Field Network architecture to the authoritative Master Book,
  including node roles, handset access, traffic classes, routing, store and
  forward, power policy, emergency handling, positioning, displays, actuators,
  implementation sequence and acceptance demonstrations.
- Added generic transport capability metadata for traffic classes, addressing,
  frame/object limits, fragmentation, durability, encryption and power policy.
- Hardened the transport runtime: provider lifecycle transitions are serialized,
  late registration is safe, secure providers retain their own encrypted
  durability, and missing consumers produce retryable failures.
- Hardened the generic journal: queue-full and corruption conditions fail closed,
  accepted records are not silently evicted, writes are atomic, and source plus
  message ID are used for deduplication.
- Moved the ESP Mesh walkie-talkie control behind module-owned overlay metadata,
  leaving the shared shell free of ESP-specific registration and placement.
- Fixed the ESP Mesh gateway launch crash caused by nested scrolling and added
  the required connected-device foreground-service permission/API handling.
- Retained the ESP Mesh encrypted store-and-forward, live voice, BLE v2 bridge,
  ESP-NOW fragmentation, ESP32-C3 image installation and diagnostics work.
- Retained the AprilTag native library boundary and Paper Bridge colour-template
  and eight-tag registration work from the previous release line.

## Validation

- `:app:assembleDebug` passes for the configured Android ABIs.
- Focused core transport and ESP Mesh protocol unit tests pass.
- ESP Mesh security, radio codec and live-voice invariant tests pass.
- The Master Book is the single authoritative home for the Field Network plan.

## Known limits

- Automatic heterogeneous route selection, topology maps, additional bearers,
  large-object transfer and asymmetric membership/revocation remain roadmap
  hooks.
- Physical two-phone/two-node field validation remains required before treating
  the network as production-ready.
- Existing unrelated documentation/XLSForm unit failures remain separate from
  the passing build and focused transport checks.
