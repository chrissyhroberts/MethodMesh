package com.example.methodmesh.modules.weather

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun WeatherDashboardScreen(
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val app = LocalContext.current
    val scope = rememberCoroutineScope()
    var latitude by rememberSaveable { mutableStateOf((context.action.settings["latitude"] ?: context.action.settings["input_latitude"]).orEmpty()) }
    var longitude by rememberSaveable { mutableStateOf((context.action.settings["longitude"] ?: context.action.settings["input_longitude"]).orEmpty()) }
    var rainThreshold by rememberSaveable { mutableStateOf((context.action.settings["threshold_mm_per_hour"] ?: context.action.settings["input_threshold_mm_per_hour"] ?: "0.2")) }
    var offlineOnly by rememberSaveable { mutableStateOf((context.action.settings["offline_only"] ?: context.action.settings["input_offline_only"]).equals("true", ignoreCase = true)) }
    var payloadJson by rememberSaveable { mutableStateOf("") }
    var committedDashboardJson by rememberSaveable { mutableStateOf("") }
    var committedSettingsJson by rememberSaveable { mutableStateOf("") }
    var running by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("Locating…") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var showLocation by rememberSaveable {
        mutableStateOf(context.isNativePresetRun && context.runtimeInputFields.isNotEmpty())
    }

    val payload = remember(payloadJson) { runCatching { JSONObject(payloadJson) }.getOrNull() }
    fun section(name: String): Map<String,String> = payload?.optJSONObject(name)?.let { o ->
        buildMap { val keys=o.keys(); while(keys.hasNext()){ val k=keys.next(); put(k,o.optString(k,"")) } }
    }.orEmpty()

    fun currentSettings(): Map<String, String> =
        context.action.settings.mapKeys { it.key.removePrefix("input_") } + mapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "threshold_mm_per_hour" to rainThreshold,
            "offline_only" to offlineOnly.toString()
        )

    fun buildDashboardExecution(values: Map<String, String>, settings: Map<String, String> = currentSettings()) =
        As100WeatherDashboardMethod.result(
            As100WeatherDashboardMethod.request(
                context.action.canonicalId,
                context.request.invocationContext.asMap(context.action.canonicalId) + context.action.settings + settings,
                emptyList(),
                emptyList()
            ),
            values,
            context.request.invocationContext
        )

    fun runDashboard() {
        if(running || latitude.toDoubleOrNull()==null || longitude.toDoubleOrNull()==null) return
        running=true; message="Refreshing meteorology…"
        scope.launch {
            val settings=currentSettings()
            val capture=withContext(Dispatchers.IO) {
                coroutineScope {
                // Fetch the two advanced products in parallel with the broad dashboard
                // request. The ordinary dashboard remains one coherent Open-Meteo + radar
                // capture, while advanced panels do not serially delay each other.
                val atmosphereDeferred = async { WeatherRuntime.capture(As100WeatherAtmosphereMethod, settings).values }
                val modelCompareDeferred = async { WeatherRuntime.capture(As100WeatherModelCompareMethod, settings).values }
                val dashboardCapture = WeatherRuntime.capture(
                    As100WeatherDashboardMethod,
                    settings + ("horizon_hours" to "240")
                )
                val shared = dashboardCapture.settings
                val radarSettings = dashboardCapture.radarPayload?.let { radarPayload ->
                    shared + mapOf(
                        "provider" to radarPayload.provider,
                        "retrieved_time_iso" to radarPayload.retrievedTimeIso,
                        "from_cache" to radarPayload.fromCache.toString(),
                        "data_age_hours" to radarPayload.dataAgeHours?.toString().orEmpty(),
                        "api_error" to radarPayload.error
                    )
                } ?: shared
                val forecastValues = As100WeatherForecastMethod.calculate(shared + ("horizon_hours" to "168"))
                val conditionsValues = As100WeatherConditionsMethod.calculate(shared)
                val precipitationValues = As100WeatherPrecipitationMethod.calculate(shared + ("horizon_hours" to "24"))
                val meteogramValues = As100WeatherMeteogramMethod.calculate(shared + ("horizon_hours" to "72"))
                val sunValues = As100WeatherSunMethod.calculate(shared)
                val snapshotTime = dashboardCapture.values["weather_dashboard_valid_time_iso"].orEmpty()
                val snapshotValues = As100WeatherSnapshotMethod.calculate(
                    shared + mapOf("target_time_iso" to snapshotTime, "source_policy" to "forecast")
                )
                val atmosphereValues = atmosphereDeferred.await()
                val modelCompareValues = modelCompareDeferred.await()
                mapOf(
                    "dashboard" to dashboardCapture.values,
                    "conditions" to conditionsValues,
                    "forecast" to forecastValues,
                    "precipitation" to precipitationValues,
                    "radar" to As100WeatherRadarMethod.calculate(radarSettings + ("zoom" to "5")),
                    "meteogram" to meteogramValues,
                    "sun" to sunValues,
                    "snapshot" to snapshotValues,
                    "atmosphere" to atmosphereValues,
                    "model_compare" to modelCompareValues
                )
                }
            }
            payloadJson=JSONObject().apply{capture.forEach{(k,v)->put(k,JSONObject(v))}}.toString()
            running=false; message="Live dashboard"; context.onSettingsChanged(settings)
            if(context.submitsImmediately) {
                val values=capture["dashboard"].orEmpty()
                onConfirmed(buildDashboardExecution(values, settings))
            }
        }
    }

    fun useGps() {
        if(!hasDashboardLocationPermission(app)) return
        running=true;message="Getting current location…"
        val token=CancellationTokenSource()
        LocationServices.getFusedLocationProviderClient(app).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,token.token)
            .addOnSuccessListener { loc ->
                if(loc==null){running=false;message="Current GPS position unavailable."}
                else{latitude=loc.latitude.toString();longitude=loc.longitude.toString();running=false;runDashboard()}
            }
            .addOnFailureListener{running=false;message=it.message?:"Location failed."}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
        if(hasDashboardLocationPermission(app))useGps() else {running=false;showLocation=true;message="Location denied. Enter coordinates below."}
    }
    fun locate(){if(hasDashboardLocationPermission(app))useGps() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}

    val nativePresetNeedsRuntimeInput = context.isNativePresetRun && context.runtimeInputFields.isNotEmpty()
    LaunchedEffect(context.action.canonicalId, nativePresetNeedsRuntimeInput) {
        if(!attempted){
            attempted=true
            if(nativePresetNeedsRuntimeInput){
                showLocation=true
                message="Complete the runtime settings, then refresh."
            } else if(latitude.toDoubleOrNull()!=null&&longitude.toDoubleOrNull()!=null){
                runDashboard()
            } else {
                locate()
            }
        }
    }

    val dashboard=section("dashboard");val conditions=section("conditions");val forecast=section("forecast");val precip=section("precipitation")
    val radar=section("radar");val meteogram=section("meteogram");val sun=section("sun");val snapshot=section("snapshot")
    val atmosphere=section("atmosphere");val modelCompare=section("model_compare")
    val committedDashboard=remember(committedDashboardJson){committedDashboardJson.dashboardStringMap()}
    val committedSettings=remember(committedSettingsJson){committedSettingsJson.dashboardStringMap()}
    val committedExecution=remember(committedDashboardJson,committedSettingsJson){
        committedDashboard.takeIf{it.isNotEmpty()}?.let{values->buildDashboardExecution(values,committedSettings.ifEmpty{currentSettings()})}
    }
    val hasEditableSettings=listOf("latitude","longitude","threshold_mm_per_hour","offline_only").any { context.settingShouldBeShown(it) }

    val rootScrollState = rememberScrollState()
    val rootModifier = if (context.presentationMode == CapabilityPresentationMode.Dashboard) {
        Modifier.fillMaxWidth().verticalScroll(rootScrollState)
    } else {
        // Presets/ODK/protocols are hosted by ExternalWorkflowActivity, which already
        // provides the vertical scroll container. Keep exactly one vertical scroller.
        Modifier.fillMaxWidth()
    }

    Column(rootModifier.padding(horizontal=18.dp,vertical=14.dp)) {
        Text("WEATHER",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        Text(if(latitude.isBlank())"Location required" else "${latitude.toDoubleOrNull()?.fmt(4)}, ${longitude.toDoubleOrNull()?.fmt(4)}",style=MaterialTheme.typography.bodyMedium)
        if(dashboard.isNotEmpty()) Text(
            "${if(dashboard["weather_dashboard_from_cache"]=="true")"Cached" else "Updated"} · ${dashboard["weather_dashboard_retrieved_time_iso"].orEmpty()}",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if (hasEditableSettings) {
                OutlinedButton(onClick={showLocation=!showLocation},enabled=!running){Text(if(showLocation)"Hide settings" else "Settings")}
            }
            if (context.settingShouldBeShown("latitude") || context.settingShouldBeShown("longitude")) {
                OutlinedButton(onClick={locate()},enabled=!running){Text("GPS")}
            }
            Button(onClick={runDashboard()},enabled=!running && latitude.toDoubleOrNull()!=null && longitude.toDoubleOrNull()!=null){Text("Refresh")}
        }
        if(showLocation && (context.settingShouldBeShown("latitude") || context.settingShouldBeShown("longitude"))) {
            Spacer(Modifier.height(10.dp))
            if(context.settingShouldBeShown("latitude")) OutlinedTextField(latitude,{latitude=it;payloadJson=""},label={Text("Latitude")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Spacer(Modifier.height(6.dp))
            if(context.settingShouldBeShown("longitude")) OutlinedTextField(longitude,{longitude=it;payloadJson=""},label={Text("Longitude")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        }
        if(showLocation && context.settingShouldBeShown("threshold_mm_per_hour")) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(rainThreshold,{rainThreshold=it.take(8);payloadJson=""},label={Text("Meaningful rain threshold (mm/h)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        }
        if(showLocation && context.settingShouldBeShown("offline_only")) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick={offlineOnly=!offlineOnly;payloadJson=""},modifier=Modifier.fillMaxWidth()) { Text(if(offlineOnly)"Cache only · on" else "Cache only · off") }
        }
        if(!context.submitsImmediately && !context.isNativePresetRun) {
            Spacer(Modifier.height(10.dp))
            WeatherPresetAuthoring(
                context=context,
                methodId=As100WeatherDashboardMethod.id,
                methodName="Weather",
                currentSettings=currentSettings()
            )
        }
        Spacer(Modifier.height(16.dp))

        if(running && dashboard.isEmpty()) {
            CircularProgressIndicator();Spacer(Modifier.height(8.dp));Text(message);Spacer(Modifier.height(420.dp))
        } else if(dashboard.isNotEmpty()) {
            CurrentHero(dashboard);Spacer(Modifier.height(10.dp));ImmediateStrip(dashboard,conditions,forecast);Spacer(Modifier.height(10.dp))

            DashboardCard("RAINFALL · NEXT 24H") {
                Text(precip["weather_precipitation_result"].orEmpty(),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                LabeledBarChart(precip["weather_precipitation_series_json"].orEmpty(),"precipitation_mm","mm",Modifier.fillMaxWidth().height(175.dp))
                precip["weather_precipitation_next_threshold_time_iso"]?.takeIf{it.isNotBlank()}?.let{CopyValue("Next meaningful rain",it)}
            }
            Spacer(Modifier.height(10.dp))

            DashboardCard("RADAR · HISTORY / NOWCAST") {
                RadarTimelinePanel(radar, Modifier.fillMaxWidth().height(320.dp))
                Spacer(Modifier.height(6.dp))
                Text("Observed history and any genuine provider-nowcast frames are radar. Model forecast precipitation is a separate product. Radar data: RainViewer.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))

            DashboardCard("HOURLY TEMPERATURE · °C") {
                LabeledLineChart(forecast["weather_forecast_series_json"].orEmpty(),"temperature_c","°C",Modifier.fillMaxWidth().height(175.dp))
            }
            Spacer(Modifier.height(10.dp))

            DashboardCard("NEXT DAYS") {
                DailyForecastStrip(forecast["weather_forecast_daily_series_json"].orEmpty())
            }
            Spacer(Modifier.height(10.dp))

            DashboardCard("METEOGRAM · NEXT 72H") {
                val series=meteogram["weather_meteogram_series_json"].orEmpty()
                Text("Temperature · °C",style=MaterialTheme.typography.labelMedium)
                LabeledLineChart(series,"temperature_c","°C",Modifier.fillMaxWidth().height(155.dp))
                Text("Pressure · hPa",style=MaterialTheme.typography.labelMedium)
                LabeledLineChart(series,"pressure_msl_hpa","hPa",Modifier.fillMaxWidth().height(145.dp))
                Text("Wind · m/s",style=MaterialTheme.typography.labelMedium)
                LabeledLineChart(series,"wind_speed_ms","m/s",Modifier.fillMaxWidth().height(145.dp))
            }
            Spacer(Modifier.height(10.dp))

            DashboardCard("SUN & DAYLIGHT") {
                Text(sun["weather_sun_result"].orEmpty(),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
                CompactMetricGrid(listOf(
                    Triple("Sunrise",sun["weather_sun_sunrise_iso"].orEmpty().substringAfter("T"),""),
                    Triple("Sunset",sun["weather_sun_sunset_iso"].orEmpty().substringAfter("T"),""),
                    Triple("Daylight",dashboardDuration(sun["weather_sun_daylight_seconds"].orEmpty()),""),
                    Triple("UV max",sun["weather_sun_uv_index_max"].orEmpty(),"")
                ))
            }
            Spacer(Modifier.height(10.dp))

            DashboardCard("DETAILED ATMOSPHERE · EXPERIMENTAL") {
                Text(atmosphere["weather_atmosphere_result"].orEmpty(),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
                CompactMetricGrid(listOf(
                    Triple("CAPE",atmosphere["weather_atmosphere_cape_jkg"].orEmpty(),"J/kg"),
                    Triple("850 hPa temp",atmosphere["weather_atmosphere_temperature_850hpa_c"].orEmpty(),"°C"),
                    Triple("850 hPa RH",atmosphere["weather_atmosphere_relative_humidity_850hpa_pct"].orEmpty(),"%"),
                    Triple("850 hPa wind",atmosphere["weather_atmosphere_wind_speed_850hpa_ms"].orEmpty(),"m/s"),
                    Triple("500 hPa temp",atmosphere["weather_atmosphere_temperature_500hpa_c"].orEmpty(),"°C"),
                    Triple("300 hPa wind",atmosphere["weather_atmosphere_wind_speed_300hpa_ms"].orEmpty(),"m/s")
                ))
            }
            Spacer(Modifier.height(10.dp))

            DashboardCard("MODEL COMPARISON · EXPERIMENTAL") {
                Text(modelCompare["weather_model_compare_result"].orEmpty(),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
                CompactMetricGrid(listOf(
                    Triple("Mean",modelCompare["weather_model_compare_temperature_mean_c"].orEmpty(),"°C"),
                    Triple("Minimum",modelCompare["weather_model_compare_temperature_min_c"].orEmpty(),"°C"),
                    Triple("Maximum",modelCompare["weather_model_compare_temperature_max_c"].orEmpty(),"°C"),
                    Triple("Members",modelCompare["weather_model_compare_member_count"].orEmpty(),"")
                ))
                Text("Ensemble spread is not a calibrated confidence interval.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))

            DashboardCard("RESEARCH WEATHER SNAPSHOT") {
                Text(snapshot["weather_snapshot_result"].orEmpty(),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
                Text(snapshot["weather_snapshot_data_class"].orEmpty().replace('_',' '),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                CompactMetricGrid(listOf(
                    Triple("Temperature",snapshot["weather_snapshot_temperature_c"].orEmpty(),"°C"),
                    Triple("Humidity",snapshot["weather_snapshot_relative_humidity_pct"].orEmpty(),"%"),
                    Triple("Pressure",snapshot["weather_snapshot_pressure_msl_hpa"].orEmpty(),"hPa"),
                    Triple("Cloud",snapshot["weather_snapshot_cloud_cover_pct"].orEmpty(),"%")
                ))
                CopyValue("Valid time",snapshot["weather_snapshot_valid_time_iso"].orEmpty())
            }
            Spacer(Modifier.height(14.dp))
            Text("Provider queries use MethodMesh's declared online-data layer. Exact requested coordinates and rounded third-party query coordinates remain distinct in provenance.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(!context.submitsImmediately) {
                Spacer(Modifier.height(12.dp))
                Button(modifier=Modifier.fillMaxWidth(),onClick={
                    committedDashboardJson=JSONObject(dashboard).toString()
                    committedSettingsJson=JSONObject(currentSettings()).toString()
                    message="Committed dashboard snapshot. Live refreshes no longer alter the frozen payload."
                }){Text(if(committedDashboardJson.isBlank())"Commit dashboard snapshot" else "Recommit dashboard snapshot")}
            }
        } else Text(message)

        committedExecution?.let{execution->
            Spacer(Modifier.height(10.dp))
            WeatherCommittedActions(
                context=context,
                label="Weather dashboard",
                result=execution,
                workingChanged=committedDashboardJson!=JSONObject(dashboard).toString(),
                onDone={onConfirmed(execution)}
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(context.stepNumber>1)OutlinedButton(onClick=onBack){Text("Back")}
            OutlinedButton(onClick=onCancel){Text("Cancel")}
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun CurrentHero(v:Map<String,String>) {
    Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(22.dp),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            val code=when(v["weather_dashboard_condition"]){"Clear"->0;"Mainly clear"->1;"Partly cloudy"->2;"Overcast"->3;"Rain"->61;"Rain showers"->80;"Thunderstorm"->95;else->2}
            WeatherGlyph(code,Modifier.width(100.dp).height(100.dp))
            Column {
                CopyValue("",v["weather_dashboard_temperature_c"].orEmpty()," °C",large=true)
                Text(v["weather_dashboard_condition"].orEmpty(),style=MaterialTheme.typography.titleLarge)
                Text(if(v["weather_dashboard_from_cache"]=="true")"CACHED WORKING RESULT" else "MODELLED CURRENT",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ImmediateStrip(dashboard:Map<String,String>,conditions:Map<String,String>,forecast:Map<String,String>) {
    DashboardCard("NOW / TODAY") {
        CompactMetricGrid(listOf(
            Triple("Feels like",conditions["weather_conditions_apparent_temperature_c"].orEmpty(),"°C"),
            Triple("High",forecast["weather_forecast_daily_high_c"].orEmpty(),"°C"),
            Triple("Low",forecast["weather_forecast_daily_low_c"].orEmpty(),"°C"),
            Triple("Rain now",dashboard["weather_dashboard_precipitation_mm"].orEmpty(),"mm"),
            Triple("Rain today",forecast["weather_forecast_daily_precipitation_mm"].orEmpty(),"mm"),
            Triple("Humidity",conditions["weather_conditions_relative_humidity_pct"].orEmpty(),"%"),
            Triple("Dew point",conditions["weather_conditions_dew_point_c"].orEmpty(),"°C"),
            Triple("Pressure",dashboard["weather_dashboard_pressure_msl_hpa"].orEmpty(),"hPa"),
            Triple("Wind",conditions["weather_conditions_wind_speed_ms"].orEmpty(),"m/s ${conditions["weather_conditions_wind_direction_compass"].orEmpty()}".trim()),
            Triple("Gust",conditions["weather_conditions_wind_gust_ms"].orEmpty(),"m/s")
        ))
        dashboard["weather_dashboard_next_rain_time_iso"]?.takeIf{it.isNotBlank()}?.let{CopyValue("Next rain",it)}
    }
}

@Composable
private fun CompactMetricGrid(items:List<Triple<String,String,String>>) {
    val visible=items.filter{it.second.isNotBlank()}
    visible.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            val left=row.getOrNull(0)
            val right=row.getOrNull(1)
            Column(Modifier.fillMaxWidth(0.48f).padding(vertical=4.dp)) {
                left?.let{(label,value,unit)->
                    Text(label.uppercase(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    CopyValue("",value,if(unit.isBlank())"" else " $unit")
                }
            }
            Column(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                right?.let{(label,value,unit)->
                    Text(label.uppercase(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    CopyValue("",value,if(unit.isBlank())"" else " $unit")
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(label:String,content:@Composable ()->Unit) {
    Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(defaultElevation=0.dp)) {
        Column(Modifier.padding(18.dp)) {Text(label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(8.dp));content()}
    }
}

private fun hasDashboardLocationPermission(context:android.content.Context):Boolean =
    ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED

private fun dashboardDuration(rawSeconds:String):String {
    val seconds=rawSeconds.toDoubleOrNull() ?: return rawSeconds
    val hours=(seconds/3600.0).toInt()
    val minutes=((seconds%3600.0)/60.0).toInt()
    return if(minutes==0) "${hours}h" else "${hours}h ${minutes}m"
}


private fun String.dashboardStringMap(): Map<String,String> {
    if (isBlank()) return emptyMap()
    return runCatching {
        val o = JSONObject(this)
        buildMap {
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                put(k, o.optString(k, ""))
            }
        }
    }.getOrDefault(emptyMap())
}
