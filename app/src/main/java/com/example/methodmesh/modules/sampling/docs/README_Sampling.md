# Sampling

`sampling.run` builds a population and performs reproducible random selection,
shuffling, systematic selection, stratified selection, weighted selection or
random partitioning.

**Status:** Development  
**Module:** `sampling`  
**Method:** `sampling.run`  
**Version:** `0.1.1`

Sampling is local-first. The sampling engine itself does not require a network
connection. It uses an explicit, versioned deterministic random stream and
records the seed, population commitment, result commitment and algorithm
versions in a provenance manifest.


## Native workflow

1. Choose a population source: pasted list, CSV, numeric sequence or random words.
2. For CSV input, choose the source file (or supply declared inline CSV text) and map the identifier/optional weight, stratum and eligibility fields.
3. Choose the operation and sampling parameters.
4. Choose annotated-full-population or selected-only output and CSV/JSON format.
5. Use an automatic or fixed seed.
6. Run Sampling and review the compact result; provenance remains under details/audit.
7. Save/export only when requested. File-producing runs create cache attachments as the result payload; they are not silently copied into the user's configured output folder.

The normal result screen is provided by `CapabilityScreenScaffold`. Scalar/list workflows expose `sampling_value`; file workflows expose `sampling_result_uri`.

## Input fields

All canonical inputs are declared in `SamplingModule.capabilitySettings()`. Important inputs include `source_type`, `manual_items`, `csv_uri`, `csv_text`, `sampling_items_json`, sequence/word settings, `operation`, sample size/fraction, replacement, field mappings, stratification/partition settings, output mode/format, output-field names and seed settings. Fixed enumerations use `ChoiceSetting`/`BooleanSetting`; numeric sequence values use numeric settings.

## Permissions, services and offline/online behaviour

Sampling itself requires no dangerous Android permission and performs population construction, randomisation, hashing and file generation locally. CSV selection/export uses Android's Storage Access Framework and the existing MethodMesh FileProvider; no sampling-specific storage permission is requested.

The only network-dependent path is the optional downstream `attestation.create` capability when a TSA-backed trusted timestamp is requested. Sampling does not contact the TSA itself and does not duplicate attestation internals. If the attestation step cannot reach its configured timestamp authority, that separate capability reports the timestamp failure according to its own public contract; the already-produced sampling result remains distinct.

## Main use cases

- choose one eligible person from a household roster;
- select records for verification or quality control;
- sample laboratory specimens from a manifest;
- randomise specimen processing order;
- divide records or specimens into random batches;
- generate and export numeric sequences;
- generate reproducible random word lists from a bundled local dictionary.

This is a general sampling utility. It is **not** a clinical-trial allocation
system, an adaptive/minimisation randomiser, a complex survey sampler or a
password-security product.

## Population sources

### Manual list

Enter one identifier per line by default. Pipe, comma and semicolon separators are also supported, which is useful when ODK constructs a list with `join()`. The item is used as both identifier and label. Identifiers must be unique.

### CSV

Select any CSV from Android's document picker. Source columns are preserved.
MethodMesh concepts are mapped onto the user's existing column names; the app
does not require the source schema to be renamed.

The downloadable template uses these convenience defaults:

| Concept | Default column |
|---|---|
| identifier | `item_id` |
| label | `item_label` |
| weight | `weight` |
| stratum | `stratum` |
| eligibility | `eligible` |

Only the identifier is intrinsically required. Optional mappings can be set to
`(none)`. If the default names are present they are detected automatically.
Otherwise the operator selects the appropriate columns.

All unrelated source columns pass through unchanged.

### Numeric sequence

Configure `sequence_start`, `sequence_end` and `sequence_step`.

For example:

```text
start = 1
end   = 100
step  = 5
```

produces `1, 6, 11, ... 96`.

Descending sequences are supported when the step is negative. Zero steps and
incompatible step directions are rejected.

### Random words

Configure word count, minimum/maximum word length and whether words must be
unique. Words come from the bundled, versioned `methodmesh.en.basic`
dictionary. The dictionary ID, version and SHA-256 are recorded in provenance.

The word list is useful as a general random-token source, but this version does
not claim password/passphrase security certification.

## Operations

- `simple_sample` — simple random sampling, with or without replacement;
- `shuffle` — Fisher-Yates random permutation;
- `weighted_sample` — sequential weighted drawing, with or without replacement;
- `stratified_sample` — equal n per stratum, proportional total, or explicit
  `stratum=n` allocation;
