# Roadmap notes

The purpose of this file is to keep a live notebook of outstanding ideas, issues, bugs and problems.

As things are fixed or abandoned, they can be removed from this list. As new things are discovered, they should be added under the most relevant heading.

Use simple Markdown checkboxes so Codex and humans can edit this file easily.

---

## UI

- scheduler now has some rails, but they're inconsistent. It should never progress without active confirmation. 
- Running a webform crashes the app - may need a specific capability with autoreturn link if possible. if not a confirm complete button. 

- last step in the schedule just vanishes and goes back to launch screen. It should have a summary and options to save etc as per protocol
- I think the scheduler should be limited to running a protocol - so protocols own what and scheduler just owns when
- protocols should be built from presets only
- Presets with preset option share still go to the page with buttons to share or home. It should automatically share. 

- Presets should also have a preset option to copy to clipboard - this would show the result, copy it to clipboard automatically, then return to home screen. 

- Too much dialog and manual steps at the end of preset runs.

- All dashboard panes should be collapsed by default

### Application packaging and capability delivery
- MethodMesh is becoming large enough that capabilities should eventually be modular rather than all bundled into the base APK.
- Design for sideload-first modular delivery, without depending on Google Play.
- Keep a relatively small MethodMesh core installed permanently, containing shared runtime, UI, RIL, provenance, storage, protocol and capability interfaces.
- Group optional capabilities into logical capability packs rather than making every capability an individual package.
- Build optional capability packs as precompiled, signed split APKs in CI/GitHub Actions. No compilation should occur on the Android device.
- Host compatible capability packs and a machine-readable capability catalogue in GitHub releases.
- Allow the capability browser to show capabilities which are available but not installed, e.g. a download/install icon next to Dice Simulator.
- User interaction should support:
  - download capability pack;
  - verify hash/signature;
  - request installation through Android PackageInstaller;
  - indicate installed status;
  - remove an installed capability pack to reclaim storage.
- Split APKs must use the same MethodMesh package, signing certificate and compatible version code as the installed base application.
- Treat this version-locking as a feature rather than allowing arbitrary plugin dependency combinations: each MethodMesh release should have a matching set of compatible capability packs.
- MethodMesh upgrades will need to account for installed capability packs and install/update the corresponding versions together.
- Before undertaking the refactor, use APK analysis to identify what is actually responsible for current app size; large shared libraries/assets may matter more than capability Kotlin code.
- Longer term, distinguish between capabilities requiring native Kotlin/Android implementation and capabilities that can be expressed using existing MethodMesh primitives.
- Explore a declarative MethodMesh capability package format (.mmcap or similar) containing manifests, UI definitions, RIL bindings, presets and assets. These could potentially be downloaded directly from GitHub without requiring Android code installation.
- Preserve compatibility with future Google Play Feature Delivery, but do not make Play Store infrastructure a requirement for the architecture.

### Storage / Offline Resources settings panel showing something like:
Storage

Application                         78 MB

Downloaded resources
  Translation models              386 MB
  Sensor firmware                   8 MB
  Data stored in app folder			4 MB
  Other cached resources            4 MB
  

Total offline resources           402 MB

[ Manage language models ]
[ Clear disposable cache ]
Ideally MethodMesh should distinguish between deliberately downloaded offline resources and genuinely disposable cache. If those 402 MB are language models you chose to install for offline use, calling all of that “cache” is technically understandable from Android's perspective but pretty unhelpful from the user's perspective.

## Dashboard 

- A world map showing GDACS events might be useful - red, yellow, green bounding boxes in affected areas. Clicking on the event should show details.

- A local weather view might be nice too. Maybe inspired by single pane app here https://github.com/chrissyhroberts/weather_app_android. Minimally a collapsed view with basic temp/time/rain, when expanded shows a fuller report.

- Find a capability box and find a preset box are useful. Not sure how preset shortcuts is populated, but it's quite a nice idea. On dashboard we should see only the nane

## Outputs

- XLS-to-CSV conversion from scan output - needs more thought
- Update all XLSForms.
- Upload/update material needed for the API work.
- Current implementation is focused on ODK control and manual 


## Presets and Preset Library

- Presets will become numerous; consider grouping them by capability.
- When a preset has been run, it shows the 'share result' and 'home' buttons. That's good, but I think a copy to clipboard would be good

## Protocols and Protocol Library

