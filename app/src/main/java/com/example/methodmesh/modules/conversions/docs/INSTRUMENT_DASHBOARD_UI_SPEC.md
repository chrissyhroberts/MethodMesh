# MethodMesh Instrument Dashboard UI — compact specification

Version: 0.9  
Status: module-local implementation snapshot; project-wide authority is the MethodMesh Master Book

## Purpose

Interactive MethodMesh capabilities should feel like purpose-built instruments rather than generic settings forms. The Emergency Instruments and Compass surfaces are the reference direction: strong information hierarchy, a controlled visual identity, compact controls, immediate live state and a clear primary result.

This specification applies to instrument-like capability panels such as conversions, navigation, sensing, scoring, timing and other tools with live or rapidly changing values.

## 1. One coherent working surface

- Prefer one primary screen for configure/interact → live result → Commit.
- Do not navigate to a second screen merely to show a calculated result.
- Avoid wizard language for a direct single-capability run.
- Keep the current result visible in the same instrument while settings change.
- The shared MethodMesh shell may remain around the capability, but the capability-owned panel does not need to resemble the generic MethodSetting UI.

## 2. Visual hierarchy

The panel should read in this order:

1. **Instrument identity and current mode** — compact, not a large page title.
2. **Mode / major control selection** — immediately visible and quickly switchable.
3. **Task controls** — only the controls relevant to the selected mode.
4. **Primary live result** — visually strongest output, normally high in the panel like a calculator display.
5. **Task controls and value entry** — directly below the display, within thumb/keyboard reach.
6. **Working / secondary values** — subordinate to the primary result.
7. **Commit / finish** — compact and persistent, without competing with the working surface.

Avoid repeated headings that restate information already visible in the selected mode.

## 3. Instrument visual language

- Use a dark neutral instrument surface when it materially improves legibility and identity.
- Use one warm reference/accent colour for labels and instrument markings.
- Use the MethodMesh teal/green family selectively for active controls, successful state and actionable values.
- Reserve red/amber for warnings and degraded state.
- Prefer thin borders, quiet surfaces and deliberate spacing over nested cards and large filled buttons.
- Numeric readouts may use a monospaced treatment where it improves scanning and alignment.
- Large typography is reserved for the primary value, not navigation controls.

The goal is **technical, calm and field-ready**, not decorative dashboard chrome.

## 4. Density and touch behaviour

- Keep related controls adjacent; do not waste vertical space between labels and selectors.
- Visually compact selectors are encouraged when there are many bounded options.
- Controls must remain reliably tappable even if their visible treatment is small.
- Avoid horizontal carousels for core modes or commonly used units when all options can fit in a compact grid or row.
- Important modes should be visible without swiping.
- Do not turn every choice into a large card.


### Legibility and accessibility

- When viewport space is available, spend it first on **larger text and larger touch targets**, not decorative whitespace.
- Primary numeric readouts should normally be visually dominant (roughly 30–36sp on a handset where layout permits). Expression/value readouts should normally be around 20–24sp.
- Ordinary selector/key labels should normally remain around 13–14sp or larger; tiny microtext is reserved for genuinely secondary status annotations.
- Calculator keys and other high-frequency touch controls should aim for approximately 48dp touch geometry where the viewport permits it.
- Mode grids should reflow to more rows before compressing important labels.
- Respect Android font scaling as far as practical; fixed-height instrument controls must leave enough vertical room to avoid clipping at ordinary accessibility font scales.
- Full-bleed instrument content MUST respect Android status/navigation safe insets. App lifecycle controls such as Cancel, Back and Commit must never sit under system status icons or gesture/navigation chrome.
- When a mode matrix starts forcing shortened labels or sub-14sp core text, reduce the number of columns and add rows before reducing type size. On a typical handset, four readable columns are preferable to six cramped columns when the viewport has spare vertical space.

## 5. Live values and copying

- Recalculate immediately when inputs change if calculation is cheap and deterministic.
- Tapping the primary result copies the useful answer, including its unit when appropriate.
- Tapping the working/formula copies the working plus final answer.
- Copy confirmation should be subtle and transient.
- Do not require a dedicated Copy button where direct tap-to-copy is available.

## 6. Precision and transient controls

Controls that primarily alter presentation of the current result belong next to the result.

For example, numeric precision should be expressed as a small inline stepper beside the result header, not as a separate settings section. Changes apply immediately to both the displayed result and the copied working.

## 7. Conversions-specific projection

The Conversions capability uses this layout:

- compact instrument header: **CONVERSIONS / current mode / LOCAL · OFFLINE**;
- all conversion/calculator families visible at once in a readable reflowing mode matrix;
- for direct conversions, including Number representation, **FROM** and **TO** rows directly adjacent, with swap available in the same control area;
- expression entry immediately below the unit rows;
- live result immediately below the instrument header, calculator-style;
- decimal-place stepper beside the Result label;
- FROM and TO rows immediately beneath the result, followed by a larger value-entry field;
- the lower working region is a capability-owned calculator keypad with large, reliable touch targets; the system keyboard is not used for ordinary numeric conversion entry;
- tap answer = copy answer;
- tap working = copy full working + answer;
- no Calculate button and no result navigation.

Special modes such as percentage, ratio, date arithmetic, age and geometry use the same instrument frame and result treatment; only their task controls change.


## 8. Post-Commit action bay

Instrument dashboards do not lose the normal MethodMesh result-action contract merely because they use a custom/full-bleed surface.

