package com.example.methodmesh.modules.signals

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object SignalsModule : MethodMeshModule {
    override val moduleId = "signals"
    override val displayName = "Signals"
    override val summary = "Observe, encode, transmit and recover checksum-verified messages and files over optical, acoustic and shared-surface links, with live diagnostics for lossy physical channels."
    override val iconKey = "hardware"


    override fun as100Methods() = listOf(
        As100SignalMorseTransmitMethod,
        As100SignalMorseReceiveMethod,
        As100SignalQrTransmitMethod,
        As100SignalQrReceiveMethod,
        As100SignalFskTransmitMethod,
        As100SignalFskReceiveMethod,
        As100SignalUltrasonicTransmitMethod,
        As100SignalUltrasonicReceiveMethod,
        As100SignalSurfaceTransmitMethod,
        As100SignalSurfaceReceiveMethod,
        As100SignalOpticalScreenTransmitMethod,
        As100SignalOpticalScreenReceiveMethod,
        As100SignalOpticalTorchTransmitMethod,
        As100SignalOpticalTorchReceiveMethod,
        As100SignalSensorScopeMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("transmit morse", As100SignalMorseTransmitMethod.id, "Send text as Morse over screen, torch and/or sound; screen can use white dots and red dashes"),
        RilBinding("receive morse", As100SignalMorseReceiveMethod.id, "Decode Morse from camera timing/colour evidence, microphone or manual START/DOT/DASH observation"),
        RilBinding("transmit qr burst", As100SignalQrTransmitMethod.id, "Loop an error-corrected message as QR frames"),
        RilBinding("receive qr burst", As100SignalQrReceiveMethod.id, "Recover an error-corrected message from QR frames"),
        RilBinding("transmit audio fsk", As100SignalFskTransmitMethod.id, "Send a small error-corrected packet through sound or a radio audio path"),
        RilBinding("receive audio fsk", As100SignalFskReceiveMethod.id, "Decode a packet from audible FSK"),
        RilBinding("transmit near ultrasonic", As100SignalUltrasonicTransmitMethod.id, "Experimentally send data using high-frequency phone audio"),
        RilBinding("receive near ultrasonic", As100SignalUltrasonicReceiveMethod.id, "Experimentally receive high-frequency phone audio data"),
        RilBinding("transmit tabletop signal", As100SignalSurfaceTransmitMethod.id, "Send a packet through a shared table, box or rail"),
        RilBinding("receive tabletop signal", As100SignalSurfaceReceiveMethod.id, "Recover a packet from shared-surface vibration"),
        RilBinding("transmit screen optical modem", As100SignalOpticalScreenTransmitMethod.id, "Send short text as four-level screen light or full-screen AprilTag16h5 Burst"),
        RilBinding("receive screen optical modem", As100SignalOpticalScreenReceiveMethod.id, "Recover short text from four-level screen light or full-screen AprilTag16h5 Burst"),
        RilBinding("transmit torch ppm", As100SignalOpticalTorchTransmitMethod.id, "Send compact short text as range-oriented single-flash 8-PPM torch pulses"),
        RilBinding("receive torch ppm", As100SignalOpticalTorchReceiveMethod.id, "Recover compact short text from edge-timed 8-PPM torch pulses"),
        RilBinding("inspect signal sensor", As100SignalSensorScopeMethod.id, "Inspect light, magnetic, motion, pressure and proximity sensors")
    )

    override fun capabilityScreens() = listOf(
        SignalMorseTransmitCapabilityScreen,
        SignalMorseReceiveCapabilityScreen,
        SignalQrTransmitCapabilityScreen,
        SignalQrReceiveCapabilityScreen,
        SignalFskTransmitCapabilityScreen,
        SignalFskReceiveCapabilityScreen,
        SignalUltrasonicTransmitCapabilityScreen,
        SignalUltrasonicReceiveCapabilityScreen,
        SignalSurfaceTransmitCapabilityScreen,
        SignalSurfaceReceiveCapabilityScreen,
        SignalOpticalScreenTransmitCapabilityScreen,
        SignalOpticalScreenReceiveCapabilityScreen,
        SignalOpticalTorchTransmitCapabilityScreen,
        SignalOpticalTorchReceiveCapabilityScreen,
        SignalSensorScopeCapabilityScreen
    )

    override fun capabilitySettings(): Map<String, List<MethodSetting>> = mapOf(
        As100SignalMorseTransmitMethod.id to listOf(
            MethodSetting.TextSetting("payload", "Message", "Text to encode. Make this a runtime input in reusable presets unless a fixed beacon message is intentional.", "Message", "hello, world"),
            MethodSetting.ChoiceSetting("route", "Output route", "Front screen, rear torch, audible tone, or a combined route.", "Channel", "screen", listOf("screen", "torch", "sound", "screen_sound", "torch_sound", "all")),
            MethodSetting.ChoiceSetting("wpm", "Morse speed", "Discrete PARIS-standard speed. Optical use is capped at 10 WPM; audio TX/RX is capped at 30 WPM.", "Timing", "5", listOf("5", "8", "10", "20", "30")),
            MethodSetting.ChoiceSetting("tone_frequency_hz", "Tone preset", "Shared Morse audio carrier catalogue used identically by TX and RX.", "Channel", "700", listOf("500", "700", "1200", "2200")),
            MethodSetting.BooleanSetting("colour_assist", "Screen colour assist", "Keep canonical Morse timing while screen dots/acquisition are white and dashes/START/END are red. Other routes are unchanged.", "Channel", true),
            MethodSetting.ChoiceSetting("element_gap_units", "Element gap", "Independent quiet gap between dots/dashes within one character, in dot units.", "Spacing", "1.25", listOf("1.0", "1.25", "1.5")),
            MethodSetting.ChoiceSetting("letter_gap_units", "Letter gap", "Independent quiet gap between letters, in dot units.", "Spacing", "4.0", listOf("3.0", "4.0", "5.0")),
            MethodSetting.ChoiceSetting("word_gap_units", "Word gap", "Independent quiet gap between words, in dot units.", "Spacing", "9.0", listOf("7.0", "9.0", "12.0")),
            MethodSetting.ChoiceSetting("loop_mode", "Loop", "Morse is a cyclic framed beacon: send a fixed number of complete START→END cycles or continue until stopped.", "Timing", "continuous", listOf("count", "continuous")),
            MethodSetting.ChoiceSetting("repeat_count", "Repeat count", "Used when Loop is count; at least two cycles are required for consensus.", "Timing", "3", listOf("2", "3", "5", "10")),
            MethodSetting.ChoiceSetting("loop_gap_units", "Cycle gap", "Quiet dot units between complete framed message cycles.", "Spacing", "15", listOf("10", "15", "24"))
        ),
        As100SignalMorseReceiveMethod.id to listOf(
            MethodSetting.ChoiceSetting("source", "Receiver", "Rear camera luminance, microphone tone envelope, or manual human-observed START/DOT/DASH entry.", "Channel", "camera", listOf("camera", "microphone", "manual")),
            MethodSetting.ChoiceSetting("dot_ms", "Expected dot", "Discrete starting speed shared with TX: 240=5 WPM, 150=8 WPM, 120=10 WPM, 60=20 WPM, 40=30 WPM.", "Timing", "240", listOf("240", "150", "120", "60", "40")),
            MethodSetting.BooleanSetting("auto_timing", "Auto timing", "Experimental timing inference. Locked timing is recommended for camera reception.", "Timing", false),
            MethodSetting.ChoiceSetting("optical_profile", "Optical profile", "Screen suppresses rolling-shutter transitions; Torch/point uses stronger underexposure and edge stability for flare decay.", "Camera", "screen", listOf("screen", "torch")),
            MethodSetting.ChoiceSetting("colour_assist", "Colour assist", "Auto calibrates white acquisition dots and the red START marker, fuses chroma with timing, and falls back to timing-only when colour is weak.", "Camera", "auto", listOf("auto", "off")),
            MethodSetting.ChoiceSetting("microphone_tone_hz", "Tone preset", "Exactly the same Morse audio carrier catalogue as TX.", "Microphone", "700", listOf("500", "700", "1200", "2200")),
            MethodSetting.ChoiceSetting("microphone_tolerance_hz", "Tone tolerance", "Discrete receive window around the selected carrier.", "Microphone", "120", listOf("80", "120", "220", "400")),
            MethodSetting.ChoiceSetting("microphone_min_dbfs", "Minimum level", "dBFS is relative to digital full scale: 0 dBFS is maximum; more-negative values are quieter/more sensitive. −78 dBFS is much quieter than −42 dBFS.", "Microphone", "-54", listOf("-78", "-66", "-54", "-42")),
            MethodSetting.ChoiceSetting("element_gap_units", "Element gap", "Expected gap between dot/dash elements, in dot units.", "Spacing", "1.25", listOf("1.0", "1.25", "1.5")),
            MethodSetting.ChoiceSetting("letter_gap_units", "Letter gap", "Expected gap between letters, in dot units.", "Spacing", "4.0", listOf("3.0", "4.0", "5.0")),
            MethodSetting.ChoiceSetting("word_gap_units", "Word gap", "Expected gap between words, in dot units.", "Spacing", "9.0", listOf("7.0", "9.0", "12.0"))
        ),
        As100SignalQrTransmitMethod.id to listOf(
            MethodSetting.ChoiceSetting("content_mode", "Content", "Send text or a file up to 1 MiB. Content is wrapped with SHA-256 before segmented MMS/1 coding.", "Payload", "text", listOf("text", "file")),
            MethodSetting.TextSetting("payload", "Message", "Text payload when Content is text.", "Payload", "MethodMesh QR Blast demonstration. This deliberately longer sample shows that the optical link can move more than a token message. It contains enough text to require several error-corrected QR frames, so you can start the receiving phone part-way through a cycle, lose some frames, and still watch Reed-Solomon recovery complete. The reconstructed text is then checked against the SHA-256 digest broadcast by the sender before MethodMesh marks the result VERIFIED."),
            MethodSetting.TextSetting("file_uri", "File URI", "Optional caller-supplied file URI. Direct native use provides a file picker.", "Payload", ""),
            MethodSetting.TextSetting("file_name", "File name", "Optional file-name override for externally supplied URIs.", "Payload", ""),
            MethodSetting.TextSetting("file_mime", "File MIME", "Optional MIME override for externally supplied URIs.", "Payload", ""),
            robustnessSetting(),
            MethodSetting.ChoiceSetting("shard_bytes", "Shard size", "Discrete QR source-shard width.", "Reliability", "96", listOf("64", "96", "160", "256")),
            MethodSetting.ChoiceSetting("frame_duration_ms", "Frame dwell", "Discrete time each QR frame remains visible.", "Timing", "650", listOf("250", "400", "650", "1000")),
            MethodSetting.BooleanSetting("loop", "Loop continuously", "Keep cycling frames so a receiver can join at any point.", "Timing", true)
        ),
        As100SignalQrReceiveMethod.id to listOf(
            MethodSetting.TextSetting("message_id_filter", "Message ID filter", "Optional MMS/1 message ID to accept; blank accepts the first active message.", "Filter", "")
        ),
        As100SignalFskTransmitMethod.id to listOf(
            MethodSetting.TextSetting("payload", "Message", "Small text payload to transmit.", "Message", "Hello from MethodMesh"),
            robustnessSetting(),
            MethodSetting.ChoiceSetting("channel_profile", "Channel profile", "Matched A–D profile sets MARK, SPACE and bit duration together. Use the same letter on TX and RX.", "Channel", "A", listOf("A", "B", "C", "D")),
            MethodSetting.ChoiceSetting("ptt_lead_ms", "PTT / squelch lead-in", "Discrete unmodulated lead time before each frame for walkie-talkie or VOX paths.", "Radio", "120", listOf("0", "120", "300", "600")),
            MethodSetting.ChoiceSetting("repeat_count", "Cycles", "Number of complete MMS/1 frame cycles to send.", "Reliability", "2", listOf("1", "2", "3", "5"))
        ),
        As100SignalFskReceiveMethod.id to listOf(
            MethodSetting.ChoiceSetting("channel_profile", "Channel profile", "Same A–D matched profile catalogue as the transmitter.", "Channel", "A", listOf("A", "B", "C", "D")),
            MethodSetting.ChoiceSetting("minimum_dbfs", "Minimum level", "0 dBFS is full scale; more-negative values are quieter and therefore more sensitive.", "Receiver", "-54", listOf("-78", "-66", "-54", "-42"))
        ),
        As100SignalUltrasonicTransmitMethod.id to listOf(
            MethodSetting.TextSetting("payload", "Message", "Small text payload to transmit.", "Message", "Hello from MethodMesh"),
            robustnessSetting(),
            MethodSetting.ChoiceSetting("channel_profile", "Channel profile", "Matched high-band A–D profile sets both carriers and bit duration. Use the same letter on RX.", "Channel", "A", listOf("A", "B", "C", "D")),
            MethodSetting.ChoiceSetting("repeat_count", "Cycles", "Number of complete MMS/1 cycles to send.", "Reliability", "2", listOf("1", "2", "3", "5"))
        ),
        As100SignalUltrasonicReceiveMethod.id to listOf(
            MethodSetting.ChoiceSetting("channel_profile", "Channel profile", "Same high-band A–D catalogue as the transmitter.", "Channel", "A", listOf("A", "B", "C", "D")),
            MethodSetting.ChoiceSetting("minimum_dbfs", "Minimum level", "0 dBFS is full scale; more-negative values are quieter and therefore more sensitive.", "Receiver", "-54", listOf("-78", "-66", "-54", "-42"))
        ),
        As100SignalSurfaceTransmitMethod.id to listOf(
            MethodSetting.TextSetting("payload", "Message", "Small text payload to send through the shared surface.", "Message", "HELLO TABLE"),
            robustnessSetting(),
            MethodSetting.ChoiceSetting("surface_profile", "Surface profile", "Experimental matched A–D carrier/bit timing profile. Use the same letter on RX.", "Channel", "B", listOf("A", "B", "C", "D")),
            MethodSetting.ChoiceSetting("repeat_count", "Cycles", "Number of complete MMS/1 cycles to send.", "Reliability", "2", listOf("1", "2", "3", "5"))
        ),
        As100SignalSurfaceReceiveMethod.id to listOf(
            MethodSetting.ChoiceSetting("surface_profile", "Surface profile", "Experimental matched A–D profile. Use the same letter on TX.", "Channel", "B", listOf("A", "B", "C", "D")),
            MethodSetting.ChoiceSetting("calibration_ms", "Rest calibration", "Discrete resting-vibration calibration period.", "Receiver", "1500", listOf("1000", "1500", "2500")),
            MethodSetting.ChoiceSetting("sensitivity", "Sensitivity", "Threshold multiplier above the resting vibration floor.", "Receiver", "2.5", listOf("2.0", "2.5", "3.5", "5.0"))
        ),

        As100SignalOpticalScreenTransmitMethod.id to listOf(
            MethodSetting.TextSetting("payload", "Message", "Text payload. AprilTag Burst uses compact CRC framing for supported short field text; use whole-screen 4-PAM/MMS for unsupported characters or broader payloads.", "Message", "HELLO FROM METHODMESH OPTICAL"),
            MethodSetting.ChoiceSetting("mode", "Screen mode", "The legacy grid token now selects AprilTag Burst: one full-screen self-registering tag per state. PAM uses the whole screen as one four-level symbol and maximises luminous area.", "Optical", "grid", listOf("pam", "grid")),
            opticalRangeSetting(),
            robustnessSetting(),
            MethodSetting.ChoiceSetting("loop_mode", "Loop", "Continuous is recommended so a receiver may join after transmission has started.", "Timing", "continuous", listOf("count", "continuous")),
            MethodSetting.ChoiceSetting("repeat_count", "Cycles", "Complete optical cycles when Loop is count.", "Reliability", "3", listOf("1", "2", "3", "5", "10"))
        ),
        As100SignalOpticalScreenReceiveMethod.id to listOf(
            MethodSetting.ChoiceSetting("mode", "Screen mode", "Must match the transmitter: whole-screen four-level PAM or AprilTag Burst (legacy value grid).", "Optical", "grid", listOf("pam", "grid")),
            opticalRangeSetting(),
            MethodSetting.ChoiceSetting("zoom_ratio", "Optical zoom", "Real CameraX zoom. Long-range acquisition benefits from zoom while the target remains easy to keep inside the reticle.", "Camera", "4", listOf("1", "2", "4", "8")),
            MethodSetting.ChoiceSetting("roi_mode", "Target region", "PAM can use a small target region. For AprilTag Burst this is only a search area; keep the complete changing tag broadly inside it and the tag supplies its own registration.", "Camera", "focus", listOf("full", "focus", "pinpoint")),
            MethodSetting.ChoiceSetting("exposure_reduction", "Underexposure", "Reduces exposure to preserve optical modulation and prevent a bright distant display from washing out.", "Camera", "0.55", listOf("0.25", "0.55", "0.75", "0.90")),
            MethodSetting.BooleanSetting("auto_lock", "Auto target", "For scalar PAM, search for the most strongly modulated region. AprilTag Burst localises and validates each tag independently.", "Camera", true)
        ),
        As100SignalOpticalTorchTransmitMethod.id to listOf(
            MethodSetting.TextSetting("payload", "Message", "Compact torch text: up to 255 characters using letters, spaces and . , ? / -. The physical frame uses CRC-32 and profile-defined parity.", "Message", "HELLO FROM METHODMESH TORCH"),
            opticalRangeSetting(),
            robustnessSetting(),
            MethodSetting.ChoiceSetting("loop_mode", "Loop", "Continuous is recommended for distant acquisition and join-in-progress reception.", "Timing", "continuous", listOf("count", "continuous")),
            MethodSetting.ChoiceSetting("repeat_count", "Cycles", "Complete compact 8-PPM cycles when Loop is count.", "Reliability", "3", listOf("1", "2", "3", "5", "10"))
        ),
        As100SignalOpticalTorchReceiveMethod.id to listOf(
            opticalRangeSetting(),
            MethodSetting.ChoiceSetting("zoom_ratio", "Optical zoom", "Real CameraX zoom for a point-source torch target.", "Camera", "4", listOf("1", "2", "4", "8")),
            MethodSetting.ChoiceSetting("roi_mode", "Target region", "Pinpoint is preferred after lock for a distant torch; Focus is easier for initial manual acquisition.", "Camera", "pinpoint", listOf("full", "focus", "pinpoint")),
            MethodSetting.ChoiceSetting("exposure_reduction", "Underexposure", "Strong underexposure preserves the leading edge of a bright torch and limits flare/saturation.", "Camera", "0.90", listOf("0.55", "0.75", "0.90", "1.0")),
            MethodSetting.BooleanSetting("auto_lock", "Auto target", "Search for the locally modulated point source and track it after acquisition.", "Camera", true)
        ),
        As100SignalSensorScopeMethod.id to listOf(
            MethodSetting.ChoiceSetting("sensor", "Sensor", "Choose a phone sensor to inspect and capture.", "Sensor", "light", listOf("light", "magnetometer", "accelerometer", "gyroscope", "pressure", "proximity"))
        )
    )


    private fun opticalRangeSetting() = MethodSetting.ChoiceSetting(
        "range_profile",
        "Link profile",
        "Long range widens symbol dwell/timing and prioritises acquisition margin. Balanced trades margin for speed. Fast prioritises throughput at shorter range.",
        "Optical",
        "long",
        listOf("long", "balanced", "fast")
    )

    private fun robustnessSetting() = MethodSetting.ChoiceSetting(
        "robustness",
        "Reliability",
        "Fast = light parity; robust = parity approximately equal to source shards; extreme = heavy redundancy.",
        "Reliability",
        "robust",
        listOf("fast", "robust", "extreme")
    )
}
