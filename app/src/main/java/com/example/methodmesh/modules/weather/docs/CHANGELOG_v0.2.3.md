# Weather v0.2.3 changes

- Fix `WeatherCapabilityScreens.kt` compile error: unresolved reference `format`.
- `parseWeatherDateTime()` returns `TemporalAccessor`, so axis labels now use `DateTimeFormatter.format(TemporalAccessor)` explicitly.
- UK-style display remains `10 Sept 14:00` / `Thu 10 Sept`.
- No canonical method IDs, inputs, outputs, settings or XLSForms changed.
