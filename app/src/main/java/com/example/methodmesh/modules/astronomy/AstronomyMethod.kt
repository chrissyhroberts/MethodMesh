package com.example.methodmesh.modules.astronomy

import android.hardware.GeomagneticField
import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.modules.pluscodecapture.OpenLocationCode
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

abstract class AstronomyMethodBase(
    final override val id: String,
    private val displayName: String,
    private val descriptionText: String,
    private val phenomenon: String,
    private val methodType: MethodObjectType,
    val mainField: String,
    private val statusField: String,
    private val errorField: String,
    private val outputs: List<String>,
    private val version: String = "0.5.0"
) : As100Method {
    final override val ref = ArchitectureRef(ArchitectureId(id), "Method", displayName)
    final override val descriptor = MethodDescriptor(
        id = ArchitectureId(id), methodType = methodType, name = displayName, version = version,
        description = descriptionText, outputs = outputs, graphOutputs = listOf(id),
        parameters = mapOf("category" to "Astronomy", "status" to "Development")
    )
    final override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    final override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, calculate(request.context), InvocationContext.from(request.context))

    open fun calculate(settings: Map<String, String>): Map<String, String> = failure("This capability requires an interactive sensor capture.")

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[statusField] == "succeeded"
        val provenance = ProvenanceContext("methodmesh.astronomy", id, version)
        val observation = Observation(
            phenomenon = phenomenon, values = values, temporalContext = request.temporalContext, provenance = provenance
        )
        val transformation = Transformation(
            action = id, method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext, provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            observations = listOf(observation), transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(errorField to values[errorField].orEmpty())
        ).withInvocationContext(invocation)
    }

    protected fun success(vararg entries: Pair<String, String>): Map<String, String> = linkedMapOf<String, String>().apply {
        put(statusField, "succeeded")
        entries.forEach { put(it.first, it.second) }
        putIfAbsent(errorField, "")
    }

    protected fun failure(message: String): Map<String, String> = linkedMapOf(
        statusField to "failed", mainField to "", errorField to message
    )

    protected fun Map<String, String>.value(key: String): String? =
        (this[key] ?: this["input_$key"] ?: this["previous_$key"])?.takeIf { it.isNotBlank() }

    protected fun f(value: Double, digits: Int = 1): String = String.format(Locale.US, "%.${digits}f", value)
}

