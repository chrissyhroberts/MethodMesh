# Aviation roadmap after v0.3

v0.3 establishes two persistent native surfaces:

- the normal aviation situational/planning dashboard; and
- the emergency phone-reference instrument panel.

Both share atomic engines/repositories and preserve one-shot external/ODK execution.

Recommended next tranche:

1. **METAR/TAF provider** using MethodMesh online-data infrastructure, with raw report preservation, observation/validity times and obvious freshness indicators.
2. Feed verified METAR wind/QNH/OAT into `aviation.dashboard`; keep the emergency panel locally useful even when online weather is absent.
3. **SIGMET / aviation warning intersection** for current location and later routes.
4. Add structured runway length/surface context to the airfield reference without ever labelling proximity as emergency suitability.
5. Route planner with waypoint/aerodrome selectors, leg distance/bearing, ETE and fuel estimate.
6. Aircraft profiles + weight/balance and aircraft-specific performance references entered from the applicable AFM/POH.
7. GPS track recorder / flight log.
8. Optional local ADS-B receiver integration before considering internet-derived traffic display.
9. Provider-specific NOTAM adapters only where licensing and operational-use terms permit.

Do not replace the package with a monolith. New data sources should feed persistent dashboards through shared engines/repositories while retaining atomic MethodMesh methods for composition, ODK and protocol use.
