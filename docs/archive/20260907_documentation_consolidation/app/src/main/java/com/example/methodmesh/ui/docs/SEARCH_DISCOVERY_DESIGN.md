# Search and discovery

MethodMesh now has enough modules/capabilities that navigation cannot depend on users remembering the catalogue.

## Current iteration

Dashboard search is intentionally the main content of the dashboard. It ranks:

- capabilities;
- presets;
- ODK design templates;
- protocols.

It searches method/module names, IDs, descriptions and a small synonym layer, so queries such as `weather`, `location`, `photo`, `random`, `sound`, `measure` and `calculate` can find relevant tools without exact terminology.

This is still deterministic local search, not AI.

## Tags

Tags are useful, but should be module-owned metadata rather than a second central taxonomy that rots independently.

Recommended future descriptor fields:

```text
tags = [temperature, humidity, astronomy, weather]
verbs = [measure, calculate]
accepts = [temperature, humidity]
produces = [dew_point]
```

Tags should improve search/discovery; they should not replace stable method IDs or typed contracts.

## Local semantic search

A later lightweight local search provider could embed the capability catalogue and rank natural-language queries such as:

> I need to work out whether my telescope will dew up tonight

The search provider should return canonical capability/preset/protocol IDs, never execute arbitrary generated code.

A future assistant layer could then propose or construct presets/protocols, but creation should still produce normal MethodMesh objects that the user can inspect and edit.