object PolarAlignFields {
    const val STATUS="astronomy_polar_status"; const val RESULT="astronomy_polar_result"; const val LAT="astronomy_polar_latitude"; const val LON="astronomy_polar_longitude"; const val DECL="astronomy_polar_declination_deg"; const val MAG="astronomy_polar_magnetic_heading_deg"; const val TRUE="astronomy_polar_true_heading_deg"; const val ERROR_DEG="astronomy_polar_error_deg"; const val CAMERA_ELEV="astronomy_polar_camera_elevation_deg"; const val ALT_ERROR="astronomy_polar_altitude_error_deg"; const val POLE_BEARING="astronomy_polar_pole_bearing_deg"; const val POLE_ALT="astronomy_polar_pole_altitude_deg"; const val HEMISPHERE="astronomy_polar_hemisphere"; const val POLARIS_CLOCK="astronomy_polar_polaris_clock_deg"; const val AUDIT="astronomy_polar_audit_json"; const val ERROR="astronomy_polar_error"
    val outputs=listOf(STATUS,RESULT,LAT,LON,DECL,MAG,TRUE,ERROR_DEG,CAMERA_ELEV,ALT_ERROR,POLE_BEARING,POLE_ALT,HEMISPHERE,POLARIS_CLOCK,AUDIT,ERROR)
}
object As100PolarAlignMethod : AstronomyMethodBase(
    "astronomy.polar_align","Polar alignment","Locate true/celestial north or south and report a polar-scope clock position.","astronomy.polar_alignment",MethodObjectType.SignalInterpreter,PolarAlignFields.RESULT,PolarAlignFields.STATUS,PolarAlignFields.ERROR,PolarAlignFields.outputs
) {
    fun capture(latitude: Double, longitude: Double, altitudeM: Double, magneticHeading: Double, toleranceDeg: Double = 2.0, instant: Instant = Instant.now(), cameraElevationDeg: Double? = null): Map<String,String> {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return failure("Valid latitude/longitude are required.")
        val field = GeomagneticField(latitude.toFloat(), longitude.toFloat(), altitudeM.toFloat(), instant.toEpochMilli())
        val declination = field.declination.toDouble()
        val trueHeading = AstronomyMath.normaliseDegrees(magneticHeading + declination)
        val north = latitude >= 0
        val poleBearing = if (north) 0.0 else 180.0
        val error = signedError(poleBearing, trueHeading)
        val poleAlt = abs(latitude)
        val clock = if (north) AstronomyMath.polarisClockAngleDeg(instant, longitude) else Double.NaN
        val tolerance = toleranceDeg.coerceIn(0.5, 15.0)
        val turn = when { abs(error) <= tolerance -> "azimuth aligned"; error > 0 -> "turn right ${f(abs(error))}°"; else -> "turn left ${f(abs(error))}°" }
        val altitudeError = cameraElevationDeg?.let { poleAlt - it }
        val tilt = altitudeError?.let { when { abs(it) <= tolerance -> "altitude aligned"; it > 0 -> "tilt up ${f(abs(it))}°"; else -> "tilt down ${f(abs(it))}°" } }
        val main = if (north) "True N ${f(trueHeading)}° · pole ${f(poleAlt)}° high · $turn${tilt?.let { " · $it" }.orEmpty()} · Polaris clock ${clockLabel(clock)}" else "True S ${f(trueHeading)}° · pole ${f(poleAlt)}° high · $turn${tilt?.let { " · $it" }.orEmpty()}"
        val audit = JSONObject().apply { put("method_id",id); put("algorithm_version",AstronomyMath.ALGORITHM_VERSION); put("geomagnetic_model","Android GeomagneticField"); put("latitude",latitude); put("longitude",longitude); put("declination_deg",declination); put("alignment_tolerance_deg",tolerance); put("instant",instant.toString()); put("north_reference","true/geographic"); put("camera_elevation_deg",cameraElevationDeg?:JSONObject.NULL); put("note","Phone magnetometer remains hardware-limited; true heading applies computed declination to magnetic heading. Camera-axis elevation is included only in AR sighting mode.") }.toString()
        return success(PolarAlignFields.RESULT to main, PolarAlignFields.LAT to f(latitude,6), PolarAlignFields.LON to f(longitude,6), PolarAlignFields.DECL to f(declination,2), PolarAlignFields.MAG to f(magneticHeading,1), PolarAlignFields.TRUE to f(trueHeading,1), PolarAlignFields.ERROR_DEG to f(error,1), PolarAlignFields.CAMERA_ELEV to cameraElevationDeg?.let{f(it,1)}.orEmpty(), PolarAlignFields.ALT_ERROR to altitudeError?.let{f(it,1)}.orEmpty(), PolarAlignFields.POLE_BEARING to f(poleBearing,1), PolarAlignFields.POLE_ALT to f(poleAlt,1), PolarAlignFields.HEMISPHERE to if(north)"north" else "south", PolarAlignFields.POLARIS_CLOCK to if(clock.isFinite()) f(clock,1) else "", PolarAlignFields.AUDIT to audit)
    }
    private fun signedError(target:Double,current:Double):Double = ((target-current+540.0)%360.0)-180.0
    private fun clockLabel(angle:Double):String {
        val totalMinutes = ((AstronomyMath.normaliseDegrees(angle) / 360.0) * 12.0 * 60.0).roundToInt() % (12 * 60)
        val hour0 = totalMinutes / 60
        val minute = totalMinutes % 60
        val hour = if (hour0 == 0) 12 else hour0
        return String.format(Locale.US, "%d:%02d", hour, minute)
    }
}

