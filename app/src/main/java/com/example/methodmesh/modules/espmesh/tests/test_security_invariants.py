#!/usr/bin/env python3
"""Static invariants for the ESP mesh trust boundary.

This does not replace Android/instrumented crypto tests. It protects the source
contract that ESP firmware only sees/reroutes ciphertext and that the generic
transport runtime does not deliberately duplicate secure-provider durability.
"""
from pathlib import Path

HERE = Path(__file__).resolve()
MAIN = HERE.parents[7]
provider = (MAIN / "java/com/example/methodmesh/modules/espmesh/EspMeshTransportProvider.kt").read_text()
security = (MAIN / "java/com/example/methodmesh/modules/espmesh/EspMeshSecurity.kt").read_text()
runtime = (MAIN / "java/com/example/methodmesh/core/transport/MethodMeshTransportRuntime.kt").read_text()
firmware = (MAIN / "assets/firmware/esp32c3_espnow_mesh/main.py").read_text()
gateway_ui = (MAIN / "java/com/example/methodmesh/modules/espmesh/EspMeshGatewayCapability.kt").read_text()
voice = (MAIN / "java/com/example/methodmesh/modules/espmesh/EspMeshWalkieTalkieController.kt").read_text()
voice_protocol = (MAIN / "java/com/example/methodmesh/modules/espmesh/EspMeshVoiceProtocol.kt").read_text()
service = (MAIN / "java/com/example/methodmesh/modules/espmesh/EspMeshGatewayService.kt").read_text()
manifest = (MAIN / "AndroidManifest.xml").read_text()

assert "override val ownsDurability: Boolean = true" in provider
assert "AES/GCM/NoPadding" in security
assert "AndroidKeyStore" in security
assert "updateAAD" in security
assert 'put("op", originPhoneId)' in security
assert 'destination = TransportEndpoint("mesh-phone", originPhoneId)' in provider
assert 'var e2eGroupKey by remember { mutableStateOf("") }' in gateway_ui
assert 'var networkKey by remember { mutableStateOf("") }' in gateway_ui
assert "if (!provider.ownsDurability) {" in runtime
assert "ingest(envelope, provider.ownsDurability)" in runtime
assert "if (!ownsDurability) store.recordInbox" in runtime
assert 'No consumer is currently registered for' in runtime
assert "if (ownsDurability) throw e" in runtime

# E2E content key must never enter the ESP provisioning/config frame or firmware.
start = provider.index('val frame = EspMeshBridgeFrame("CONFIG"')
end = provider.index('return if (enqueueBridgeFrame', start)
config_slice = provider[start:end]
for forbidden in ("e2e_group_key", "group_key_wrapped", "AES/GCM"):
    assert forbidden not in config_slice
    assert forbidden not in firmware

# ESP persistent records carry only routing id/direction, ciphertext wire and expiry.
assert 's.sp.append({"id":mid,"dir":d,"wire":wire,"e":wire.get("x")})' in firmware
assert 'dk=="mesh-phone" and (not s.c.get("phone_id") or dst!=s.c["phone_id"])' in firmware
assert 'MAX_SPOOL=96' in firmware
assert 'MAX_WIRE=32768' in firmware
assert 'GATT_BUF=512' in firmware
assert 'MAX_BRIDGE=65535' in firmware
assert 'MAX_SECURE_WIRE_BYTES = 32 * 1024' in provider
assert 'esp_spool_full' in firmware
assert 'esp_spool_unreadable' in firmware
assert 'encryptVoiceFrame' in security and 'decryptVoiceFrame' in security
assert 'MethodMesh ESP mesh live voice v1' in security
assert 'AudioRecord' in voice and 'AudioTrack' in voice
assert 'EspMeshMuLawCodec.encode' in voice and 'EspMeshMuLawCodec.decode' in voice
assert 'packet.size <= EspMeshLiveVoicePacket.MAX_PACKET_BYTES' in provider
assert 'EspMeshBridgeFrame("LIVE_VOICE"' in provider
assert 'store.enqueue' not in provider[provider.index('fun sendLiveVoicePacket'):provider.index('fun phoneId')]
assert 'AUDIO_FRAME_BYTES = 160' in voice_protocol
assert 'cachedVoiceKey' in security and 'invalidateVoiceKeyCache()' in security
assert 'requireSinglePacket = true' in provider
assert 'MAX_LIVE_BLE_QUEUE_PACKETS = 12' in provider
assert 'liveVoiceWriteQueue' in provider
assert 'startForeground(NOTIFICATION_ID' in service
assert 'FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE' in service
assert 'FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK' in service
assert 'mediaPlayback = voice.receiving' in service
assert 'connectedDevice|mediaPlayback' in manifest
assert 'FOREGROUND_SERVICE_MEDIA_PLAYBACK' in manifest
assert 'foregroundServiceType="microphone"' not in manifest

print("ESP mesh security invariants: PASS")
