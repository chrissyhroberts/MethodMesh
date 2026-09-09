# Roadmap — Time & Alarms

Development follow-up before Production:

- run the full MethodMesh Gradle test/build in the host repository;
- physical-device exercise on current Pixel/Android plus at least one non-Pixel OEM;
- verify notification channel behaviour under DND, lock-screen privacy and denied exact-alarm access;
- verify reboot/timezone-change restoration;
- verify notification Lap/Pause/Resume/Stop actions while MethodMesh is not foregrounded;
- verify Done cancels outstanding follow-ups and Snooze does not disturb the next recurring alarm occurrence;
- add focused host JUnit tests around module registration, capability screens and ODK projection;
- consider a future generic MethodMesh module-dashboard hook so Time & Alarms can be promoted as a top-level module surface without teaching HomeScreen about this module.
