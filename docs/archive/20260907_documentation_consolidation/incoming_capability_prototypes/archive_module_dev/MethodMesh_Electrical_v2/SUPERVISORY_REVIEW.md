# Electrical module — immediate supervisory review

## V1 assessment

The initial implementation direction was sound: a module-level workbench fits MethodMesh better than many disconnected calculator screens, and all selected calculations are deterministic/offline primitives. The result-first UI also aligns with the current dashboard doctrine.

However, V1 had three material design problems.

1. **Optional-input corruption risk.** Numeric defaults in capability settings made "unknown" V/I/R/P values non-null. That is unacceptable for an any-two Ohm's-law solver and would be especially confusing through presets or external invocation.
2. **AC power semantics.** An efficiency input had been applied inside the real-power calculation. That conflated electrical real input power with downstream useful/mechanical output power.
3. **Voltage-drop authority signalling.** The calculation was mathematically useful but could be read as a cable-sizing/compliance check unless the resistance-only approximation and regulatory boundary were explicit.

## V2 corrections

- Optional numeric preset fields are blank by default; defaults exist only where they represent actual assumptions (phase, PF, conductor material, temperature).
- Over-specified Ohm's-law inputs are checked for consistency rather than silently accepting contradictory values.
- AC real power is now `S × PF`; efficiency is removed from the core calculation.
- Voltage drop is explicitly labelled resistance-only and non-compliance-determining.
- Result cards are tap-to-copy, with explicit Copy and Share affordances.
- Tool-specific progressive disclosure keeps the workbench compact.
- One AS1.00 method provides stable automation/ODK/protocol access while the UI remains a single coherent control surface.

## Residual review findings

The biggest remaining UX weakness is unit entry: RC/RL/network inputs use base SI units. That is technically unambiguous but not field-friendly. Prefix-aware entry (µF, nF, kΩ, mA, etc.) should be the next refinement before calling the module polished.

The voltage-drop model intentionally omits conductor reactance. That is acceptable for this first calculator if the limitation stays explicit, but a later AC cable model should support R/X data or a standards dataset rather than overextending this approximation.

The workbench presently shares text results; a future electrical test/commissioning workflow will need a richer record object and likely CSV/PDF/shareable artifact export.
