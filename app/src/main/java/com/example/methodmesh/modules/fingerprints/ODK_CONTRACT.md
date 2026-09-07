# ODK / external Intent contract

## Compatibility rule

Existing actions and parameters remain supported unchanged.

### Existing actions

```text
uk.ac.lshtm.keppel.android.SCAN
uk.ac.lshtm.keppel.android.MATCH
uk.ac.lshtm.keppel.android.MULTI_MATCH
```

### Existing concepts

```text
iso_template
iso_template_1
iso_template_2
...
return_iso_template
return_nfiq
return_score
fast
```

A legacy form should not need alteration after the UX redesign.

## New action: SCAN_SET

Recommended:

```text
uk.ac.lshtm.keppel.android.SCAN_SET
```

Inputs:

```text
finger_slots=R_INDEX,L_INDEX,R_THUMB
captures_per_finger=2
fast=true|false
```

Optional named returns:

```text
return_template_set
return_template_count
return_quality_summary
```

## MATCH / MULTI_MATCH extensions

Optional new metadata outputs:

```text
return_match_index
return_match_id
return_match_slot
return_runner_up_score
```

Legacy numbered templates remain valid. Optional parallel metadata:

```text
template_id_1=P0123:R_INDEX
template_id_2=P0123:L_INDEX

template_slot_1=R_INDEX
template_slot_2=L_INDEX
```

Existing callers asking only for `return_score` continue to receive only the score.

## Structured fingerprint-set input

New callers may alternatively supply:

```text
template_set=<keppel.fingerprint-set/1 JSON>
```

Internally, legacy numbered templates and structured JSON become the same `FingerprintSet` model.

## Single-value ODK return defaults

```text
SCAN        → ISO template
MATCH       → score
MULTI_MATCH → best score
SCAN_SET    → fingerprint-set JSON
```

Do not change existing defaults for old actions.
