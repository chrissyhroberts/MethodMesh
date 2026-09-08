# Astronomy v0.4.0 changes

Adds the first experimental MethodMesh dashboard-style capability.

- New public method: `astronomy.dashboard`.
- Visual-first native screen with a large overall verdict, semantic Material-theme cards, explicit `↻ Refresh`, data source/time, dew, current sky, Moon/tonight, and upper-atmosphere panels.
- Six-hour cloud forecast strip.
- Refresh updates preview state only; native users explicitly choose `Use this snapshot` before a graph observation is recorded. External callers still receive a normal capability result automatically.
- New Open-Meteo GFS declaration `openmeteo.astronomy_jetstream` requesting wind speed/direction at 300, 250 and 200 hPa in m/s.
- Dashboard returns normal flat fields plus `astronomy_dashboard_json` for ODK or other callers.
- Upper-atmosphere / planetary rating is explicitly heuristic and is not presented as direct seeing.
- Satellite cloud nowcasting remains out of v0.4 pending EUMETSAT access/licensing validation.

Existing v0.3.1 capability IDs remain unchanged.
