# Ham Radio v0.4.0 — deterministic test vectors

| Function | Input | Expected / tolerance |
|---|---|---|
| Maidenhead encode | lat `51.5074`, lon `-0.1278`, precision `6` | `IO91wm` |
| Maidenhead decode/roundtrip | `IO91wm` | centre within roughly 0.03° latitude / 0.05° longitude |
| Great-circle path | London -> NYC | ~5570 km; initial bearing ~288° |
| Dipole | `14.2 MHz`, factor `0.95` | total ~10.03 m; leg ~5.02 m |
| SWR | forward `100 W`, reflected `4 W` | gamma `0.2`; SWR `1.5:1` |
| FSPL | `145 MHz`, `25 km` | ~103.63 dB |
| Horizon | TX/RX heights `10 m` | radio horizon > optical horizon |
| Daylight DX heuristic | London, 2026-06-21 12:00Z, 5000 km, Kp 2, F10.7 180, R0, SSB | top band in 20/17/15/12/10 m set |
| Short-path night heuristic | London, 2026-12-21 00:00Z, 300 km, Kp 2, F10.7 90, R0 | top band in 160/80/60/40 m set |


## Dashboard composition vectors (v0.4)

The dashboard method itself is a pure composition step: provide component-value JSON rather than allowing `ham.dashboard` to make provider calls. This keeps orchestration in the screen/repository boundary and makes the method deterministic.

1. Supply a successful space-weather map containing Kp `2`, F10.7 `180`, R/G/S `0`; a successful band-advice map whose first ranked band is `20m` at `14.15` MHz with score `82`; and no PSK component. Assert `ham_dashboard_status=succeeded`, primary band `20m`, MHz `14.15`, score `82`, PSK enabled `false`, and a main result beginning `Try 20m`.
2. Supply the same components with Kp `6`. Assert the dashboard alert is non-empty and reflects disturbed geomagnetic conditions while the component values remain traceable in `ham_dashboard_audit_json`.
3. Supply a successful PSK map with report count `42`, 18 unique counterpart callsigns and 14 grids. Assert those fields are copied into dashboard core outputs and `ham_dashboard_psk_enabled=true`.
4. Supply a PSK `rate_limited` map with `ham_psk_retry_after_seconds=300`. Dashboard composition should still succeed if space weather and band advice succeeded, preserving the PSK status/retry metadata rather than failing the whole dashboard.
5. Remove/mark failed the required band-advice component. Assert dashboard status is failed with an explanatory error.

### Dashboard UI semantics

1. In `CapabilityPresentationMode.Dashboard`, a successful refresh updates the dashboard cards but `CapabilityScreenScaffold` receives `capturedResult=null`; therefore refresh alone does not commit an observation.
2. **Use this snapshot** passes the current reconstructed `ExecutionResult` to `onConfirmed`.
3. In external/AutomaticReturn presentation, the same composed result is supplied to the scaffold and returns through the normal automatic completion path.
4. Changing range or mode after a refresh recalculates from the saved QTH + NOAA component without another provider request.
5. With no callsign configured, PSK Reporter is not queried. With a callsign configured, any PSK query uses `HamRadioRepository.shared`, so the hard 420-second gate cannot be bypassed by dashboard refresh.
6. If GPS permission is absent in automatic QTH mode, the dashboard offers the permission flow and manual Maidenhead fallback rather than silently using 0°,0°.

## PSK Reporter hard-gate vector

Use a fake HTTP client and mutable UTC clock, starting `2026-09-04T10:00:00Z`.

| Step | Query/time | Expected | Upstream GETs |
|---|---|---|---:|
| 1 | `M0AAA`, T+0 | `succeeded` | 1 |
| 2 | same query, T+60 s | cached `succeeded`, retry-after 360 | 1 |
| 3 | `M0CCC`, T+60 s | `rate_limited`, retry-after 360 | 1 |
| 4 | same time through a new repository instance | `rate_limited`; instance cannot bypass gate | unchanged |
| 5 | `M0CCC`, T+420 s | `succeeded` | +1 |
| 6 | forced HTTP failure when gate next opens | `failed`; slot still reserved | +1 |
| 7 | different query +60 s after failure | `rate_limited`; no GET | unchanged |

Also assert the public rate-limit field is exactly `420`, matching cached reports may be returned without a GET during lockout, and concurrent calls cannot create two upstream requests inside the interval.

HF heuristic tests should assert broad ordering rather than every score; score-weight changes require an explicit heuristic-version change.


## Native UI acceptance (v0.3)

1. **Space weather:** initial screen contains no required text entry; refresh control is hidden under Advanced settings.
2. **PSK Reporter:** the normal path exposes one free-text subject field; subject type, direction, time window and mode are button choices; frequency limits/report limit/no-locator are Advanced.
3. **Band advice:** location source, space-weather source and operating mode are button choices; manual Kp/F10.7/R fields appear only for manual conditions; UTC override is Advanced.
4. **Maidenhead:** operation and precision are choices; only the coordinate pair or locator input relevant to the selected operation is shown.
5. **Antenna:** design is a choice; velocity/end-effect factor is Advanced.
6. **RF link:** TX power/gain/feedline-loss controls are Advanced; geometry inputs remain on the main screen.
7. Every screen displays a short numbered **How to use it** section before controls.
8. Primary result preview contains only operator-useful fields; full AS100 outputs remain in the execution/audit payload.
9. Rotation/configuration recreation continues to restore settings/result JSON via `rememberSaveable`.