object ConditionsFields { const val STATUS="astronomy_conditions_status"; const val RESULT="astronomy_conditions_result"; const val SCORE="astronomy_conditions_score"; const val LABEL="astronomy_conditions_label"; const val CLOUD="astronomy_conditions_cloud_score"; const val TRANS="astronomy_conditions_transparency_score"; const val WIND="astronomy_conditions_wind_score"; const val DEW="astronomy_conditions_dew_score"; const val DARK="astronomy_conditions_darkness_score"; const val AUDIT="astronomy_conditions_audit_json"; const val ERROR="astronomy_conditions_error"; val outputs=listOf(STATUS,RESULT,SCORE,LABEL,CLOUD,TRANS,WIND,DEW,DARK,AUDIT,ERROR) }
object As100ConditionsMethod : AstronomyMethodBase("astronomy.conditions","Astro conditions","Score forecast/current imaging conditions using cloud, transparency, wind, dew and moonlight.","astronomy.conditions",MethodObjectType.Calculation,ConditionsFields.RESULT,ConditionsFields.STATUS,ConditionsFields.ERROR,ConditionsFields.outputs) {
    override fun calculate(settings:Map<String,String>):Map<String,String> {
        val weather = parseCurrent(settings.value("weather_json"))
        val air = parseCurrent(settings.value("air_quality_json"))
        val source = settings.value("data_source") ?: settings.value("source_mode") ?: "auto"
        val manualFirst = source == "manual" || source == "upstream"
        val lat = settings.value("latitude")?.toDoubleOrNull()
        val lon = settings.value("longitude")?.toDoubleOrNull()
        val moon = if (lat != null && lon != null) AstronomyMath.moonHorizontal(Instant.now(), lat, lon) else null
        fun number(key:String, api:Double?):Double? = if(manualFirst) settings.value(key)?.toDoubleOrNull() ?: api else api ?: settings.value(key)?.toDoubleOrNull()
        val humidity = if(manualFirst) {
            settings.value("relative_humidity_pct")?.toDoubleOrNull() ?: settings.value("humidity_pct")?.toDoubleOrNull() ?: weather["relative_humidity_2m"]
        } else {
            weather["relative_humidity_2m"] ?: settings.value("relative_humidity_pct")?.toDoubleOrNull() ?: settings.value("humidity_pct")?.toDoubleOrNull()
        }
        val suppliedDew = if (settings.value("use_supplied_dew_point")?.toBooleanStrictOrNull() == true && manualFirst) {
            settings.value("dew_point_c")?.toDoubleOrNull()
        } else weather["dew_point_2m"]
        val s=AstronomyMath.conditionsScore(
            number("cloud_cover_pct",weather["cloud_cover"]),
            number("visibility_m",weather["visibility"]),
            number("wind_kmh",weather["wind_speed_10m"]),
            number("wind_gust_kmh",weather["wind_gusts_10m"]),
            number("temperature_c",weather["temperature_2m"]),
            humidity, suppliedDew,
            number("aerosol_optical_depth",air["aerosol_optical_depth"]),
            number("pm2_5",air["pm2_5"]),
            if(manualFirst) settings.value("moon_altitude_deg")?.toDoubleOrNull() ?: moon?.altitudeDeg else moon?.altitudeDeg ?: settings.value("moon_altitude_deg")?.toDoubleOrNull(),
            if(manualFirst) settings.value("moon_illumination_pct")?.toDoubleOrNull() ?: moon?.illuminationPct else moon?.illuminationPct ?: settings.value("moon_illumination_pct")?.toDoubleOrNull()
        )
        val main="IMAGING ${s.label} · ${s.overall}/100 · cloud ${s.cloud} · transparency ${s.transparency} · wind ${s.wind} · dew ${s.dew} · darkness ${s.darkness}"
        val audit=JSONObject(settings).put("algorithm_version",AstronomyMath.ALGORITHM_VERSION).put("resolved_data_source",source).put("weather_json_used",weather.isNotEmpty()).put("air_quality_json_used",air.isNotEmpty()).put("humidity_alias","relative_humidity_pct|humidity_pct")
        return success(ConditionsFields.RESULT to main,ConditionsFields.SCORE to s.overall.toString(),ConditionsFields.LABEL to s.label,ConditionsFields.CLOUD to s.cloud.toString(),ConditionsFields.TRANS to s.transparency.toString(),ConditionsFields.WIND to s.wind.toString(),ConditionsFields.DEW to s.dew.toString(),ConditionsFields.DARK to s.darkness.toString(),ConditionsFields.AUDIT to audit.toString())
    }
    private fun parseCurrent(raw:String?):Map<String,Double> = runCatching {
        if(raw.isNullOrBlank()) return@runCatching emptyMap<String,Double>()
        val root=JSONObject(raw); val current=root.optJSONObject("current") ?: root
        buildMap { current.keys().forEach { key -> current.optDouble(key,Double.NaN).takeIf { it.isFinite() }?.let { put(key,it) } } }
    }.getOrDefault(emptyMap())
}

