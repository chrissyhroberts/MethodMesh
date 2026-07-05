# ResearchOS DCE capability panel integration fix

## Problem
The previous patch made the DCE / choice experiment screens work, but did so by adding a separate dashboard playground/debug runner. That proved the DCE screen implementations were valid, but it broke the intended UI model: DCEs should be normal canonical capabilities inside the Capabilities panel, not a parallel top-level feature.

## Fix
- Removed the standalone `DcePlaygroundCard` from `HomeScreen`.
- Stopped filtering `dce.*` methods out of `CapabilityRegistryCard`.
- DCE methods now appear in the same canonical Capabilities panel as every other AS100 method.
- DCE capability screens are rendered through the same `CapabilityCard` / `DashboardCapabilityRunner` path as NFC, GPS, admin fingerprint, and other focused screens.
- DCE cards are initially expanded with their in-place runners open, so the working option/profile cards are visible inside the Capabilities panel immediately.

## Files changed
- `java/com/example/researchos/ui/HomeScreen.kt`

## Architectural intent
The Capabilities panel is now the single dashboard execution surface. Modules still register their methods and screen specs once via `ResearchOSModuleRegistry`; the dashboard merely discovers and renders them in place.
