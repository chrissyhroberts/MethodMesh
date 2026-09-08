# Clinical Instruments constrained YAML schema — v0.1

The YAML is the canonical definition format for both bundled and user-created Clinical Instruments. v0.1 deliberately describes **linear checklists and scoring instruments only**.

Every question is presented in the declared order. There is no conditional relevance, skip logic, repeat group or dynamic question path in this schema. Those features belong to a future protocol engine and must not be simulated with local workarounds.

The parser supports a constrained subset of YAML: root scalar keys plus list-of-map sections. This keeps definitions deterministic, inspectable and dependency-light.

## Minimal definition

```yaml
schema: methodmesh.clinical-instrument.v1
id: example_score
name: Example score
version: 1.0.0
status: core
type: clinical_score
category: acute_bedside
tags: [bedside, example]
summary: Example only.
source_url: https://example.org/source
citation: Example citation.
rights_status: reviewed
rights_note: Example only.

questions:
  - id: rate
    label: Rate
    type: integer
    unit: per_min
    required: true
    min: 0
    max: 100
  - id: feature
    label: Feature present?
    type: boolean
    required: true

derived:
  - id: rate_criterion
    expression: rate >= 22

scores:
  - id: example_score
    expression: rate_criterion + feature

classifications:
  - when: example_score >= 1
    value: threshold_met
    label: Threshold met
  - when: example_score < 1
    value: below_threshold
    label: Below threshold

tests:
  - name: threshold_case
    input_json: '{"rate":22,"feature":false}'
    expect_json: '{"rate_criterion":true,"example_score":1,"classification":"threshold_met"}'
```

## Root keys

| Key | Required | Meaning |
|---|---:|---|
| `schema` | no | Defaults to `methodmesh.clinical-instrument.v1` |
| `id` | yes | Stable ID using letters, numbers, `.`, `_`, `-` |
| `name` | yes | User-facing instrument name |
| `version` | recommended | Definition version; semantic-version form recommended |
| `status` | no | `core` or `local`; repository controls imported status |
| `type` | no | e.g. `clinical_score`, `screening`, `structured_assessment` |
| `category` | no | Catalogue grouping |
| `tags` | no | Inline list |
| `summary` | no | Plain-language description |
| `source_url` | recommended | Canonical source/guidance URL |
| `citation` | recommended | Human-readable citation |
| `rights_status` | recommended | Rights/licensing review state |
| `rights_note` | no | Redistribution/attribution detail |

## Questions

Supported `type` values:

- `integer`
- `decimal` / `number`
- `boolean` / `yes_no`
- `select_one` / `choice`
- `text`

Supported fields:

```yaml
questions:
  - id: systolic_bp
    label: Systolic blood pressure
    hint: Enter measured systolic pressure.
    type: integer
    unit: mmHg
    required: true
    min: 20
    max: 300
```

For `select_one`, choices are encoded as `value|Label` tokens:

```yaml
choices: [alert|Alert, voice|Responds to voice, pain|Responds to pain, unresponsive|Unresponsive]
```

### v0.1 validation/constraint scope

Question-level constraints are deliberately simple:

- required / optional;
- integer versus decimal;
- numeric minimum / maximum;
- allowed `select_one` values;
- boolean values;
- units as metadata/display.

`visible_if`, relevance, required-if, repeated groups and conditional branching are **not supported**. A definition containing `visible_if` fails validation so that a protocol cannot silently run with the wrong semantics.

## Calculations

`derived:` and `scores:` use the same deterministic expression grammar:

```yaml
derived:
  - id: sbp_criterion
    expression: systolic_bp <= 100

scores:
  - id: qsofa
    expression: rr_criterion + sbp_criterion + mentation_criterion
```

Boolean values coerce to 1/0 in numeric operations.

Supported syntax:

- identifiers;
- numeric literals;
- quoted text literals;
- `true`, `false`, `null`;
- parentheses;
- arithmetic: `+ - * /`;
- comparisons: `< <= == != >= >`;
- boolean: `and or not` and `&& || !`.

No arbitrary code execution, reflection, networking or function calls are supported.

## Classification

Rules are evaluated in order and the first true rule is returned:

```yaml
classifications:
  - when: score >= 3
    value: high
    label: High risk
  - when: score >= 1
    value: intermediate
    label: Intermediate risk
  - when: score == 0
    value: low
    label: Low risk
```

## Embedded definition tests

Each core definition should contain representative boundary cases. Tests stay with the definition so a reviewer can inspect both the rule and expected behaviour.

```yaml
tests:
  - name: exact_thresholds
    input_json: '{"respiratory_rate":22,"systolic_bp":100,"altered_mentation":true}'
    expect_json: '{"qsofa":3,"classification":"two_or_more_criteria"}'
```

Expected keys may refer to question responses, derived variables, scores, or the special `classification` key.

## Versioning and hashing

`definition_sha256` is calculated over the exact normalized YAML used by MethodMesh. Every completed result returns the declared version and this content hash.

A locally saved definition may not overwrite an existing identical `id + version` with different content. Change the version to create the next definition revision.

## Future protocol layer

A later schema/version may add relevance, skip logic, cross-field constraints and repeats. That work is intentionally excluded from v0.1. See `FUTURE_PROTOCOL_ENGINE.md`.

## Non-testable score components (v0.4)

A linear question may explicitly define a non-testable value:

```yaml
- id: verbal_response
  type: select_one
  choices: [5|Orientated, 4|Confused, 3|Words, 2|Sounds, 1|None, NT|Not testable]
  not_testable_value: NT
```

A calculation that must not be produced when one of its source components is not testable declares those dependencies explicitly:

```yaml
scores:
  - id: gcs_total
    expression: eye_response + verbal_response + motor_response
    requires_testable: [eye_response, verbal_response, motor_response]
```

If any listed question holds its `not_testable_value`, the expression returns null. This is intentionally narrower than relevance/skip logic.