object SkyTestFields { const val STATUS="astronomy_sky_status"; const val RESULT="astronomy_sky_result"; const val OVERALL="astronomy_sky_overall_score"; const val STABILITY="astronomy_sky_stability_score"; const val TRANSPARENCY="astronomy_sky_transparency_score"; const val DARKNESS="astronomy_sky_darkness_score"; const val FOCUS="astronomy_sky_focus_score"; const val CENTROID="astronomy_sky_centroid_rms_px"; const val FWHM="astronomy_sky_fwhm_px"; const val BG="astronomy_sky_background_luma"; const val CONTRAST="astronomy_sky_contrast"; const val SCINT="astronomy_sky_scintillation_cv"; const val FRAMES="astronomy_sky_frames"; const val CALIBRATED="astronomy_sky_calibration_available"; const val CALIBRATION_DATE="astronomy_sky_calibration_date"; const val WARNING="astronomy_sky_warning"; const val AUDIT="astronomy_sky_audit_json"; const val ERROR="astronomy_sky_error"; val outputs=listOf(STATUS,RESULT,OVERALL,STABILITY,TRANSPARENCY,DARKNESS,FOCUS,CENTROID,FWHM,BG,CONTRAST,SCINT,FRAMES,CALIBRATED,CALIBRATION_DATE,WARNING,AUDIT,ERROR) }
object As100SkyTestMethod : AstronomyMethodBase("astronomy.sky_test","Live sky test","Measure relative point-source stability, transparency and sky background against a saved good-night calibration.","astronomy.sky_quality",MethodObjectType.SignalInterpreter,SkyTestFields.RESULT,SkyTestFields.STATUS,SkyTestFields.ERROR,SkyTestFields.outputs) {
    fun capture(summary:StarTestSummary,scores:Map<String,Int?>,calibration:AstronomyRepository.GoodNightCalibration?):Map<String,String> {
        if(summary.detectionCount==0) return failure(summary.qualityWarning ?: "No point source detected.")
        val overall=scores["overall"]; val main=if(overall!=null) "Sky ${overall}/100 vs good-night · stability ${scores["stability"] ?: "—"} · transparency ${scores["transparency"] ?: "—"} · darkness ${scores["darkness"] ?: "—"}" else "Sky test captured · save a good-night calibration for relative scores"
        val audit=JSONObject().apply{put("method_id",id);put("metric_meaning","relative phone-camera point-source proxies, not arcsecond seeing or calibrated sky magnitude");put("frames",summary.frameCount);put("detections",summary.detectionCount);put("centroid_rms_px",summary.centroidRmsPx);put("fwhm_px",summary.medianFwhmPx);put("background_luma",summary.medianBackgroundLuma);put("contrast",summary.medianContrast);put("scintillation_cv",summary.scintillationCv);put("calibration_saved_at",calibration?.savedAtIso?:JSONObject.NULL)}.toString()
        return success(SkyTestFields.RESULT to main,SkyTestFields.OVERALL to overall?.toString().orEmpty(),SkyTestFields.STABILITY to scores["stability"]?.toString().orEmpty(),SkyTestFields.TRANSPARENCY to scores["transparency"]?.toString().orEmpty(),SkyTestFields.DARKNESS to scores["darkness"]?.toString().orEmpty(),SkyTestFields.FOCUS to scores["focus"]?.toString().orEmpty(),SkyTestFields.CENTROID to f(summary.centroidRmsPx,3),SkyTestFields.FWHM to f(summary.medianFwhmPx,3),SkyTestFields.BG to f(summary.medianBackgroundLuma,2),SkyTestFields.CONTRAST to f(summary.medianContrast,3),SkyTestFields.SCINT to f(summary.scintillationCv,4),SkyTestFields.FRAMES to summary.frameCount.toString(),SkyTestFields.CALIBRATED to (calibration!=null).toString(),SkyTestFields.CALIBRATION_DATE to calibration?.savedAtIso.orEmpty(),SkyTestFields.WARNING to summary.qualityWarning.orEmpty(),SkyTestFields.AUDIT to audit)
    }
}

