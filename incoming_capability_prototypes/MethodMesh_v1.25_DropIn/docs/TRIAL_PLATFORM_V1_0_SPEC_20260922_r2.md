# Trial Platform v1.0 Architecture — ODK Central + Sentinel + MethodMesh

**Status:** v1.0 architecture baseline and implementation candidate; not yet a validated production clinical-trial system
**Revision:** 2026-09-22 r2 — universal temporal provenance, frozen MethodMesh software provenance, shared-core Clock Assurance ownership, Workbench/Home assurance surfaces, and explicit Sentinel temporal-policy interpretation
**Primary use case:** Regulated clinical trials using ODK for field and operational data capture, designed to scale from a small pilot to many concurrent trials
**Architecture:** ODK Central + Sentinel + MethodMesh + independent backup + third-party timestamp authority (TSA) + independent watchdog
**Design principle:** ODK remains the durable authoritative data plane; Sentinel is the central governance, reconciliation, enforcement, reconstruction and attestation layer; MethodMesh is the offline-capable local execution and assurance layer at the person/device boundary.

> **Revision note.** This is the second controlled 2026-09-22 revision of `TRIAL_PLATFORM_V1_0_SPEC_20260918.md`. It updates the cross-system Clock Assurance and MethodMesh execution-envelope contract after implementation of the shared MethodMesh core Clock Assurance service and alignment with MethodMesh Master Book v1.25. It also makes producing module/capability version and maturity immutable execution provenance. Sections and requirements not amended below remain unchanged from the 2026-09-18 v1.0 architecture baseline.

---

## 1. Architectural effect of this revision

This revision does **not** change the three-layer platform model:

```text
ODK Collect / field workflow
        ↕
MethodMesh local/offline execution + assurance
        ↓
ODK Central durable authoritative data plane
        ↕
Sentinel governance / reconciliation / verification
```

It clarifies a cross-cutting contract that was already present in the v1.0 design: temporal provenance is universal MethodMesh execution evidence.

The revised rule is:

> Every generated MethodMesh FULL execution envelope carries a versioned, policy-neutral `time_assurance` object describing the temporal evidence available at execution time. A capability or governed workflow whose result materially depends on time applies an explicit temporal policy separately and records the resulting `time_decision`. Sentinel preserves the original evidence and may later evaluate it under trial/action policy without rewriting the historical MethodMesh envelope.

This revision also corrects earlier wording that described Clock Assurance as a **Workbench** service. Clock Assurance is shared **MethodMesh core infrastructure**. Workbench may expose diagnostics and inspection views, but it is not the architectural owner of trusted-current-time evidence.

The canonical FULL envelope also freezes the producing **module ID/version/maturity** and **capability ID/version/maturity** at execution completion. Sentinel therefore receives the implementation identity that actually produced the field evidence rather than a version looked up after a later MethodMesh upgrade. Capability version is the authoritative reproducibility identifier for an individual execution.

---

## 2. Cross-system temporal-provenance principle

### 2.1 Evidence is universal; enforcement is selective

Every MethodMesh capability execution may have a timestamp, but not every capability makes a time-dependent decision.

The platform therefore distinguishes:

- **temporal evidence** — what MethodMesh knew about time at the moment of execution; and
- **temporal decision** — what an explicitly governed capability/workflow did with that evidence.

The universal MethodMesh FULL envelope contains temporal evidence even for ordinary capabilities such as compass, barcode, text processing or sensor capture. Those capabilities do not acquire an implicit trial freshness policy simply because their audit envelope includes time provenance.

Where time materially affects an outcome — for example credential expiry, protocol eligibility, timed approval, scheduling validity, safety SLA interpretation or another governed action — the capability/workflow must use an explicit temporal policy and record that decision separately.

### 2.2 MethodMesh core owns Clock Assurance

Clock Assurance is shared MethodMesh core infrastructure.

It separates:

- Android wall-clock observation;
- same-boot monotonic elapsed time;
- boot/session continuity;
- externally validated trusted-time anchors;
- trusted-time lower and upper bounds;
- accumulated uncertainty;
- anchor provenance and age;
- wall-clock divergence or rollback evidence.

