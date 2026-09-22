# Network Tools manifest integration

Network Tools uses existing MethodMesh network/location permissions for active-network state and host diagnostics.

Nearby Wi-Fi discovery additionally requires these app-level manifest permissions:

```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

Add them beside the existing network/location permissions in:

```text
app/src/main/AndroidManifest.xml
```

The current MethodMesh manifest already declares `ACCESS_FINE_LOCATION`; Network Tools requests that runtime permission when the operator asks Android to expose nearby Wi-Fi results.

Android may still decline a fresh scan or expose cached/redacted results because of scan throttling, disabled Wi-Fi/location services, device policy, OS behaviour or privacy controls. The module treats all of these as visible availability states. They must not crash the capability.

## Why the manifest is not bundled

The MethodMesh Master Book requires module-only handoffs. A genuine shared app-manifest integration requirement is documented separately rather than returning an unrelated modified app tree.

The capability remains useful before this integration: current transport/validation state, interface/CIDR tools and host diagnostics continue to work. The Nearby Wi-Fi panel explains the missing integration rather than bypassing Android permission controls.
