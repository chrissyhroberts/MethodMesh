# Weather v0.2.0 changes

- Reworked dashboard **Now / Today** metrics into a compact two-column layout and removed the repeated Today card.
- Added explicit time/value axes to rainfall, hourly temperature and meteogram charts.
- Renamed the ambiguous Hourly panel to **Hourly temperature · °C** and removed the repeated selected-hour rain/wind summary beneath it.
- Radar now defaults to page-scroll mode, has an explicit **Move map / Scroll page** interaction toggle, Play/Stop animation and a time/source overlay on the map.
- Promoted Sun & daylight, detailed atmosphere, model comparison and current research Weather Snapshot into the main dashboard.
- Added a native calendar + time picker to `weather.snapshot` while retaining direct ISO-8601 entry. Picker values are converted from device-local time to UTC ISO-8601 before execution.
- Preserved all canonical method IDs, inputs, outputs and ODK/XLSForm return contracts from v0.1.x.
- Bumped module/method implementation version to `0.2.0`.
