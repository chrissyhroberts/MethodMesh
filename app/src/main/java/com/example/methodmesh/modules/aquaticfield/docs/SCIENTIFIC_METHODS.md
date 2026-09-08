# Aquatic Fieldwork scientific methods

## PSS-78 Practical Salinity

The calculation accepts conductivity in `mS/cm`, `uS/cm` or `S/m`, converts to mS/cm, and applies the PSS-78 conductivity-ratio polynomial using ITS-90 temperature converted to IPTS-68 with `T68 = T90 * 1.00024`.

Reference conductivity for `SP=35`, `t=15 °C`, `p=0 dbar` is 42.9140 mS/cm.

The implementation records:

```text
aquatic_algorithm = PSS-78 (TEOS-10 compatible Practical Salinity definition)
```

Results below SP=2 are flagged for validation; do not promote this method to Production for low-salinity work until the low-salinity extension has been checked against authoritative GSW vectors.

## Reference Salinity

Where Practical Salinity is calculated, the module also returns:

```text
SR = SP × 35.16504 / 35
```

as `aquatic_reference_salinity_g_kg`.

This is **not Absolute Salinity**.

## Absolute Salinity

Not yet enabled. A defensible implementation requires the TEOS-10 Absolute Salinity Anomaly / SAAR data treatment.

## Conductivity → TDS

This is an empirical estimate:

```text
TDS_mg_L = conductivity_uS_cm × coefficient
```

The coefficient is a user/project input and is stored in audit metadata.

## Specific conductance at 25 °C

The initial implementation uses the explicit operator-supplied linear temperature coefficient:

```text
K25 = Kt / (1 + alpha × (t - 25))
```

It must not be interpreted as an instrument-specific compensation curve unless that instrument/project has validated `alpha`.

## Pressure → depth

Uses the UNESCO 1983 / Saunders polynomial with latitude-dependent gravity.

Input pressure is sea pressure in dbar.

Pressure zero offset, vertical sensor/bottle offset and an optional user-defined correction factor are separately represented.

## Wire-out depth

Simple geometry only.

For angle from vertical:

```text
vertical_depth = wire_length × cos(angle)
```

For angle from horizontal:

```text
vertical_depth = wire_length × sin(angle)
```

Cable stretch and catenary are not modelled.

## Secchi

When both observations are present:

```text
Secchi depth = (disappearance + reappearance) / 2
```

When only one is present, that value is returned with a warning.

Carlson transparency trophic-state index when enabled:

```text
TSI(SD) = 60 - 14.41 ln(SD)
```

where `SD` is metres.

Euphotic depth is not enabled by default. If explicitly enabled:

```text
estimated euphotic depth = Secchi depth × configured multiplier
```

and is labelled empirical.

## Profile gradients

Processed rows are sorted by depth.

For adjacent valid values:

```text
gradient = (value_i - value_(i-1)) / (depth_i - depth_(i-1))
```

The strongest absolute gradient midpoint is reported as the candidate thermocline / halocline / oxycline / pycnocline.

This is a reproducible operational definition, not a claim that every waterbody has a unique ecological boundary at that exact depth.

## Mixed-layer depth

The selected temperature or density value at the nearest row to the configured reference depth is used as the reference.

The first deeper row that differs by at least the configured threshold is returned.

## Buoyancy frequency

If density is supplied:

```text
N² = g / rho_mean × d(rho)/dz
```

with depth positive downward. Stable density increase with depth therefore gives positive N².

## Whole-lake metrics

Schmidt stability and whole-lake heat content are not silently approximated from a single profile. They remain unavailable until appropriate bathymetric/hypsographic geometry integration is supplied.