Android wall time is an observation, not inherently trusted time.

Monotonic time is elapsed-duration evidence, not UTC time. Reboot or other boot-session discontinuity breaks the continuity needed to advance a previous trusted anchor.

Trusted Timestamp is an evidence source for Clock Assurance, not the owner of the service. A validated RFC 3161 result may refresh the shared anchor only where the MethodMesh trust contract for that authority has been satisfied.

### 2.3 Historical evidence is immutable

The MethodMesh `time_assurance` object records what evidence existed when the capability execution occurred.

A later network connection, newer trusted timestamp, Sentinel run or server receipt time must not silently replace or upgrade that historical snapshot.

Sentinel may derive a later governance interpretation from the retained evidence, but it must preserve the source MethodMesh envelope and distinguish:

- original MethodMesh temporal evidence;
- ODK submission/receipt time;
- Sentinel observation time;
- any later independent TSA time;
- any derived Sentinel policy result.

---

## 3. MethodMesh FULL execution-envelope contract

### 3.0 Frozen software provenance

Every generated MethodMesh FULL execution envelope identifies the implementation that produced the execution:

```json
{
  "methodmesh_envelope_schema_version": "2",
  "module": {
    "id": "nfc",
    "version": "2.3.0",
    "maturity": "development"
  },
  "capability": {
    "id": "nfc_credential_verification",
    "version": "2.0.0",
    "maturity": "development"
  }
}
```

The exact module/capability identity, version and maturity are captured when the execution completes. Later application/module upgrades must not rewrite historical sidecars. Multi-capability combined workflows may additionally carry a `capabilities` array retaining each distinct producing capability/version.

Sentinel must treat capability version as implementation provenance material to reproducibility and interpretation. Maturity is also historical context: later promotion from Experimental to Development or Production does not retroactively change the maturity under which an old execution occurred.

### 3.1 Universal `time_assurance`

Every generated canonical MethodMesh FULL execution envelope must contain a top-level versioned `time_assurance` object.

Representative structure:

```json
{
  "time_assurance": {
    "schema_version": "1",
    "observed_wall_time_iso": "2026-09-22T08:37:14.218Z",
    "evidence_state": "trusted_interval_available",
    "monotonic": {
      "elapsed_realtime_ms": 58239112,
      "boot_session_id": "android_boot_count:42",
      "continuity": "same_boot"
    },
    "trusted_time": {
      "estimate_iso": "2026-09-22T08:37:13.991Z",
      "lower_bound_iso": "2026-09-22T08:37:13.742Z",
      "upper_bound_iso": "2026-09-22T08:37:14.240Z",
      "uncertainty_ms": 249
    },
    "anchor": {
      "source": "rfc3161",
      "evidence_hash": "...",
      "age_ms": 371991
    },
    "wall_clock": {
      "relation": "within_trusted_interval",
      "offset_from_trusted_interval_ms": 0
    },
    "reason": "Trusted same-boot interval is available."
  }
}
```

The exact serialisation belongs to the MethodMesh Clock Assurance/execution-envelope contract and evolves through an explicit schema version.

The object is **policy-neutral**. It records evidence rather than claiming that a particular trial action should be accepted or rejected.

### 3.2 Reduced assurance is explicit

`time_assurance` remains present where trusted time is unavailable.

Representative evidence states include:

- `trusted_interval_available`;
- `wall_clock_only`;
- `reboot_unanchored`;
- `indeterminate`.

Wall-clock comparison is a separate dimension and may additionally report states such as:

- `consistent`;
- `diverged_forward`;
- `rollback_detected`;
- `not_comparable`.

The platform must not represent weak assurance by omitting temporal evidence or by silently treating device wall time as trusted.

### 3.3 Explicit `time_decision`

Where a capability or governed workflow materially depends on time, it records its explicit temporal evaluation separately.

Representative structure:

```json
{
  "time_decision": {
    "schema_version": "1",
    "policy_id": "nfc_credential_validity_v1",
    "result": "within_window",
    "time_used_iso": "2026-09-22T08:37:13.991Z",
    "assurance_status": "trusted_fresh",
    "assurance_satisfied": true,
    "reason": "Credential validity interval is satisfied under the declared policy."
  }
}
```

