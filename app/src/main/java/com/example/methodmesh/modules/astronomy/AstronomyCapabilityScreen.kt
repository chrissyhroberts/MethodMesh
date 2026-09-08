package com.example.methodmesh.modules.astronomy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.Executors

private data class AstroInput(val key: String, val label: String, val multiline: Boolean = false, val boolean: Boolean = false)

private class AstronomyCalculationScreen(
    override val capabilityId: String,
    override val title: String,
    override val description: String,
    private val method: AstronomyMethodBase,
    private val inputs: List<AstroInput>
) : CapabilityScreenSpec {
    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val initial = remember(context.action.settings) {
            inputs.associate { it.key to (context.action.settings[it.key] ?: context.action.settings["input_${it.key}"].orEmpty()) }
        }
        var settingsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf(JSONObject(initial).toString()) }
        var resultJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Ready.") }
        val settings = remember(settingsJson) { jsonToMap(settingsJson) }

        fun update(key: String, value: String) {
            settingsJson = JSONObject(settings.toMutableMap().apply { put(key, value) }).toString()
        }

        LaunchedEffect(settingsJson) { context.onSettingsChanged(settings) }

        fun run() {
            val request = method.request(
                action = context.action.canonicalId,
                context = context.request.invocationContext.asMap(context.action.canonicalId) + context.action.settings + settings,
                signals = emptyList(), inputs = emptyList()
            )
            val values = method.calculate(settings)
            val execution = method.result(request, values, context.request.invocationContext)
            status = values[method.mainField].orEmpty().ifBlank { values.values.firstOrNull { it.isNotBlank() }.orEmpty() }
            if (!context.submitsImmediately) {
                result = execution
                resultJson = JSONObject(values).toString()
            }
            if (method.id == As100SessionMethod.id && settings["save_session"]?.toBooleanStrictOrNull() == true && values[SessionFields.JSON].orEmpty().isNotBlank()) {
                runCatching { AstronomyRepository(androidContext).saveSession(JSONObject(values[SessionFields.JSON])) }
            }
            if (context.submitsImmediately) onConfirmed(execution)
        }

        val restored = remember(resultJson) {
            resultJson?.let(::jsonToMap)?.let { values ->
                method.result(
                    method.request(context.action.canonicalId, context.request.invocationContext.asMap(context.action.canonicalId) + context.action.settings + settings, emptyList(), emptyList()),
                    values, context.request.invocationContext
                )
            }
        }
        val captured = result ?: restored

        LaunchedEffect(context.presentationMode, launched) {
            if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && !launched) {
                launched = true
                run()
            }
        }

        CapabilityScreenScaffold(
            title = title, capabilityId = capabilityId, context = context, canGoBack = context.stepNumber > 1,
            capturedResult = captured,
            resultPreview = captured?.let { ex -> mapOf(method.mainField to OutputFormatter.fields(ex, false)[method.mainField]?.toString().orEmpty()) }.orEmpty(),
            onBack = onBack, onRetry = { result = null; resultJson = null; status = "Ready." }, onConfirm = { captured?.let(onConfirmed) }, onCancel = onCancel
        ) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            inputs.forEach { spec ->
                if (context.settingShouldBeShown(spec.key)) {
                    if (spec.boolean) {
                        val enabled = settings[spec.key]?.toBooleanStrictOrNull() ?: false
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(spec.label, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { update(spec.key, (!enabled).toString()) }) { Text(if (enabled) "On" else "Off") }
                        }
                    } else {
                        OutlinedTextField(
                            value = settings[spec.key].orEmpty(), onValueChange = { update(spec.key, it) },
                            label = { Text(spec.label) }, modifier = Modifier.fillMaxWidth(),
                            minLines = if (spec.multiline) 3 else 1, maxLines = if (spec.multiline) 8 else 1,
                            singleLine = !spec.multiline
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = ::run, modifier = Modifier.fillMaxWidth()) { Text(if (captured == null) "Calculate" else "Calculate again") }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// Location/API-backed conditions, dew and imaging-window screens live in AstronomyAutoScreens.kt.
// The generic calculation screen remains for purely local/manual utilities.

// Interactive image-scale and resumable session screens live in AstronomyInteractiveScreens.kt.

val ExposureLimitCapabilityScreen: CapabilityScreenSpec = AstronomyCalculationScreen(
    As100ExposureLimitMethod.id,
    "Untracked exposure limit",
    "Estimate a pixel-aware star-trail exposure limit and show the 500-rule comparator.",
    As100ExposureLimitMethod,
    listOf(
        AstroInput("focal_length_mm", "Focal length (mm)"),
        AstroInput("pixel_size_micron", "Pixel size (µm)"),
        AstroInput("declination_deg", "Target declination (°)"),
        AstroInput("max_trail_pixels", "Maximum acceptable trail (px)"),
        AstroInput("crop_factor", "Crop factor")
    )
)

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
    override val capabilityId=As100SkyTestMethod.id
    override val title="Relative sky-quality test"
    override val description="Compare the same phone/camera and a bright point source with a clear-night reference you saved yourself."

    @Composable override fun Render(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val androidContext=LocalContext.current
        val repo=remember{AstronomyRepository(androidContext)}
        var cameraGranted by remember{mutableStateOf(ContextCompat.checkSelfPermission(androidContext,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
        var running by rememberSaveable{mutableStateOf(false)}
        var summaryText by rememberSaveable{mutableStateOf("Aim the rear camera at a bright star or similarly compact point source and keep the phone fixed during the sample.")}
        var resultJson by rememberSaveable(context.action.canonicalId){mutableStateOf<String?>(null)}
        var result by remember{mutableStateOf<ExecutionResult?>(null)}
        var lastSummary by remember{mutableStateOf<StarTestSummary?>(null)}
        var scores by remember{mutableStateOf<Map<String,Int?>>(emptyMap())}
        var calibration by remember{mutableStateOf(repo.loadCalibration())}
        val analyzer=remember{StarFrameAnalyzer()}
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted: Boolean -> cameraGranted = granted }
        val duration=(context.action.settings["duration_seconds"]?:context.action.settings["input_duration_seconds"]?:"20").toLongOrNull()?.coerceIn(3,60)?:20
        val minimumFrames=(context.action.settings["minimum_frames"]?:context.action.settings["input_minimum_frames"]?:"30").toIntOrNull()?.coerceIn(10,300)?:30

        fun finish(){
            val summary=analyzer.summary(minimumFrames)
            lastSummary=summary
            calibration=repo.loadCalibration()
            val relative=repo.scoreAgainstCalibration(summary,calibration)
            scores=relative
            val values=As100SkyTestMethod.capture(summary,relative,calibration)
            val ex=As100SkyTestMethod.result(As100SkyTestMethod.request(context.action.canonicalId,context.request.invocationContext.asMap(context.action.canonicalId)+context.action.settings,emptyList(),emptyList()),values,context.request.invocationContext)
            summaryText=values[SkyTestFields.RESULT].orEmpty()
            if(context.submitsImmediately)onConfirmed(ex) else {result=ex;resultJson=JSONObject(values).toString()}
        }
        LaunchedEffect(running){if(running){analyzer.clear();lastSummary=null;scores=emptyMap();delay(duration*1000);running=false;finish()}}
        val restored=remember(resultJson){resultJson?.let(::jsonToMap)?.let{As100SkyTestMethod.result(As100SkyTestMethod.request(context.action.canonicalId,context.request.invocationContext.asMap(context.action.canonicalId)+context.action.settings,emptyList(),emptyList()),it,context.request.invocationContext)}}
        val captured=result?:restored
        CapabilityScreenScaffold(title,capabilityId,context,context.stepNumber>1,captured,captured?.let{mapOf(SkyTestFields.RESULT to OutputFormatter.fields(it,false)[SkyTestFields.RESULT]?.toString().orEmpty())}.orEmpty(),onBack,{result=null;resultJson=null;lastSummary=null;scores=emptyMap()},{captured?.let(onConfirmed)},onCancel){
            Text("What this does",fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
            Text("It measures relative centroid movement, point-source width (FWHM), contrast, background luma and scintillation. Those are phone-camera proxies. It does not report calibrated mag/arcsec² or arcsecond seeing.",style=MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("For useful comparisons, use the same phone, rear camera, similar point source and similar mounting/geometry each time. Auto-exposure/focus behaviour can still affect comparability.",style=MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            calibration?.let{
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(12.dp)){
                        Text("CLEAR-NIGHT REFERENCE SAVED",style=MaterialTheme.typography.labelLarge)
                        Text(it.savedAtIso,style=MaterialTheme.typography.bodySmall)
                        if(it.note.isNotBlank())Text(it.note,style=MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }?:Text("No clear-night reference yet. Run the test on a night you judge genuinely good, then save that completed sample as the reference.",style=MaterialTheme.typography.bodySmall)

            if(!cameraGranted){
                Button(onClick={launcher.launch(Manifest.permission.CAMERA)},modifier=Modifier.fillMaxWidth()){Text("Allow camera")}
            }else{
                StarCamera(analyzer,Modifier.fillMaxWidth().height(280.dp),"rear")
                Spacer(Modifier.height(8.dp))
                Button(onClick={running=true},enabled=!running,modifier=Modifier.fillMaxWidth()){Text(if(running)"Sampling…" else "Run ${duration}s sky test")}
            }
            Text(summaryText,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=6.dp))
            if(scores.isNotEmpty()){
                Card(Modifier.fillMaxWidth().padding(top=8.dp)){
                    Column(Modifier.padding(12.dp)){
                        Text("RELATIVE TO REFERENCE",style=MaterialTheme.typography.labelLarge)
                        Text("Overall ${scores["overall"]?.let{"$it%"}?:"—"}",style=MaterialTheme.typography.headlineSmall,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("Stability ${scores["stability"]?.let{"$it%"}?:"—"} · Transparency ${scores["transparency"]?.let{"$it%"}?:"—"}")
                        Text("Darkness ${scores["darkness"]?.let{"$it%"}?:"—"} · Point width ${scores["focus"]?.let{"$it%"}?:"—"}")
                    }
                }
            }
            lastSummary?.takeIf{it.detectionCount>0}?.let{summary->
                OutlinedButton(onClick={
                    repo.saveCalibration(summary,context.action.settings["calibration_note"].orEmpty())
                    calibration=repo.loadCalibration()
                    summaryText="Saved this completed sample as the clear-night reference. Future results are scored relative to it."
                },modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("Save this run as CLEAR-NIGHT reference")}
            }
            if(calibration!=null){
                OutlinedButton(onClick={repo.clearCalibration();calibration=null;scores=emptyMap();summaryText="Clear-night reference removed."},modifier=Modifier.fillMaxWidth()){Text("Clear saved reference")}
            }
        }
    }
}

object FocusCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId=As100FocusMethod.id
    override val title="Telescope focus assistant"
    override val description="Measure a bright point source through the telescope/eyepiece. Lower rolling-median FWHM is better."

    @Composable override fun Render(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val androidContext=LocalContext.current
        var granted by remember{mutableStateOf(ContextCompat.checkSelfPermission(androidContext,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
        var cameraFacing by rememberSaveable(context.action.canonicalId){mutableStateOf((context.action.settings["camera_facing"]?:context.action.settings["input_camera_facing"]?:"rear").lowercase())}
        val averagingFrames=(context.action.settings["sample_frames"]?:context.action.settings["input_sample_frames"]?:"30").toIntOrNull()?.coerceIn(5,200)?:30
        var currentFrame by rememberSaveable{mutableStateOf<Double?>(null)}
        var rollingMedian by rememberSaveable{mutableStateOf<Double?>(null)}
        var best by rememberSaveable{mutableStateOf(Double.POSITIVE_INFINITY)}
        var resultJson by rememberSaveable(context.action.canonicalId){mutableStateOf<String?>(null)}
        var result by remember{mutableStateOf<ExecutionResult?>(null)}
        val handler=remember{Handler(Looper.getMainLooper())}
        val rolling=remember(averagingFrames){java.util.ArrayDeque<Double>()}
        val analyzer=remember(averagingFrames){StarFrameAnalyzer(onMetric={m->
            if(m.detected&&m.fwhmPx.isFinite())handler.post{
                currentFrame=m.fwhmPx
                rolling.addLast(m.fwhmPx)
                while(rolling.size>averagingFrames)rolling.removeFirst()
                val med=rolling.sorted().let{v->if(v.isEmpty())Double.NaN else if(v.size%2==1)v[v.size/2] else (v[v.size/2-1]+v[v.size/2])/2.0}
                if(med.isFinite()){
                    rollingMedian=med
                    if(med<best)best=med
                }
            }
        })}
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { permissionGranted: Boolean -> granted = permissionGranted }

        fun reset(){
            rolling.clear();currentFrame=null;rollingMedian=null;best=Double.POSITIVE_INFINITY;analyzer.clear();result=null;resultJson=null
        }
        fun capture(){
            val summary=analyzer.summary(10)
            val current=rollingMedian?:summary.medianFwhmPx
            val b=best.takeIf{it.isFinite()}?:current
            val values=As100FocusMethod.capture(summary,b,current,cameraFacing,averagingFrames)
            val ex=As100FocusMethod.result(As100FocusMethod.request(context.action.canonicalId,context.request.invocationContext.asMap(context.action.canonicalId)+context.action.settings+mapOf("camera_facing" to cameraFacing,"sample_frames" to averagingFrames.toString()),emptyList(),emptyList()),values,context.request.invocationContext)
            if(context.submitsImmediately)onConfirmed(ex) else {result=ex;resultJson=JSONObject(values).toString()}
        }
        LaunchedEffect(cameraFacing){context.onSettingsChanged(context.action.settings+mapOf("camera_facing" to cameraFacing,"sample_frames" to averagingFrames.toString()));reset()}
        val restored=remember(resultJson){resultJson?.let(::jsonToMap)?.let{As100FocusMethod.result(As100FocusMethod.request(context.action.canonicalId,context.request.invocationContext.asMap(context.action.canonicalId)+context.action.settings,emptyList(),emptyList()),it,context.request.invocationContext)}}
        val captured=result?:restored
        CapabilityScreenScaffold(title,capabilityId,context,context.stepNumber>1,captured,captured?.let{mapOf(FocusFields.RESULT to OutputFormatter.fields(it,false)[FocusFields.RESULT]?.toString().orEmpty())}.orEmpty(),onBack,{reset()},{captured?.let(onConfirmed)},onCancel){
            Text("Use this only when the selected phone camera is optically coupled to an adjustable telescope/eyepiece or other optical train. The number is relative, not an arcsecond seeing measurement.",style=MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text("Camera",fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth()){
                OutlinedButton(onClick={cameraFacing="rear"},modifier=Modifier.weight(1f)){Text(if(cameraFacing=="rear")"✓ Rear" else "Rear")}
                Spacer(Modifier.padding(3.dp))
                OutlinedButton(onClick={cameraFacing="front"},modifier=Modifier.weight(1f)){Text(if(cameraFacing=="front")"✓ Front" else "Front")}
            }
            Text("Rolling median over $averagingFrames detected frames suppresses seeing/noise spikes.",style=MaterialTheme.typography.bodySmall)
            if(!granted){
                Button(onClick={launcher.launch(Manifest.permission.CAMERA)},modifier=Modifier.fillMaxWidth()){Text("Allow camera")}
            }else{
                StarCamera(analyzer,Modifier.fillMaxWidth().height(280.dp),cameraFacing)
                Text("Current frame: ${currentFrame?.let{"%.2f px".format(it)}?:"—"}")
                Text("Rolling median: ${rollingMedian?.let{"%.2f px".format(it)}?:"—"}",fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Best rolling median: ${best.takeIf{it.isFinite()}?.let{"%.2f px".format(it)}?:"—"}")
                Row(Modifier.fillMaxWidth()){
                    OutlinedButton(onClick=::reset,modifier=Modifier.weight(1f)){Text("Reset best")}
                    Spacer(Modifier.padding(3.dp))
                    Button(onClick=::capture,modifier=Modifier.weight(1f)){Text("Capture focus")}
                }
            }
        }
    }
}

object MountStabilityCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId=As100MountStabilityMethod.id;override val title="Mount stability";override val description="Put the phone firmly on the telescope/tripod and measure vibration."
    @Composable override fun Render(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){val androidContext=LocalContext.current;val sampler=remember{MountStabilitySampler(androidContext)};var running by rememberSaveable{mutableStateOf(false)};var status by rememberSaveable{mutableStateOf("Ready. Avoid touching the mount during the baseline test.")};var resultJson by rememberSaveable(context.action.canonicalId){mutableStateOf<String?>(null)};var result by remember{mutableStateOf<ExecutionResult?>(null)};val duration=(context.action.settings["duration_seconds"]?:"10").toLongOrNull()?.coerceIn(3,60)?:10;DisposableEffect(Unit){onDispose{sampler.stop()}}
        fun finish(){sampler.stop();val values=As100MountStabilityMethod.capture(sampler.summary());val ex=As100MountStabilityMethod.result(As100MountStabilityMethod.request(context.action.canonicalId,context.request.invocationContext.asMap(context.action.canonicalId)+context.action.settings,emptyList(),emptyList()),values,context.request.invocationContext);status=values[MountFields.RESULT].orEmpty();if(context.submitsImmediately)onConfirmed(ex) else {result=ex;resultJson=JSONObject(values).toString()}};LaunchedEffect(running){if(running){sampler.start();delay(duration*1000);running=false;finish()}}
        val restored=remember(resultJson){resultJson?.let(::jsonToMap)?.let{As100MountStabilityMethod.result(As100MountStabilityMethod.request(context.action.canonicalId,context.request.invocationContext.asMap(context.action.canonicalId)+context.action.settings,emptyList(),emptyList()),it,context.request.invocationContext)}};val captured=result?:restored;CapabilityScreenScaffold(title,capabilityId,context,context.stepNumber>1,captured,captured?.let{mapOf(MountFields.RESULT to OutputFormatter.fields(it,false)[MountFields.RESULT]?.toString().orEmpty())}.orEmpty(),onBack,{result=null;resultJson=null},{captured?.let(onConfirmed)},onCancel){Text("Baseline: leave the mount alone. For a tap test, start the run and give the rig one light tap immediately after starting.");Spacer(Modifier.height(8.dp));Button(onClick={running=true},enabled=!running,modifier=Modifier.fillMaxWidth()){Text(if(running)"Measuring…" else "Measure ${duration}s")};Text(status,style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable private fun StarCamera(analyzer:StarFrameAnalyzer,modifier:Modifier,cameraFacing:String="rear"){val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current;val executor=remember{Executors.newSingleThreadExecutor()};var previewView by remember{mutableStateOf<PreviewView?>(null)};var provider by remember{mutableStateOf<ProcessCameraProvider?>(null)}
    AndroidView(factory={ctx->PreviewView(ctx).also{previewView=it}},modifier=modifier.background(Color.Black));LaunchedEffect(previewView,lifecycle,cameraFacing){val view=previewView?:return@LaunchedEffect;val future=ProcessCameraProvider.getInstance(context);future.addListener({runCatching{val p=future.get();provider=p;val preview=Preview.Builder().build().also{it.setSurfaceProvider(view.surfaceProvider)};val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also{it.setAnalyzer(executor,analyzer)};val selector=if(cameraFacing=="front")CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA;p.unbindAll();p.bindToLifecycle(lifecycle,selector,preview,analysis)}},ContextCompat.getMainExecutor(context))};DisposableEffect(Unit){onDispose{provider?.unbindAll();executor.shutdown()}}
}

@Composable private fun SimpleCameraPreview(modifier:Modifier){
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    var previewView by remember{mutableStateOf<PreviewView?>(null)}
    var provider by remember{mutableStateOf<ProcessCameraProvider?>(null)}
    AndroidView(factory={ctx->PreviewView(ctx).also{previewView=it}},modifier=modifier)
    LaunchedEffect(previewView,lifecycle){
        val view=previewView?:return@LaunchedEffect
        val future=ProcessCameraProvider.getInstance(context)
        future.addListener({runCatching{
            val p=future.get();provider=p
            val preview=Preview.Builder().build().also{it.setSurfaceProvider(view.surfaceProvider)}
            p.unbindAll();p.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,preview)
        }},ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(Unit){onDispose{provider?.unbindAll()}}
}

private fun requestLocation(context:Context,onSuccess:(Double,Double,Double)->Unit,onError:(String)->Unit){if(!hasLocationPermission(context)){onError("Location permission is required.");return};val token=CancellationTokenSource();LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,token.token).addOnSuccessListener{loc->if(loc==null)onError("Current GPS location unavailable.")else onSuccess(loc.latitude,loc.longitude,loc.altitude)}.addOnFailureListener{onError(it.message?:"Location failed.")}}
private fun jsonToMap(json:String):Map<String,String> = runCatching{val o=JSONObject(json.ifBlank{"{}"});buildMap{o.keys().forEach{k->put(k,o.optString(k,""))}}}.getOrDefault(emptyMap())