- Old protocol versions should be stored in an archive with an option to unarchive.
- Explore a text-message workflow where an SMS carries a link to a MethodMesh protocol on a second phone, providing an immediate pathway to countersignature and a return link by SMS.
- Consider plus/minus a review payload in the countersignature workflow.
- Experimental ALCOA/ResearchOS protocol: determine how to prevent temporal spoofing.
- Add a visual pipe editor for protocols so users can deliberately map outputs from previous steps into later runtime inputs, e.g. barcode payload -> Qutie printer message. The runtime now carries previous outputs forward, but the UI still needs a friendly mapping layer.


## Scheduler

- Any need for pipes should be delegated to protocols rather than coded into scheduling


## Device Registry
- This registry seems to only allow one device at a time. adding a second sensor wipes the original from the registry?
- There's a 'read' button on the devices in the registry. That should jump straight to the live feed view.
- The device registry should be more like the view you get in read sensor when you press search for sensors.
- It'd be nice to have a repeating scan to refresh which devices are currently active (green) or not detected (red)
- Not sure what pause button is for

## Workbench

### Online API links

- GDACS data stream working, but no capabilities currently use data from this
- Open-Meteo current weather working, but no capabilities currently use data from this
- Need to add bespoke API calls as a capability
- Create a prepackaged 'local context' big bag of API results to make an easily refreshable and cached source of truth for the area. Many APIs sourced for info and searchable results that can be called into ODK etc. 
- Exchange-rate UI is more coherent, but result values still need another pass. The country/currency picker currently has duplicate or ambiguous currency-code choices, e.g. GBP can appear as British pound / Guernsey pound and Dominican peso has duplicate entries. This may be why selected rates do not always surface as values, especially when a non-1 amount is entered.

### Android app inspector

- Currently obscure
- Purpose of this workbench tool is to expose inbuilt intents - this works
- REAL purpose of this workbench tool should be to figure out how to use those intents. At the moment we can open them. It'd be cool to understand them.
- Probe within the app intent screen for common intent structures - like if apps tend to have some kind of syntax for doing stuff with intents
- At the moment you can see there's an intent, but never have a clue how to tweak it to be useful. Running an external intent with parameters should be discoverable; work out how this should or could be exposed.
- Control of other apps in the absence of good documentation may need slightly hacky behaviour
- copy everything to clipboard so I can AI the heck out of it? 
- may want to consolidate a scan through all intents and compile into a single technical report that's shared from clipboard as a file/text

### Bluetooth device inspector

- currently obscure
- Purpose is to discover endpoints and streams from BT devices - that works
- What to do with them though? 
- copy everything to clipboard so I can AI the heck out of it? 
- may want to consolidate a scan through all endpoints and streams into a single technical report that's shared from clipboard as a file/text

### ESP32 Sensor Framework

- This is mature and functions correctly both in sensor image install and provisioning of sensor devices
- May want to make some more sensor firmware files
- Online repo so we can download and destroy to limit file storage and app size bloat if not using any sensors? 

## Capabilities

- Running an external intent should be a standalone operation through a capability >> preset.
- Incoming prototypes from non-Work chats should land in `incoming_capability_prototypes/` first, then be admitted to `modules/` only after Work-mode build/review.

### Acoustics

- Admitted to the real `modules/` path as a Development capability on 2026-09-03.
- Builds in the main app and keeps its docs / example ODK form nested inside the module folder.
- Uses local microphone capture for acoustic analysis, tuner, sound level and tone comparison.
- Needs native/preset/ODK device testing and calibration review before promotion to Production.
- Does not persist raw audio; decide later whether optional waveform capture is needed for audit/replay.

### Automatic QR / barcode scanner

- In Production

### BLE sensor-node provisioner

- In Production

### BLE sensor reading

- landing page for a sensor reads should be a list of all local sensors currently detected
- clicking on a sensor should then show the live feed
- Then select how to sample data from it (averaging, point, etc)
- Then this collects the data and freezes it for delivery payload
- LD2410C signals seem to work at first, but stall, freeze or return error after a while
- LD2410C probably needs controls to adjust gain on the system
- AHT20 working very nicely except sharing payload says profile "ld2410c, temp..."

### Bluetooth printer

- works for Qutie very well
- In Production

### Calibrated scale

- Cannot enter text into the length settings.

### Choice experiments

- These ask questions nicely but don't return helpful outputs
- Need to learn how to analyse the DCEs and do on-board calculations if possible
- Return results with scores/probabilities or whatever DCEs spit out.

### Conversation translator

- Prepopulate buttons like "talk" and text placeholders "ready to translate chinese, press talk and speak to the phone" type stuff in the conversation capability.
- Languages outside ML Kit's on-device translation set, e.g. Krio, need a separate future plan rather than appearing as downloadable ML Kit packs.
- In Production 

