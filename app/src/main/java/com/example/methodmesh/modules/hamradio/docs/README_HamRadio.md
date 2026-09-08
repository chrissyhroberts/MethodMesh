# Amateur radio / Ham Radio

**MethodMesh module:** `hamradio`  
**Status:** Development  
**Version:** `0.4.0`  
**Canonical delivery folder:** `app/src/main/java/com/example/methodmesh/modules/hamradio/`

## Purpose

The Ham Radio module combines a **live operator dashboard** with independently callable amateur-radio methods. The dashboard answers the immediate operating question — **which HF band should I try now?** — while the underlying methods remain separately callable for space weather, PSK Reporter activity, localised HF band guidance and offline RF calculations.

The module is designed so that each method can be used:

- directly from its native MethodMesh capability screen;
- as a saved preset;
- as a step in a protocol or schedule;
- through RIL bindings;
- from ODK/XLSForm through the normal MethodMesh Android-intent transport.

Most calculations are fully offline. `ham.dashboard`, `ham.spaceweather.snapshot`, `ham.activity.pskreporter`, and `ham.band.recommend` when configured for live NOAA conditions require network access. The dashboard also uses Android location permission when its QTH source is `auto`.

## Public capabilities

| Capability | Method ID | Network? | Main result |
|---|---|---:|---|
| **Ham radio dashboard** | `ham.dashboard` | Yes | Best band now, ranked alternatives, space weather, QTH and optional PSK activity |
| Space weather | `ham.spaceweather.snapshot` | Yes | Compact Kp / SFI / G-R-S / solar-wind summary |
| PSK Reporter activity | `ham.activity.pskreporter` | Yes | Recent observed reports plus station/grid/band/mode summary |
| Best HF bands now | `ham.band.recommend` | Optional | Ranked top bands with representative MHz and score |
| Maidenhead converter | `ham.maidenhead.convert` | No | Locator or decoded centre coordinate |
| Radio path | `ham.path.calculate` | No | Distance and short-path bearing between two locators |
| Antenna length | `ham.antenna.calculate` | No | Starting antenna dimensions for a frequency/design |
| SWR calculator | `ham.swr.calculate` | No | SWR plus mismatch loss |
| RF link calculator | `ham.link.calculate` | No | FSPL and midpoint first-Fresnel radius, with optional horizon/EIRP |

All nine methods are declared as **Development** in v0.4.0.

## Native UI: operator-first workflow

v0.3.0 replaced the original generic settings form with a task-oriented native UI. v0.4.0 adds the live dashboard as the default situational-awareness surface. The eight existing method contracts remain unchanged; `ham.dashboard` is added as a ninth method.

The native screens now follow four rules:

- **choices are choices, not text boxes** — callsign/grid, sent/received, operating mode, antenna type, locator precision and similar enums are rendered as labelled buttons;
- **normal use is short** — only the inputs needed for the common case are visible initially; provider/cache controls, link-budget extras and uncommon filters live under **Advanced settings**;
- **every screen says how to use it** — a short numbered workflow appears above the controls;
- **results are compact** — the scaffold receives only the useful result fields for its primary result panel, while the complete AS100 result remains available for provenance/export.

### Quick-start

| Tool | What you normally do |
|---|---|
| **Ham radio dashboard** | Open it, allow GPS (or enter your locator), choose **Regional / Continental / DX / Long DX** and mode, then tap **Refresh**. Optionally add your callsign for PSK Reporter activity. |
| Space weather | Open it and tap **Check space weather**. |
| PSK Reporter | Enter a callsign/grid, choose *who heard it* / *what it heard*, a time window and mode, then tap **Check activity**. |
| Best HF bands now | Enter your QTH locator, a target grid or approximate distance, choose mode, then tap **Recommend bands**. |
| Maidenhead | Choose *Coordinates* or *Grid locator*, enter the value and tap **Convert**. |
| Radio path | Enter *From grid* and *To grid*, then tap **Calculate path**. |
| Antenna length | Enter frequency, choose antenna type, then tap **Calculate length**. |
| SWR | Enter forward and reflected power, then tap **Calculate SWR**. |
| RF link / Fresnel | Enter frequency, distance and antenna heights, then tap **Calculate link**. Open Advanced only for TX power/gain/loss. |

