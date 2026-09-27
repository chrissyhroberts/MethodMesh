# Android manifest integration — Text documents v0.12

`DocumentActivity` is module-owned, but Android resolver registration lives in the shared app manifest and therefore cannot be placed inside the canonical `textdocuments/` module handoff.

The host activity declaration must advertise the MIME types the editor actually supports:

```xml
<activity
    android:name=".modules.textdocuments.DocumentActivity"
    android:exported="true"
    android:label="Text documents"
    android:theme="@style/Theme.MethodMesh">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <action android:name="android.intent.action.EDIT" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
        <data android:mimeType="text/markdown" />
        <data android:mimeType="text/*" />
        <data android:mimeType="application/json" />
        <data android:mimeType="text/json" />
        <data android:mimeType="application/x-ndjson" />
        <data android:mimeType="application/ndjson" />
        <data android:mimeType="application/jsonl" />
        <data android:mimeType="application/xml" />
        <data android:mimeType="application/yaml" />
        <data android:mimeType="application/toml" />
        <data android:mimeType="application/sql" />
        <data android:mimeType="application/x-sh" />
        <data android:mimeType="*/*" />
    </intent-filter>
</activity>
```

A complete replacement `AndroidManifest.xml` based on the supplied current app manifest accompanies this module release as a separate integration artefact. Do not copy the manifest into the module directory.
