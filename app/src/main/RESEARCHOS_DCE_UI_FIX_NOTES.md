# ResearchOS DCE UI fix notes

This patch restores the discrete choice experiment screens as visible, runnable dashboard capabilities.

## What changed

- Added a dedicated **DCE / choice experiment runner** section near the top of the dashboard.
- The five DCE methods are expanded by default in that section and their focused runners are opened immediately.
- Removed DCE methods from the generic capability list to avoid presenting them twice.
- Fixed the malformed duplicate `totalSteps = 1` line in `HomeScreen.kt`.
- Changed pairwise, ranking, and points-allocation DCE interactions so their options render as Material cards, matching the visual behaviour of MaxDiff and conjoint.

## Expected behaviour

On app launch, the dashboard should show:

1. Runtime summary.
2. DCE / choice experiment runner.
3. Five visible DCE method cards:
   - Pairwise comparison
   - MaxDiff / Best-Worst
   - Ranking
   - Points allocation
   - Conjoint selection
4. Each DCE method card should contain its runnable screen immediately, including option/profile cards.

## Files changed

- `java/com/example/researchos/ui/HomeScreen.kt`
- `java/com/example/researchos/modules/choiceexperiment/ChoiceExperimentScreens.kt`
