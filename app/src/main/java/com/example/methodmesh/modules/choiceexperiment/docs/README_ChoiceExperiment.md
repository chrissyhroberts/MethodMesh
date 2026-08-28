# Choice experiments

Self-contained interactive preference-elicitation tasks with deterministic seeded designs and structured JSON results.

## Capabilities

- `dce.pairwise` — choose between two items over a configured number of rounds.
- `dce.maxdiff` — select best and worst items from each presented set.
- `dce.ranking` — rank the supplied item list over one or more rounds.
- `dce.points` — allocate a fixed points budget across supplied items.
- `dce.conjoint` — choose between profiles generated from caller-defined classes and levels.

Manual/debug launches show task configuration before starting. Intent calls open
the first interactive round immediately using the supplied configuration. They
never complete until the participant has answered every required round.

## Android intent

For ODK multi-field intents, place the method and its static task definition in
the `body::intent` cell. For example:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='dce.pairwise',input_rounds='5',input64_items='UGFuYXNvbmljClNvbnkKTmludGVuZG8KU2Ftc3VuZw',return_mode='flat')
```

`input64_items` is URL-safe Base64 without padding. MethodMesh decodes it to the
UTF-8 item list before invoking the module. This keeps study-design settings
out of the participant interface and submission dataset.

When a study genuinely collects a configuration value from the user, pass it
as an `input_*` child field. Unprefixed child fields are outputs.

## Inputs

| Capability | Inputs |
|---|---|
| Pairwise | `rounds`, `items`, optional `seed` |
| MaxDiff | `rounds`, `items`, optional `items_per_round`, `seed` |
| Ranking | `rounds`, `items`, optional `seed` |
| Points | `points`, `items`, optional `seed` |
| Conjoint | `rounds` (normally 3–10), `classes`, optional `profiles_per_round`, `seed` |

Item lists may use one item per line, or comma, semicolon, or pipe delimiters.
The example forms use one item per line.

Conjoint uses one class per line:

```text
BRAND: Panasonic, Sony, Nintendo
FEATURE: Basic, Premium
PRICE: Low, Medium, High
```

Static lists are encoded with `input64_`, so colons, commas, newlines, and pipes
never appear as XLSForm intent syntax.

## Outputs

All methods return `method`, `module`, `result_json`, `session_id`, `seed`, `round_count`, and `response_count`. `result_json` contains the complete shown design and participant responses for every round.

## ODK example

The module includes one independently importable example for each method:

- [`example_odk_dce.pairwise.xlsx`](example_odk_dce.pairwise.xlsx)
- [`example_odk_dce.maxdiff.xlsx`](example_odk_dce.maxdiff.xlsx)
- [`example_odk_dce.ranking.xlsx`](example_odk_dce.ranking.xlsx)
- [`example_odk_dce.points.xlsx`](example_odk_dce.points.xlsx)
- [`example_odk_dce.conjoint.xlsx`](example_odk_dce.conjoint.xlsx)
