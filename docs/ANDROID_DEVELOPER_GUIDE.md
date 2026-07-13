# Android Developer Guide

> ⚠️ **PARTIALLY STALE — last reviewed against an older package layout.**
> 
> Several code paths, package names and file references in this guide no longer
> match the current implementation. Specifically:
> - Package references use `xyz/researchos/` — the current package is `com/example/researchos/`
> - `TransportRouter.kt` and `OdkMethodAdapter.kt` do not exist in the current codebase
> - The "Adding a New Capability" section's file paths and registration steps are outdated
> 
> **For current implementation state** see [`docs/implementation-notes.md`](implementation-notes.md).  
> **For recent refactor context** see the `*.md` notes in the project root and `app/src/main/`.  
> This guide is useful for conceptual orientation only; do not use it as a code-map.

---

Welcome to ResearchOS Android development. This guide will help you understand the architecture and get started contributing.

## Quick Start

1. **Understand the vision**: Read `../specifications/ResearchOS_Philosophy_0.01.md` (5 min)
2. **Learn the model**: Read `../specifications/design/ResearchOS_Conceptual_Model_v0.03.md` (10 min)
3. **See how it runs**: Read "Android Implementation" section below (10 min)
4. **Start coding**: Pick an issue or follow "Adding a New Capability" tutorial

## Core Concepts (10-minute overview)

ResearchOS has a layered architecture:

```
Intent Layer      ← Research wants to accomplish X (RIL)
Method Layer      ← How to accomplish X (procedural steps)
Capability Layer  ← Concrete Android actions (NFC, GPS, Biometric, etc.)
Platform Layer    ← Android OS abstractions (permissions, sensors, intents)
```

### The Knowledge Model

Everything in ResearchOS flows through this pipeline:

1. **Entity**: A thing being studied (person, location, sample, device)
2. **Assertion**: A claim about an entity
3. **Observation**: Evidence collected to validate an assertion (measurement, reading, photo)
4. **Method**: The procedure used to collect an observation

### Example: Checking Blood Pressure

- **Entity**: Patient ID 12345
- **Assertion**: "Patient has elevated blood pressure"
- **Method**: "Use calibrated scale to measure blood pressure"
- **Observation**: Reading of 150/95 mmHg from calibrated scale

In ResearchOS this becomes:
```kotlin
val method = ExecutionRequest(
    intent = Intent("measure", mapOf("on" to "blood_pressure")),
    where = Location("clinic_123"),
    when = Timestamp.now()
)
// Returns: Observation with reading, quality, attestation
```

## Android Implementation Structure

### Core Kernel (`app/src/main/java/xyz/researchos/core/`)
**Responsibility**: Knowledge model objects

- `Entity.kt`: Things being studied
- `Observation.kt`: Evidence collected
- `Assertion.kt`: Claims about entities
- `Relationship.kt`: How entities relate
- `ResearchGraph.kt`: In-memory graph of knowledge

**Quality Bar**: Fully tested, immutable, no Android dependencies

### Modules (`app/src/main/java/xyz/researchos/modules/`)
**Responsibility**: Specific capabilities

Each module is semi-independent:
- `nfc/`: NFC tag reading with verification
- `gps/`: Location and navigation
- `biometric/`: Fingerprint, face, iris
- `scale/`: Weight and calibration
- `qr/`: QR code scanning
- `attestation/`: Proof of evidence integrity
- `workflow/`: Sequential method execution

### Transport & Adapters (`app/src/main/java/xyz/researchos/transport/`)
**Responsibility**: Multiple protocol support

- `ril/`: ResearchOS Intent Language interpreter
- `android/`: Android Intent routing
- `odk/`: ODK Collect integration
- `methods/`: Method registry and execution engine

### UI (`app/src/main/java/xyz/researchos/ui/`)
**Responsibility**: User-facing Compose components

Modern Jetpack Compose with:
- `HomeScreen`: Navigation hub
- `MethodCard`: Display available research methods
- `CalibratedScale`: Specialized weight capture
- `NFC`, `QR`, `GPS` screens: Capability-specific UIs

## Adding a New Capability

Follow this tutorial to add a capability (e.g., new sensor, new workflow).

### Step 1: Define the Intent
What is the research trying to accomplish?

In `specifications/registries/intents/ResearchOS Intent_Registry_v0.02.md`, add:
```
- Intent Name: "measure_temperature"
- Parameters: {"sensor_type": "thermal|infrared", "location": "required"}
- Expected Result: Observation with temperature, uncertainty, timestamp
```

