class LD2410C:
    sensor_id = "ld2410c_1"
    sensor_type = "LD2410C"
    bus = "uart"
    fields = ["presence", "moving_distance_cm", "stationary_distance_cm"]

    def __init__(self, *args, **kwargs):
        self.present = False

    def manifest(self, error=""):
        return {
            "sensor_id": self.sensor_id,
            "sensor_type": self.sensor_type,
            "bus": self.bus,
            "present": False,
            "status": "not_implemented",
            "error": error or "LD2410C driver placeholder; UART implementation not bundled yet.",
            "fields": self.fields,
        }

    def read(self):
        raise OSError("LD2410C driver placeholder; UART implementation not bundled yet.")
