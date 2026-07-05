# DCE choice experiment module

Adds a self-contained `modules/choiceexperiment` capability package implementing five externally callable DCE-style interactions:

- `dce.pairwise`
- `dce.maxdiff`
- `dce.ranking`
- `dce.points`
- `dce.conjoint`

The module owns its RIL bindings, capability screens, JSON result generation and AS method descriptors. No workflow, transport or central registry code needs to know DCE-specific phrases or screens.

Example RIL:

```text
WHAT; run pairwise(options=Cost|Privacy|Speed|Offline use,rounds=3,options_per_round=2,seed=test001); WHERE; participant/P001; RESULT; return observation.dce.result_json as dce_result; format json
```

Legacy-style generic intent extras can also call the methods using `actions=dce.pairwise` etc. The historical `org.lshtm.choice.*` direct action names are not registered as separate Android manifest actions; callers should use the ResearchOS generic intent action and specify the DCE method through RIL or `actions`.
