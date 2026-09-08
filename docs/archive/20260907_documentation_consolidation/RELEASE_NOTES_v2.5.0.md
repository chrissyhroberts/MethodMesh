# MethodMesh v2.5.0

MethodMesh v2.5.0 adds the first shared artifact foundation and packages the banked module and ODK work from the real project checkout.

## Highlights

- Added a core Artifact Service that separates artifact identity and handoff from persistence.
- Added bundled, managed and external artifact origins.
- Added persistent, session and transient artifact lifecycles.
- Added explicit artifact references and a shared picker contract.
- Added the Files surface for importing and browsing persistent artifacts.
- Added generated module-owned ODK/XLSForm asset projection while keeping module folders as the canonical source of truth.
- Preserved drag-and-drop module packaging; source workbooks are copied and indexed during the build rather than moved.
- Added hashes, provenance fields and policy findings to generated XLSForm catalogue entries.
- Included the banked Arcade, GameDeck, Digital Signing, Reference Library, Trusted Timestamp, Kobo and ODK work.
- Kept existing module-owned workbooks, deployed form identities and capability contracts intact.

## Validation

- Kotlin compilation succeeds for the real Android Studio checkout.
- The generated ODK/XLSForm catalogue runs from module-owned `docs/` folders.
- The artifact service and catalogue tests are included in the repository.

The full project test suite still contains pre-existing documentation and XLSForm contract failures. Device-level signing, sharing and ODK roundtrip testing remain follow-up validation for the next increment.

## Upgrade notes

Files is the first consumer of the shared artifact layer. Reference Library, signing and timestamp workflows retain their existing repositories and contracts in this release; they can migrate incrementally to shared artifact references.
