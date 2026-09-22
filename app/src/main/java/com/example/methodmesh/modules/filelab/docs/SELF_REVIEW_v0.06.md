# File Lab v0.6 final self-review

1. **Established contracts preserved:** `file.inspect` and `file.convert` remain unchanged; v0.6 only adds new `file.inspect` outputs.
2. **Independent native execution:** both capabilities retain their own `CapabilityScreenSpec`.
3. **Presets/protocols:** both remain exported through `FileLabModule.as100Methods()` with typed settings where required.
4. **ODK parity:** the inspect showcase captures every current declared inspect output plus shared status/full JSON; conversion showcase remains aligned to the unchanged conversion contract.
5. **XLSForm status:** workbook structure and ZIP integrity checked; full device/Central validation remains a release check while maturity is Development.
6. **Task-specific native UI:** format explanation and file-specific evidence live on the inspection instrument itself.
7. **Commit semantics:** working inspection remains mutable until Commit freezes the canonical result values.
8. **Launch-origin closeout:** delegated to the shared MethodMesh capability scaffold as before.
9. **Lifecycle state:** selected URI, working inspection JSON and committed values remain `rememberSaveable`.
10. **Tap-to-copy:** useful displayed knowledge/fact/hash fields use `FileLabValue` and remain directly copyable.
11. **No generic-result detour:** inspection remains on the capability screen; the shared scaffold only handles generic lifecycle/export framing.
12. **ODK metadata/files:** showcase captures `methodmesh_full_json`; acquired source file semantics remain unchanged; conversion output remains the sole converted attachment.
13. **ODK Integration Card:** `README_FileInspect.md` documents inputs, call, outputs and file-return semantics.
14. **Tags:** one module maturity tag (`Development`) and one connectivity tag (`Offline`).
15. **No shared capability knowledge:** all format knowledge and Android handler logic are module-local.
16. **Clean handoff:** packaged as exactly one top-level `filelab/` folder.
