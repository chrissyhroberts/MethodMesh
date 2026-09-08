## Clinical Instruments (Development)

Prototype offline **Clinical Instruments** runner (`clinical.instrument.run`) focused on linear, resumable clinical checklists and deterministic scoring instruments. MethodMesh owns the versioned YAML definition, question presentation, per-item validation, calculations/classification, temporary resumable state and provenance. ODK supplies case/context identifiers and stores the returned observations + derived values + score/classification + instrument version/definition hash rather than reimplementing established instruments in XLSForm.

Initial core definitions: qSOFA, CRB-65, AVPU and adult BMI classification. Core definitions are immutable but may be duplicated into editable local definitions. Users can also create/import/export local YAML definitions.

v0.1 intentionally has **no relevance/skip/branching language**. WHO Verbal Autopsy and other branching protocols are moved to a future protocol-engine design rather than represented incompletely in the current library.

Production gates: Android debug build; native/session/device tests; ODK round-trip; backup/privacy review for temporary clinical state; clinical/source/rights review of core definitions; curated library expansion; guided local-definition editor.
