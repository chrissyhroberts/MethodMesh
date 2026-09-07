# Future protocol engine — relevance, constraints and WHO Verbal Autopsy

Clinical Instruments v0.1 deliberately supports **linear checklists only**. Every declared question is presented in order. Questions may have local type/range/choice validation, and definitions may contain deterministic derived values, scores and classifications.

Conditional relevance/skip logic is **not** part of the v0.1 schema. This is intentional: once relevance is introduced, the engine must define cascading invalidation, navigation history, required-if semantics, cross-field constraints, repeated groups, retained/skipped values and what happens when an upstream response changes. That deserves a separately designed protocol language rather than a partial implementation.

## WHO Verbal Autopsy 2022

WHO Verbal Autopsy remains a high-value future target because it stress-tests exactly that richer protocol layer: long interviews, canonical identifiers, age/sex-specific sections, skip/flow logic, resumability and interoperability with downstream cause-of-death software. It is **not included in the v0.1 Clinical Instruments library** and should not be presented as runnable until the protocol engine exists and an exact WHO-conformant mapping has been validated.

Future work should preserve canonical WHO question IDs and coded values, pin the exact source release and definition hash, and validate generated output against supported VA analytical workflows. Cause-of-death assignment remains conceptually separate from interview execution.
