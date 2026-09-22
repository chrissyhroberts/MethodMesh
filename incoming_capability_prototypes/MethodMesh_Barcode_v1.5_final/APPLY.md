# Apply — barcode module v1.5

Overlay this folder onto the existing `app/src/main/java/com/example/methodmesh/modules/qrcode/` module.

- Replace `QrCodeModule.kt`.
- Replace `QrCodeMethods.kt`.
- Add/replace `BarcodeGenerateCapabilityScreen.kt`.
- Add the generator documentation under `docs/`.
- Leave the existing scanner screen, scanner documentation, notices, attribution and scanner XLSForms unchanged.

v1.5 removes custom-logo selection completely. QR codes automatically carry the canonical MethodMesh mark using the same geometry and colours as the existing launcher foreground vector. No new image/logo asset or URI state is introduced. Non-QR symbologies remain unbranded.

Before push: run the Android build/test gates and create the canonical `barcode.generate` XLSForm by cloning the current scanner showcase transport structure, as specified in `docs/VALIDATION_GENERATE.md`.
