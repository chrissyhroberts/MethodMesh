# MethodMesh Examples

Working examples of MethodMesh in action. Each example shows how a research intent flows through the system.

## How to Use These Examples

1. **Read the human explanation** in each example's README.md
2. **See the RIL declaration** (What MethodMesh sees)
3. **See the JSON representation** (Wire format)
4. **See the Android implementation** (How it runs on device)
5. **Trace the execution** (Step-by-step flow)

## Available Examples

### scan_nfc/
Scanning an NFC tag to collect attestation of presence.

```
examples/
└── scan_nfc/
    ├── README.md              ← Start here: What is this example?
    ├── ril.yaml               ← Canonical RIL declaration
    ├── request.json           ← JSON serialization
    ├── android-intent.md      ← Android Intent routing
    └── sequence.md            ← Execution flow diagram
```

**Key concepts**: Intent routing, capability selection, observation creation, attestation

## Adding a New Example

1. Create `examples/[capability_name]/`
2. Add `README.md` explaining the scenario
3. Add `ril.yaml` with the RIL declaration
4. Add `request.json` with JSON representation
5. Add `android-intent.md` showing Android routing
6. Add `sequence.md` with execution flow

Use `scan_nfc/` as a template.

## Example Template

```yaml
# ril.yaml
intent: measure
on: blood_pressure
where: clinic_123
when: 2026-07-13T09:43:00Z
how: calibrated_scale
result:
  - value: mmHg
  - quality: confidence
  - attestation: required
```

---

**Examples folder**: Under active development. Check often for new examples.

**Contributing**: Add examples when you build new capabilities.  