`time_assurance` answers:

> What temporal evidence was available?

`time_decision` answers:

> What did this capability or workflow decide under its declared policy?

An ordinary capability that does not use time semantically should omit `time_decision`.

---

## 4. ODK contract

ODK/XLSForm continues to capture the standard MethodMesh transport fields:

```text
methodmesh_status
capability-specific useful return fields
methodmesh_full_json
```

The complete universal `time_assurance` object and frozen module/capability software provenance are carried inside `methodmesh_full_json`.

Canonical XLSForms should **not** add a large synthetic family of hidden universal clock fields merely to flatten the sidecar.

A time-sensitive capability may expose a small number of declared convenience fields where the ODK workflow actually needs them, for example:

```text
clock_assurance_status
credential_time_status
decision_time_iso
```

Those convenience fields do not replace the complete temporal evidence in the FULL envelope.

ODK remains the durable record. MethodMesh does not retain a second permanent clinical datastore merely because it generated the evidence.

---

## 5. Sentinel contract

Sentinel must parse and preserve the MethodMesh `time_assurance` object as source execution evidence.

Sentinel may then apply governed trial/action policy to determine whether the recorded evidence was adequate for the action being assessed.

Sentinel must distinguish:

- producing MethodMesh module ID/version/maturity;
- producing capability ID/version/maturity, including each distinct capability in a combined workflow;
- MethodMesh FULL-envelope schema version;
- MethodMesh observed wall time;
- MethodMesh trusted-time estimate/bounds where available;
- uncertainty;
- anchor source, evidence commitment and age;
- boot/monotonic continuity;
- wall-clock anomaly state;
- MethodMesh `time_assurance` schema version;
- capability/workflow `time_decision`, where present;
- the Sentinel policy/version used for any later central interpretation.

A Sentinel-derived temporal judgement must be recorded as a **derived governance result**, not substituted back into the original MethodMesh sidecar.

This permits later governance questions such as:

- Was the credential cryptographically valid?
- What temporal evidence existed at verification time?
- Was the credential certainly within, certainly outside or overlapping its validity boundary?
- Was the clock anchor sufficiently fresh under the applicable trial policy?
- Was wall-clock rollback/divergence visible?
- Did the local capability itself enforce a temporal policy, or is Sentinel evaluating the evidence retrospectively?

---

## 6. Revised trial-design YAML pattern

The earlier illustrative YAML treated `default_max_age` / `stale_action` too much like a hidden universal MethodMesh capability policy.

The revised pattern separates universal evidence from named governed temporal policies:

```yaml
schema_version: "1.0"

methodmesh:
  enabled: true
  offline_field_assurance: true
  assertion_contract: attestation.schema.v4

  # Universal FULL-envelope temporal evidence is a MethodMesh platform contract,
  # not an optional per-trial switch.
  expected_time_assurance_schema_versions: ["1"]

  temporal_policies:
    staff_credential_validity:
      policy_id: nfc_credential_validity_v1
      max_anchor_age: 24h
      max_uncertainty: 2m
      insufficient_assurance_action: block

    correction_approval:
      policy_id: correction_approval_time_v1
      max_anchor_age: 24h
      max_uncertainty: 5m
      insufficient_assurance_action: warn

    database_lock_approval:
      policy_id: database_lock_time_v1
      max_anchor_age: 1h
      max_uncertainty: 1m
      insufficient_assurance_action: require_check

  assurance_policies:
    correction_approval:
      required: true
      verification_methods: [nfc_pin, device_credential]
    database_lock_approval:
      required: true
      verification_methods: [device_credential, biometric]
```

There is deliberately no universal hidden `stale_action` applied to every MethodMesh capability.

A trial may define a default **Sentinel review policy** for classes of governance events if useful, but that is a governed central interpretation rule and must not be confused with a policy silently executed by all MethodMesh capabilities.

---

## 7. Replacement for the v1.0 Clock Assurance section

### 7.1 Purpose

MethodMesh clock assurance does not claim that an offline device clock is infallible. It makes the quality, provenance and uncertainty of local event time visible and auditable alongside ODK receipt time, Sentinel observation time and any later TSA time.

