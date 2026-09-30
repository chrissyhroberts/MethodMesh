# Module UI audit

Quick UI/UX audit of the native MethodMesh modules. This is an implementation triage document, not a requirement that every module grow a dashboard.

## Overall finding

The module surface is more complete than the first impression suggests:

- Every discovered module currently registers at least one native capability screen.
- Several large modules already have a real operator dashboard, including Music, Astronomy, Aviation, Geocaching, GPS Target Navigator, Network Tools, Web Actions, and the time tools.
- Small, single-purpose capabilities generally benefit from staying focused rather than gaining a launcher screen.
- The main recurring weakness is discoverability: larger families expose many related tools as separate actions without a module-level starting point.

## Triage

### Improved in this pass

| Module | Finding | Action |
| --- | --- | --- |
| NFC | Eleven related tag, credential and protocol-card actions were discoverable only as separate capabilities. | Added an NFC dashboard with grouped launch cards for Tag tools, Credentials and Protocol cards. |
| Signals | Seventeen transmit, receive and inspection actions were presented as a flat list. | Added a dashboard grouped by Text and QR, Audio and physical links, and Optical and inspection. |
| Reference Library | Search, email sharing and nearby management were separate entry points with no common starting point. | Added a compact library home with direct cards for the three workflows. |
| Earth Science | Twelve field and calculation capabilities were presented as a flat list across unrelated domains. | Added a workbench grouped into Geodesy/GNSS, Structural geology, and Earth materials/time. |
| Acoustics | Four live measurement modes had no shared starting point. | Added a compact measurement and tuning workbench. |
| Web Actions | The online-data and workflow surfaces had been conflated, and the fetch result could leave the user without an in-page result surface. | Kept the data dashboard and web-workflow dashboard separate; the data dashboard now owns selection, location input, fetch, readable preview and standard output actions. |
| Text Tools | Ten small text operations were exposed as a flat capability list, making it difficult to choose the right tool quickly. | Added a grouped dashboard for clean/reshape, inspect/extract and encode workflows; the underlying individual capabilities and contracts are unchanged. |
| Codes | Scan, generate and clone were separate entry points despite sharing an exact-payload workflow. | Added a compact Codes dashboard with clear choices for scanning, creating and cloning while preserving the existing barcode contracts. |
| Spatial geometry | Height, slope and distance tools were separate entry points despite sharing a phone-as-instrument workflow. | Added a compact field-measurement dashboard with clear choices for the three existing calculations. |
| Conversation Translate | Two-person and four-person live translation were separate entry points even though the first user decision is the physical conversation shape. | Added a simple chooser for two-person versus table translation; language and audio settings remain inside each live session. |
| Display | Sign, countdown and clock were separate entry points despite being three modes of the same screen utility. | Added a simple Display hub that routes to the existing full-screen modes. |
| Chance | Dice, coin, card, concealed-choice, spinner and weighted-choice tools were a flat set of separate entry points. | Added a grouped Chance dashboard while preserving each tool’s secure-random and fixed-seed settings. |
| Media | The module summary promised a dashboard, but catalogue, library, capture and identification were separate entry points. | Added a grouped Media dashboard without changing the local library or media capability contracts. |
| Traceable Attestation | Signed event creation and nightly ODK anchoring were separate entry points despite being one evidence workflow. | Added a two-card attestation hub; evidence remains handed to ODK and is not made into a MethodMesh store. |
| Local Device Authentication | Device authentication and browser biometric callout were separate entry points with no explanation of their distinct roles. | Added a security-focused chooser that explicitly distinguishes local access confirmation from browser callout. |
| File Lab | Inspection and conversion were separate entry points even though inspection is the safer first step for unfamiliar files. | Added a small File Lab hub that explains the inspect-versus-convert choice. |
| Surveying | Thirteen coordinate, field-book and GPS tools were exposed as a flat list. | Added a grouped field hub for coordinate geometry, field calculations and traverse/levelling books. |
| ESP Mesh | Messaging, gateway setup and diagnostics were separate entry points despite being one transport workflow. | Added a network-task hub without changing transport ownership or persistence. |
| Paper Bridge | Form design and form transcription were separate entry points despite forming one design-then-use workflow. | Added a compact Paper Bridge chooser. |
| Choice Experiments | Pairwise, MaxDiff, ranking, points and conjoint tasks were a flat set of study actions. | Added a task-format chooser while leaving study settings and result contracts inside each task. |
| Star Spectrum | Spectrum analysis and calibration-reference creation were separate entry points despite being sequential parts of one workflow. | Added a small analyser hub that makes the reference-then-analysis path explicit. |
| Field Statistics | Ten epidemiology and quantitative calculators were exposed as a flat list. | Added a grouped hub for diagnostics, estimation/planning, detection and probability. |
| Emergency | Status, location, exit planning, reference and pack preparation were separate entry points in a time-pressured module. | Added a prioritised Emergency hub with clear offline-first boundaries. |

### Already coherent; no cosmetic dashboard added

| Module family | Why it is currently acceptable |
| --- | --- |
| Music | Multiple purpose-built dashboards already cover practice, performance, reference and creation. |
| Astronomy, Aviation, Geocaching, GPS Target Navigator, Network Tools | Each has a persistent or task-oriented dashboard appropriate to the workflow. |
| Lab Bench, Aquatic Fieldwork, Surveying, Diving, Cryptography, Time Tools | The module surfaces already group related work or provide a durable working surface. |
| Atomic utilities such as Compass, Magnifier, SMS, Speech Transcription and Calibrated Scale | A direct task screen is clearer than an extra launcher page. |

### Next candidates for a deeper UX pass

These are not broken, but would benefit from a hub when their capability count or workflow complexity grows:

1. NFC — the basic hub is now in place; a later pass could add recent task shortcuts if the host exposes safe non-persistent session context.

## Guardrails for future work

- Keep module UI inside the module; do not add module-specific branches to `HomeScreen`.
- Prefer a dashboard only where it reduces discovery cost or coordinates several related capabilities.
- Keep the existing capability contracts and ODK return behaviour unchanged during visual refactors.
- Treat persistent state, audit logs and domain databases as separate work: a nicer dashboard must not silently make MethodMesh the system of record.
- Validate each dashboard with native launch, back/cancel, rotation, result confirmation and external workflow return.
