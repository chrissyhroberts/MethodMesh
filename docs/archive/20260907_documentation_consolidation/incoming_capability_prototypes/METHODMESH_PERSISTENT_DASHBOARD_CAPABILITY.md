# MethodMesh persistent dashboard capability pattern

## Purpose

Use this pattern for a MethodMesh capability whose primary native UI is a **live or refreshable dashboard** and which should **remain on that dashboard when launched as a native preset**.

The normal MethodMesh capability scaffold is result-oriented: once `capturedResult` becomes non-null during a manual/native run, the scaffold may replace the capability UI with the generic result/close-out screen. That is correct for ordinary capture capabilities, but wrong for a dashboard whose result is mainly a snapshot of a richer live view.

The dashboard therefore needs to separate:

1. the **latest calculated `ExecutionResult`**, which must still exist so it can eventually be returned/recorded; from
2. the **result passed to `CapabilityScreenScaffold`**, which should remain `null` while the native dashboard is meant to stay visible.

This is the pattern used by the astronomy dashboard.

---

## Required behaviour

A dashboard capability should behave differently depending on how it was invoked.

| Invocation | Behaviour |
|---|---|
| MethodMesh native dashboard/browser | Stay on the dashboard; refresh in place |
| Native preset | Stay on the dashboard; refresh in place; show a final **Finish** / **Use this snapshot** action |
| External caller / ODK | Run normally and automatically return a structured result |
| Protocol/sequence step | Follow the normal protocol completion contract rather than trapping the protocol on the dashboard |

The critical rule is:

> **Native preset execution must not hand the live dashboard result to the generic scaffold until the user explicitly chooses to finish/confirm.**

---

## Relevant MethodMesh context fields

`CapabilityScreenContext` exposes the information needed to make this decision.

```kotlin
context.presentationMode
context.isNativePresetRun
context.completionMode
context.submitsImmediately
context.isLastStep
```

In particular:

```kotlin
context.isNativePresetRun
```

is preferable to re-parsing `methodmesh_native_preset_run` from the request settings yourself.

`CapabilityPresentationMode.Dashboard` identifies normal native dashboard presentation.

A native preset may still have:

```kotlin
presentationMode == CapabilityPresentationMode.IntentLaunch
```

so checking `presentationMode == Dashboard` alone is **not sufficient**.

---

## Core implementation pattern

Keep the latest real result in local state:

```kotlin
var result by remember { mutableStateOf<ExecutionResult?>(null) }
var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
```

When the dashboard refreshes, calculate and retain the real result as usual:

```kotlin
val calculated = MyDashboardMethod.calculate(settings)
val execution = methodExecution(
    MyDashboardMethod,
    context,
    calculated,
    settings
)

values = calculated
result = execution
```

Then decide whether this invocation should stay in the live dashboard presentation:

```kotlin
val keepLiveDashboard =
    context.presentationMode == CapabilityPresentationMode.Dashboard ||
    context.isNativePresetRun
```

The key step is to **withhold the result from the scaffold** while keeping the live dashboard visible:

```kotlin
val scaffoldResult = if (keepLiveDashboard) null else result
```

Then use that value in `CapabilityScreenScaffold`:

```kotlin
CapabilityScreenScaffold(
    title = title,
    capabilityId = capabilityId,
    context = context,
    canGoBack = context.stepNumber > 1,
    capturedResult = scaffoldResult,
    resultPreview = scaffoldResult
        ?.let { OutputFormatter.fields(it, false) }
        .orEmpty(),
    onBack = onBack,
    onRetry = {
        result = null
        values = emptyMap()
        refresh()
    },
    onConfirm = {
        result?.let(onConfirmed)
    },
    onCancel = onCancel
) {
    DashboardBody(
        values = values,
        onRefresh = ::refresh,
        onUseSnapshot = if (keepLiveDashboard && result != null) {
            { onConfirmed(result!!) }
        } else {
            null
        },
        snapshotButtonLabel = if (context.isNativePresetRun) {
            "Finish"
        } else {
            "Use this snapshot"
        }
    )
}
```

This gives the scaffold `null` while the native dashboard is live, preventing it from switching to its generic result view. The genuine `ExecutionResult` remains available in `result` and is passed to `onConfirmed(...)` only when the user explicitly finishes.

---

## Minimal complete template

```kotlin
object ExampleDashboardCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = ExampleDashboardMethod.id
    override val title = "Example dashboard"
    override val description =
        "Refreshable dashboard. Refresh changes the preview; Finish returns the current snapshot."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var loading by rememberSaveable { mutableStateOf(false) }
        var attempted by rememberSaveable { mutableStateOf(false) }

        fun refresh() {
            if (loading) return
            loading = true

            // Fetch / calculate whatever the dashboard needs.
            val calculated = ExampleDashboardMethod.calculate(/* settings */)
            val execution = methodExecution(
                ExampleDashboardMethod,
                context,
                calculated,
                /* merged settings */ emptyMap()
            )

            values = calculated
            result = execution
            loading = false
        }

        LaunchedEffect(Unit) {
            if (!attempted) {
                attempted = true
                refresh()
            }
        }

        val keepLiveDashboard =
            context.presentationMode == CapabilityPresentationMode.Dashboard ||
            context.isNativePresetRun

        val scaffoldResult = if (keepLiveDashboard) null else result

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = scaffoldResult,
            resultPreview = scaffoldResult
                ?.let { OutputFormatter.fields(it, false) }
                .orEmpty(),
            onBack = onBack,
            onRetry = {
                result = null
                values = emptyMap()
                refresh()
            },
            onConfirm = {
                result?.let(onConfirmed)
            },
            onCancel = onCancel
        ) {
            Column {
                ExampleDashboardBody(
                    values = values,
                    loading = loading,
                    onRefresh = ::refresh
                )

                if (keepLiveDashboard && result != null) {
                    Button(
                        onClick = { result?.let(onConfirmed) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (context.isNativePresetRun) {
                                "Finish"
                            } else {
                                "Use this snapshot"
                            }
                        )
                    }
                }
            }
        }
    }
}
```