Clock Assurance serves two related but distinct purposes:

1. provide reusable trusted-current-time evidence for local MethodMesh capabilities that genuinely need time-sensitive decisions; and
2. provide a policy-neutral temporal-provenance snapshot in every MethodMesh FULL execution envelope.

### 7.2 Offline model

Once a suitable externally validated trusted-time anchor exists, MethodMesh may advance a bounded trusted-time interval while same-boot monotonic continuity survives. It does not require a network connection for every execution.

Uncertainty grows with acquisition uncertainty, source precision and elapsed-time oscillator assumptions. Reboot breaks monotonic trust continuity until a suitable new anchor is established.

### 7.3 Wall-clock anomaly

Wall-clock divergence and trusted-time availability are separate axes.

A device wall clock may be materially behind or ahead of the assured interval while the same-boot monotonic trusted interval remains usable. Conversely, the wall clock may appear plausible while no trusted anchor exists.

MethodMesh therefore records both temporal evidence and wall-clock comparison status rather than collapsing them into a single Boolean “clock valid” field.

### 7.4 External trusted time

A third-party RFC 3161 timestamp may act as a Clock Assurance anchor only where the relevant MethodMesh trust contract has validated the configured authority/signer chain and retained a cryptographic commitment to the evidence.

A self-consistent or custom/unconfigured TSA response must not silently become platform trusted current time.

### 7.5 Policy boundary

Clock Assurance core supplies evidence.

The action-specific policy determines whether that evidence is adequate for the decision.

Examples include:

- NFC credential validity;
- protocol eligibility windows;
- governed approval freshness;
- timing-sensitive safety workflow checks;
- database lock/finalisation actions.

Where the trusted uncertainty interval overlaps a relevant time boundary, the decision should remain overlapping/indeterminate rather than being forced to one side using the midpoint alone.

### 7.6 Workbench and Home role

Workbench is the canonical human inspection/control surface for Clock Assurance. It exposes policy-neutral evidence such as observed wall time, trusted estimate/bounds, anchor source/age, boot/monotonic continuity, wall-clock relation/divergence and evidence/model schema versions.

Where an externally trusted acquisition provider is available, Workbench provides an explicit **Sync trusted time** action. This obtains and validates a fresh external observation through the normal trusted-anchor admission path. It refreshes MethodMesh evidence and **does not set the Android system clock**.

Home may expose a discreet policy-neutral recency/evidence indicator (for example anchor age, unanchored, reboot or check) and link directly to the Workbench inspector. It must not manufacture a universal freshness threshold.

Workbench is **not** the architectural owner of Clock Assurance and a normal capability does not depend on Workbench being opened.

---

## 8. Replacement wording for MethodMesh responsibilities (§28.1)

Replace the earlier responsibility:

> shared clock-assurance service in the Workbench, with silent freshness checks and global provenance output for every capability

with:

> shared **core Clock Assurance** infrastructure supplying versioned policy-neutral temporal evidence to every generated FULL capability envelope, plus explicit temporal-policy evaluation for capabilities/workflows whose result materially depends on time; Workbench provides inspection and deliberate trusted-time refresh while remaining a control surface rather than the service owner; Home may expose compact policy-neutral recency.

Retain the adjacent managed-device policy principle:

> managed-device clock-policy observation/integration, with MDM/device policy preferred for enforcing automatic network time.

Managed-device automatic time is a useful preventative control, but it does not replace the retained MethodMesh evidence model.

---

## 9. Revised v1.0 pilot qualification requirement (§29)

Replace:

> MethodMesh global clock-assurance evidence included in every capability envelope, with Workbench visibility and trial-configurable freshness policy

with:

> MethodMesh FULL envelopes freeze producing module/capability version+maturity and include versioned policy-neutral `time_assurance` evidence through shared core infrastructure; Workbench exposes evidence and deliberate trusted-time refresh; representative time-sensitive workflows demonstrate explicit named temporal policies and separate `time_decision` evidence; Sentinel parses/preserves the original software/time evidence and applies configured trial/action policy without rewriting the source envelope.

---

## 10. Revised PoP acceptance criterion 22 (§30)

