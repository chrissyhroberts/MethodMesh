# Field Statistics v0.2 — native UI/UX refresh

This release changes presentation only. Statistical formulas, public method IDs, settings keys, result field names and ODK contracts are preserved.

## Interaction principles

- Start with the field question, not the statistical implementation.
- Keep the common path short; specialist assumptions live under **Advanced settings**.
- Use layouts that match the mental model: diagnostic 2×2 matrix, Group A/B comparison cards, results-first summaries.
- Keep native calculations on-screen so inputs and results can be understood together.
- After the first calculation, changing an input refreshes the result without another start/submit cycle.
- Keep external/ODK execution single-shot and contract-compatible.
- Preserve explicit commit semantics for the persistent ROC explorer.
- Use MethodMesh theme tokens rather than capability-specific hard-coded styling.

## Major changes

- Diagnostic 2×2 counts are entered in a reference-standard × test-result matrix.
- Two-group binary comparison uses separate Group A and Group B input cards.
- Confidence level, design effect, FPC, attrition/non-response and similar specialist controls are progressively disclosed.
- Numeric fields request numeric/decimal keyboards.
- Results use prominent headline metrics plus compact secondary measures rather than a raw field dump.
- Validation failures appear as a clear **Check the inputs** card.
- ROC threshold selection has stronger visual hierarchy and keeps threshold, AUC, operating characteristics and confusion matrix together.

## Compatibility

No piping model is implemented or assumed. The existing Sampling module remains the owner of random sampling.
