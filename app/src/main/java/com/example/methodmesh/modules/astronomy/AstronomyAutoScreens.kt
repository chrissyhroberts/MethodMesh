package com.example.methodmesh.modules.astronomy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.modules.pluscodecapture.OpenLocationCode
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class AstroLocation(val latitude: Double, val longitude: Double, val source: String)

internal fun settingsMap(json: String): Map<String,String> = runCatching {
    val o=JSONObject(json.ifBlank { "{}" }); buildMap { o.keys().forEach { k -> put(k,o.optString(k,"")) } }
}.getOrDefault(emptyMap())

private fun upstreamValue(settings: Map<String,String>, key: String): String? {
    settings["previous_$key"]?.takeIf { it.isNotBlank() }?.let { return it }
    return settings.entries
        .filter { (k,v) -> k.matches(Regex("step_\\d+_${Regex.escape(key)}")) && v.isNotBlank() }
        .maxByOrNull { (k,_) -> k.substringAfter("step_").substringBefore('_').toIntOrNull() ?: 0 }
        ?.value
}

private fun upstreamMap(settings: Map<String,String>, keys: List<String>): Map<String,String> =
    keys.mapNotNull { key -> upstreamValue(settings,key)?.let { key to it } }.toMap()

internal fun explicitLocation(settings: Map<String,String>): AstroLocation? {
    val upstreamPlus = upstreamValue(settings,"plus_code")
    val directPlus = (settings["plus_code"] ?: settings["input_plus_code"]).orEmpty().trim()
    val plus = upstreamPlus ?: directPlus.takeIf { it.isNotBlank() }
    if (!plus.isNullOrBlank()) return runCatching { OpenLocationCode.decode(plus) }.getOrNull()?.let { AstroLocation(it.centerLatitude,it.centerLongitude,if(upstreamPlus!=null) "Plus Code from previous workflow step" else "supplied Plus Code") }

    val upstreamLat = upstreamValue(settings,"latitude")?.toDoubleOrNull()
    val upstreamLon = upstreamValue(settings,"longitude")?.toDoubleOrNull()
    if (upstreamLat!=null && upstreamLon!=null) return AstroLocation(upstreamLat,upstreamLon,"coordinates from previous workflow step")

    val lat=(settings["latitude"] ?: settings["input_latitude"])?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val lon=(settings["longitude"] ?: settings["input_longitude"])?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val explicitMode = settings["location_source"] == "manual" || settings["source_mode"] == "manual"
    return if(lat!=null && lon!=null && (explicitMode || lat!=0.0 || lon!=0.0)) AstroLocation(lat,lon,"supplied coordinates") else null
}

internal fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED

internal fun currentLocation(context: Context, ok:(AstroLocation)->Unit, fail:(String)->Unit) {
    if(!hasLocationPermission(context)){ fail("Location permission is required for automatic GPS mode."); return }
    val token=CancellationTokenSource()
    LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,token.token)
        .addOnSuccessListener { loc -> if(loc==null) fail("Current GPS position is unavailable.") else ok(AstroLocation(loc.latitude,loc.longitude,"live GPS")) }
        .addOnFailureListener { fail(it.message ?: "Location failed.") }
}

internal fun methodExecution(method: AstronomyMethodBase, context: CapabilityScreenContext, values:Map<String,String>, settings:Map<String,String>):ExecutionResult =
    method.result(method.request(context.action.canonicalId, context.request.invocationContext.asMap(context.action.canonicalId)+context.action.settings+settings, emptyList(), emptyList()), values, context.request.invocationContext)

object AstronomyConditionsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId=As100ConditionsMethod.id
    override val title="Astro conditions"
    override val description="Automatically combine GPS, Open-Meteo weather/air quality and astronomical Moon geometry. Values supplied by the same multi-step external workflow override API values; manual entry is fallback."

    @Composable override fun Render(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val app=LocalContext.current; val scope=rememberCoroutineScope()
        val initial=remember(context.action.settings){context.action.settings.mapKeys{it.key.removePrefix("input_")}}
        var settingsJson by rememberSaveable(context.action.canonicalId){mutableStateOf(JSONObject(initial).toString())}; val settings=settingsMap(settingsJson)
        var result by remember{mutableStateOf<ExecutionResult?>(null)}; var status by rememberSaveable{mutableStateOf("Resolving data source…")}; var source by rememberSaveable{mutableStateOf("auto")}; var loading by rememberSaveable{mutableStateOf(false)}; var manual by rememberSaveable{mutableStateOf(false)}; var attempted by rememberSaveable{mutableStateOf(false)}
        fun merge(values:Map<String,String>){settingsJson=JSONObject(settings.toMutableMap().apply{putAll(values)}).toString(); context.onSettingsChanged(settingsMap(settingsJson))}
        fun calculate(extra:Map<String,String> = emptyMap()){val merged=settings+extra; val values=As100ConditionsMethod.calculate(merged); val ex=methodExecution(As100ConditionsMethod,context,values,merged); result=ex; status=values[ConditionsFields.RESULT].orEmpty(); if(context.submitsImmediately)onConfirmed(ex)}
        fun fetchAt(loc:AstroLocation){loading=true;status="Fetching weather and air quality for ${loc.source}…";scope.launch{runCatching{withContext(Dispatchers.IO){AstronomyLiveData.current(app,loc.latitude,loc.longitude)}}.onSuccess{payload->val extra=mapOf("latitude" to loc.latitude.toString(),"longitude" to loc.longitude.toString(),"weather_json" to payload.weatherJson,"air_quality_json" to payload.airQualityJson,"data_source" to "api");merge(extra);source="${loc.source} + Open-Meteo";loading=false;calculate(extra)}.onFailure{loading=false;status="Live data failed: ${it.message}. Use upstream workflow values or manual fallback.";manual=true}}}
        fun auto(){
            val upstream=upstreamMap(settings,listOf("weather_json","air_quality_json","cloud_cover_pct","visibility_m","wind_kmh","wind_gust_kmh","temperature_c","relative_humidity_pct","humidity_pct","aerosol_optical_depth","pm2_5","moon_altitude_deg","moon_illumination_pct"))
            if(upstream.isNotEmpty()){source="previous workflow step";calculate(upstream+mapOf("data_source" to "upstream"));return}
            if(settings["source_mode"]=="upstream"){source="supplied upstream values";calculate(mapOf("data_source" to "upstream"));return}
            if(settings["weather_json"].orEmpty().isNotBlank()||settings["air_quality_json"].orEmpty().isNotBlank()){source="supplied API result";calculate(mapOf("data_source" to "api"));return}
            explicitLocation(settings)?.let{fetchAt(it);return};currentLocation(app,::fetchAt){status=it;manual=true}
        }
        val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(hasLocationPermission(app))auto()else{status="Location denied. Supplied Plus Code/coordinates, upstream workflow values, or manual data remain available.";manual=true}}
        fun startAuto(){if(explicitLocation(settings)!=null||settings["weather_json"].orEmpty().isNotBlank()||hasLocationPermission(app))auto()else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
        LaunchedEffect(Unit){if(!attempted){attempted=true;startAuto()}}
        CapabilityScreenScaffold(title,capabilityId,context,context.stepNumber>1,result,result?.let{mapOf(ConditionsFields.RESULT to OutputFormatter.fields(it,false)[ConditionsFields.RESULT]?.toString().orEmpty())}.orEmpty(),onBack,{result=null;startAuto()},{result?.let(onConfirmed)},onCancel){
            Text("Source: $source",style=MaterialTheme.typography.bodySmall);Text(status,style=MaterialTheme.typography.bodySmall);Spacer(Modifier.height(8.dp));Button(onClick=::startAuto,enabled=!loading,modifier=Modifier.fillMaxWidth()){Text(if(loading)"Refreshing…" else "Refresh GPS + Open-Meteo")};OutlinedButton(onClick={manual=!manual},modifier=Modifier.fillMaxWidth()){Text(if(manual)"Hide manual fallback" else "Manual fallback / inspect values")}
            if(manual){listOf("cloud_cover_pct" to "Cloud cover (%)","visibility_m" to "Visibility (m)","wind_kmh" to "Wind (km/h)","wind_gust_kmh" to "Wind gust (km/h)","temperature_c" to "Temperature (°C)","relative_humidity_pct" to "Relative humidity (%)","aerosol_optical_depth" to "Aerosol optical depth","pm2_5" to "PM2.5").forEach{(k,l)->OutlinedTextField(settings[k].orEmpty(),{merge(mapOf(k to it))},label={Text(l)},modifier=Modifier.fillMaxWidth(),singleLine=true)};Button(onClick={source="manual values";calculate(mapOf("data_source" to "manual"))},modifier=Modifier.fillMaxWidth()){Text("Calculate from these values")}}
        }
    }
}

object DewRiskCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId=As100DewRiskMethod.id
    override val title="Dew risk"
    override val description="Fetch local weather automatically from GPS. If temperature/humidity were supplied by an earlier step in the same multi-step external workflow (including sensor.read/AHT20), use those instead. Manual values remain an offline fallback."
    @Composable override fun Render(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val app=LocalContext.current; val scope=rememberCoroutineScope();val initial=remember(context.action.settings){context.action.settings.mapKeys{it.key.removePrefix("input_")}};var settingsJson by rememberSaveable(context.action.canonicalId){mutableStateOf(JSONObject(initial).toString())};val settings=settingsMap(settingsJson);var result by remember{mutableStateOf<ExecutionResult?>(null)};var status by rememberSaveable{mutableStateOf("Resolving dew data…")};var source by rememberSaveable{mutableStateOf("auto")};var manual by rememberSaveable{mutableStateOf(false)};var attempted by rememberSaveable{mutableStateOf(false)}
        fun merge(v:Map<String,String>){settingsJson=JSONObject(settings.toMutableMap().apply{putAll(v)}).toString();context.onSettingsChanged(settingsMap(settingsJson))}
        fun calc(extra:Map<String,String> = emptyMap()){val merged=settings+extra;val values=As100DewRiskMethod.calculate(merged);val ex=methodExecution(As100DewRiskMethod,context,values,merged);result=ex;status=values[DewFields.RESULT].orEmpty().ifBlank{values[DewFields.ERROR].orEmpty()};if(context.submitsImmediately)onConfirmed(ex)}
        fun fetch(loc:AstroLocation){status="Fetching weather for ${loc.source}…";scope.launch{runCatching{withContext(Dispatchers.IO){AstronomyLiveData.current(app,loc.latitude,loc.longitude)}}.onSuccess{p->val e=mapOf("latitude" to loc.latitude.toString(),"longitude" to loc.longitude.toString(),"weather_json" to p.weatherJson,"data_source" to "api");merge(e);source="${loc.source} + Open-Meteo";calc(e)}.onFailure{status="Weather unavailable: ${it.message}. Use upstream workflow values or manual fallback.";manual=true}}}
        fun auto(){
            val upstream=upstreamMap(settings,listOf("temperature_c","relative_humidity_pct","humidity_pct","dew_point_c","use_supplied_dew_point","sensor_profile","weather_json"))
            val hasUpstreamTemp=upstream["temperature_c"].orEmpty().isNotBlank() && (upstream["relative_humidity_pct"].orEmpty().isNotBlank()||upstream["humidity_pct"].orEmpty().isNotBlank())
            if(hasUpstreamTemp){source=if(upstream["sensor_profile"]=="aht20")"AHT20 from previous workflow step" else "previous workflow measurement";calc(upstream+mapOf("data_source" to "upstream"));return}
            if(settings["source_mode"]=="upstream"){source="supplied upstream values";calc(mapOf("data_source" to "upstream"));return}
            if(settings["weather_json"].orEmpty().isNotBlank()){source="supplied weather result";calc(mapOf("data_source" to "api"));return}
            explicitLocation(settings)?.let{fetch(it);return};currentLocation(app,::fetch){status=it;manual=true}
        }
        val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(hasLocationPermission(app))auto()else{status="Location denied. Upstream workflow values or manual fallback are still available.";manual=true}}
        fun start(){if(explicitLocation(settings)!=null||settings["temperature_c"].orEmpty().isNotBlank()||settings["weather_json"].orEmpty().isNotBlank()||hasLocationPermission(app))auto()else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
        LaunchedEffect(Unit){if(!attempted){attempted=true;start()}}
        CapabilityScreenScaffold(title,capabilityId,context,context.stepNumber>1,result,result?.let{mapOf(DewFields.RESULT to OutputFormatter.fields(it,false)[DewFields.RESULT]?.toString().orEmpty())}.orEmpty(),onBack,{result=null;start()},{result?.let(onConfirmed)},onCancel){Text("Source: $source",style=MaterialTheme.typography.bodySmall);Text(status,style=MaterialTheme.typography.bodySmall);Spacer(Modifier.height(8.dp));Button(onClick=::start,modifier=Modifier.fillMaxWidth()){Text("Refresh automatic source")};OutlinedButton(onClick={manual=!manual},modifier=Modifier.fillMaxWidth()){Text(if(manual)"Hide manual fallback" else "Manual fallback")};if(manual){OutlinedTextField(settings["temperature_c"].orEmpty(),{merge(mapOf("temperature_c" to it))},label={Text("Temperature (°C)")},modifier=Modifier.fillMaxWidth());OutlinedTextField((settings["relative_humidity_pct"]?:settings["humidity_pct"]).orEmpty(),{merge(mapOf("relative_humidity_pct" to it))},label={Text("Relative humidity (%)")},modifier=Modifier.fillMaxWidth());Button(onClick={source="manual";calc(mapOf("data_source" to "manual"))},modifier=Modifier.fillMaxWidth()){Text("Calculate dew risk")}}}
    }
}

object ImagingWindowCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId=As100ImagingWindowMethod.id
    override val title="Tonight's imaging window"
    override val description="Resolve location from a supplied Plus Code/coordinates or live GPS, fetch the hourly forecast automatically, then score the coming night."
    @Composable override fun Render(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val app=LocalContext.current;val scope=rememberCoroutineScope();val initial=remember(context.action.settings){context.action.settings.mapKeys{it.key.removePrefix("input_")}};var settingsJson by rememberSaveable(context.action.canonicalId){mutableStateOf(JSONObject(initial).toString())};val settings=settingsMap(settingsJson);var result by remember{mutableStateOf<ExecutionResult?>(null)};var status by rememberSaveable{mutableStateOf("Resolving tonight…")};var source by rememberSaveable{mutableStateOf("auto")};var attempted by rememberSaveable{mutableStateOf(false)};var manualLocation by rememberSaveable{mutableStateOf(false)}
        fun merge(v:Map<String,String>){settingsJson=JSONObject(settings.toMutableMap().apply{putAll(v)}).toString();context.onSettingsChanged(settingsMap(settingsJson))}
        fun calc(extra:Map<String,String> = emptyMap()){val merged=settings+extra;val values=As100ImagingWindowMethod.calculate(merged);val ex=methodExecution(As100ImagingWindowMethod,context,values,merged);result=ex;status=values[ImagingWindowFields.RESULT].orEmpty().ifBlank{values[ImagingWindowFields.ERROR].orEmpty()};if(context.submitsImmediately)onConfirmed(ex)}
        fun at(loc:AstroLocation){val base=mapOf("latitude" to loc.latitude.toString(),"longitude" to loc.longitude.toString(),"data_source" to "api");val upstreamForecast=upstreamValue(settings,"forecast_json");val suppliedForecast=upstreamForecast ?: settings["forecast_json"].orEmpty().takeIf{it.isNotBlank()};if(!suppliedForecast.isNullOrBlank()){val e=base+mapOf("forecast_json" to suppliedForecast);merge(e);source="${loc.source} + supplied forecast";calc(e);return};status="Fetching hourly forecast for ${loc.source}…";scope.launch{runCatching{withContext(Dispatchers.IO){AstronomyLiveData.hourly(app,loc.latitude,loc.longitude)}}.onSuccess{forecast->val e=base+mapOf("forecast_json" to forecast);merge(e);source="${loc.source} + Open-Meteo hourly";calc(e)}.onFailure{status="Hourly forecast unavailable: ${it.message}. Location is retained; you can still calculate astronomy-only windows.";merge(base);source=loc.source;calc(base)}}}
        fun auto(){explicitLocation(settings)?.let{at(it);return};currentLocation(app,::at){status=it;manualLocation=true}}
        val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(hasLocationPermission(app))auto()else{status="Location denied. Supply a Plus Code or enter coordinates.";manualLocation=true}}
        fun start(){if(explicitLocation(settings)!=null||hasLocationPermission(app))auto()else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
        LaunchedEffect(Unit){if(!attempted){attempted=true;start()}}
        CapabilityScreenScaffold(title,capabilityId,context,context.stepNumber>1,result,result?.let{mapOf(ImagingWindowFields.RESULT to OutputFormatter.fields(it,false)[ImagingWindowFields.RESULT]?.toString().orEmpty())}.orEmpty(),onBack,{result=null;start()},{result?.let(onConfirmed)},onCancel){Text("Source: $source",style=MaterialTheme.typography.bodySmall);Text(status,style=MaterialTheme.typography.bodySmall);Spacer(Modifier.height(8.dp));Button(onClick=::start,modifier=Modifier.fillMaxWidth()){Text("Refresh location + forecast")};OutlinedTextField(settings["plus_code"].orEmpty(),{merge(mapOf("plus_code" to it))},label={Text("Plus Code (optional supplied location)")},modifier=Modifier.fillMaxWidth(),singleLine=true);Row(Modifier.fillMaxWidth()){OutlinedTextField(settings["target_ra_hours"].orEmpty(),{merge(mapOf("target_ra_hours" to it,"use_target" to "true"))},label={Text("Target RA h (optional)")},modifier=Modifier.weight(1f).padding(end=3.dp));OutlinedTextField(settings["target_dec_deg"].orEmpty(),{merge(mapOf("target_dec_deg" to it,"use_target" to "true"))},label={Text("Dec °")},modifier=Modifier.weight(1f).padding(start=3.dp))};OutlinedButton(onClick={manualLocation=!manualLocation},modifier=Modifier.fillMaxWidth()){Text(if(manualLocation)"Hide coordinate fallback" else "Manual coordinate fallback")};if(manualLocation){OutlinedTextField(settings["latitude"].orEmpty(),{merge(mapOf("latitude" to it))},label={Text("Latitude")},modifier=Modifier.fillMaxWidth());OutlinedTextField(settings["longitude"].orEmpty(),{merge(mapOf("longitude" to it))},label={Text("Longitude")},modifier=Modifier.fillMaxWidth())};Button(onClick={explicitLocation(settings)?.let{at(it)}?:start()},modifier=Modifier.fillMaxWidth()){Text("Plan tonight")}}
    }
}