- **Commit freezes the canonical execution result.** Working controls must not continue mutating that committed payload.
- A manual/native committed instrument reveals the customary compact actions **Copy**, **Share**, **Save to Downloads**, **Include full JSON / audit**, **Done**, and **Edit / new run** where applicable.
- The committed primary result stays visible; the action bay should use space that was previously occupied by transient working controls such as a keypad rather than opening a generic second result screen.
- The instrument may style these controls to match its own visual language, but it must delegate communication/persistence to the shared MethodMesh result projection and transport infrastructure rather than rebuilding receiver-specific Android intents.
- **Share** is communication-oriented and uses the shared beef-first share contract. **Save** is the explicit file-oriented Downloads persistence path. **Copy** copies the shared committed text projection. **Full JSON / audit** is off by default and uses the canonical FULL projection when enabled.
- External/ODK roundtrips suppress the manual Share/Save action bay unless their contract explicitly calls for it; Commit returns the canonical payload directly to the caller.
- **Done** performs launch-origin-aware closeout. **Edit / new run** returns to an editable working state without silently changing the already committed payload.

## 9. Contract boundary

The visual projection must not create a second capability contract. Dashboard/direct-native/preset/protocol/ODK surfaces continue to use the canonical method settings and return fields. Presentation-only controls such as decimal precision may be canonical settings where they affect the returned human-readable result, but visual styling remains local to the native surface.


## 10. Density and use of space

Instrument dashboards should behave like instruments, not like stacked forms. On a handset, the primary live workflow should normally fit within one screen before the system keyboard is shown, and should remain as compact as practical when the keyboard is visible.

- Prefer one compact status/header line over a title block with multiple stacked labels.
- Mode switching should consume the minimum vertical space **consistent with comfortable reading and tapping**. Reflow to additional rows before shrinking labels below a useful size; density must not be achieved by microscopic typography.
- Secondary unselected controls should be visually quiet: transparent or near-transparent backgrounds and low-contrast borders. Selection, not every control, should carry the accent colour.
- Related controls belong on the same visual axis. For unit conversion, `FROM` and `TO` are consecutive rows with the same geometry; the swap control belongs within that pair.
- Input fields should not automatically occupy full card-height form rows. A compact labelled inline field is preferred when only one primary value is required.
- The result is the primary visual anchor. For calculator-like instruments it should sit near the top, remain visible while editing, and carry precision controls locally when precision can be changed interactively.
- Avoid decorative whitespace between mode selection, input controls and result. Spacing should communicate grouping rather than act as padding for its own sake.
- A dashboard may deliberately ignore generic MethodMesh form styling inside its instrument surface when doing so improves instrument legibility and density, while preserving the MethodMesh capability lifecycle and contracts around it.


## 11. Full-bleed instrument mode

For capabilities whose primary interaction is an instrument rather than a form, the capability may replace the generic `CapabilityScreenScaffold` with a capability-owned full-bleed surface. This is preferred when the shared scaffold would spend more screen area on step/status/version chrome than on the instrument itself.

- The instrument should consume the full capability viewport available beneath the app-level navigation.
- Generic step badges, repeated capability title/ID, module version/development badges, Retry and large footer actions should not be duplicated inside the instrument view.
- Lifecycle actions still have to exist. Back, cancel and commit/use may be projected as compact controls in the instrument header.
- Live calculation removes the need for a Retry action.
- The instrument should not manufacture empty vertical space merely to fill the viewport. Calculator-like instruments should use the upper working region efficiently and assume the software keyboard may occupy roughly the lower third while editing.
- The capability contract, settings visibility rules, invocation context, Commit lifecycle, presets/protocols and ODK behaviour remain unchanged. This is a presentation override, not a new execution path.

For Conversions specifically, the dark instrument surface is the capability body rather than a card inside the generic white capability card. In immersive presentation the capability owns its compact Back/Cancel/Commit chrome while still respecting Android system-bar insets and launch-origin closeout.


## 12. Integrated calculator keypad layout

For calculator-style capabilities such as Conversions, prefer a capability-owned keypad when the task is primarily numeric. This keeps the interaction inside the instrument, avoids system-keyboard layout shifts and permits arithmetic expressions to be evaluated before the domain calculation.

- The result/display sits near the top of the instrument and remains visible while an expression is being entered.
- Live result/readout bays reserve their normal populated geometry from the start. Empty, calculating, failed and populated states must not change the height of the readout or push surrounding controls; use stable one-line previews/ellipsis where necessary and keep the full value available through the existing copy interaction.
- Mode selection follows the display, then paired selectors such as FROM/TO, then the expression readout.
- The lower roughly one-third of the instrument is reserved for the keypad. It should be large enough for reliable one-handed tapping without visually dominating the readout.
- The ordinary numeric keypad supports digits, decimal point, `+`, `−`, `×`, `÷`, parentheses, backspace and clear. Where results are already live, do not waste a primary key on a no-op equals button: use that high-value position for backspace/edit instead. Scientific/engineering number entry may add `EXP`; base-conversion modes should expose only digits valid for the selected source radix.
- Arithmetic is parsed locally with normal operator precedence and unary negative. Do not use `eval` or execute arbitrary code.
- A valid expression is evaluated live. The resulting scalar becomes the input to the selected conversion or calculation; for example `20 × 44` in millimetres is evaluated as `880 mm` before conversion.
- `=` may act as an explicit confirmation affordance, but live calculation must not depend on pressing it.
- Working must preserve both stages when an expression is used: expression evaluation followed by the domain calculation/conversion.
- Tapping the primary answer still copies the answer; tapping working copies expression + evaluation + calculation + answer.
- Numeric expression entry must not summon the system keyboard. Date/text-specific modes may use an appropriate native input where a calculator keypad is not suitable.