**22.** Every generated MethodMesh FULL capability envelope carries a versioned policy-neutral `time_assurance` object describing the temporal evidence available at that execution. Sentinel can parse and preserve the recorded wall-time, trusted-time bounds/uncertainty, anchor provenance/age, monotonic/boot continuity and anomaly evidence. Where a capability or governed workflow declares a temporal-assurance policy, insufficient/stale/indeterminate assurance produces the configured warn/check/require-check/block behaviour and the applied temporal decision is recorded separately from the underlying evidence.

This replaces the earlier wording that could be read as applying one stale-clock enforcement policy to every capability invocation.

**23.** Every generated MethodMesh FULL execution envelope records the producing module/capability ID, version and maturity captured at execution completion. An execution generated before a MethodMesh upgrade remains attributable to the older capability version when exported/verified after that upgrade; Sentinel preserves and exposes that producing version rather than substituting the currently installed version.

---

## 11. Revised architectural Claim 14 (§32)

**Claim 14 — temporal provenance is universal while temporal enforcement is policy-specific.**
MethodMesh FULL execution provenance freezes producing module/capability version+maturity and includes versioned policy-neutral Clock Assurance evidence for every generated capability envelope. Capabilities/workflows whose result materially depends on time apply explicit named temporal policy separately. Sentinel preserves the original evidence and may derive later governance interpretation without rewriting the historical local execution envelope. Managed-device automatic time remains preferred as an additional preventative control where available.

---

## 12. Revised implementation/qualification table language (§33)

### MethodMesh envelope row

| Item | v1.0 requirement | State / work needed |
|---|---|---|
| MethodMesh envelopes | Trial forms invoking MethodMesh retain the canonical FULL execution envelope containing frozen module/capability version+maturity and universal versioned policy-neutral `time_assurance`; time-sensitive capabilities additionally retain explicit `time_decision` evidence | **Core Clock Assurance and envelope v2 software provenance implemented in MethodMesh; validate representative ODK roundtrips and Sentinel parsing** |

### Sentinel clock-assurance row

| Item | v1.0 requirement | State / work needed |
|---|---|---|
| Clock-assurance interpretation | Parse and preserve original MethodMesh `time_assurance`; validate supported schema version; evaluate configured trial/action temporal policy; retain Sentinel's derived result separately from the source envelope | **Develop after universal MethodMesh envelope injection is fixed; do not recompute/overwrite historical MethodMesh evidence from later anchors** |

---

## 13. Revised summary-architecture Clock Assurance bullet (§35)

Replace:

> **Clock assurance:** MethodMesh provides a shared Workbench clock-assurance service and includes freshness/offset evidence in every capability envelope; managed-device automatic time is preferably enforced through device policy.

with:

> **Clock assurance:** MethodMesh provides shared **core** Clock Assurance infrastructure. Every generated FULL capability envelope carries frozen software provenance plus versioned policy-neutral temporal evidence describing wall-clock observation, monotonic/boot continuity, trusted-time bounds/uncertainty and anchor/anomaly evidence where available. Time-sensitive capabilities/workflows apply explicit temporal policy separately. Sentinel preserves and interprets that historical evidence under governed trial/action policy. Workbench exposes inspection and deliberate trusted-time refresh; Home may expose compact recency; managed-device automatic time is preferably enforced as an additional device-policy control where available.

---

## 14. Cross-system implementation obligations created by this revision

### MethodMesh

MethodMesh must:

1. provide one shared core API for obtaining a policy-neutral Clock Assurance evidence snapshot suitable for canonical envelope serialisation;
2. capture module/capability ID, version and maturity at execution completion and serialize that frozen software provenance in every generated FULL envelope;
3. inject versioned temporal evidence into every generated FULL execution envelope centrally rather than requiring module authors to implement it;
4. preserve the existing explicit-policy API for time-sensitive decisions;
5. record a separate versioned `time_decision` where a capability/workflow materially uses temporal policy;
6. ensure later anchors or app/module upgrades do not mutate already-captured historical time/software provenance;
7. expose Clock Assurance evidence and deliberate trusted-time refresh in Workbench, with any Home indicator remaining compact and policy-neutral;
8. keep ordinary native UX beef-first; universal temporal/software provenance remains audit salad unless operationally relevant to the user.

