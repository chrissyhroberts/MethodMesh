# Aviation v0.3 — emergency reference panel

Status: **Development**

v0.3 adds `aviation.emergency.instruments`, a persistent phone/tablet emergency-reference surface designed for glanceability and explicit sensor provenance rather than imitation of certified aircraft instruments.

Highlights:

- continuous GNSS groundspeed, track, altitude and estimated vertical speed;
- mounted-device attitude reference using the existing shared phone-sensor repository;
- explicit level calibration and mount-orientation selection;
- neutral/non-moving horizon until calibration is established;
- `ATT FALLBACK`, `ATT UNCAL`, `ATT DYNAMIC`, GNSS and magnetometer quality states;
- phone-barometer standard-pressure altitude where available;
- nearest-airfield positional reference that does not claim landing suitability;
- responsive portrait/wide cockpit-style UI with monospaced data and a persistent safety band;
- live native/native-preset behaviour with explicit **Use this snapshot** / **Finish**;
- bounded one-shot external/ODK snapshot;
- updated XLSForm, tests, build checklist and multi-round UX design review.

The main aviation dashboard and all v0.2 atomic methods remain available unchanged in purpose.