- `systematic_sample` — systematic sample with a seeded random start;
- `partition` — balanced random division into groups;
- `population_only` — construct/export the population without a second
  selection operation.

Sample size can be a fixed `n` or a fraction. Fractions are converted to a
record count using ceiling rounding and that rule is recorded in provenance.

## Eligibility

If an eligibility field is mapped, recognised values are:

```text
true / false
1 / 0
yes / no
y / n
```

Unrecognised values fail rather than being silently guessed. Records excluded
by eligibility remain in the default annotated output.

## Output modes

### Annotated population — default

The complete original population is returned with sampling columns appended.
Default names are:

```text
sampling_selected
sampling_count
sampling_order
sampling_group
```

Only fields relevant to the operation are added. Names are configurable.
Existing source columns are never overwritten silently: a collision stops the
run and requires different output field names.

`sampling_count` preserves repeated draws when sampling with replacement.
`sampling_order` is semicolon-separated in annotated output when a source row
was drawn more than once.

### Selected records only

Only selected rows are returned. Sampling with replacement emits one row per
draw, so repeated source records remain explicit rather than being collapsed.

Selected-only output can be returned in draw order, original input order or
sorted order.

## File outputs

Native CSV/JSON runs create:

```text
<name>.csv                 or <name>.json
<name>.manifest.json
```

The result and companion manifest are returned as `content://` URIs. MethodMesh
external intent handling grants `_uri` outputs back to callers such as ODK.
The normal **Save/export full output** action also copies both attachments into
MethodMesh's configured output package.

Known Development limitation: the current shared native **Share result** helper
recognises image/PDF attachments but not generic CSV/JSON attachments. Use
**Save/export full output** for native file export until that generic framework
helper gains CSV/JSON sharing support. This module does not patch shared runtime
code from inside the capability folder.

## Main result and output projection

Sampling follows MethodMesh's beef-first/audit-second contract. For scalar list workflows `sampling_value` is the compact main value (the first selected identifier), with `sampling_selected_id` retained as the explicit semantic alias. File-producing workflows expose `sampling_result_uri` as the useful attachment. Full sampling provenance remains in `sampling_audit_json` / `methodmesh_full_json` rather than on the primary result surface.

## Core outputs

| Field | Meaning |
|---|---|
| `sampling_value` | compact first selected identifier for scalar/list workflows |
| `sampling_result_uri` | result CSV/JSON attachment when a file was produced |
| `sampling_manifest_uri` | companion provenance manifest attachment |
| `sampling_selected_id` | first selected ID, convenient for `n=1` |
| `sampling_selected_ids` | selected IDs in draw order, newline separated |
| `sampling_selected_label` | first selected label/value; useful for generated words |
| `sampling_selected_labels` | selected labels/values in draw order |
| `sampling_selected_count` | number of draws |
| `sampling_population_count` | input population count |
| `sampling_eligible_count` | eligible population count |
| `sampling_result_json` | structured result data |
| `sampling_audit_json` | full provenance manifest |
| `sampling_provenance_payload_sha256` | hash to commit through attestation |
| `sampling_attestation_method_id` | currently `attestation.create` |
| `sampling_attestation_event_payload_hash` | same committed provenance hash, ready for attestation |

Hash/manifest fields are intentionally background/audit data under MethodMesh's
normal CORE/AUDIT/FULL output projection.

## Reproducibility

Automatic runs generate a 256-bit seed using `SecureRandom`. Fixed runs accept
either a 64-character hexadecimal seed or arbitrary text; arbitrary text is
normalised to 256 bits with SHA-256.

The deterministic random stream is:

```text
rng_algorithm         = methodmesh.sha256_counter
rng_algorithm_version = 1.0.0
```

Random bytes are generated from SHA-256 over a fixed domain separator, the
recorded seed and an incrementing 64-bit counter. This avoids making Kotlin's
runtime RNG implementation part of the long-term reproducibility contract.

Every sampling operation also records a sampling algorithm ID and version.
Algorithm behaviour must not be silently changed under an existing version.

## Hashes and provenance

The capability distinguishes:

- `input_file_sha256` — exact bytes of an uploaded CSV;
- `population_sha256` — canonical population actually understood by the engine;
- `result_sha256` — canonical draw/result payload;
- `output_file_sha256` — exact generated output file bytes, when applicable;
- `provenance_payload_sha256` — SHA-256 commitment to the canonical sampling
  provenance payload.

Canonical JSON keys are sorted and canonicalisation is explicitly versioned.

