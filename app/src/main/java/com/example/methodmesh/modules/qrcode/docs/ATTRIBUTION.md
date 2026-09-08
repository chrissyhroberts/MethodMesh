# Attribution

The barcode camera integration uses the JourneyApps ZXing Android Embedded API (`com.journeyapps.barcodescanner.ScanContract` and `ScanOptions`) and the ZXing barcode-decoding ecosystem.

The MethodMesh module does not bundle a separate scanner implementation or scanner data service. The current MethodMesh host resolves `com.journeyapps:zxing-android-embedded:4.3.0`; Android packaging remains owned by the host application.

Project/licence attribution should remain available in MethodMesh's third-party notices in accordance with the dependency licences. See `THIRD_PARTY_NOTICES.md` for the module-local notice.