---

# 0. Ham radio dashboard

## Method

`ham.dashboard`

## What it does

The dashboard is the module's operator-facing situational-awareness capability. It composes the existing Ham Radio methods rather than replacing them:

- resolves the current QTH from a supplied Maidenhead locator, supplied coordinates, or Android GPS;
- reads one current NOAA SWPC space-weather snapshot;
- feeds that snapshot into the lightweight HF band heuristic without making a second NOAA request;
- ranks the bands for the selected operating distance and mode;
- optionally includes recent PSK Reporter activity for the configured callsign, while retaining the repository's hard seven-minute upstream gate.

The main hero card is deliberately operational: **Try 20m · 14.15 MHz · score 82/100**, followed by the next bands to try. The score is a relative ranking from the v0.1 heuristic, not a probability of completing a QSO and not a MUF/VOACAP prediction.

### Dashboard controls

The common workflow is intentionally short:

1. Open the dashboard. With QTH source `auto`, MethodMesh requests location permission and converts the current GPS position to a six-character Maidenhead locator.
2. Choose the approximate path: **Regional (500 km)**, **Continental (1500 km)**, **DX (2500 km)** or **Long DX (7000 km)**.
3. Choose **General**, **SSB**, **CW** or **FT8**. Changing range or mode recalculates the band ranking locally from the already-fetched conditions; it does not make another NOAA or PSK request.
4. Tap **Refresh** to reacquire QTH and live provider data.
5. Optionally add your callsign to show recent PSK Reporter report/station/grid counts.

A manual Maidenhead entry is available if GPS is unavailable or the operator wants advice for another QTH.

### Dashboard-as-capability semantics

The implementation follows the Astronomy dashboard pattern. `ham.dashboard` is a normal AS1.00 method with stable inputs/outputs and a normal `CapabilityScreenSpec`, but **dashboard refreshes are preview state rather than automatic graph writes**.

When opened from the MethodMesh dashboard:

- live refreshes update the visible cards only;
- the screen passes no captured result to `CapabilityScreenScaffold` while it remains in live-dashboard mode;
- **Use this snapshot** explicitly commits the current dashboard state as the capability result.

When called externally (ODK, intent, protocol or dependency), the same capability composes a normal AS1.00 result and follows the standard AutomaticReturn behavior.

This distinction lets the screen behave like a continuously refreshed instrument without filling the MethodMesh graph with incidental refreshes.

### Core outputs

| Output | Meaning |
|---|---|
| `ham_dashboard_result` | **Main result.** Best-band recommendation and immediate alternatives. |
| `ham_dashboard_primary_band` / `ham_dashboard_primary_mhz` | Top-ranked band and representative frequency. |
| `ham_dashboard_primary_score` | Relative heuristic score, 0–100. |
| `ham_dashboard_secondary_bands` | Compact next-band alternatives. |
| `ham_dashboard_ranked_bands_json` | Full ranked band list used by the dashboard. |
| `ham_dashboard_qth_locator` | Effective QTH Maidenhead locator. |
| `ham_dashboard_location_source` | `live GPS`, supplied locator, or supplied coordinates. |
| `ham_dashboard_target_distance_km` / `ham_dashboard_mode` | Operating scenario used for the ranking. |
| `ham_dashboard_kp`, `ham_dashboard_f107`, `ham_dashboard_wind_speed_kms`, `ham_dashboard_bz_nt` | Radio-relevant space-weather values. |
| `ham_dashboard_g_scale`, `ham_dashboard_r_scale`, `ham_dashboard_s_scale` | NOAA alert scales. |
| `ham_dashboard_alert` | Compact operational warning derived from current conditions/staleness. |
| `ham_dashboard_psk_result` | Optional PSK Reporter summary. |
| `ham_dashboard_psk_report_count` | Optional recent report count. |
| `ham_dashboard_psk_retry_after_seconds` | Remaining PSK upstream lockout, if any. |
| `ham_dashboard_json` | Structured operator snapshot. |
| `ham_dashboard_audit_json` | Component/audit payload used to compose the dashboard. |
| `ham_dashboard_status` / `ham_dashboard_error` | Execution state and failure detail. |