---

## Why the obvious implementation fails

This looks reasonable but is wrong for a persistent dashboard:

```kotlin
CapabilityScreenScaffold(
    ...
    capturedResult = result,
    ...
)
```

During a native preset run the dashboard calculates successfully, `result` becomes non-null, and the shared scaffold interprets that as:

> capture complete — show the normal result screen.

The user therefore sees the attractive dashboard briefly and is then moved to a less useful generic field/result view.

The fix is **not** to stop producing an `ExecutionResult`. The result is still needed for MethodMesh output, provenance and preset completion. The fix is to keep that result in capability-owned state while passing `null` to the scaffold until explicit confirmation.

---

## Do not globally change `CapabilityScreenScaffold`

Do not solve this by changing the scaffold so that all presets stay on their capability screen.

Most MethodMesh capabilities are capture/transform operations and should proceed to normal result handling after completion. Persistent presentation is a property of a specific dashboard-style capability.

Keep the behaviour local to the capability:

```kotlin
val scaffoldResult = if (keepLiveDashboard) null else result
```

This prevents changes to unrelated capabilities, ODK workflows, protocols and generic result handling.

---

## External / ODK execution

External invocation should still be machine-friendly.

For an external call:

```kotlin
keepLiveDashboard == false
```

so:

```kotlin
scaffoldResult == result
```

and the normal automatic-return behaviour remains available.

Do not require an external caller to press **Finish** on a dashboard.

A dashboard capability must therefore support both:

```text
native use     -> persistent interactive dashboard
external use   -> structured single-shot result
```

using the same underlying calculation/data engine.

---

## Capability-browser / `intent_test` caution

MethodMesh can launch capabilities internally with:

```text
source = intent_test
```

Do not automatically treat every `IntentLaunch` as a true external/ODK caller.

For dashboard-like or cache-management capabilities, an `intent_test` launch may still need to remain interactive. Where that distinction matters, use the established MethodMesh context helpers and/or explicitly exclude `intent_test` from auto-submit logic.

For example:

```kotlin
val genuineExternalAutoSubmit =
    context.submitsImmediately &&
    !context.request.source.equals("intent_test", ignoreCase = true)
```

This is particularly important when a first attempt can legitimately fail because required local data have not yet been cached: the capability should show the user how to fix that state rather than immediately returning an empty/failure result screen.

---

## Refresh semantics

A dashboard refresh should update only its live state:

```text
Refresh
   ↓
fetch / calculate
   ↓
update cards
   ↓
replace local result snapshot
   ↓
stay on dashboard
```

It should **not** record a new MethodMesh result on every refresh.

Only explicit confirmation should commit/return the chosen snapshot:

```text
Use this snapshot / Finish
   ↓
onConfirmed(result)
```

This avoids filling the graph/output history with transient refreshes.

---

## Recommended dashboard controls

For native use:

```text
[ Refresh ]

... live dashboard cards ...

[ Use this snapshot ]
```

For a native preset:

```text
[ Refresh ]

... live dashboard cards ...

[ Finish ]
```

`Finish` should return the most recent successful `ExecutionResult` through `onConfirmed(...)`.

If no valid result exists yet, disable or hide the button.

---

## Shared-engine rule

The dashboard should not invoke other capability screens in order to obtain their values.

Prefer:

```text
atomic capability ─┐
                   ├── shared calculation/data engine
persistent dashboard ─┘
```

For example, the astronomy dashboard calls the same underlying imaging-window calculation used by the atomic imaging-window capability. The dashboard aggregates the engine outputs; it does not simulate button presses or pipe itself through another screen.

---

## Checklist

Before considering a persistent dashboard complete, verify:

- [ ] `context.isNativePresetRun` is explicitly considered.
- [ ] Normal native dashboard use remains on the dashboard after refresh.
- [ ] Native preset use remains on the dashboard after refresh.
- [ ] `capturedResult` is `null` while persistent dashboard presentation is active.
- [ ] The genuine latest `ExecutionResult` is retained separately in local state.
- [ ] **Finish** / **Use this snapshot** calls `onConfirmed(result)` explicitly.
- [ ] The finish button is unavailable until a valid snapshot exists.
- [ ] Refresh does not record a graph result automatically.
- [ ] External/ODK invocation still returns without requiring native interaction.
- [ ] Internal `intent_test` launches are not accidentally treated as external machine calls where interaction is required.
- [ ] Protocol/sequence execution is tested separately so a persistent dashboard cannot trap a multi-step run.
- [ ] Dashboard and atomic capabilities share engines/repositories rather than duplicating domain logic.

---

## Reference implementation

The astronomy implementation uses this exact core pattern in:

```text
modules/astronomy/AstronomyDashboardCapabilityScreen.kt
```

The essential lines are:

```kotlin
val keepLiveDashboard =
    context.presentationMode == CapabilityPresentationMode.Dashboard ||
    context.isNativePresetRun

val scaffoldResult = if (keepLiveDashboard) null else result
```

followed by an explicit native action:

```kotlin
onUseSnapshot = if (keepLiveDashboard && result != null) {
    { onConfirmed(result!!) }
} else {
    null
}
```

That is the reusable MethodMesh pattern for a dashboard which remains visible when run as a preset.
