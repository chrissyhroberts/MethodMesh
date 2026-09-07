# Protocol pipes — design direction

Pipes should allow an output from one protocol step to become an input to a later step without requiring the two capabilities to have been designed together.

This should be built on MethodMesh's structured result contract, not on ad-hoc string scraping.

## Core model

A protocol step already produces a canonical result. Treat that result as a typed tree of addressable leaves.

Conceptually:

```text
Step A: AHT20 read
  result.temperature = 23.0
  result.humidity = 56.0

Step B: astronomy calculation
  input.temperature
  input.humidity
```

The ideal connection is direct:

```text
A.result.temperature  ─────▶  B.input.temperature
A.result.humidity     ─────▶  B.input.humidity
```

If the downstream capability genuinely expects one legacy/composite string such as `23|56`, insert a transform node rather than changing either capability:

```text
A.temperature ─┐
               ├─▶ Join("|") ─▶ B.input.conditions
A.humidity ────┘
```

## Why JSON/result-tree leaves are the right base

- outputs stay canonical and typed;
- modules do not need prior knowledge of each other;
- the pipe editor can enumerate available leaves;
- protocol validation can catch missing/incompatible inputs before execution;
- lineage can record exactly which upstream value created a downstream input;
- ODK remains independent of the visual editor.

## Binding types

A future protocol input binding should support:

1. **Literal** — fixed value in the protocol;
2. **Result reference** — path into an earlier step's result tree;
3. **Transform** — deterministic conversion from one or more upstream values;
4. **Runtime input** — ask the user when the step starts;
5. **Default/coalesce** — use upstream value if present, otherwise a fallback.

## Safe transform palette

Start deliberately small and deterministic:

- select/rename a leaf;
- text template, e.g. `${temperature}|${humidity}`;
- join/split with delimiter;
- number format / rounding;
- parse number / boolean / text;
- unit conversion;
- arithmetic on named numeric inputs;
- JSON object/array construction;
- substring / case / trim;
- regex replace only if bounded and testable;
- coalesce/default;
- list map/filter later if necessary.

Do not allow arbitrary Kotlin, JavaScript or shell code inside protocol definitions.

## Visual editor

A Scratch-like editor is a good fit, but should remain lightweight.

Suggested interaction:

- each preset is a compact block;
- left side = required/runtime inputs;
- right side = available outputs;
- output/input sockets are coloured by broad type (number, text, boolean, media, location, structured object);
- drag an output onto a compatible input for a direct binding;
- dropping onto an incompatible input offers valid transform blocks;
- transform blocks sit visibly between steps;
- selecting any connector shows the exact source path and conversion.

The normal linear protocol list should remain available. The graph is an editor/view, not the only representation.

## Protocol storage

Do not serialize screen coordinates as the meaning of the protocol. Persist a semantic graph:

```text
steps[]
bindings[]
transforms[]
```

Canvas positions may be optional UI metadata.

A binding needs stable step IDs, canonical result paths, target input keys, expected types, and transform references.

## Validation

At design time:

- source step must precede target step unless future DAG execution is deliberately supported;
- source path must exist in the declared output schema where statically known;
- target input must exist;
- type/unit compatibility should be checked;
- required inputs must resolve from literal, pipe or runtime input.

At runtime:

- missing/null/type errors produce a clear protocol diagnostic;
- no silent string coercion unless the binding explicitly requests it;
- lineage records the resolved upstream path and transform used.

## Recommended implementation sequence

1. Add addressable `ResultRef(stepId, path)` semantics to protocol definitions.
2. Support direct leaf-to-input bindings only.
3. Add static compatibility checking and UI dropdown binding editor.
4. Add a small transform registry.
5. Add the visual Scratch-like graph editor once the semantic model is stable.
6. Only then consider AI-assisted protocol construction.

This avoids making the visual editor define an architecture that later becomes difficult to execute or audit.
