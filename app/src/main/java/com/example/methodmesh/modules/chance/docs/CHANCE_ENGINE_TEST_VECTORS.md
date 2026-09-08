# Chance selection engine regression notes

These complement the existing dice regression vectors.

Pure Kotlin smoke checks performed during prototype construction:

- Standard-deck draw of five cards with fixed seed `abc` is reproducible across repeated executions of the same engine version.
- A five-card draw contains five distinct cards (without replacement).
- `Pick one` / Numbers / six choices produces a permutation of exactly `1..6`.
- Spinner with three labels always returns an index in `0..2`.
- Weighted choice `A:5, B:3, C:1` returns only one of the declared choices and reports selected probability as `selected_weight / 9`.
- 5,000 deterministic-seed weighted-choice smoke executions stayed within valid option bounds.

Fixed-seed compatibility is versioned by `ChanceSelectionEngine.FIXED_ALGORITHM_VERSION`. Changing deterministic RNG semantics requires changing the version and updating these vectors.
