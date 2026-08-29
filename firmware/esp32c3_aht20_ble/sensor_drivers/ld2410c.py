import time


class LD2410C:
    sensor_id = "ld2410c_1"
    sensor_type = "LD2410C"
    bus = "uart"
    fields = [
        "presence",
        "target_state",
        "moving_distance_cm",
        "moving_energy",
        "stationary_distance_cm",
        "stationary_energy",
        "detection_distance_cm",
    ]

    FRAME_HEADER = b"\xf4\xf3\xf2\xf1"
    FRAME_FOOTER = b"\xf8\xf7\xf6\xf5"

    def __init__(self, uart):
        self.uart = uart
        self.present = True
        self._buffer = b""

    def manifest(self, error=""):
        return {
            "sensor_id": self.sensor_id,
            "sensor_type": self.sensor_type,
            "bus": self.bus,
            "uart_baud": 256000,
            "present": bool(self.present),
            "status": "ok" if self.present else "missing",
            "error": error or "",
            "fields": self.fields,
        }

    def read(self):
        frame = self._read_frame()
        if frame is None:
            raise OSError("No LD2410C report frame received")
        return self._decode_target_frame(frame)

    def _read_frame(self, timeout_ms=1200):
        deadline = time.ticks_add(time.ticks_ms(), timeout_ms)
        while time.ticks_diff(deadline, time.ticks_ms()) > 0:
            chunk = self.uart.read()
            if chunk:
                self._buffer += chunk
                parsed = self._pop_frame()
                if parsed is not None:
                    return parsed
            time.sleep_ms(25)
        return None

    def _pop_frame(self):
        start = self._buffer.find(self.FRAME_HEADER)
        if start < 0:
            self._buffer = self._buffer[-3:]
            return None
        if start > 0:
            self._buffer = self._buffer[start:]
        if len(self._buffer) < 10:
            return None
        length = self._buffer[4] | (self._buffer[5] << 8)
        end = 6 + length
        footer_end = end + 4
        if len(self._buffer) < footer_end:
            return None
        payload = self._buffer[6:end]
        footer = self._buffer[end:footer_end]
        self._buffer = self._buffer[footer_end:]
        if footer != self.FRAME_FOOTER:
            return None
        return payload

    def _decode_target_frame(self, payload):
        # Common LD2410 report frames contain target data at the start of the
        # payload. Some firmware modes prefix a frame type byte; accept both.
        data = payload
        # Normal LD2410C engineering report payloads begin with 02 aa, then the
        # target data bytes. Older drafts of this driver stripped only one byte,
        # causing 0xaa to be decoded as the target state.
        if len(data) >= 11 and data[0] == 0x02 and data[1] == 0xaa:
            data = data[2:]
        elif len(data) >= 10 and data[0] in (0x01, 0x02, 0xaa):
            data = data[1:]
        if len(data) < 9:
            raise OSError("LD2410C frame too short")
        state = data[0]
        moving_distance = data[1] | (data[2] << 8)
        moving_energy = data[3]
        stationary_distance = data[4] | (data[5] << 8)
        stationary_energy = data[6]
        detection_distance = data[7] | (data[8] << 8)
        return {
            "sensor_id": self.sensor_id,
            "sensor_type": self.sensor_type,
            "presence": state != 0,
            "target_state": state,
            "moving_distance_cm": moving_distance,
            "moving_energy": moving_energy,
            "stationary_distance_cm": stationary_distance,
            "stationary_energy": stationary_energy,
            "detection_distance_cm": detection_distance,
        }
