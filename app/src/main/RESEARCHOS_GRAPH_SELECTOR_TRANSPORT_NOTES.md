# ResearchOS graph-selector transport refactor

This build adds a small graph-selector language to the Android/ODK transport boundary.

The intent route can now do:

1. parse caller context such as `participant_id`, `entity_id`, `visit_id`, `form_id`, `operator_id`;
2. execute the requested ResearchOS capability;
3. record the resulting execution into the ResearchOS graph;
4. resolve requested return values from graph paths rather than module-specific flat fields;
5. return those selected values as Android result extras and in the formatted `value` payload.

## Public intent action

The manifest exposes:

```text
com.example.researchos.EXECUTE_METHOD
```

## Accepted method/context extras

```text
method=nfc.read
entity_type=participant
entity_id=P001
visit_id=baseline
form_id=registration
operator_id=alice
return_mode=json
```

Legacy aliases are still accepted for method selection:

```text
method_id
module
module_id
capability
capability_id
```

Subject aliases are also accepted:

```text
subject_id
participant_id
specimen_id
context_entity_id
context_entity_type
```

## New graph selector extras

Any of these can carry the requested graph outputs:

```text
returns
return
select
graph_return
graph_returns
selector
selectors
```

`return=json`, `return=fields`, etc. still works as a return-mode shortcut. If `return` looks like a graph selector, it is treated as a selector instead.

## Selector examples

```text
returns=execution.id:execution_id,observation.nfc.uid:tag_uid
```

```text
returns=graph://latest/observation/nfc.tag.uid as tag_uid, execution.status as status
```

```text
select=state.navigation.arrived as arrived
```

Supported selector roots:

```text
execution.id
execution.status
execution.method.id
execution.action
context.<key>
observation.<type-token>.<value-key>
state.<type-token>.<value-key>
entity.id
entity.type
entity.<attribute-key>
diagnostic.<key>
```

Selectors resolve against the current execution first. If the current execution does not contain that object type, they can resolve against the accumulated ResearchOS graph.

If no selectors are supplied, OutputFormatter keeps the previous full flattened payload behaviour.

## Example Android intent URI

```text
intent:#Intent;action=com.example.researchos.EXECUTE_METHOD;S.method=nfc.read;S.entity_type=participant;S.entity_id=P001;S.visit_id=baseline;S.form_id=registration;S.operator_id=alice;S.return_mode=json;S.returns=execution.id:execution_id,observation.nfc.uid:tag_uid;end
```
