# Third-party data and service notes

## OpenCaching / OKAPI

MethodMesh Geocaching can use OKAPI-compatible OpenCaching installations for public cache discovery and OAuth-authenticated user operations.

Implemented installations:

- OpenCache UK — `https://opencache.uk` (starter OKAPI provider profile)
- OpenCaching PL — `https://opencaching.pl` (starter OKAPI provider profile)
- Other user-configured OKAPI installations may be added at runtime; their own terms and attribution requirements apply.

Current integration uses the provider's normal OKAPI OAuth 1.0a flow. Provider passwords are entered only on the provider's own authorization page.

Provider attribution returned with cache data should be preserved in human-readable cache content where supplied.

OKAPI project: `https://github.com/opencaching/okapi/`

## MapLibre and OpenFreeMap

The module uses the MapLibre Android SDK already shipped by MethodMesh. Where online street tiles are used, the current default style is OpenFreeMap.

OpenFreeMap: `https://openfreemap.org/`

Maps must keep MapLibre/provider attribution enabled where required.

## Geocaching.com

MethodMesh may import GPX files a user legitimately obtained and supplied. It does not scrape Geocaching.com.

Geocaching.com account/API integration is intentionally gated until MethodMesh receives official Authorized Developer/API credentials. The disabled provider entry is an architectural placeholder, not a claim of current API access.

## User-supplied GPX and media

Imported GPX, photographs and other content remain user/provider content. MethodMesh does not grant additional rights to redistribute or publish that material.

## Trackables

The module stores the public trackable reference used to identify/follow an item. Private tracking codes used to prove possession are intentionally not persisted.