### Step 2: Create the Module
```bash
mkdir -p app/src/main/java/xyz/researchos/modules/temperature/{ui,logic}
```

Create `TemperatureModule.kt`:
```kotlin
class TemperatureModule(context: Context) {
    fun startMeasurement(params: Map<String, Any>): Observable<Observation> {
        // 1. Initialize hardware
        // 2. Collect readings
        // 3. Validate quality
        // 4. Return Observation with attestation
    }
}
```

### Step 3: Add Capability to Registry
Register in `TransportRouter.kt`:
```kotlin
"measure_temperature" -> TemperatureModule(context).startMeasurement(params)
```

### Step 4: Create UI Component
In `app/src/main/java/xyz/researchos/ui/temperature/`:
```kotlin
@Composable
fun TemperatureCapture(
    onResult: (Observation) -> Unit
) {
    // Compose UI that calls TemperatureModule
    // Returns Observation
}
```

### Step 5: Add to Method Card
Register in `MethodCard.kt` so users can discover it.

### Step 6: Test
Add integration test in `app/src/test/java/xyz/researchos/modules/temperature/`:
```kotlin
@Test
fun testTemperatureMeasurement() {
    val module = TemperatureModule(context)
    val observation = module.startMeasurement(
        mapOf("sensor_type" to "thermal")
    ).blockingFirst()
    
    assert(observation.value != null)
    assert(observation.attestation != null)
}
```

## Architecture Decision Log

Key design decisions are documented in `../../docs/implementation-notes.md`:
- July 10: Refactored method runtime bridge (backwards compatibility layer)
- June 22: Switched to Jetpack Compose for UI
- Earlier: Registry-driven architecture to avoid vendor lock-in

## Understanding the RIL Pipeline

ResearchOS Intent Language (RIL) is how research is declared:

1. **User/Research Protocol**: "Measure blood pressure using calibrated scale"
2. **RIL Declaration**: 
   ```json
   {
     "intent": "measure",
     "on": "blood_pressure",
     "where": "clinic_123",
     "when": "2026-07-13T09:43:00Z",
     "how": "calibrated_scale",
     "result": {"value": "mmHg", "attestation": "required"}
   }
   ```
3. **Router**: `TransportRouter.kt` routes to correct module
4. **Module Execution**: `TemperatureModule.startMeasurement()` (or similar)
5. **Observation**: Returns typed Observation with metadata

See `specifications/ril/RIL_ResearchOS_Intent_Language_v0.03.md` for full spec.

## Common Tasks

### Running the App
```bash
# Build and run on emulator
./gradlew installDebug
adb shell am start -n xyz.researchos/.ui.MainActivity
```

### Running Tests
```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew connectedAndroidTest
```

### Adding a New Observation Type
Edit `core/Observation.kt` and `registries/observations/` spec.

### Integrating with ODK
See `transport/odk/OdkMethodAdapter.kt` for how legacy methods map to RIL.

## File Organization Rules

```
app/src/main/java/xyz/researchos/
├── core/              ← Knowledge model (no Android deps)
│   ├── Entity.kt
│   ├── Observation.kt
│   ├── Assertion.kt
│   └── ResearchGraph.kt
├── modules/           ← Capabilities (Android deps OK)
│   ├── nfc/
│   ├── gps/
│   ├── biometric/
│   └── [new_capability]/
├── transport/         ← Protocol routing
│   ├── ril/
│   ├── android/
│   └── odk/
└── ui/               ← Compose screens
    ├── HomeScreen.kt
    ├── MethodCard.kt
    └── [capability_ui]/
```

## Code Style

- Kotlin idioms (no Java boilerplate)
- Compose for UI (no XML layouts)
- Minimal inline comments (code should be self-documenting)
- Descriptive variable/function names
- Immutable data classes where possible
- Observable streams for async operations

## Resources

- **Philosophy**: `specifications/ResearchOS_Philosophy_0.01.md`
- **Conceptual Model**: `specifications/design/ResearchOS_Conceptual_Model_v0.03.md`
- **Architecture Standard**: `specifications/architecture-standard/Architecture_Standard_v1.02.md`
- **RIL Spec**: `specifications/ril/RIL_ResearchOS_Intent_Language_v0.03.md`
- **All Registries**: `specifications/registries/`

## Getting Help

1. Check `docs/implementation-notes.md` for recent decisions
2. Read the relevant specification (see SPEC_STATUS.md for which is current)
3. Look at similar capabilities in `modules/`
4. Check git log for how similar features were added

---

**Last updated**: July 13, 2026
