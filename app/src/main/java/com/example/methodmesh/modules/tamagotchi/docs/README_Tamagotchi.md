# Tamagotchi Lab

Status: **Development / module-first implementation**

Tamagotchi Lab is a configurable, local-first longitudinal teaching simulator disguised as a very cute virtual creature. The learner experiences a living companion; MethodMesh records a reproducible intervention/observation history suitable for later analysis.

## Canonical capabilities

| Capability | Purpose |
|---|---|
| `teaching.tamagotchi.session` | Start/resume/end a persistent care period and expose the rich care dashboard. |
| `teaching.tamagotchi.intervene` | Apply a configured or generated care action. |
| `teaching.tamagotchi.observe` | Record a purposive visible observation. |
| `teaching.tamagotchi.measure` | Record one scenario-authorised measurement. |
| `teaching.tamagotchi.advance` | Advance classroom/turn-based simulation time. |
| `teaching.tamagotchi.history` | Inspect care-visible history and trajectories. |
| `teaching.tamagotchi.analyse` | Reveal latent state after the care period ends. |
| `teaching.tamagotchi.export` | Export the longitudinal teaching dataset ZIP. |
| `teaching.tamagotchi.configure` | Inspect/validate scenario configuration. |

The dashboard is only a composition of these capabilities. The canonical capability IDs remain independently available to native launch, Presets, Protocols, RIL and ODK.

## Starter creatures

The starter cohort is intentionally original and phenotype-bearing rather than six interchangeable skins:

- **Mossbit** — tiny forest optimist; leaf ears and a treasured button.
- **Nib** — aquatic creature; maximum frill wiggle.
- **Mallow** — possibly fur, possibly cloud; quietly social.
- **Pip** — long ears and enormous feet; energetic bursts plus serious naps.
- **Sprig** — mobile botanical problem; visibly orients toward light.
- **Tumble** — round, stoic and technically ambulatory; mostly rolls.

Creature definitions affect expression, idle behaviour and sleep/activity style. Scenario definitions own the actual state variables and interventions, so teaching models can be swapped without rewriting creature art.

## Starter scenarios

### Foundation Care

Care-visible variables include satiety, hydration, energy, mood, cleanliness and stress. A hidden resilience variable exists for analysis. It includes a procedural **mystery snacks** generator; the student sees cute option names while the seed-reproducible effect parameters are logged latently.

### Hidden Infection

Care-visible signals include hydration, energy, mood, stress and measurable temperature. Infection burden and drug susceptibility are latent until analysis unlock. Two candidate medicines have different hidden effects.

### Sprig Growth Lab

A non-animal example using soil moisture, light, nutrition, growth, leaf vigor and latent root stress. This demonstrates that the Tamagotchi engine is a generic longitudinal care/simulation engine rather than a hard-coded pet game.

## Authoritative history and scientific separation

Each session creates one persistent MethodMesh JSONL artifact. That artifact is the authoritative history.

Events distinguish:

- `care` visibility — may be shown during care;
- `audit` visibility — system/attention records such as suppressed notifications;
- `latent` visibility — hidden state transitions, generated option parameters and hidden intervention effects.

**Rendered phenotype is not an observation.** A sprite can visibly droop, sleep or perk up without MethodMesh claiming the learner saw it. Only explicit Observe/Measure actions create formal observation events.

The session snapshot is a resumable projection. The JSONL ledger is the history.

## Timing and between-session behaviour

Supported simulation clocks:

- `real_time`
- `accelerated`
- `classroom`
- `turn_based`

Supported between-session modes:

- `pause`
- `sleep`
- `autonomous`
- `continuous`

The default child-friendly setup uses classroom time plus `sleep`, so a learner is not punished because they were at school or asleep.

## Notifications / attention policy

Notification behaviour is deliberately policy-driven rather than `notify=true`.

Session settings include:

- maximum notifications/day;
- minimum spacing;
- school start/end;
- quiet-hours start/end;
- notification profile.

The receiver periodically evaluates the policy using an inexact `AlarmManager` wakeup. Candidates suppressed because of quiet hours, school hours, spacing or the daily cap are recorded as `notification_suppressed`. Delivered notifications are `notification_delivered`.

Notification copy is intentionally non-guilt-based.

## Desktop companion status

The cute desktop-companion concept is deliberately **not registered by this module**. MethodMesh modules do not patch the host manifest, add shell activities/services, or mutate app-level UI/navigation. A persistent Android overlay requires a host-owned generic overlay facility; until MethodMesh exposes one, Tamagotchi remains entirely inside its canonical capability surfaces.

Background care reminders remain supported through MethodMesh's existing **core Scheduler**. The module registers ordinary scheduled capability launches and therefore owns no Android receiver/service/manifest component.

## Export package

`teaching.tamagotchi.export` creates a ZIP containing:

```text
manifest.json
scenario.json
session_snapshot.json
history.jsonl
 actions.csv
observations.csv
states.csv
generated_options.csv
notifications.csv
analysis/README.txt
analysis/latent_final_state.json   # only after care ends and analysis requested
```

`history.jsonl` remains authoritative. CSV files are derived conveniences for R, Python, Excel, Stata, etc.

Latent state is never exported during active care even when `include_analysis=true`. The care period must first be ended.

The returned fields include:

- `tamagotchi_export_uri`
- `tamagotchi_export_sha256`
- `tamagotchi_export_artifact_ref`

## ODK

`example_odk_tamagotchi_teaching.xlsx` demonstrates a longitudinal classroom journey across all canonical capability classes. The XLSForm passes and returns the persistent `tamagotchi_session_id` rather than treating each capability execution as a separate virtual pet.

The expected MethodMesh action pattern is:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='teaching.tamagotchi.observe',input_session_id=${tamagotchi_session_id},input_payload_mode='FULL',return_mode='flat')
```

## Integration

Copy this entire `tamagotchi/` folder into:

```text
app/src/main/java/com/example/methodmesh/modules/tamagotchi/
```

The current MethodMesh module discovery contract expects the module object:

```text
com.example.methodmesh.modules.tamagotchi.TamagotchiModule
```

`docs/module_index_entry.txt` contains that line for builds where the raw module index is generated/maintained separately.


## Dependencies

No new third-party library is introduced by the module. It uses:

- Jetpack Compose / Material 3 already used by MethodMesh;
- MethodMesh core Scheduler for background care reminders;
- existing MethodMesh `ArtifactService`, `FileProvider`, execution runtime and capability scaffold;
- `org.json` from Android.

## Privacy / network

The module is fully local-first. It does not require the network and does not transmit creature history, student actions, notification exposure or exported datasets off device.

## v0.1.2 interaction hardening

Canonical capability screens follow the MethodMesh v1.05 live-working-result → Commit lifecycle. A working observation/intervention/measurement/history/analysis/export result stays on the capability screen; it is **not** passed into the shared scaffold as `capturedResult`. Dashboard/native runs leave only through an explicit **Commit** action. External/ODK automatic-return launches still return as soon as the requested operation has valid inputs and succeeds.

Runtime state errors are failure-contained. Missing/stale session IDs, invalid actions or measurements, export errors, missing overlay manifest integration and overlay service/window failures are surfaced in the capability UI rather than deliberately crashing the host process.

## v0.1.3 architecture correction

Removed the Tamagotchi-specific Android overlay Activity, foreground Service, BroadcastReceiver and all host-manifest patch/install files. The module no longer attempts to alter MethodMesh app-level UI or Android component registration. Timed care reminders now consume MethodMesh's existing core Scheduler. Desktop-companion rendering is deferred until the host exposes a generic module-safe overlay surface.