The dashboard sends the configured callsign/query parameters to PSK Reporter only when PSK integration is enabled by supplying a callsign. GPS/QTH is **not** sent to NOAA.

---

# 1. Space weather

## Method

`ham.spaceweather.snapshot`

## What it does

Retrieves current radio-relevant values from the NOAA Space Weather Prediction Center (SWPC):

- planetary **Kp**;
- **F10.7 / 10.7 cm solar radio flux**;
- solar-wind speed;
- interplanetary magnetic-field **Bt** and **Bz** where available;
- NOAA **G**, **R** and **S** scale levels.

Provider endpoints used in v0.4.0:

- `https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json`
- `https://services.swpc.noaa.gov/products/summary/10cm-flux.json`
- `https://services.swpc.noaa.gov/products/summary/solar-wind-speed.json`
- `https://services.swpc.noaa.gov/products/summary/solar-wind-mag-field.json`
- `https://services.swpc.noaa.gov/products/noaa-scales.json`

Provider: **NOAA Space Weather Prediction Center (SWPC)**.

The repository parser accepts the current object-array JSON format and retains compatibility with the older header-array shape. It uses a short in-memory cache so repeated native/preset calls do not unnecessarily poll NOAA.

## Inputs

| Input | Default | Meaning |
|---|---|---|
| `refresh_mode` | `cache_preferred` | `cache_preferred` reuses a recent result; `fresh_required` attempts a fresh fetch. |

## Core outputs

| Output | Meaning |
|---|---|
| `ham_space_weather_result` | **Main result.** Human-readable compact summary. |
| `ham_space_weather_kp` | Planetary Kp. |
| `ham_space_weather_f107` | F10.7 solar flux. |
| `ham_space_weather_wind_speed_kms` | Solar-wind speed in km/s. |
| `ham_space_weather_bt_nt` | IMF Bt in nT where available. |
| `ham_space_weather_bz_nt` | IMF Bz in nT where available. |
| `ham_space_weather_g_scale` | NOAA geomagnetic-storm G scale. |
| `ham_space_weather_r_scale` | NOAA radio-blackout R scale. |
| `ham_space_weather_s_scale` | NOAA solar-radiation-storm S scale. |
| `ham_space_weather_provider_time` | Provider timestamp where exposed. |
| `ham_space_weather_retrieved_time_iso` | MethodMesh retrieval timestamp. |
| `ham_space_weather_from_cache` | Whether the returned snapshot came from the module cache. |
| `ham_space_weather_stale` | Whether fallback data is older than the stale threshold. |
| `ham_space_weather_provider` | Provider attribution. |
| `ham_space_weather_source_urls` | Pipe-separated provider endpoints used. |
| `ham_space_weather_warnings` | Non-fatal fetch/parse warnings. |
| `ham_space_weather_status` / `ham_space_weather_error` | Execution state and failure detail. |

### Example main result

```text
Kp 2.0 · SFI 154 · G0 R0 S0 · wind 417 km/s · Bz -1.8 nT
```

---

# 2. PSK Reporter activity

## Method

`ham.activity.pskreporter`

## What it does

Queries the PSK Reporter reception-record API for recent observed amateur-radio activity. The method can look up a callsign or grid square, restrict the look-back window, direction, mode and frequency range, and returns both a compact operational summary and the underlying structured reception reports.

Provider: **PSK Reporter**. Retrieval endpoint: `https://retrieve.pskreporter.info/query`. Developer documentation: `https://www.pskreporter.info/pskdev.html`.

PSK Reporter asks clients not to retrieve reception data more often than once every five minutes. MethodMesh deliberately uses a stricter **seven-minute hard floor (420 seconds)** between upstream retrieval attempts. The gate is enforced at the repository network boundary and shared across repository instances within the running app process.

The seven-minute rule is not merely a UI timer:

- the gate is reserved **before** network I/O, so concurrent calls cannot race around it;
- failed upstream attempts also consume the seven-minute slot;
- an identical query made during the lockout returns the cached matching result without contacting PSK Reporter again;
- a different uncached query during the lockout returns `rate_limited` and `ham_psk_retry_after_seconds`;
- a new upstream request becomes eligible at 420 seconds after the previous upstream attempt.

The gate and cache are process-memory state in v0.4.0. Restarting the MethodMesh process resets them; persistent cross-restart rate-state is a possible hardening step before Production.

## Inputs

| Input | Default | Meaning |
|---|---|---|
| `subject_type` | `callsign` | `callsign` or `grid`. Grid mode uses PSK Reporter's `modify=grid` semantics. |
| `subject` | blank | Callsign or Maidenhead/grid value to query. Required. |
| `direction` | `sent` | `sent`, `received`, or `either`. |
| `lookback_minutes` | `30` | Retrieval window, constrained to 5–1440 minutes. |
| `mode` | blank | Optional ADIF mode/submode filter; blank means any. |
| `min_frequency_mhz` | `0` | Optional lower frequency bound; 0 means unset. |
| `max_frequency_mhz` | `0` | Optional upper frequency bound; 0 means unset. |
| `record_limit` | `100` | Maximum reports requested, 1–100. |
| `include_no_locator` | `no` | Whether reports lacking a locator may be included. |

## Core outputs

| Output | Meaning |
|---|---|
| `ham_psk_result` | **Main result.** Report/station/grid summary and dominant band/mode. |
| `ham_psk_report_count` | Number of reports returned. |
| `ham_psk_unique_counterpart_callsigns` | Number of unique counterpart callsigns in the result. |
| `ham_psk_unique_grids` | Number of unique counterpart grids. |
| `ham_psk_latest_time_iso` | Latest report timestamp found. |
| `ham_psk_band_counts_json` | Counts by derived amateur band. |
| `ham_psk_mode_counts_json` | Counts by mode/submode. |
| `ham_psk_reports_json` | Structured report array with callsigns, locators, frequency, band, mode, SNR and time. |
| `ham_psk_from_cache` / `ham_psk_cache_age_seconds` | Cache provenance. |
| `ham_psk_rate_limit_seconds` | Fixed MethodMesh upstream floor: `420`. |
| `ham_psk_retry_after_seconds` | Seconds until another upstream attempt is permitted. |
| `ham_psk_request_url` | Exact provider request used/that would be used. |
| `ham_psk_provider` / `ham_psk_source_url` | Provider attribution. |
| `ham_psk_warning` | Non-fatal cache/gate/fallback warning. |
| `ham_psk_status` / `ham_psk_error` | `succeeded`, `rate_limited`, or failure detail. |

### Example main result

```text
42 reports · 18 stations · 14 grids · 20m/FT8
```

This is an observation of reports received by PSK Reporter, not proof that a path is currently available to every station or mode.

---

# 3. Best HF bands now

## Method

`ham.band.recommend`

## What it does

Ranks the amateur HF bands from 160 m through 10 m using a transparent v0.1 heuristic based on:

- operator QTH latitude/longitude or Maidenhead locator;
- current UTC time and local solar geometry;
- requested path length, or distance derived from an optional target Maidenhead locator;
- live NOAA Kp, F10.7 and R-scale values, or manual values when offline;
- broad operating-mode sensitivity.

The output is intentionally a **band ranking**, not a claim to calculate an exact MUF, LUF or circuit reliability. The heuristic is not VOACAP, REC533, an ionosonde assimilation model, or a replacement for on-air observation.

### Frequency semantics

Each ranked band includes a **representative MHz value** purely to make the result legible and useful for calculations. That value is **not** an instruction to transmit there.

Legal amateur allocations, licence privileges, power limits, channelisation and mode sub-bands differ by country and can change. Operators must use their current national rules and applicable band plan before transmitting.

## Inputs

