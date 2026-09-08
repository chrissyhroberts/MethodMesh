# Earth Science v0.1.1

Usability revision following first native testing.

## Fixed

- Calculation failures are surfaced in the screen instead of producing an apparently blank result. This specifically makes `geodesy.destination` failures diagnosable.
- `geodesy.utm_to_wgs84` can parse the complete output line returned by `geodesy.wgs84_to_utm` (for example `30N 699316.2 E 5710164.4 N`).

## Improved

- Geological-time lookup now has a live 0–4600 Ma slider plus segmented eon, Phanerozoic-era and period views.
- GNSS averaging now shows the continuously updated current average coordinate and a convergence/spread ring.
- Structural-plane capture now explains strike, dip, phone placement and the meaning of the phone's top edge in plain language.

## Unchanged

- Scientific algorithms and output field names are unchanged except for the optional new `utm_text` input.
- All capabilities remain Development pending complete Android/ODK and physical validation.
