package com.example.methodmesh.modules.astronomy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.platform.sensors.PhoneSensorRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONObject
import java.time.Instant

val ExposureLimitCapabilityScreen: CapabilityScreenSpec = object : CapabilityScreenSpec {
    override val capabilityId = As100ExposureLimitMethod.id
    override val title = "Untracked exposure limit"
    override val description = "Estimate a pixel-aware star-trail exposure limit and show the 500-rule comparator."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        AstronomyExposureScreen(context, onBack, onConfirmed, onCancel)
    }
}

object PolarAlignCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100PolarAlignMethod.id
    override val title = "Polar alignment"
    override val description = "Use either the ordinary compass or an AR sighting view along the rear-camera optical axis."

    @Composable override fun Render(context: CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit) {
        val androidContext=LocalContext.current
        val orientation=remember{PolarCameraOrientationSampler(androidContext)}
        var lat by rememberSaveable{mutableStateOf<Double?>(null)}
        var lon by rememberSaveable{mutableStateOf<Double?>(null)}
        var alt by rememberSaveable{mutableStateOf(0.0)}
        var mode by rememberSaveable{mutableStateOf("compass")}
        var resultJson by rememberSaveable(context.action.canonicalId){mutableStateOf<String?>(null)}
        var result by remember{mutableStateOf<ExecutionResult?>(null)}
        var status by rememberSaveable{mutableStateOf("Acquire location, then align the phone away from metal." )}
        var locationGranted by remember{mutableStateOf(hasLocationPermission(androidContext))}
        var cameraGranted by remember{mutableStateOf(ContextCompat.checkSelfPermission(androidContext,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
        var locationLaunchAttempted by rememberSaveable{mutableStateOf(false)}
        val locationLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { _: Map<String, Boolean> ->
            locationGranted = hasLocationPermission(androidContext)
            if (locationGranted) requestLocation(androidContext,{a,b,c->lat=a;lon=b;alt=c;status="Location acquired."},{error->status=error})
        }
        val cameraLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted:Boolean->cameraGranted=granted}
        DisposableEffect(androidContext){
            PhoneSensorRepository.start(androidContext)
            orientation.start()
            onDispose{PhoneSensorRepository.stop();orientation.stop()}
        }
        fun acquire(){
            if(!locationGranted)locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
            else requestLocation(androidContext,{a,b,c->lat=a;lon=b;alt=c;status="Location acquired."},{status=it})
        }
        LaunchedEffect(context.presentationMode, locationGranted) {
            if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && !locationLaunchAttempted) { locationLaunchAttempted=true; acquire() }
        }
        val tolerance=(context.action.settings["alignment_tolerance_deg"]?:context.action.settings["input_alignment_tolerance_deg"]?:"2").toDoubleOrNull()?:2.0
        val magneticHeading:Double? = if(mode=="ar") orientation.cameraAzimuthDegrees else PhoneSensorRepository.headingDegrees?.toDouble()
        val cameraElevation:Double? = if(mode=="ar") orientation.cameraElevationDegrees else null
        val liveValues = if(lat!=null&&lon!=null&&magneticHeading!=null) As100PolarAlignMethod.capture(lat!!,lon!!,alt,magneticHeading,tolerance,Instant.now(),cameraElevation) else null
        fun capture(){
            val a=lat;val b=lon;val heading=magneticHeading
            if(a==null||b==null){status="Acquire location first.";return}
            if(heading==null){status=if(mode=="ar")"Waiting for camera-axis orientation." else "Waiting for compass heading.";return}
            val values=As100PolarAlignMethod.capture(a,b,alt,heading,tolerance,Instant.now(),cameraElevation)
            val request=As100PolarAlignMethod.request(context.action.canonicalId,context.request.invocationContext.asMap(context.action.canonicalId)+context.action.settings,emptyList(),emptyList())
            val ex=As100PolarAlignMethod.result(request,values,context.request.invocationContext)
            status=values[PolarAlignFields.RESULT].orEmpty()
            if(context.submitsImmediately)onConfirmed(ex) else {result=ex;resultJson=JSONObject(values).toString()}
        }
        val restored=remember(resultJson){resultJson?.let(::jsonToMap)?.let{As100PolarAlignMethod.result(As100PolarAlignMethod.request(context.action.canonicalId,context.request.invocationContext.asMap(context.action.canonicalId)+context.action.settings,emptyList(),emptyList()),it,context.request.invocationContext)}}
        val captured=result?:restored
        CapabilityScreenScaffold(title,capabilityId,context,context.stepNumber>1,captured,captured?.let{mapOf(PolarAlignFields.RESULT to OutputFormatter.fields(it,false)[PolarAlignFields.RESULT]?.toString().orEmpty())}.orEmpty(),onBack,{result=null;resultJson=null},{captured?.let(onConfirmed)},onCancel){
            Text("The AR mode is intended for a phone fixed vertically to a tripod/mount. It sights along the rear camera axis; compass mode uses the conventional phone heading.")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()){
                OutlinedButton(onClick={mode="compass"},modifier=Modifier.weight(1f)){Text(if(mode=="compass")"✓ Compass" else "Compass")}
                Spacer(Modifier.padding(3.dp))
                OutlinedButton(onClick={mode="ar"},modifier=Modifier.weight(1f)){Text(if(mode=="ar")"✓ AR sighting" else "AR sighting")}
            }
            Button(onClick=::acquire,modifier=Modifier.fillMaxWidth()){Text(if(lat==null)"Use current location" else "Refresh location")}
            Text(lat?.let{"Location ${"%.5f".format(it)}, ${"%.5f".format(lon)}"}?:"No location yet",style=MaterialTheme.typography.bodySmall)
            lat?.let{Text("Celestial pole: ${if(it>=0)"true north" else "true south"}, ${"%.1f°".format(kotlin.math.abs(it))} above horizon")}
            if(mode=="ar"){
                if(!cameraGranted){Button(onClick={cameraLauncher.launch(Manifest.permission.CAMERA)},modifier=Modifier.fillMaxWidth()){Text("Allow camera for AR sighting")}}
                else{
                    Box(Modifier.fillMaxWidth().height(300.dp).background(Color.Black)){
                        SimpleCameraPreview(Modifier.fillMaxSize())
                        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(2.dp).background(Color.White.copy(alpha=0.8f)))
                        Box(Modifier.align(Alignment.Center).size(width=2.dp,height=120.dp).background(Color.White.copy(alpha=0.8f)))
                        Text("+",modifier=Modifier.align(Alignment.Center),color=Color.White,style=MaterialTheme.typography.headlineMedium)
                    }
                    Text("Camera azimuth: ${orientation.cameraAzimuthDegrees?.let{"%.1f°".format(it)}?:"—"} · elevation: ${orientation.cameraElevationDegrees?.let{"%.1f°".format(it)}?:"—"}")
                }
            }else{
                Text("Magnetic heading: ${PhoneSensorRepository.headingDegrees?.let{"%.1f°".format(it)}?:"—"}")
            }
            liveValues?.let{vals->
                Card(Modifier.fillMaxWidth().padding(top=8.dp)){
                    Column(Modifier.padding(12.dp)){
                        Text(vals[PolarAlignFields.RESULT].orEmpty(),fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                        if(mode=="ar")Text("Azimuth error ${vals[PolarAlignFields.ERROR_DEG].orEmpty()}° · altitude error ${vals[PolarAlignFields.ALT_ERROR].orEmpty().ifBlank{"—"}}°",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick=::capture,modifier=Modifier.fillMaxWidth()){Text("Capture polar alignment")}
            Text(status,style=MaterialTheme.typography.bodySmall)
        }
    }
}

object SkyTestCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SkyTestMethod.id
    override val title = "Relative sky-quality test"
    override val description = As100SkyTestMethod.descriptor.description.orEmpty()
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        AstronomySkyTestScreen(context, onBack, onConfirmed, onCancel)
    }
}

object FocusCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100FocusMethod.id
    override val title = "Telescope focus assistant"
    override val description = As100FocusMethod.descriptor.description.orEmpty()
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        AstronomyFocusScreen(context, onBack, onConfirmed, onCancel)
    }
}

object MountStabilityCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100MountStabilityMethod.id
    override val title = "Mount stability"
    override val description = As100MountStabilityMethod.descriptor.description.orEmpty()
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        AstronomyMountScreen(context, onBack, onConfirmed, onCancel)
    }
}

@Composable private fun SimpleCameraPreview(modifier: Modifier) {
    AstronomyOwnedCamera(modifier = modifier)
}

private fun requestLocation(context:Context,onSuccess:(Double,Double,Double)->Unit,onError:(String)->Unit){if(!hasLocationPermission(context)){onError("Location permission is required.");return};val token=CancellationTokenSource();LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,token.token).addOnSuccessListener{loc->if(loc==null)onError("Current GPS location unavailable.")else onSuccess(loc.latitude,loc.longitude,loc.altitude)}.addOnFailureListener{onError(it.message?:"Location failed.")}}
private fun jsonToMap(json:String):Map<String,String> = runCatching{val o=JSONObject(json.ifBlank{"{}"});buildMap{o.keys().forEach{k->put(k,o.optString(k,""))}}}.getOrDefault(emptyMap())