object ImagingWindowFields { const val STATUS="astronomy_window_status"; const val RESULT="astronomy_window_result"; const val START="astronomy_window_start_iso"; const val END="astronomy_window_end_iso"; const val PEAK="astronomy_window_peak_iso"; const val SCORE="astronomy_window_peak_score"; const val TARGET_ALT="astronomy_window_target_altitude_deg"; const val MOON_ALT="astronomy_window_moon_altitude_deg"; const val MOON_ILL="astronomy_window_moon_illumination_pct"; const val MOON_SEP="astronomy_window_moon_separation_deg"; const val SAMPLES="astronomy_window_samples_json"; const val AUDIT="astronomy_window_audit_json"; const val ERROR="astronomy_window_error"; val outputs=listOf(STATUS,RESULT,START,END,PEAK,SCORE,TARGET_ALT,MOON_ALT,MOON_ILL,MOON_SEP,SAMPLES,AUDIT,ERROR) }
object As100ImagingWindowMethod : AstronomyMethodBase("astronomy.imaging_window","Imaging window","Plan the best imaging window using astronomical darkness, target altitude, moon geometry and optional hourly forecast data.","astronomy.imaging_window",MethodObjectType.Calculation,ImagingWindowFields.RESULT,ImagingWindowFields.STATUS,ImagingWindowFields.ERROR,ImagingWindowFields.outputs) {
    override fun calculate(settings:Map<String,String>):Map<String,String> {
        val plus=settings.value("plus_code").orEmpty(); val area=plus.takeIf{it.isNotBlank()}?.let{runCatching{OpenLocationCode.decode(it)}.getOrNull()}; val lat=area?.centerLatitude ?: settings.value("latitude")?.toDoubleOrNull() ?: return failure("Latitude or Plus Code is required."); val lon=area?.centerLongitude ?: settings.value("longitude")?.toDoubleOrNull() ?: return failure("Longitude or Plus Code is required.")
        val useTarget=settings.value("use_target")?.toBooleanStrictOrNull() == true; val raH=settings.value("target_ra_hours")?.toDoubleOrNull(); val dec=settings.value("target_dec_deg")?.toDoubleOrNull(); val target=if(useTarget&&raH!=null&&dec!=null) AstronomyMath.Equatorial(raH*15.0,dec) else null
        val hours=settings.value("hours_ahead")?.toIntOrNull()?.coerceIn(2,24)?:14; val step=settings.value("interval_minutes")?.toIntOrNull()?.coerceIn(10,60)?:30; val minAlt=settings.value("minimum_altitude_deg")?.toDoubleOrNull()?:25.0; val threshold=settings.value("minimum_score")?.toIntOrNull()?.coerceIn(0,100)?:55
        val start=settings.value("start_time_iso")?.let{runCatching{Instant.parse(it)}.getOrNull()}?:Instant.now(); val forecast=ForecastLookup.parse(settings.value("forecast_json"))
        val samples=mutableListOf<AstronomyMath.WindowSample>(); var i=0; while(i<=hours*60){ val t=start.plusSeconds(i*60L); val fw=forecast?.at(t); samples += AstronomyMath.imagingWindowSample(t,lat,lon,target,minAlt,fw?.cloud,fw?.wind,fw?.transparency); i+=step }
        val best=samples.maxByOrNull{it.score}?:return failure("No planning samples generated."); val qualifying=samples.filter{it.score>=threshold}; val windowStart=qualifying.firstOrNull()?.instant?:best.instant; val windowEnd=qualifying.lastOrNull()?.instant?.plusSeconds(step*60L)?:best.instant.plusSeconds(step*60L)
        val name=settings.value("target_name")?.ifBlank{null} ?: if(target!=null)"target" else "sky"; val main="Best $name window ${windowStart} – ${windowEnd} · peak ${best.score}/100 at ${best.instant}"
        val arr=JSONArray(); samples.forEach{arr.put(JSONObject().apply{put("time",it.instant.toString());put("score",it.score);put("sun_altitude_deg",it.sunAltitudeDeg);put("target_altitude_deg",it.targetAltitudeDeg?:JSONObject.NULL);put("moon_altitude_deg",it.moonAltitudeDeg);put("moon_illumination_pct",it.moonIlluminationPct);put("moon_separation_deg",it.moonSeparationDeg?:JSONObject.NULL)})}
        return success(ImagingWindowFields.RESULT to main,ImagingWindowFields.START to windowStart.toString(),ImagingWindowFields.END to windowEnd.toString(),ImagingWindowFields.PEAK to best.instant.toString(),ImagingWindowFields.SCORE to best.score.toString(),ImagingWindowFields.TARGET_ALT to best.targetAltitudeDeg?.let{f(it,1)}.orEmpty(),ImagingWindowFields.MOON_ALT to f(best.moonAltitudeDeg,1),ImagingWindowFields.MOON_ILL to f(best.moonIlluminationPct,1),ImagingWindowFields.MOON_SEP to best.moonSeparationDeg?.let{f(it,1)}.orEmpty(),ImagingWindowFields.SAMPLES to arr.toString(),ImagingWindowFields.AUDIT to JSONObject(settings).put("algorithm_version",AstronomyMath.ALGORITHM_VERSION).put("forecast_supplied",forecast!=null).toString())
    }
    private data class ForecastPoint(val time:Instant,val cloud:Double?,val wind:Double?,val transparency:Int?)
    private class ForecastLookup(private val p:List<ForecastPoint>){ fun at(t:Instant)=p.minByOrNull{kotlin.math.abs(it.time.epochSecond-t.epochSecond)}?.takeIf{kotlin.math.abs(it.time.epochSecond-t.epochSecond)<=5400}
        companion object { fun parse(raw:String?):ForecastLookup? { if(raw.isNullOrBlank())return null; return runCatching{ val root=JSONObject(raw); val hourly=root.optJSONObject("hourly")?:root; val times=hourly.optJSONArray("time")?:return@runCatching null; val clouds=hourly.optJSONArray("cloud_cover"); val gusts=hourly.optJSONArray("wind_gusts_10m")?:hourly.optJSONArray("wind_speed_10m"); val vis=hourly.optJSONArray("visibility"); val offset=root.optInt("utc_offset_seconds",0); val out=mutableListOf<ForecastPoint>(); for(i in 0 until times.length()){val text=times.optString(i); val instant=runCatching{Instant.parse(text)}.getOrElse{LocalDateTime.parse(text,DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.ofTotalSeconds(offset))}; val transparency=vis?.optDouble(i,Double.NaN)?.takeIf{it.isFinite()}?.let{((it-5000.0)/25000.0*100.0).roundToInt().coerceIn(0,100)}; out+=ForecastPoint(instant,clouds?.optDouble(i,Double.NaN)?.takeIf{it.isFinite()},gusts?.optDouble(i,Double.NaN)?.takeIf{it.isFinite()},transparency)}; ForecastLookup(out)}.getOrNull() } }
    }
}

