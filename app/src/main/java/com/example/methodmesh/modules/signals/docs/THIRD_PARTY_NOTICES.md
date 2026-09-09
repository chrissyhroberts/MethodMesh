# Third-party notices

## ZXing Android Embedded / JourneyApps

Signals uses the JourneyApps ZXing Android Embedded scanner API already resolved by the MethodMesh host for continuous QR-frame capture.

Project: ZXing Android Embedded (JourneyApps), currently resolved by MethodMesh as 4.3.0  
Licence: Apache License 2.0  
Purpose in this module: local camera scanning of QR frames.

## ZXing

ZXing ("Zebra Crossing") supplies QR encoding/decoding used by the host scanner ecosystem.

Licence: Apache License 2.0  
Purpose in this module: local QR rendering and decoding.

No remote QR-decoding service is used. This notice is informational and does not replace the full licence texts supplied/resolved with the host application's dependencies.

## MMS/1 Reed-Solomon implementation

`SignalPacketCodec.kt` contains a module-authored GF(256) systematic Reed-Solomon erasure implementation for MMS/1. It does not embed or copy a third-party Reed-Solomon library.
