# Arcade v0.021 startup crash review

The strongest concrete startup fault in v0.02 was the result-wrapping path.

On ordinary native launch, v0.02 immediately called:

```text
refreshSnapshot()
  -> buildResult()
  -> As100ArcadeMethod.result()
  -> As100ExecutionEngine.complete()
```

Arcade's `result()` implementation differed from the already working GameDeck and
Chance result wrappers: it completed an observation/transformation without creating
the corresponding entity and without the standard status/diagnostics handling.

v0.021 fixes both sides:

1. `ArcadeMethod.result()` now mirrors the proven MethodMesh result-completion shape.
2. Merely displaying the native Arcade launcher does not create a graph result at all.

The second change is intentionally defensive: the shelf is presentation state, not a
meaningful game snapshot.

A defensive fallback was also added around local record reads so bad development
SharedPreferences values cannot take down the launcher.

If v0.021 still exits immediately, capture the Android stack trace because the
remaining failure will be outside these three startup paths.