object DewFields { const val STATUS="astronomy_dew_status"; const val RESULT="astronomy_dew_result"; const val DEW="astronomy_dew_point_c"; const val MARGIN="astronomy_dew_margin_c"; const val RISK="astronomy_dew_risk"; const val AUDIT="astronomy_dew_audit_json"; const val ERROR="astronomy_dew_error"; val outputs=listOf(STATUS,RESULT,DEW,MARGIN,RISK,AUDIT,ERROR) }
object As100DewRiskMethod : AstronomyMethodBase("astronomy.dew_risk","Dew risk","Calculate dew point margin and condensation risk.","astronomy.dew_risk",MethodObjectType.Calculation,DewFields.RESULT,DewFields.STATUS,DewFields.ERROR,DewFields.outputs) {
    override fun calculate(settings:Map<String,String>):Map<String,String>{
        val current=runCatching{val root=JSONObject(settings.value("weather_json").orEmpty());root.optJSONObject("current")?:root}.getOrNull()
        val source=settings.value("data_source") ?: settings.value("source_mode") ?: "auto"
        val manualFirst=source=="manual" || source=="upstream"
        val apiTemp=current?.optDouble("temperature_2m",Double.NaN)?.takeIf{it.isFinite()}
        val apiRh=current?.optDouble("relative_humidity_2m",Double.NaN)?.takeIf{it.isFinite()}
        val resolvedTemperature: Double? = if (manualFirst) {
            settings.value("temperature_c")?.toDoubleOrNull() ?: apiTemp
        } else {
            apiTemp ?: settings.value("temperature_c")?.toDoubleOrNull()
        }
        val t: Double = resolvedTemperature
            ?: return failure("Temperature is required from Open-Meteo, an upstream workflow value, or manual fallback.")

        val resolvedHumidity: Double? = if (manualFirst) {
            settings.value("relative_humidity_pct")?.toDoubleOrNull()
                ?: settings.value("humidity_pct")?.toDoubleOrNull()
                ?: apiRh
        } else {
            apiRh
                ?: settings.value("relative_humidity_pct")?.toDoubleOrNull()
                ?: settings.value("humidity_pct")?.toDoubleOrNull()
        }
        val rh: Double = resolvedHumidity
            ?: return failure("Relative humidity is required from Open-Meteo, an upstream workflow value, or manual fallback.")
        val supplied=if(settings.value("use_supplied_dew_point")?.toBooleanStrictOrNull()==true && manualFirst) settings.value("dew_point_c")?.toDoubleOrNull() else current?.optDouble("dew_point_2m",Double.NaN)?.takeIf{it.isFinite()}
        val d=AstronomyMath.dewRisk(t,rh,supplied)
        return success(DewFields.RESULT to "Dew ${f(d.dewPointC)}°C · margin ${f(d.marginC)}°C · ${d.risk} RISK",DewFields.DEW to f(d.dewPointC,2),DewFields.MARGIN to f(d.marginC,2),DewFields.RISK to d.risk,DewFields.AUDIT to JSONObject(settings).put("formula","Magnus").put("resolved_data_source",source).put("accepted_upstream_fields","temperature_c,relative_humidity_pct").toString())
    }
}

object ImageScaleFields { const val STATUS="astronomy_scale_status"; const val RESULT="astronomy_scale_result"; const val SCALE="astronomy_scale_arcsec_per_pixel"; const val WIDTH="astronomy_scale_fov_width_deg"; const val HEIGHT="astronomy_scale_fov_height_deg"; const val EFL="astronomy_scale_effective_focal_length_mm"; const val FRATIO="astronomy_scale_f_ratio"; const val AUDIT="astronomy_scale_audit_json"; const val ERROR="astronomy_scale_error"; val outputs=listOf(STATUS,RESULT,SCALE,WIDTH,HEIGHT,EFL,FRATIO,AUDIT,ERROR) }
object As100ImageScaleMethod : AstronomyMethodBase("astronomy.image_scale","Field of view & sampling","Calculate image scale, field of view, effective focal length and focal ratio.","astronomy.image_scale",MethodObjectType.Calculation,ImageScaleFields.RESULT,ImageScaleFields.STATUS,ImageScaleFields.ERROR,ImageScaleFields.outputs) { override fun calculate(settings:Map<String,String>):Map<String,String>{return runCatching{val r=AstronomyMath.imageScale(settings.value("focal_length_mm")!!.toDouble(),settings.value("pixel_size_micron")!!.toDouble(),settings.value("sensor_width_mm")!!.toDouble(),settings.value("sensor_height_mm")!!.toDouble(),settings.value("aperture_mm")?.toDoubleOrNull(),settings.value("multiplier")?.toDoubleOrNull()?:1.0);success(ImageScaleFields.RESULT to "${f(r.arcsecPerPixel,2)} arcsec/px · FoV ${f(r.fieldWidthDeg,2)}° × ${f(r.fieldHeightDeg,2)}°${r.focalRatio?.let{" · f/${f(it,1)}"}.orEmpty()}",ImageScaleFields.SCALE to f(r.arcsecPerPixel,4),ImageScaleFields.WIDTH to f(r.fieldWidthDeg,4),ImageScaleFields.HEIGHT to f(r.fieldHeightDeg,4),ImageScaleFields.EFL to f(r.effectiveFocalLengthMm,2),ImageScaleFields.FRATIO to r.focalRatio?.let{f(it,2)}.orEmpty(),ImageScaleFields.AUDIT to JSONObject(settings).put("formula","206.265 * pixel_um / focal_mm; exact angular FoV").toString())}.getOrElse{failure("Enter positive focal length, pixel size and sensor dimensions.")}} }

