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

For ODK multi-field intents, place the fixed method route in the
`body::intent` cell:

```text
com.example.researchos.EXECUTE_METHOD(method_id='dce.pairwise',return_mode='flat')
```

Replace `dce.pairwise` with the required capability ID. Put only the
configuration inputs and return fields inside the same `field-list` group.
ResearchOS receives those child fields as Android extras. Do not rely on a
calculated `method_id` child field: ODK does not pass it as the routing action.

Do not interpolate a list field into the intent expression. For example, avoid
`items='${items}'` or `classes='${classes}'`: ODK then has to parse substituted
list punctuation as part of the expression.

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

These strings are ordinary form data sent as intent extras. Colons, commas,
newlines, and pipes never appear in the fixed `body::intent` expression.

## Outputs

All methods return `method`, `module`, `result_json`, `session_id`, `seed`, `round_count`, and `response_count`. `result_json` contains the complete shown design and participant responses for every round.

## ODK example

The module includes one independently importable example for each method:

- [`example_odk_ChoicePairwise.xlsx`](example_odk_ChoicePairwise.xlsx)
- [`example_odk_ChoiceMaxDiff.xlsx`](example_odk_ChoiceMaxDiff.xlsx)
- [`example_odk_ChoiceRanking.xlsx`](example_odk_ChoiceRanking.xlsx)
- [`example_odk_ChoicePoints.xlsx`](example_odk_ChoicePoints.xlsx)
- [`example_odk_ChoiceConjoint.xlsx`](example_odk_ChoiceConjoint.xlsx)