A SHA-256 demonstrates integrity; it does not by itself establish when the
sampling event happened.

## Trusted timestamp / attestation

Sampling does **not** reimplement signing, timestamp authority calls, device
keys or the attestation chain.

The manifest includes an attestation request pointing to the existing public
MethodMesh method:

```text
method_id          = attestation.create
event_payload_hash = sampling_provenance_payload_sha256
event_type         = sampling_result
trusted_timestamp  = required
```

`attestation.create` is responsible for the signed/hash-chained attestation and
its TSA-backed trusted timestamp. The existing attestation API also requires
real verification evidence. Sampling must not invent or bypass that evidence.

A protocol can therefore use Sampling followed by Traceable attestation, with
the sampling provenance hash supplied as the attestation event payload hash.
The TSA timestamp then establishes that the committed sampling provenance
existed no later than the timestamp authority's trusted time.

## ODK / XLSForm

The expected ODK use is a simple structured roster, not file input.

Use an intent call such as:

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='sampling.run',
  input_source_type='manual',
  input_manual_items=${eligible_ids},
  input_manual_separator='pipe',
  input_operation='simple_sample',
  input_sample_mode='n',
  input_sample_size='1',
  input_replacement='false',
  input_output_format='json',
  input_payload_mode='FULL',
  return_mode='flat'
)
```

For a household repeat, ODK can construct `eligible_ids` with `join('|', ${person_id})` outside the repeat and pass that pipe-delimited list to Sampling. Structured JSON is also supported when a caller already has it. JSON-result ODK calls return data directly and do not require an output file. Store `sampling_selected_id` (or `sampling_value`) as the convenient result, `sampling_audit_json` as the capability-owned audit field, and `methodmesh_full_json` when the form also wants the shared MethodMesh FULL envelope. The example XLSForm includes all three patterns explicitly.

Static configuration belongs in `body::intent` under `input_*`. Unprefixed
children of the intent group are return placeholders, consistent with other
MethodMesh XLSForm examples.

See `example_odk_Sampling.xlsx` for an importable example.

## Presets

All canonical inputs, including `csv_uri` and inline `csv_text`, are declared through normal MethodMesh capability metadata. Fixed preset configuration is hidden with `CapabilityScreenContext.settingShouldBeShown(...)`; genuine runtime inputs such as the actual list/roster or CSV choice remain visible. Native preset runs do **not** auto-execute merely because they use intent-style presentation: the operator supplies the declared runtime inputs and then runs Sampling. External automatic-return/ODK calls still execute immediately.

Settings are declared through the normal MethodMesh capability metadata. Fixed
preset configuration should be hidden by the shared preset machinery; genuine
runtime inputs such as the actual list/roster remain visible/externally
supplied.

## Error behaviour

The capability fails rather than silently resolving ambiguous or unsafe input,
including:

- missing/blank/non-unique identifiers;
- missing mapped columns;
- malformed CSV;
- invalid eligibility values;
- invalid/negative/non-finite weights;
- sample larger than the available population without replacement;
- invalid numeric sequences;
- missing strata for stratified sampling;
- impossible systematic intervals;
- output-field collisions.

## Limits

The current Development implementation caps constructed populations at 100,000
records to avoid accidental runaway sequence/list generation on a phone.

## Validation completed for this drop-in

The pure engine was compiled with Kotlin 1.9 and smoke-tested for:

- deterministic fixed-seed replay;
- CSV parsing/writing and quoted values;
- preservation of original CSV columns;
- eligibility;
- with/without replacement behaviour;
- weighted selection;
- stratified equal allocation;
- balanced partition;
- numeric sequence generation;
- random-word generation;
- deterministic population/result/provenance hashes.

An Android Gradle build was not run in the packaging environment because the
repository and Android SDK dependencies are not mounted there. The Android
interfaces in this module were written against the current MethodMesh `master`
public module, screen, FileProvider and attestation contracts.


## Protocol/schedule close-out and pipes

Sampling does not implement capability-specific protocol completion. It returns a normal `ExecutionResult`; the shared MethodMesh workflow/scheduler layer supplies `methodmesh_closeout_status`, step counts, payload detection and cancellation semantics.

Sampling inputs are normal declared settings, so protocol/schedule orchestration may pipe prior fields into them using the shared `previous_*`, `step_N_*` or unprefixed runtime-value contract. The module does not inspect another capability's internals. The explicit dependency on `attestation` is through the public `attestation.create` method only.