### Compass

- Admitted to the real `modules/` path as a Development capability on 2026-09-03.
- Builds in the main app and keeps its docs / example ODK form nested inside the module folder.
- Works well in basic testing as a magnetic heading / bearing-sighting tool.
- Consider capturing a target photo as reference.
- Needs native/preset/ODK device testing and magnetic/AR validation before promotion to Production.


### Document scanner

- In Production
- Consider return text only as an option (i.e. no PDF)


### External command library

- WhatsApp send has no configuration box for entering a phone number.
- Configuration dropdown does not anchor correctly to its button.
- Each command needs to refresh its own configuration sub-panel.
- The overall interaction is unclear / "WTF?" and needs a usability pass.
- Running an external intent should be a standalone capability.
- Parameterised external intents need a discoverable configuration workflow.
- Probe for common intent structures from within the app.
- A lot of this could be bundled to do cool stuff with mainstream apps using known configs.

### GPS target navigation

- In Production
- Reasonably complete - works with raw GPS and Plus codes
- No plans for changes now

### Image redaction

- In Production
- Mature and works as needed

### Local device authentication

- this is used primarily for getting a fingerprint - it functions as a basic lock on things like an ODK form. i.e. open form, unlock with finger, rest of form is relevant only if successful. 
- In Production

### ML Kit language translation

- The block translation works on text input and returns text output. 
- In Production

### ML Kit vision

- slightly different from the document scanner but use case needs thought
- In Production

### NFC credential provisioning

- This needs a real-world test to ensure still functional after many refactors

### NFC credential verification

- This needs a real-world test to ensure still functional after many refactors

### NFC tag read

- This needs a real-world test to ensure still functional after many refactors

### NFC tag wipe
- This needs a real-world test to ensure still functional after many refactors

### NFC tag write
- This needs a real-world test to ensure still functional after many refactors

### ODK form launcher
- In Production

### Plus Code capture

- In Production
 
### Psychomotor vigilance test

- Admitted to the real `modules/` path as a Development capability on 2026-09-03.
- Builds in the main app and keeps its docs / example ODK form nested inside the module folder.
- Runs and returns results.
- Capability-rule review passed for Development: module owns its UI, uses the shared scaffold/close-out, hides fixed preset settings, starts native preset runs directly, and uses `pvt_result` as the main result with trial-level audit JSON in the background.
- Keep in Development until representative Android device timing/latency characterisation is done.
- Needs native/preset/ODK device testing before promotion to Production.

### Protocol NFC check

- untested / alpha

### Protocol NFC completion

- untested / alpha

### Question — multiple choice

- not functional

### Question — number

- not functional

### Question — single choice

- not functional

### Question — text

- not functional

### Random number generator

- In Production

### Sampling
- Admitted to the real `modules/` path as a Development capability on 2026-09-03.
- Compiles in the main app and keeps its docs / example ODK form nested inside the module folder.
- Needs native/preset/ODK device testing before promotion to Production.
- export csv file doesn't work. Probably default to main downloads folder on phone

### Scaled photo capture / selector

- Functional but may need revision before promotion
- Needs real world test
- Think about useful return outputs and media

### Signed event attestation

- Was working, haven't tested after refactors
- Very important function as core validation ALCOA capability
- Review return payload

### SMS

- Entering a value "0" into the phone number box on the config card makes that box suddenly vanish. Needs bug review

### Spatial geometry

- Essentially functional, untested for quality and accuracy with real world verification
- might not be the best.

### Speech transcription

- Consider adding explicit Start / Stop / Pause controls. Native speech recognition currently waits for a pause before transcribing.

### Sound generator

- Admitted to the real `modules/` path as a Development capability on 2026-09-03.
- Builds in the main app and keeps its docs / example ODK form nested inside the module folder.
- Generates local tones/noise/sweeps through Android audio.
- App manifest now includes the audio-settings permission needed for the optional temporary media-volume policy.
- Needs native/preset/ODK device testing, routed-device validation and safe-volume review before promotion to Production.

### SVG polygon selector



### Calibrated scale

- Cannot enter text into the length settings.

### Speech transcription

- Consider explicit Start / Stop / Pause controls. 
- Native speech recognition currently waits for a pause before transcribing, which can be a pain when you take a breath or stop for a second to turn a page etc. 

## Runtime State

The runtime isn't really doing anything at the moment, but this is one for the future. 

## Settings

The settings sub-panels should be collapsed by default.

## Display accessibility


# Quarantined Capabilities

### Trusted timestamp
- prototype in place in development capabilities
- needs testing
- Current prototype does not build.


