package com.example.methodmesh.modules.geocaching

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.location.Location
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ExternalActionRequest
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

internal fun ExternalActionRequest.setting(key:String):String? = (settings[key] ?: settings["input_$key"])?.takeIf { it.isNotBlank() }

internal fun commitResult(
    context: CapabilityScreenContext,
    method: GeocachingMethodBase,
    base: Map<String,String>,
    onConfirmed: (ExecutionResult)->Unit
): ExecutionResult {
    val values = method.captured(base)
    val request = method.request(
        action = context.action.canonicalId,
        context = context.request.invocationContext.asMap(method.id) + context.action.settings,
        signals = emptyList(),
        inputs = emptyList()
    )
    return method.result(request, values, context.request.invocationContext).also {
        if (context.submitsImmediately) onConfirmed(it)
    }
}

internal fun commitRejected(
    context: CapabilityScreenContext,
    method: GeocachingMethodBase,
    message: String,
    onConfirmed: (ExecutionResult)->Unit
): ExecutionResult {
    val request = method.request(context.action.canonicalId, context.request.invocationContext.asMap(method.id)+context.action.settings, emptyList(), emptyList())
    return method.result(request, method.rejected(message), context.request.invocationContext).also {
        if (context.submitsImmediately) onConfirmed(it)
    }
}

@Composable
internal fun MethodSurface(
    title:String,
    subtitle:String,
    context:CapabilityScreenContext,
    onBack:()->Unit,
    onCancel:()->Unit,
    committed:Boolean,
    onDone:(()->Unit)?=null,
    body:@Composable ColumnScope.()->Unit
){
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
                if(context.stepNumber>1) TextButton(onClick=onBack){Text("Back")}
                Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                if(!context.submitsImmediately) TextButton(onClick=onCancel){Text("Cancel")}
            }
            HorizontalDivider()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=body)
            if(committed && !context.submitsImmediately && onDone!=null){
                Surface(tonalElevation=3.dp){Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Text("Committed · result frozen",Modifier.weight(1f),fontWeight=FontWeight.SemiBold);Button(onClick=onDone){Text("Done")}}}
            }
        }
    }
}

@Composable internal fun HeroPanel(content:@Composable ColumnScope.()->Unit){Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.48f)),shape=RoundedCornerShape(24.dp)){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}}
@Composable internal fun SectionTitle(text:String){Text(text,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary)}
@Composable internal fun StatTile(value:String,label:String,modifier:Modifier=Modifier){Card(modifier){Column(Modifier.padding(12.dp)){Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);Text(label,style=MaterialTheme.typography.bodySmall)}}}
@Composable internal fun DashboardAction(title:String,subtitle:String,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick)){Column(Modifier.padding(14.dp)){Text(title,fontWeight=FontWeight.Bold);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
@Composable internal fun MiniAction(label:String,modifier:Modifier=Modifier,onClick:()->Unit){OutlinedButton(onClick=onClick,modifier=modifier.height(64.dp)){Text(label)}}
@Composable internal fun PrimaryAction(label:String,onClick:()->Unit){Button(onClick=onClick,modifier=Modifier.fillMaxWidth()){Text(label)}}

@Composable
internal fun CopyableValue(value:String,label:String?=null){
    val context=LocalContext.current
    Card(Modifier.fillMaxWidth().clickable{
        val cm=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label?:"MethodMesh",value));Toast.makeText(context,"Copied",Toast.LENGTH_SHORT).show()
    },colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f))){
        Column(Modifier.padding(horizontal=12.dp,vertical=9.dp)){label?.let{Text(it,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Text(value,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.SemiBold)}
    }
}

@Composable internal fun CommitButton(committed:Boolean,onCommit:()->Unit){if(!committed)Button(onClick=onCommit,modifier=Modifier.fillMaxWidth()){Text("Commit")}else OutlinedButton(onClick=onCommit,modifier=Modifier.fillMaxWidth()){Text("Recommit current result")}}

@Composable internal fun Segmented(options:List<Pair<String,String>>,selected:String,onSelected:(String)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){options.forEach{(id,label)->if(selected==id)Button(onClick={onSelected(id)},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=6.dp)){Text(label,maxLines=1)}else OutlinedButton(onClick={onSelected(id)},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=6.dp)){Text(label,maxLines=1)}}}}

