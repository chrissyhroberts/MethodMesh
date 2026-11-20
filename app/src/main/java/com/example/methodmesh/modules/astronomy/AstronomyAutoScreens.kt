package com.example.methodmesh.modules.astronomy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.platform.location.OpenLocationCode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONObject

internal data class AstroLocation(val latitude: Double, val longitude: Double, val source: String)

internal fun settingsMap(json: String): Map<String,String> = runCatching {
    val o=JSONObject(json.ifBlank { "{}" }); buildMap { o.keys().forEach { k -> put(k,o.optString(k,"")) } }
}.getOrDefault(emptyMap())

internal fun upstreamValue(settings: Map<String,String>, key: String): String? {
    settings["previous_$key"]?.takeIf { it.isNotBlank() }?.let { return it }
    return settings.entries
        .filter { (k,v) -> k.matches(Regex("step_\\d+_${Regex.escape(key)}")) && v.isNotBlank() }
        .maxByOrNull { (k,_) -> k.substringAfter("step_").substringBefore('_').toIntOrNull() ?: 0 }
        ?.value
}

internal fun upstreamMap(settings: Map<String,String>, keys: List<String>): Map<String,String> =
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
    private val kind = AstronomyAutoKind.Conditions
    override val capabilityId = kind.method.id
    override val title = kind.title
    override val description = "Use local weather, supplied workflow measurements or manual offline values."

    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        AstronomyAutoScreen(kind, context, onBack, onConfirmed, onCancel)
    }
}

object DewRiskCapabilityScreen : CapabilityScreenSpec {
    private val kind = AstronomyAutoKind.Dew
    override val capabilityId = kind.method.id
    override val title = kind.title
    override val description = "Use local weather, supplied workflow measurements or manual offline values."

    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        AstronomyAutoScreen(kind, context, onBack, onConfirmed, onCancel)
    }
}

object ImagingWindowCapabilityScreen : CapabilityScreenSpec {
    private val kind = AstronomyAutoKind.Window
    override val capabilityId = kind.method.id
    override val title = kind.title
    override val description = "Plan observing windows using a supplied site or GPS and optional hourly forecast."

    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        AstronomyAutoScreen(kind, context, onBack, onConfirmed, onCancel)
    }
}