object ExposureFields { const val STATUS="astronomy_exposure_status"; const val RESULT="astronomy_exposure_result"; const val PIXEL="astronomy_exposure_pixel_limit_s"; const val RULE500="astronomy_exposure_500_rule_s"; const val SCALE="astronomy_exposure_arcsec_per_pixel"; const val AUDIT="astronomy_exposure_audit_json"; const val ERROR="astronomy_exposure_error"; val outputs=listOf(STATUS,RESULT,PIXEL,RULE500,SCALE,AUDIT,ERROR) }
object As100ExposureLimitMethod : AstronomyMethodBase("astronomy.exposure_limit","Untracked exposure limit","Estimate star-trail exposure limits from pixel scale and declination, with a 500-rule comparator.","astronomy.exposure_limit",MethodObjectType.Calculation,ExposureFields.RESULT,ExposureFields.STATUS,ExposureFields.ERROR,ExposureFields.outputs) { override fun calculate(settings:Map<String,String>):Map<String,String>{val fmm=settings.value("focal_length_mm")?.toDoubleOrNull()?:return failure("Focal length is required.");val px=settings.value("pixel_size_micron")?.toDoubleOrNull()?:return failure("Pixel size is required.");val dec=settings.value("declination_deg")?.toDoubleOrNull()?:0.0;val trail=settings.value("max_trail_pixels")?.toDoubleOrNull()?:1.0;val crop=settings.value("crop_factor")?.toDoubleOrNull()?:1.0;val limits=AstronomyMath.exposureLimitSeconds(fmm,px,dec,trail,crop);val scale=206.265*px/fmm;return success(ExposureFields.RESULT to "Pixel limit ${f(limits.first,2)} s · 500-rule ${f(limits.second,1)} s",ExposureFields.PIXEL to f(limits.first,4),ExposureFields.RULE500 to f(limits.second,3),ExposureFields.SCALE to f(scale,4),ExposureFields.AUDIT to JSONObject(settings).put("sidereal_rate_arcsec_s",15.041067).toString())} }

object MountFields { const val STATUS="astronomy_mount_status"; const val RESULT="astronomy_mount_result"; const val LABEL="astronomy_mount_label"; const val ACCEL="astronomy_mount_accel_rms_ms2"; const val GYRO="astronomy_mount_gyro_rms_rads"; const val PEAK="astronomy_mount_peak_accel_delta_ms2"; const val SAMPLES="astronomy_mount_samples"; const val AUDIT="astronomy_mount_audit_json"; const val ERROR="astronomy_mount_error"; val outputs=listOf(STATUS,RESULT,LABEL,ACCEL,GYRO,PEAK,SAMPLES,AUDIT,ERROR) }
object As100MountStabilityMethod : AstronomyMethodBase("astronomy.mount_stability","Mount stability","Measure vibration of a telescope/tripod using phone accelerometer and gyroscope.","astronomy.mount_stability",MethodObjectType.SignalInterpreter,MountFields.RESULT,MountFields.STATUS,MountFields.ERROR,MountFields.outputs) { fun capture(s:MountStabilitySampler.Summary):Map<String,String>{if(!s.accelRmsMs2.isFinite())return failure("Not enough motion samples.");return success(MountFields.RESULT to "Mount ${s.label} · accel RMS ${f(s.accelRmsMs2,4)} m/s²${s.gyroRmsRadS?.let{" · gyro ${f(it,4)} rad/s"}.orEmpty()}",MountFields.LABEL to s.label,MountFields.ACCEL to f(s.accelRmsMs2,6),MountFields.GYRO to s.gyroRmsRadS?.let{f(it,6)}.orEmpty(),MountFields.PEAK to f(s.peakAccelDeltaMs2,6),MountFields.SAMPLES to s.samples.toString(),MountFields.AUDIT to JSONObject().put("method_id",id).put("interpretation","relative mount vibration on the phone attachment point").toString())} }