@Composable internal fun SliderCard(title:String,valueLabel:String,value:Float,range:ClosedFloatingPointRange<Float>,onValue:(Float)->Unit){Card{Column(Modifier.padding(12.dp)){Row(Modifier.fillMaxWidth()){Text(title,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text(valueLabel)};Slider(value=value,onValueChange=onValue,valueRange=range)}}}

@Composable internal fun CacheRow(cache:CacheRecord,selected:Boolean,distanceM:Double?=null,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick),colors=CardDefaults.cardColors(containerColor=if(selected)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer,CircleShape),contentAlignment=Alignment.Center){Text(cache.type.take(1).uppercase(),fontWeight=FontWeight.Black)};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(cache.name,fontWeight=FontWeight.Bold);Text("${cache.code} · ${cache.type} · D ${cache.difficulty?:"—"} / T ${cache.terrain?:"—"}",style=MaterialTheme.typography.bodySmall)};distanceM?.let{Text(distanceLabel(it),fontWeight=FontWeight.SemiBold)}}}}

internal data class MapPoint(val label:String,val latitude:Double,val longitude:Double,val id:String="")

@Composable
internal fun GeoMap(points:List<MapPoint>, line:List<MapPoint> = emptyList(), height:Int=300){
    if(points.isEmpty() && line.isEmpty()) return
    val all=if(points.isNotEmpty())points else line
    val centreLat=all.map{it.latitude}.average(); val centreLon=GeocachingMath.circularMeanLongitude(all.map{it.longitude})
    val onlineStyle="https://tiles.openfreemap.org/styles/liberty"
    AndroidView(
        modifier=Modifier.fillMaxWidth().height(height.dp),
        factory={ctx->MapView(ctx).apply{
            onCreate(null)
            getMapAsync{map->
                map.uiSettings.isLogoEnabled=false
                map.uiSettings.isAttributionEnabled=true
                map.setStyle(onlineStyle){
                    map.cameraPosition=CameraPosition.Builder().target(LatLng(centreLat,centreLon)).zoom(autoZoom(all)).build()
                    points.forEach{map.addMarker(MarkerOptions().position(LatLng(it.latitude,it.longitude)).title(it.label))}
                    if(line.size>=2) map.addPolyline(PolylineOptions().addAll(line.map{LatLng(it.latitude,it.longitude)}).width(4f))
                }
            }
            onResume()
        }},
        update={view->view.getMapAsync{map->
            map.cameraPosition=CameraPosition.Builder().target(LatLng(centreLat,centreLon)).zoom(autoZoom(all)).build()
            map.clear()
            points.forEach{map.addMarker(MarkerOptions().position(LatLng(it.latitude,it.longitude)).title(it.label))}
            if(line.size>=2) map.addPolyline(PolylineOptions().addAll(line.map{LatLng(it.latitude,it.longitude)}).width(4f))
        }}
    )
}

private fun autoZoom(points:List<MapPoint>):Double{if(points.size<=1)return 14.0;val span=max(points.maxOf{it.latitude}-points.minOf{it.latitude},points.maxOf{it.longitude}-points.minOf{it.longitude});return when{span<.01->14.0;span<.05->12.0;span<.2->10.0;span<1->8.0;span<5->6.0;else->4.0}}

@Composable
internal fun Radar(caches:List<CacheRecord>,lat:Double,lon:Double,selected:String,onSelected:(String)->Unit){
    val maxD=(caches.maxOfOrNull{GeocachingMath.distanceM(lat,lon,it.latitude,it.longitude)}?:1.0).coerceAtLeast(1.0)
    Card{Box(Modifier.fillMaxWidth().height(260.dp).padding(12.dp)){Canvas(Modifier.fillMaxSize()){val r=min(size.width,size.height)*.43f;val c=Offset(size.width/2,size.height/2);for(f in listOf(.33f,.66f,1f))drawCircle(Color.Gray.copy(alpha=.28f),r*f,c,style=Stroke(1.dp.toPx()));drawLine(Color.Gray.copy(alpha=.35f),Offset(c.x,c.y-r),Offset(c.x,c.y+r));drawLine(Color.Gray.copy(alpha=.35f),Offset(c.x-r,c.y),Offset(c.x+r,c.y));caches.take(50).forEach{cache->val d=GeocachingMath.distanceM(lat,lon,cache.latitude,cache.longitude);val b=Math.toRadians(GeocachingMath.bearingDeg(lat,lon,cache.latitude,cache.longitude));val rr=(d/maxD*r).toFloat();val p=Offset(c.x+(sin(b)*rr).toFloat(),c.y-(cos(b)*rr).toFloat());drawCircle(if(cache.code==selected)Color(0xFFFF9800) else Color(0xFF2E7D32),if(cache.code==selected)7.dp.toPx() else 5.dp.toPx(),p)}}}}
}

