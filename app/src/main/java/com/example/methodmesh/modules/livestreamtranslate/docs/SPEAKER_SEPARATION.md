# Speaker separation design

## Current implementation

Speaker identity is **explicit**, not inferred.

When speaker tagging is enabled, the meeting surface exposes `Speaker ?` plus a configurable number of numbered speaker chips. The active label is attached to each completed utterance.

This is intentionally conservative. Android `SpeechRecognizer` provides speech text, confidence values and (on API 34+) language-detection/switching information, but it does not provide speaker diarisation. The current ML Kit GenAI Speech Recognition alpha is also treated as a text-recognition provider only; the module does not infer speaker identity from it.

## Why not use a local acoustic heuristic?

A heuristic based only on loudness, pitch, zero-crossing rate or short microphone buffers would be highly sensitive to:

- microphone placement;
- speaker distance;
- room acoustics;
- background noise;
- overlapping speech;
- the same speaker changing volume or orientation.

Mis-labelling speakers would be worse than leaving the label unknown, particularly when MethodMesh results may enter research data flows.

## Future provider boundary

A future diarisation provider can be added independently of the hot-swappable speech-recognition provider without changing the canonical method IDs or result schema. The provider should return a stable local speaker label for each completed utterance and should declare:

- whether audio leaves the device;
- authentication/credential requirements;
- cost model;
- data retention;
- supported languages;
- whether overlapping speech is handled;
- confidence or uncertainty.

The current segment JSON already has a `speaker` field, so a future provider can populate it without breaking downstream ODK or MethodMesh contracts.

## Cloud option

Google Cloud Speech-to-Text supports streaming speaker diarisation, but enabling it would materially change MethodMesh's privacy and deployment model. It should therefore be a separately configured provider/capability, not an invisible fallback in the default live translator.

Reference: https://cloud.google.com/speech-to-text/docs/multiple-voices