### ODK

ODK integration must:

1. continue capturing `methodmesh_full_json` for every handled MethodMesh roundtrip;
2. avoid exploding the universal clock object into many hidden fields in every canonical XLSForm;
3. capture declared scalar temporal fields only where the form workflow genuinely needs them;
4. preserve the FULL envelope with the submission so Sentinel can verify the evidence later.

### Sentinel

Sentinel must:

1. support explicit MethodMesh FULL-envelope schema and `time_assurance.schema_version` handling;
2. parse and preserve producing module/capability ID, version and maturity as source software provenance;
3. preserve the original source object exactly or through a cryptographically committed canonical representation;
4. reject or quarantine unsupported/malformed assurance schemas rather than silently interpreting them as trusted;
5. evaluate configured named temporal policies against the retained evidence;
6. retain policy ID/version and derived outcome in governance evidence;
7. distinguish the original MethodMesh evidence from ODK receipt time, Sentinel observation time and later TSA time;
8. never upgrade the original MethodMesh execution merely because better time evidence became available later.

---

## 15. Revised v1.0 critical-path implication

The cross-system critical path now treats these as separate deliverables:

```text
1. Shared MethodMesh core Clock Assurance       — implemented foundation
2. Frozen module/capability software provenance — implemented MethodMesh envelope v2
3. Universal policy-neutral FULL-envelope time_assurance injection
4. Workbench trusted-time inspection/manual refresh + Home recency surface
5. Explicit time_decision contract for time-sensitive capabilities
6. ODK retention of the revised FULL envelope
7. Sentinel envelope/time/software-provenance parser/schema handling
8. Sentinel governed temporal-policy evaluation
9. End-to-end qualification with rollback/reboot/stale/indeterminate/version-upgrade fixtures
```

The implementation must not collapse universal temporal evidence and explicit temporal decision policy into a hidden universal freshness policy.

---

## 16. Qualification scenarios added by this revision

At minimum the integrated MethodMesh → ODK → Sentinel path should demonstrate:

1. ordinary non-time-sensitive capability with `time_assurance` present and no `time_decision`;
2. trusted same-boot interval with wall clock consistent;
3. wall-clock rollback while trusted monotonic interval remains available;
4. forward divergence;
5. no trusted anchor / wall-clock-only evidence;
6. reboot with prior anchor producing `reboot_unanchored` or equivalent reduced-evidence state;
7. corrupted/unusable persisted anchor represented explicitly and later recoverable from fresh validated evidence;
8. time-sensitive credential/action accepted under an explicit policy;
9. the same evidence rejected/warned under a stricter governed policy without rewriting the source `time_assurance` object;
10. trusted uncertainty overlapping a validity boundary and producing an indeterminate/overlap decision;
11. ODK submission retaining the exact FULL envelope;
12. Sentinel preserving source temporal evidence while recording a separately versioned derived governance interpretation;
13. a later trusted anchor not retroactively changing the historical MethodMesh execution evidence;
14. capability/module upgrade after an execution, followed by later export/verification, with the historical envelope still naming the original producing capability version/maturity;
15. Workbench manual trusted-time refresh obtaining a newly validated anchor without changing Android system time, with Home recency updating from the shared evidence state.

---

## 17. Revision conclusion

The v1.0 architecture already intended Clock Assurance to be global MethodMesh provenance that Sentinel could interpret later. This revision makes the contract precise:

```text
Universal FULL envelope
    = capability result/provenance
    + frozen module/capability version+maturity
    + versioned policy-neutral time_assurance

Time-sensitive method/workflow
    = universal evidence
    + explicit named temporal policy
    + separate time_decision

Sentinel
    = preserve source evidence
    + apply governed central interpretation where required
    + never rewrite historical local evidence
```

This preserves the defining platform boundaries:

- ODK remains the durable authoritative data plane;
- MethodMesh remains the offline-capable local execution and assurance layer;
- Sentinel remains the retrospective governance/reconciliation/verification layer;
- external TSA evidence provides independent cryptographic time evidence without becoming an online dependency for each field action.
