# Compass roadmap note

The v0.2.0 change is a MethodMesh v1.05 migration, not a scope redesign.

Completed in this refresh:

- preserved `compass.read` and all established setting/output keys;
- instrument-first, luxury-styled live compass with settings below;
- live same-screen heading/target/error;
- explicit live-working-result -> Commit lifecycle;
- frozen committed payload with explicit recommit;
- universal tap-to-copy for useful displayed values;
- same-screen Copy/Share/Save/full-JSON/Done actions;
- launch-origin-aware automatic return and native-preset/widget closeout;
- saveable working + committed state;
- ODK example expanded to all declared compass outputs;
- generic dashboard/widget icon hint set to `location`.

Future work remains deliberately out of scope for this migration:

- optional true north from explicit location + geomagnetic declination;
- haptic/audio on-target cue;
- bearing hold/lock;
- inclinometer as a separate method;
- composition with the GPS target navigator through public capability outputs.