### Clinical Instruments
- Prepackaged DSM5 questionnaires with scoring built-in
- clinical instruments like qSOFA etc
- linear checklists only - no relevance or constraint
- resumable
- in prep
- Prototype builds, but running it kills the app.

### Expenses
- in prep
- Current prototype does not build.

### Dice simulator

- Current prototype does not build.

### Tabletop utilities

- Current prototype does not build.

### Visual acuity

- Prototype builds and runs.
- Optotypes are not shown.
- Needs swipe navigation or another comfortable result-entry interaction.


# Roadmap Capabilities and Features

### Open link (webforms etc)
- this used to be handled by scheduler but needs it's own capability
- may open a link in chrome or default browser
- may also open a mini browser within app (or a portal to chrome etc) that has better controls over what happens when the form is completed. It ideally knows when the form is done or the window is closed
- can call out links to a webform have a round-trip thing built in? 
- very important for scheduling webform completion (enketo etc) as part of a protocol rather than as a standalone, even though we could just schedule webforms as a one shot thing - which is often fine, we'd be like 1030 trigger webform 1, 1100 trigger other protocol shit

### API calls
- Bespoke API call capability
- Draws data from online source and parses into a searchable and individually addressable data source
- Automatic warning if data gets more than x hours out of date. Suggest refresh but override with warnings possible for off-grid work. 


### Hearing test
- A protocol of multiple frequencies and multiple volumes
- User presses a button as soon as they hear a sound
- Repeats for each ear (stero channels on headphones)
- Shows a multifrequency trace of detectable frequencies with freq (x) and amplitude of detection (y)


### Decibel meter

- Add a decibel meter capability
- Unlikely to be very accurate?



### Currency conversion
- visuals like the conversation tool, facing two ways
- uses data from packaged API call to a forex server


### Third party attestation

- SMS-linked capability for countersignature on a second phone and return by SMS.
- Consider optional review payload.
- Experimental ALCOA protocol: determine how to prevent temporal spoofing.
- SMS include links? 
- two way interactions between different individual instances of MM. 

### Dice
- dice
- coin toss
- high scores
- Complex RPG dice things
- in prep

### RPG Stuff
- character sheets
- Manna/EXP/HP counters
- etc
- in prep

### WHO verbal autopsy
- in prep but skip logic constraint and relevance is a big problem to solve without more thought.


### Visual acuity screening - WHOEyes port
- in prep

---

## Done

- Marked Qutie-family Bluetooth printer as Production.
- Marked BLE sensor-node provisioner as Production.
- Marked ODK form launcher as Production.
- Marked ML Kit language translation as Production.
- Marked ML Kit vision analysis as Production.
- Marked random number generator as Production.
- Confirmed existing Production status in the roadmap for barcode scanner, document scanner, GPS target navigation, image redaction, local device authentication, conversation translator and Plus Code capture.
- Improved World Bank indicator handling so the latest non-empty value is returned with its matching year.
- Added a capability writing guide for external AI chats and contributors.
- Added a formal close-out contract for external/native preset runs so capabilities report completed/cancelled, step count and whether a useful payload exists.
- Repaired protocol and scheduled-run sequencing so multi-step runs can progress step-to-step and collate returned fields into one final grouped output package when output is enabled.
- Fixed native protocol step close-out so presets used inside protocols auto-return between steps and end on a combined result screen instead of sitting on each individual capability result.
- Added first-pass guided rails for presets, protocol steps, scheduled chains and multi-action ODK launches: intro screen before each step, manual completion confirmation for form/web-form steps, and final run actions for sharing/copying/saving/closing.
- Added a public Android Downloads close-out action for native preset/protocol/schedule results: text data is written as `result.txt`, media are saved as files, and full audit export remains a separate option.
- Added first-pass runtime piping context: completed steps expose `step_N_<field>`, `previous_<field>` and unprefixed field values to later steps. A visual pipe editor remains on the roadmap.
- Rationalised ML Kit translation language handling around canonical ML Kit codes, including aliases for Chinese/Japanese/Korean and safer speech/TTS locale mapping for conversation translation.
- Created `incoming_capability_prototypes/` quarantine for externally drafted modules and moved Sampling / Trusted Timestamp prototypes out of the auto-discovered build path.
- Reviewed and admitted Acoustics, Compass, Psychomotor Vigilance Test, Sampling and Sound Generator as Development capabilities with nested docs and example ODK forms.
- Tightened AUDIT payload projection so capability-owned `*_audit_json` fields, including PVT trial audit data, are retained in audit-mode returns without appearing in compact core sharing.