@Composable
internal fun NavigationCompass(relativeBearing:Double?,distanceM:Double?,heading:Double?,arrived:Boolean){
    val accent=if(arrived)Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
    Card(shape=RoundedCornerShape(28.dp)){Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(260.dp),contentAlignment=Alignment.Center){Canvas(Modifier.fillMaxSize()){val r=size.minDimension*.43f;val c=Offset(size.width/2,size.height/2);drawCircle(Color.Gray.copy(alpha=.25f),r,c,style=Stroke(2.dp.toPx()));for(i in 0 until 36){val a=Math.toRadians(i*10.0);val outer=Offset(c.x+(sin(a)*r).toFloat(),c.y-(cos(a)*r).toFloat());val innerR=r-if(i%9==0)18.dp.toPx() else 9.dp.toPx();val inner=Offset(c.x+(sin(a)*innerR).toFloat(),c.y-(cos(a)*innerR).toFloat());drawLine(Color.Gray,inner,outer,if(i%9==0)3.dp.toPx() else 1.dp.toPx())};relativeBearing?.let{deg->val a=Math.toRadians(deg);val tip=Offset(c.x+(sin(a)*r*.82f).toFloat(),c.y-(cos(a)*r*.82f).toFloat());drawLine(accent,c,tip,8.dp.toPx());drawCircle(accent,10.dp.toPx(),tip)}};Text(if(arrived)"✓" else "N",fontSize=26.sp,fontWeight=FontWeight.Black)};Text(if(arrived)"ARRIVED" else distanceM?.let(::distanceLabel)?:"Waiting for GPS",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);heading?.let{Text("Heading ${it.roundToInt()}°",style=MaterialTheme.typography.bodySmall)}}}
}

internal fun distanceLabel(m:Double):String=if(m<1000)"${m.roundToInt()} m" else "${(m/1000).gcFmt(2)} km"
internal fun friendlyTime(iso:String):String=runCatching{Instant.parse(iso).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm"))}.getOrDefault(iso)
internal fun valuesToJson(values:Map<String,String>)=JSONObject(values).toString()
internal fun jsonToValues(json:String): Map<String,String> =runCatching{val o=JSONObject(json);buildMap{o.keys().forEach{key->put(key,o.optString(key))}}}.getOrDefault(emptyMap())
internal fun jsonCaches(json:String): List<CacheRecord> =runCatching{val a=JSONArray(json);buildList{for(i in 0 until a.length())add(CacheRecord.fromJson(a.getJSONObject(i)))}}.getOrDefault(emptyList())
internal fun jsonVisits(records:List<CacheVisitRecord>)=JSONArray().apply{records.forEach{put(it.toJson())}}.toString()
internal fun jsonTrackables(records:List<TrackableEventRecord>)=JSONArray().apply{records.forEach{put(it.toJson())}}.toString()
internal fun materialiseBytes(context:Context,name:String,bytes:ByteArray):String{val safe=name.replace(Regex("[^A-Za-z0-9._-]"),"_");val f=File(context.cacheDir,"methodmesh-geocaching-${System.currentTimeMillis()}-$safe");f.writeBytes(bytes);return FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",f).toString()}
internal fun materialiseArtifact(context:Context,repo:GeocachingRepository,artifactId:String,name:String):String{if(artifactId.isBlank())return "";val f=repo.materialiseArtifactToCache(artifactId,name);return FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",f).toString()}
internal fun publishedOffset(visit:CacheVisitRecord):Double?=if(visit.actualLatitude!=null&&visit.actualLongitude!=null&&visit.publishedLatitude!=null&&visit.publishedLongitude!=null)GeocachingMath.distanceM(visit.actualLatitude,visit.actualLongitude,visit.publishedLatitude,visit.publishedLongitude)else null
internal fun journeyDistance(events:List<TrackableEventRecord>):Double{val p=events.filter{it.latitude!=null&&it.longitude!=null}.sortedBy{it.timestamp};return p.zipWithNext().sumOf{(a,b)->GeocachingMath.distanceM(a.latitude!!,a.longitude!!,b.latitude!!,b.longitude!!)}}
internal fun trackableState(events:List<TrackableEventRecord>):String=events.maxByOrNull{it.timestamp}?.eventType?.label.orEmpty()