| Input | Default | Meaning |
|---|---|---|
| `location_mode` | `locator` | Explicitly choose `locator` or `coordinates`. |
| `origin_locator` | blank | Required when `location_mode=locator`; 2/4/6/8-character Maidenhead locator. |
| `latitude` | `0` | Required explicitly when `location_mode=coordinates`. |
| `longitude` | `0` | Required explicitly when `location_mode=coordinates`. |
| `target_locator` | blank | Optional destination Maidenhead locator. |
| `target_distance_km` | `2000` | Used when `target_locator` is blank. |
| `conditions_source` | `live_noaa` | `live_noaa` or `manual`. |
| `kp` | `2` | Manual/fallback Kp. |
| `f107` | `100` | Manual/fallback F10.7. |
| `r_scale` | `0` | Manual/fallback NOAA R scale. |
| `mode` | `mixed` | `mixed`, `ssb`, `cw`, `ft8`, `ft4`, or `digital`. |
| `when_iso` | blank | Optional UTC ISO-8601 time; blank means now. |

For protocol/ODK use, set `location_mode` explicitly. Locator mode requires `origin_locator`; coordinate mode requires both latitude and longitude. This prevents an omitted QTH from silently becoming 0°,0°.

## Core outputs

| Output | Meaning |
|---|---|
| `ham_band_result` | **Main result.** Top-three ranked bands. |
| `ham_band_primary` | Highest-ranked band. |
| `ham_band_primary_mhz` | Representative MHz for the top band. |
| `ham_band_primary_score` | 0–100 relative heuristic score. |
| `ham_band_ranked_json` | Full ranked list with reasons. |
| `ham_band_solar_elevation_deg` | Solar elevation at the origin. |
| `ham_band_local_solar_hour` | Approximate local solar hour. |
| `ham_band_target_distance_km` | Effective path distance. |
| `ham_band_origin_locator` | Effective/normalised origin locator. |
| `ham_band_target_locator` | Normalised target locator when supplied. |
| `ham_band_kp`, `ham_band_f107`, `ham_band_r_scale` | Conditions used by the heuristic. |
| `ham_band_conditions_source` | Live/manual source selection. |
| `ham_band_mode` | Mode used. |
| `ham_band_heuristic_version` | Version of the ranking heuristic. |
| `ham_band_advisory` | Explicit non-regulatory/non-prediction caveat. |
| `ham_band_status` / `ham_band_error` | Execution state and failure detail. |

### Example main result

```text
Best now: 15m (21.20 MHz), then 17m / 20m · score 91/100
```

The score is for ordering candidates within this heuristic. It is **not** a probability of successful contact.

---

# 4. Maidenhead converter

## Method

`ham.maidenhead.convert`

Encodes WGS84 coordinates to 2-, 4-, 6- or 8-character Maidenhead locators, or decodes a locator to the centre coordinate and cell span.

## Inputs

| Input | Default | Meaning |
|---|---|---|
| `operation` | `encode` | `encode` or `decode`. |
| `latitude` | `0` | Latitude for encode. |
| `longitude` | `0` | Longitude for encode. |
| `precision` | `6` | Output characters: 2, 4, 6 or 8. |
| `locator` | blank | Locator for decode. |

## Core outputs

`ham_maidenhead_result` is the main result. Structured fields return the normalised locator, centre latitude/longitude, and latitude/longitude cell spans.

Example:

```text
IO91wm · 51.5208, -0.1250
```

---

# 5. Grid-to-grid radio path

## Method

`ham.path.calculate`

Calculates a spherical great-circle path between two Maidenhead locator cell centres.

## Inputs

- `origin_locator`
- `destination_locator`

## Core outputs

| Output | Meaning |
|---|---|
| `ham_path_result` | **Main result.** Distance + initial short-path bearing. |
| `ham_path_distance_km` | Great-circle km. |
| `ham_path_distance_mi` | Great-circle miles. |
| `ham_path_initial_bearing_deg` | Initial short-path bearing from origin. |
| `ham_path_reverse_bearing_deg` | Initial bearing from destination back to origin. |
| `ham_path_long_path_bearing_deg` | Opposite initial bearing for long-path orientation. |
| `ham_path_origin_locator` / `ham_path_destination_locator` | Normalised locators. |

The bearing is a spherical initial bearing; it is not a terrain-aware route.

---

# 6. Antenna-length calculator

## Method

`ham.antenna.calculate`

Calculates wavelength-derived starting dimensions for:

- quarter-wave;
- half-wave;
- centre-fed dipole;
- five-eighths-wave;
- full-wave.

## Inputs

| Input | Default | Meaning |
|---|---|---|
| `frequency_mhz` | `14.2` | Frequency in MHz. |
| `design` | `dipole` | Antenna design. |
| `velocity_factor` | `0.95` | Multiplicative velocity/end-effect factor. |

## Core outputs

`ham_antenna_result` is the main result. Structured outputs include wavelength, total length and element length in metres and feet, plus the factor used.

For a dipole, `element_length` is one leg.

**Operational caveat:** this is a starting dimension. Installation geometry, conductor diameter, insulation, height and surroundings alter resonance. Cut long and tune with measurement.

---

# 7. SWR calculator

## Method

`ham.swr.calculate`

Calculates reflection coefficient, SWR, return loss and mismatch loss from forward and reflected power.

## Inputs

- `forward_power_w`
- `reflected_power_w`

Reflected power must be non-negative and lower than forward power for a finite SWR.

## Core outputs

- `ham_swr_result` — main result;
- `ham_swr_value`;
- `ham_swr_reflection_coefficient`;
- `ham_swr_return_loss_db` (`infinite` for zero reflected power);
- `ham_swr_mismatch_loss_db`.

---

# 8. RF link / Fresnel calculator

## Method

`ham.link.calculate`

Calculates:

- free-space path loss (FSPL);
- optical horizon from two antenna heights;
- approximate radio horizon using a standard effective-Earth-radius shortcut;
- first Fresnel-zone radius at the path midpoint;
- optional EIRP from TX power, antenna gain and feedline loss;
- optional free-space received power from that EIRP.

## Inputs

| Input | Default | Meaning |
|---|---|---|
| `frequency_mhz` | `145` | Frequency in MHz. |
| `distance_km` | `25` | Path distance. |
| `tx_height_m` | `10` | TX antenna height. |
| `rx_height_m` | `10` | RX antenna height. |
| `tx_power_w` | `0` | TX power; `0` means omit EIRP/received-power calculation. |
| `antenna_gain_dbi` | `0` | TX antenna gain. |
| `feedline_loss_db` | `0` | TX feedline loss. |

## Core outputs

`ham_link_result` is the main result. Structured outputs return FSPL, optical/radio horizon, midpoint Fresnel radius, optional EIRP and optional free-space received power.

This is a **free-space approximation**. Terrain, clutter, diffraction, polarization, fading, RX antenna gain/loss and receiver sensitivity are not modelled.

---

# Native workflow

Each public method has its own `CapabilityScreenSpec` and uses the shared `CapabilityScreenScaffold`.

Typical workflows:

1. **Space weather:** open, read current conditions, optionally refresh.
2. **PSK Reporter:** enter a callsign/grid and filters, then read recent observed reports; repeated retrievals remain behind the seven-minute upstream gate.
3. **Best HF bands now:** enter your QTH grid or coordinates, optionally a target grid, select live/manual conditions and mode, then rank.
4. **Maidenhead:** encode coordinates or decode a locator.
5. **Path:** enter two locators and calculate.
6. **Antenna/SWR/link:** enter measurements/parameters and calculate.

The screens use normal `capabilitySettings()` metadata and hide fixed preset settings through `settingShouldBeShown(...)`.

External intent launches execute without requiring a second native configuration gate.

# Preset / protocol / schedule workflow

Every input is declared as a normal MethodMesh method setting, so it can be fixed in a preset or supplied at runtime.

Useful compositions include:

- scheduled `ham.spaceweather.snapshot` → conditional notification/protocol;
- a prior public location/grid method → `ham.band.recommend`;
- `ham.path.calculate` distance → `ham.link.calculate`;
- band recommendation representative frequency → `ham.antenna.calculate` for a quick portable-antenna starting length.

The ham-radio module does not copy location, QR, Plus Code or generic online-data capability internals. Cross-capability use should occur through public MethodMesh fields/pipes.

# RIL bindings

The module exposes:

```text
read space weather
read psk reporter
recommend ham band
convert maidenhead
calculate radio path
calculate antenna length
calculate swr
calculate radio link
```

