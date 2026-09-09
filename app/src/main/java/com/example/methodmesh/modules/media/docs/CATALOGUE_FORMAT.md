# Media catalogue interchange

Minimum CSV:

```csv
title,provider,region,access_type
The Example,Netflix,GB,subscription_included
Another Film,Prime Video,GB,rent
Channel Film,Prime Video,GB,addon_subscription
```

Recommended CSV:

```csv
work_id,title,media_type,year,creator,genres,language,runtime_minutes,provider,region,access_type,addon_required,price,currency,external_id,external_uri,available_from,available_until,last_verified
```

Equivalent JSON is an array of objects using the same keys.

Do not map an ambiguous source value to `subscription_included`. Prefer `unknown` when the source does not distinguish subscription, rental, purchase and add-on availability.

## Personal-state join

The catalogue never owns favourites/watchlist/watched state. MethodMesh stores those separately by `work_id` and joins them to current catalogue rows at query time. Therefore a favourite survives provider removal or catalogue refresh, and one favourite can appear under multiple provider availability rows.

For reliable cross-provider joins, sources should provide a stable `work_id`. If absent, MethodMesh derives one from `external_id` when present, otherwise from normalised title + year.
