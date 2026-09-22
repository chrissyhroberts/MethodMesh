# Network tools roadmap

v0.3.0 deliberately keeps a narrow bounded diagnostic boundary.

## Appropriate extensions

- IPv6 CIDR/subnet calculation.
- Better display of hidden/multi-security Wi-Fi networks without broad device discovery.
- Richer stable Android link metrics where public APIs support them.
- A generic shared Android-context execution service if MethodMesh later needs fully headless device-state capture for schedules.
- Optional traceroute hop-table presentation while retaining `network_value` as the beef.
- Focused automated tests in the main repository for CIDR, host validation, operation projection and ODK workbook contract.

## Explicit non-goals

Do not casually extend this module into:

- subnet sweeps;
- automatic LAN inventory;
- port-range scanners;
- vulnerability probing;
- service/version fingerprinting;
- packet sniffing/passive traffic inspection.

If a future research/diagnostic need genuinely requires one of those, treat it as a separate reviewed capability with its own safety, privacy, UX and external-caller contract rather than smuggling it into `network.tools`.