object FocusFields {
    const val STATUS="astronomy_focus_status"; const val RESULT="astronomy_focus_result"; const val FWHM="astronomy_focus_fwhm_px"; const val BEST="astronomy_focus_best_fwhm_px"; const val CONTRAST="astronomy_focus_contrast"; const val FRAMES="astronomy_focus_frames"; const val CAMERA="astronomy_focus_camera_facing"; const val AVERAGING="astronomy_focus_averaging_frames"; const val WARNING="astronomy_focus_warning"; const val AUDIT="astronomy_focus_audit_json"; const val ERROR="astronomy_focus_error"
    val outputs=listOf(STATUS,RESULT,FWHM,BEST,CONTRAST,FRAMES,CAMERA,AVERAGING,WARNING,AUDIT,ERROR)
}
object As100FocusMethod : AstronomyMethodBase("astronomy.focus","Telescope focus assistant","Use a phone attached to an eyepiece/optical train and a bright point source to minimise rolling-median PSF width.","astronomy.focus",MethodObjectType.SignalInterpreter,FocusFields.RESULT,FocusFields.STATUS,FocusFields.ERROR,FocusFields.outputs) {
    fun capture(summary:StarTestSummary,bestFwhm:Double,currentFwhm:Double=summary.medianFwhmPx,cameraFacing:String="rear",averagingFrames:Int=30):Map<String,String>{
        if(summary.detectionCount==0)return failure("No point source detected.")
        val current=currentFwhm.takeIf{it.isFinite()}?:summary.medianFwhmPx
        return success(
            FocusFields.RESULT to "Focus ${f(current,2)} px rolling median · best ${f(bestFwhm,2)} px",
            FocusFields.FWHM to f(current,4),FocusFields.BEST to f(bestFwhm,4),FocusFields.CONTRAST to f(summary.medianContrast,4),FocusFields.FRAMES to summary.frameCount.toString(),FocusFields.CAMERA to cameraFacing,FocusFields.AVERAGING to averagingFrames.toString(),FocusFields.WARNING to summary.qualityWarning.orEmpty(),
            FocusFields.AUDIT to JSONObject().put("method_id",id).put("metric","relative luminance-plane PSF FWHM").put("live_metric","rolling median").put("camera_facing",cameraFacing).put("averaging_frames",averagingFrames).toString()
        )
    }
}

object SessionFields {
    const val STATUS="astronomy_session_status"; const val RESULT="astronomy_session_result"; const val SESSION_ID="astronomy_session_id"; const val STATE="astronomy_session_state"; const val TARGET="astronomy_session_target"; const val START="astronomy_session_start_iso"; const val END="astronomy_session_end_iso"; const val DURATION="astronomy_session_active_duration_seconds"; const val SCORE="astronomy_session_conditions_score"; const val JSON="astronomy_session_json"; const val AUDIT="astronomy_session_audit_json"; const val ERROR="astronomy_session_error"
    val outputs=listOf(STATUS,RESULT,SESSION_ID,STATE,TARGET,START,END,DURATION,SCORE,JSON,AUDIT,ERROR)
}
object As100SessionMethod : AstronomyMethodBase("astronomy.session","Astronomy session record","Run a resumable observing/imaging session with explicit start, pause/resume and stop controls.","astronomy.session",MethodObjectType.Calculation,SessionFields.RESULT,SessionFields.STATUS,SessionFields.ERROR,SessionFields.outputs) {
    override fun calculate(settings:Map<String,String>):Map<String,String>{
        val target=settings.value("target")?.ifBlank{"Unspecified target"}?:"Unspecified target"
        val start=settings.value("start_iso")?.ifBlank{Instant.now().toString()}?:Instant.now().toString()
        val end=settings.value("end_iso").orEmpty()
        val equipment=settings.value("equipment").orEmpty()
        val conditions=settings.value("conditions_summary").orEmpty()
        val sky=settings.value("sky_test_summary").orEmpty()
        val notes=settings.value("notes").orEmpty()
        val score=settings.value("conditions_score").orEmpty()
        val sessionId=settings.value("session_id").orEmpty()
        val state=settings.value("session_state")?.ifBlank{"finished"}?:"finished"
        val duration=settings.value("active_duration_seconds").orEmpty()
        val text=buildString{
            append("ASTRO SESSION\n");append(target);append(" · ");append(start)
            if(end.isNotBlank())append(" → $end")
            if(duration.isNotBlank())append("\nActive time: ${duration.toDoubleOrNull()?.let{f(it/60.0,1)}?:duration} min")
            if(equipment.isNotBlank())append("\nEquipment: $equipment")
            if(conditions.isNotBlank())append("\nConditions: $conditions")
            if(sky.isNotBlank())append("\nSky test: $sky")
            if(notes.isNotBlank())append("\nNotes: $notes")
        }
        val json=JSONObject().apply{
            put("session_id",sessionId);put("state",state);put("target",target);put("start_iso",start);put("end_iso",end);put("active_duration_seconds",duration)
            put("equipment",equipment);put("conditions_summary",conditions);put("conditions_score",score);put("sky_test_summary",sky);put("notes",notes);put("created_iso",Instant.now().toString())
        }
        return success(SessionFields.RESULT to text,SessionFields.SESSION_ID to sessionId,SessionFields.STATE to state,SessionFields.TARGET to target,SessionFields.START to start,SessionFields.END to end,SessionFields.DURATION to duration,SessionFields.SCORE to score,SessionFields.JSON to json.toString(),SessionFields.AUDIT to JSONObject().put("method_id",id).put("resumable",true).put("saved_locally",settings.value("save_session")?.toBooleanStrictOrNull()?:false).toString())
    }
}