Example:

```text
WHAT; read space weather; RESULT; return execution.id as execution_id; format json
```

# ODK / XLSForm workflow

Use:

```text
docs/example_odk_HamRadio.xlsx
```

The workbook exercises all nine public methods through MethodMesh Android intents, including `ham.dashboard`. Calls are made through XLSForm groups; inputs are supplied as intent extras; useful core fields return as ordinary columns; and `methodmesh_full_json` retains the full audit/provenance envelope.

Generic pattern:

```text
ODK group -> com.example.methodmesh.EXECUTE_METHOD -> ham.* method -> core fields + methodmesh_full_json
```

Example space-weather call:

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='ham.spaceweather.snapshot',
  input_refresh_mode=${refresh_mode},
  input_payload_mode='FULL',
  return_mode='flat'
)
```

Example PSK Reporter call:

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='ham.activity.pskreporter',
  input_subject_type=${psk_subject_type},
  input_subject=${psk_subject},
  input_direction=${psk_direction},
  input_lookback_minutes=${psk_lookback_minutes},
  input_mode=${psk_mode},
  input_record_limit=${psk_record_limit},
  input_payload_mode='FULL',
  return_mode='flat'
)
```

Example band-advice call:

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='ham.band.recommend',
  input_location_mode=${location_mode},
  input_origin_locator=${origin_locator},
  input_target_locator=${target_locator},
  input_target_distance_km=${target_distance_km},
  input_conditions_source=${conditions_source},
  input_kp=${kp},
  input_f107=${f107},
  input_r_scale=${r_scale},
  input_mode=${mode},
  input_payload_mode='FULL',
  return_mode='flat'
)
```

# Permissions, services and privacy

## Network

`ham.spaceweather.snapshot`, `ham.activity.pskreporter`, and live `ham.band.recommend` require Android Internet access. Space-weather reads contact NOAA SWPC endpoints listed above; PSK activity reads contact `retrieve.pskreporter.info`.

**The operator's QTH is not sent to NOAA.** Localisation and solar/path calculations happen locally; the NOAA call contains no latitude, longitude, locator or callsign.

A PSK Reporter query necessarily sends the supplied `subject` (callsign or grid) and any selected query filters to PSK Reporter. The exact request URL is retained in `ham_psk_request_url` for auditability. MethodMesh does not silently attach GPS coordinates to this request.

## Location

v0.4.0 **does** use Android location permission for `ham.dashboard` when `location_source=auto`. The standalone calculation methods remain usable with explicit locators/coordinates and do not require GPS. The dashboard converts GPS locally to Maidenhead; it does not send the QTH to NOAA.

This is deliberate: a propagation helper should not silently acquire or transmit precise position.

# Offline behaviour

Fully offline methods:

- `ham.maidenhead.convert`;
- `ham.path.calculate`;
- `ham.antenna.calculate`;
- `ham.swr.calculate`;
- `ham.link.calculate`.

`ham.band.recommend` also works offline with `conditions_source=manual`.

`ham.activity.pskreporter` is network-backed. During the seven-minute lockout, an identical query can be served from its matching process-memory cache without another provider request. A different uncached query is not sent upstream until the gate opens.

The NOAA repository keeps a short process-memory cache. If a live refresh fails and a prior snapshot exists, it can return that snapshot with cache/staleness metadata. NOAA and PSK cache/rate-gate state are not persisted across app restarts in v0.4.0.

# Result design / audit

Each method returns a compact human-usable main result first, followed by structured fields and normal MethodMesh provenance. The ODK example requests `payload_mode=FULL`, so `methodmesh_full_json` is the canonical background audit payload.

The module deliberately avoids making JSON the only useful output.

# Known limitations — Development

1. `ham.band.recommend` is an explicit heuristic, not a physical ionospheric propagation model. It does not calculate MUF, LUF, reliability, take-off angle, hops or path absorption.
2. The heuristic uses conditions at the **origin** and a path-length class. It does not yet model solar illumination/geomagnetic conditions along the entire great-circle path.
3. Representative band frequencies are not regulatory advice or mode-specific operating frequencies.
4. v0.4.0 contains no country/licence band-plan database.
5. No direct GPS fix is acquired by this module; localisation is explicit/manual or piped from another public capability.
6. NOAA cache and the PSK Reporter cache/seven-minute gate are process-memory only and are lost when the app process stops.
7. Provider formats can change. NOAA JSON and PSK Reporter XML integrations require device/network regression tests.
8. PSK Reporter is an observational digital-mode reporting network; absence of reports is not evidence of absence of propagation.
9. VHF/UHF tropospheric ducting, sporadic-E, meteor scatter and auroral propagation are not modelled.
10. The full Android build, orientation recreation, native preset flows, live provider calls and ODK Collect round-trip still require validation in the complete MethodMesh checkout before Production promotion.

# Roadmap / high-value follow-ons

## A. Compose observed activity with band advice

Keep `ham.activity.pskreporter` as a separate observation method, but allow protocols/UI to place its recent observed band/path evidence beside `ham.band.recommend`. Do not silently merge PSK observations into the versioned heuristic score; preserve provenance and let the operator see predicted guidance versus observed reports.

## B. Proper propagation model

Add a versioned method using an established propagation engine/model (for example VOACAP/REC533-style inputs) with:

- origin + destination;
- date/time;
- antenna gains/pattern assumptions;
- TX power;
- SSN/solar inputs;
- reliability output by band/hour.

The current heuristic should remain available for lightweight/offline use and retain its own stable method ID/version.

## C. Satellite pass predictor

A separate `ham.satellite.passes` capability could combine cached TLEs with local SGP4 calculation to return:

- next visible/radio pass;
- AOS / LOS;
- max elevation;
- azimuth track;
- Doppler shift at configured uplink/downlink frequencies.

Keep orbital calculation local after TLE acquisition and record TLE age/source.

## D. Repeater finder

Provider-backed VHF/UHF repeater search by QTH, distance, band and mode. Provider terms/API access must be checked before implementation. Return frequency/offset/tone/mode as structured data, with an explicit regulatory/currentness caveat.

## E. Portable-operator helpers

Potential independent methods:

- POTA/SOTA activation checklist and spot hand-off;
- compass/bearing-to-DX composition using the existing Compass public boundary;
- coax-loss calculator by cable type/frequency/length;
- dB / watts / volts / field-strength conversion pack;
- RF exposure / exclusion-distance calculator tied to explicit jurisdictional rules;
- battery/runtime estimator for portable stations;
- solar/battery planning for field activations.

## F. QSO record / ADIF

Logging should probably be a **separate persistent module** rather than mixed into these stateless calculators. It could provide:

- quick QSO capture;
- callsign, grids, frequency/band/mode, RST, notes;
- automatic timestamp;
- ADIF import/export;
- duplicate/worked-before lookup;
- statistics by band/mode/grid/country.

## G. Radio training / utility

Possible lightweight operators' tools:

- Morse code trainer/keyer practice;
- Q-code and common-abbreviation lookup;
- phonetic alphabet drill;
- contest serial / exchange helper;
- band-edge reminder from an explicitly selected jurisdiction/band-plan dataset.

# Validation required before Production

At minimum:

1. run `./gradlew :app:assembleDebug` in the complete current MethodMesh checkout;
2. run pure Kotlin unit tests for Maidenhead, path, antenna, SWR, link and band-ranking logic;
3. test the PSK Reporter seven-minute network-boundary gate, including identical-query cache reuse, different-query blocking, failure-slot consumption and exact 420-second reopening;
4. test current NOAA and PSK Reporter responses plus offline/cache fallback on device/emulator;
5. verify every native screen on phone-sized layouts;
6. rotate during configuration and after capture; confirm useful result state survives;
7. test native preset creation and fixed/runtime-field hiding;
8. test scheduled/protocol execution and close-out;
9. run every group in `example_odk_HamRadio.xlsx` through ODK Collect;
10. verify main share actions expose the useful result rather than verbose audit metadata;
11. only then change descriptor status from Development to Production.

See `BUILD_REPORT.md` in this module's `docs/` folder for the validation performed while preparing this prototype.
