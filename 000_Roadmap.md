# Roadmap notes

The purpose of this file is to keep a live notebook of outstanding ideas, issues, bugs and problems.

As things are fixed or abandoned, they can be removed from this list. As new things are discovered, they should be added under the most relevant heading.

Use simple Markdown checkboxes so Codex and humans can edit this file easily.

---

## UI

- A lot of UI issues relate to the problem that capabilities still don't close out gracefully. A thing happens and it sits on the result page with no signal of closing out the step

- Any place where multiple presets are run back to back, like protocols, schedules or scheduled protocols, needs a graceful way to move from step-to-step. At present there's no 'this is done, now move on signal

- Needs to be concept of piping results into the next thing - i.e. for protocols where input of one thing is output of the previous thing

- Presets with preset option share still go to the page with buttons to share or home. It should automatically share. 

- Presets should also have a preset option to copy to clipboard - this would show the result, copy it to clipboard automatically, then return to home screen. 

- Too much dialog and manual steps at the end of preset runs.

- All dashboard panes should be collapsed by default

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
- Protocols currently don't work well with some capabilities. These depend on the automatic outcomes like copy, share etc. We need [a] a method to pipe results of one into the next - so need a new outcome (pipe) that for instance scans a barcode, pipes the payload into Qutie printer and prints a sticker with the same barcode and human readable. We also need [b] a true completion signal from a preset. At the moment if I scan a document and share via whatsapp, when I return to the app I press home or cancel and it quits to dashboard. It should signal that step x of the protocol x,y,z is complete, triggering step y. This is not always going to require a piped output. 


## Scheduler

- The scheduler is mature but the bridges between multiple activities are dead, just like for protocols. This used to work well, but I think we broke it a while back. The issue comes from the fact that there's no official completion signal for presets. They need a formal closeout and at the moment it goes to the result screen with share, home etc, but there's no 'ping' to say it is done and carry on.
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
- In Production 


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





# Roadmap Capabilities and Features

### API calls
- Bespoke API call capability
- Draws data from online source and parses into a searchable and individually addressable data source
- Automatic warning if data gets more than x hours out of date. Suggest refresh but override with warnings possible for off-grid work. 

### Sound generator

- Create a tone of specific frequency and volume
- Useful for hearing tests
- Multiple waveforms / white noises etc
- Override system volume? 

### Hearing test
- A protocol of multiple frequencies and multiple volumes
- User presses a button as soon as they hear a sound
- Repeats for each ear (stero channels on headphones)
- Shows a multifrequency trace of detectable frequencies with freq (x) and amplitude of detection (y)

### Sampling
- Sample n items from a list of y items
- Sorted or unsorted
- Words or numbers
- Sampling with replacement
- Sampling without replacement

### Decibel meter

- Add a decibel meter capability
- Unlikely to be very accurate?

### Compass

- Add a basic compass capability

### Frequency/Oscilloscope analyser

- Listens to a tone and shows frequency/wavelength/amplitude
- Add a guitar tuner capability
- Play a note and see the frequency
- Targets with green zones for guitar, uke, violin etc

### Currency conversion
- visuals like the conversation tool, facing two ways
- uses data from packaged API call to a forex server

### Receipts and expenses
- system for recording expenses
- photos of receipts
- undocumented / no receipt option
- local storage of data?
- Good use of the background data storage
- running count
- categorises expenses travel/subsistence/etc

### Third party attestation

- SMS-linked capability for countersignature on a second phone and return by SMS.
- Consider optional review payload.
- Experimental ALCOA protocol: determine how to prevent temporal spoofing.
- SMS include links? 
- two way interactions between different individual instances of MM. 

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
