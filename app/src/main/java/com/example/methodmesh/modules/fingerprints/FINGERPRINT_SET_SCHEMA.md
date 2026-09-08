# Fingerprint-set data model

## Principle

A multi-finger enrolment is a **set of independent fingerprint templates**.

Do not concatenate or fuse minutiae from different fingers into a synthetic ISO template.

## Recommended model

```json
{
  "schema": "keppel.fingerprint-set/1",
  "templates": [
    {
      "id": "R_INDEX",
      "slot": "R_INDEX",
      "format": "ISO_19794_2",
      "template": "<hex>",
      "quality": {
        "metric": "NFIQ1",
        "value": 1
      }
    }
  ]
}
```

## Finger slot vocabulary

```text
R_THUMB
R_INDEX
R_MIDDLE
R_RING
R_LITTLE
L_THUMB
L_INDEX
L_MIDDLE
L_RING
L_LITTLE
UNKNOWN
```

Use `UNKNOWN` only where historical data genuinely does not identify the finger.

## Multiple accepted impressions of one finger

Retain them as separate records with `impression: 1`, `impression: 2`, etc. Matching should score the fresh scan against each impression according to an explicit matching policy.

## Versioning

The `schema` field is mandatory. Semantic changes require a new schema identifier.
