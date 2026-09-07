# Patch 0.2.0 - core library expansion

Adds 15 new immutable definitions, bringing the total core library to 19.

All definitions are linear and compatible with the current focus-mode runner. No relevance/skip logic was introduced.

Validation performed in an isolated Kotlin harness:
- all 19 YAML definitions parse;
- all 19 pass schema validation;
- all embedded regression cases pass through `ClinicalInstrumentEngine`.

The Android application still needs the normal repository build/device smoke test after drop-in.
