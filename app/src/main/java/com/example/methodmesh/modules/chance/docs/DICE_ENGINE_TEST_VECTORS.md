# Dice Simulator engine regression vectors

These vectors are for the Development v0.2 deterministic engine and are useful when repo unit tests are added.

Deterministic RNG:

- algorithm: `methodmesh.sha256_counter_u32_rejection`
- algorithm version: `2.0.0`
- engine version: `0.2.0`

## Required regression vectors

| Expression | Seed | Expected primitive draws | Expected final result |
|---|---|---|---:|
| `d6!` | `seed-3` | `initial:6`, `explosion:1` | 7 |
| `d20r1` | `seed-1` | `initial:1`, `reroll:17` | 17 |
| `d%` | `methodmesh-test` | `initial:62` | 62 |
| `d20+5>=15` | `methodmesh-test` | `initial:2` | `7`, failure |

## Parser/canonicalisation vectors

| Input | Canonical |
|---|---|
| `d20` | `1d20` |
| `6d6+d20` | `6d6+1d20` |
| `4d6kh3` | `4d6kh3` |
| `4d6dl1` | `4d6dl1` |
| `d20r1` | `1d20r1` |
| `d6rr<3` | `1d6rr<3` |
| `d6!>=5` | `1d6!>=5` |
| `8d6cs>=5` | `8d6cs>=5` |
| `dF+2` | `1dF+2` |
| `d%` | `1d100` |
| `d20+5>=15` | `1d20+5>=15` |

## Required rejection cases

- `d6rr<7` must fail because the repeated-reroll condition matches every D6 face.
- `d6!>=1` must fail because the explosion condition matches every D6 face.
- `d1` must fail because numeric dice require at least two sides.
- more than 100 base dice must fail.
- keep/drop counts greater than the pool size must fail.

These are documentation vectors only. Before Production they should become normal repository unit tests under the app test source set.

## Cascading explosion visual regression

Expression: `d6!`  
RNG mode: `fixed_seed`  
Seed: `cascade-6`

Expected engine outcome for the current Development engine:

- initial value: `6`
- explosion values: `[6, 3]`
- die total: `15`

This vector is useful for checking that the native tray displays two sequential explosion events rather than only one aggregate `+9` decoration.
