# ResearchOS DCE capability discovery fix

## Problem
The DCE screens worked in the standalone/debug runner, but disappeared after moving them back into the canonical Capabilities panel.

The root cause was module discovery. `ResearchOSModuleRegistry.initialise()` replaced the fallback built-in module list with whatever Dex discovery returned, as long as it returned at least one module. If Dex discovery found NFC/GPS/etc. but failed to instantiate `ChoiceExperimentModule`, the registry became a partial list and the DCE AS methods were absent from `As100MethodRegistry.all()`.

## Fix
`ResearchOSModuleRegistry.initialise()` now merges discovered modules with the built-in fallback modules, de-duplicated by `moduleId`, instead of treating a non-empty discovered list as complete.

## Result
DCE/choice experiment capabilities remain inside the canonical Capabilities panel and cannot vanish merely because opportunistic Dex discovery returns a partial module set.
