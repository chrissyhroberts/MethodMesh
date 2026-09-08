# ResearchOS 2.1.2

ResearchOS 2.1.2 adds an Android app-inspection prototype for discovering and testing public intent integrations, together with documentation and usability refinements for the scheduler.

## Android app inspector

The new `android_app_inspector` capability provides a safe, user-directed way to explore installed Android applications:

- lists installed launchable applications;
- inspects exported activities, services, receivers, and providers;
- probes common public actions, categories, and URI schemes;
- targets exported activities explicitly;
- sends an action, optional URI, and simple string extras;
- captures result codes, returned URIs, and returned extras;
- saves a tested package/component/action/URI/extras combination as a local integration definition.

This makes it possible to investigate applications such as Peek Acuity Pro and test exported entry points such as visual-acuity activities. The inspector does not bypass non-exported components, permissions, authentication, or private implementation details. It is a discovery and test harness, not a universal reverse-engineering tool.

## Runtime and scheduler refinements

- The scheduler is collapsed by default when the ResearchOS app opens.
- Scheduler state remains available through the expanded central scheduler view.
- Public launcher resolution is more robust for applications whose package-level `MAIN` probe does not resolve directly.
- Documentation now describes standalone module discovery, Android interoperability boundaries, and saved integration definitions.

## Documentation

- Added module-local app-inspector implementation documentation.
- Added an ODK example workbook for `android_app_inspector`.
- Updated the repository README and Android developer guide.
- Documented the limits of generic intent-filter discovery and the expected workflow for combining inspection with application documentation or source review.

## Verification

The release candidate was validated with:

```text
./gradlew testDebugUnitTest assembleDebug
```

Both the unit-test suite and debug APK build passed.
