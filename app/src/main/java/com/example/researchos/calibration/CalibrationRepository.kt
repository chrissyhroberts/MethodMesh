package com.example.researchos.calibration

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.calibrationDataStore by preferencesDataStore(
    name = "device_calibration"
)

object CalibrationRepository {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    private val dpPerMmKey = floatPreferencesKey("dp_per_mm")
    private val calibratedKey = booleanPreferencesKey("calibrated")

    private var appContext: Context? = null

    var calibration = mutableStateOf(DeviceCalibration())
        private set

    fun initialise(context: Context) {
        appContext = context.applicationContext

        scope.launch {
            val loadedCalibration = withContext(Dispatchers.IO) {
                val preferences = context.calibrationDataStore.data.first()

                DeviceCalibration(
                    dpPerMm = preferences[dpPerMmKey] ?: DeviceCalibration().dpPerMm,
                    calibrated = preferences[calibratedKey] ?: false
                )
            }

            calibration.value = loadedCalibration
        }
    }

    fun current(): DeviceCalibration {
        return calibration.value
    }

    fun update(dpPerMm: Float) {
        val updatedCalibration = DeviceCalibration(
            dpPerMm = dpPerMm.coerceIn(1f, 10f),
            calibrated = true
        )

        calibration.value = updatedCalibration

        val context = appContext ?: return

        scope.launch {
            withContext(Dispatchers.IO) {
                context.calibrationDataStore.edit { preferences ->
                    preferences[dpPerMmKey] = updatedCalibration.dpPerMm
                    preferences[calibratedKey] = updatedCalibration.calibrated
                }
            }
        }
    }
}
