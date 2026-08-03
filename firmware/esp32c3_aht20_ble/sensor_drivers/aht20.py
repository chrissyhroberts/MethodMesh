import time


class AHT20:
    sensor_id = "aht20_1"
    sensor_type = "AHT20"
    bus = "i2c"
    i2c_address = "0x38"
    fields = ["temperature_c", "relative_humidity_pct"]

    ADDRESS = 0x38

    def __init__(self, i2c):
        self.i2c = i2c
        self.present = self.ADDRESS in self.i2c.scan()
        if self.present:
            self.initialize()

    def initialize(self):
        self.i2c.writeto(self.ADDRESS, bytes((0xBE, 0x08, 0x00)))
        time.sleep_ms(20)

    def manifest(self, error=""):
        return {
            "sensor_id": self.sensor_id,
            "sensor_type": self.sensor_type,
            "bus": self.bus,
            "i2c_address": self.i2c_address,
            "present": bool(self.present),
            "status": "ok" if self.present else "missing",
            "error": "" if self.present else (error or "AHT20 not found at I2C address 0x38"),
            "fields": self.fields,
        }

    def read(self):
        if not self.present:
            raise OSError("AHT20 not found at I2C address 0x38")
        self.i2c.writeto(self.ADDRESS, bytes((0xAC, 0x33, 0x00)))
        time.sleep_ms(90)
        data = self.i2c.readfrom(self.ADDRESS, 6)
        if data[0] & 0x80:
            raise OSError("AHT20 sample was busy")
        humidity_raw = ((data[1] << 16) | (data[2] << 8) | data[3]) >> 4
        temp_raw = ((data[3] & 0x0F) << 16) | (data[4] << 8) | data[5]
        humidity = humidity_raw * 100.0 / 1048576.0
        temperature = temp_raw * 200.0 / 1048576.0 - 50.0
        return {
            "sensor_id": self.sensor_id,
            "sensor_type": self.sensor_type,
            "temperature_c": round(temperature, 2),
            "relative_humidity_pct": round(humidity, 2),
        }
