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

## AprilTag tag16h5 family data

`SignalAprilTagCodec.kt` embeds the published `tag16h5` family codewords and bit-coordinate ordering from the AprilRobotics AprilTag project. MethodMesh uses those family constants to render and decode the full-screen optical Burst symbols; it does **not** bundle the AprilTag native detector/library.

Project: AprilTag, APRIL Robotics Laboratory / University of Michigan  
Source: https://github.com/AprilRobotics/apriltag  
Licence: BSD-style licence in the upstream project  
Purpose in this module: `tag16h5` fiducial family constants for offline screen optical signalling.

Copyright (C) 2013-2016, The Regents of The University of Michigan. All rights reserved. Redistribution and use are subject to the conditions in the upstream AprilTag licence; the upstream project disclaims warranties and liability.
