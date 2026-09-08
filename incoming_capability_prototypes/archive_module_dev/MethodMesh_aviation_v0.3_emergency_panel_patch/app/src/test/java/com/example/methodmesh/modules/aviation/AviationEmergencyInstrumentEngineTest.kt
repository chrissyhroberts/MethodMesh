package com.example.methodmesh.modules.aviation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AviationEmergencyInstrumentEngineTest {
    @Test
    fun portraitCalibrationOffsetsDeviceAttitude() {
        val attitude = AviationEmergencyInstrumentEngine.transformAttitude(
            rawPitchDeg = 8.0,
            rawRollDeg = -12.0,
            calibration = AviationEmergencyCalibration(
                mountOrientation = "portrait_up",
                pitchZeroDeg = 3.0,
                rollZeroDeg = -2.0,
                calibrated = true
            )
        )
        assertTrue(attitude.valid)
        assertTrue(abs(attitude.pitchDeg!! - 5.0) < 0.001)
        assertTrue(abs(attitude.rollDeg!! + 10.0) < 0.001)
    }

    @Test
    fun landscapeMountRemapsDeviceAxes() {
        val attitude = AviationEmergencyInstrumentEngine.transformAttitude(
            rawPitchDeg = 10.0,
            rawRollDeg = 20.0,
            calibration = AviationEmergencyCalibration("landscape_left", calibrated = true)
        )
        assertTrue(abs(attitude.pitchDeg!! + 20.0) < 0.001)
        assertTrue(abs(attitude.rollDeg!! - 10.0) < 0.001)
    }

    @Test
    fun attitudeWithoutCalibrationIsExplicitlyInvalid() {
        val attitude = AviationEmergencyInstrumentEngine.transformAttitude(
            rawPitchDeg = 1.0,
            rawRollDeg = 2.0,
            calibration = AviationEmergencyCalibration(calibrated = false)
        )
        assertFalse(attitude.valid)
    }

    @Test
    fun standardPressureIsApproximatelySeaLevelPressureAltitude() {
        val altitude = AviationEmergencyInstrumentEngine.pressureAltitudeFt(1013.25)
        assertTrue(altitude != null && abs(altitude) < 2.0)
    }

    @Test
    fun dynamicAccelerationAddsAttitudeCaution() {
        val sample = AviationEmergencySample(
            rawPitchDeg = 2.0,
            rawRollDeg = 5.0,
            gpsAccuracyM = 5.0,
            gpsFixAgeSeconds = 0.5,
            accelerationMagnitudeG = 1.35,
            rotationVectorAvailable = true
        )
        val attitude = AviationEmergencyInstrumentEngine.transformAttitude(
            sample.rawPitchDeg,
            sample.rawRollDeg,
            AviationEmergencyCalibration(calibrated = true)
        )
        assertTrue(AviationEmergencyInstrumentEngine.quality(sample, attitude).contains("ATT DYNAMIC"))
    }

    @Test
    fun outputNeverLabelsGroundspeedAsAirspeed() {
        val values = AviationEmergencyInstrumentEngine.values(
            sample = AviationEmergencySample(
                rawPitchDeg = 0.0,
                rawRollDeg = 0.0,
                gpsGroundSpeedKt = 85.0,
                gpsTrackDeg = 270.0,
                gpsAltitudeFt = 2500.0,
                gpsAccuracyM = 5.0,
                gpsFixAgeSeconds = 0.5,
                rotationVectorAvailable = true
            ),
            calibration = AviationEmergencyCalibration(calibrated = true)
        )
        assertEquals("85.0", values[AviationEmergencyFields.GPS_GROUND_SPEED_KT])
        assertTrue(values[AviationEmergencyFields.VALUE].orEmpty().startsWith("GS "))
        assertFalse(values[AviationEmergencyFields.VALUE].orEmpty().contains("airspeed", ignoreCase = true))
        assertTrue(values[AviationEmergencyFields.WARNING].orEmpty().contains("not certified", ignoreCase = true))
    }
}
