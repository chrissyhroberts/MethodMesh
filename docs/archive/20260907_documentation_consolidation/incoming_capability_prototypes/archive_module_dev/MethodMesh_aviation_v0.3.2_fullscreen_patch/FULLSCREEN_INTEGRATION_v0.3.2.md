# Aviation v0.3.2 — immersive/fullscreen integration

## Decision

The fullscreen guide recommends immersive hosting only where host-owned padding, Home chrome or scrolling materially interferes with the capability. Applying that test to the aviation pack:

| Capability | Host | Rationale |
| --- | --- | --- |
| `aviation.dashboard` | Immersive | Complex dashboard; benefits from fixed chrome/footer and capability-owned scrolling. |
| `aviation.emergency.instruments` | Immersive | Real-time glance surface; needs maximum bounded area and fixed emergency actions. |
| `aviation.airfield.nearby` | Standard | Compact parameter/result workflow. |
| `aviation.runway.wind` | Standard | Small calculator. |
| `aviation.altitude.calculate` | Standard | Small calculator. |
| `aviation.e6b.calculate` | Standard | Structured calculator. |

## Architecture

The two immersive screens set only:

```kotlin
override val hostPresentation = CapabilityHostPresentation.Immersive
```

No aviation-specific change is made to the host.

Because the immersive host supplies no Home button, padding or scroll container, each aviation screen now owns:

1. `fillMaxSize()` bounded root;
2. lightweight `AviationImmersiveTopBar`;
3. explicit Back/Exit actions using existing callbacks;
4. a single scrollable middle region constrained by `weight(1f)`;
5. a fixed snapshot/finish footer.

## Lifecycle

The persistent-dashboard semantics are unchanged:

- dashboard/native preset/`intent_test`: remain live;
- refresh/recomposition updates only capability-owned state;
- explicit **Use this snapshot / Finish** calls `onConfirmed`;
- external/ODK: once a result exists and completion mode is `AutomaticReturn`, the capability calls `onConfirmed` automatically.

## Regression focus

On device verify:

- no host Home button appears for the two immersive screens;
- no outer 18dp host padding surrounds them;
- dashboard and emergency middle regions scroll independently while top and bottom chrome stay fixed;
- no `Vertically scrollable component was measured with an infinity maximum height constraints` exception;
- Back and Exit behave correctly in single- and multi-step runs;
- the four Standard aviation capabilities retain ordinary MethodMesh chrome and scrolling;
- external ODK calls still return automatically.
