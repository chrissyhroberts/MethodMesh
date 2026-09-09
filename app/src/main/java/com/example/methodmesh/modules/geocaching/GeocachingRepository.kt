package com.example.methodmesh.modules.geocaching

import android.content.Context
import com.example.methodmesh.core.artifacts.AndroidArtifacts
import com.example.methodmesh.core.artifacts.ArtifactRef
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant

class GeocachingRepository(private val context: Context) {
    private val root = File(context.filesDir, "geocaching").apply { mkdirs() }
    private val cachesFile = File(root, "caches.ndjson")
    private val visitsFile = File(root, "visits.ndjson")
    private val trackablesFile = File(root, "trackables.ndjson")
    private val receiptsFile = File(root, "sync_receipts.ndjson")
    private val activeFile = File(root, "active_hunt.json")

    @Synchronized fun caches():List<CacheRecord> = readLines(cachesFile).mapNotNull { runCatching{CacheRecord.fromJson(JSONObject(it))}.getOrNull() }
    @Synchronized fun saveCache(cache:CacheRecord) { val all=caches().associateBy{it.code}.toMutableMap(); all[cache.code]=cache; writeAll(cachesFile,all.values.map{it.toJson().toString()}) }
    @Synchronized fun saveCaches(items:List<CacheRecord>) { val all=caches().associateBy{it.code}.toMutableMap(); items.forEach{all[it.code]=it}; writeAll(cachesFile,all.values.map{it.toJson().toString()}) }
    @Synchronized fun removeCache(code:String){ writeAll(cachesFile,caches().filterNot{it.code==code}.map{it.toJson().toString()}) }
    @Synchronized fun clearCaches(){ cachesFile.delete(); activeFile.delete() }

    @Synchronized fun visits():List<CacheVisitRecord> = readLines(visitsFile).mapNotNull{runCatching{CacheVisitRecord.fromJson(JSONObject(it))}.getOrNull()}
    @Synchronized fun appendVisit(record:CacheVisitRecord){ append(visitsFile,record.toJson().toString()) }
    @Synchronized fun upsertImportedVisit(record:CacheVisitRecord){ if(visits().none{it.source==record.source && it.remoteLogId.isNotBlank() && it.remoteLogId==record.remoteLogId}) appendVisit(record) }
    @Synchronized fun findVisit(id:String)=visits().firstOrNull{it.id==id}

    @Synchronized fun trackableEvents():List<TrackableEventRecord> = readLines(trackablesFile).mapNotNull{runCatching{TrackableEventRecord.fromJson(JSONObject(it))}.getOrNull()}
    @Synchronized fun appendTrackable(record:TrackableEventRecord){ append(trackablesFile,record.toJson().toString()) }

    @Synchronized fun receipts():List<SyncReceipt> = readLines(receiptsFile).mapNotNull{runCatching{SyncReceipt.fromJson(JSONObject(it))}.getOrNull()}
    @Synchronized fun appendReceipt(r:SyncReceipt){ append(receiptsFile,r.toJson().toString()) }
    fun successfulUploadFor(visitId:String,provider:String)=receipts().lastOrNull{it.localVisitId==visitId&&it.provider==provider&&it.status=="succeeded"}

    @Synchronized fun activeHunt():ActiveHunt? = if(activeFile.isFile) runCatching{ActiveHunt.fromJson(JSONObject(activeFile.readText()))}.getOrNull() else null
    @Synchronized fun setActiveHunt(h:ActiveHunt?){ if(h==null) activeFile.delete() else atomicWrite(activeFile,h.toJson().toString()) }

    fun savePersistentBytes(name:String,mime:String,bytes:ByteArray):ArtifactRef = AndroidArtifacts.service(context).createPersistent(name,mime,ByteArrayInputStream(bytes))
    fun materialiseArtifactToCache(refId:String, preferredName:String):File {
        val safe=preferredName.replace(Regex("[^A-Za-z0-9._-]"),"_"); val file=File(context.cacheDir,"geocaching-${System.currentTimeMillis()}-$safe")
        AndroidArtifacts.service(context).open(ArtifactRef(refId)).use{input->file.outputStream().use{input.copyTo(it)}}; return file
    }

    fun visitCsv(records:List<CacheVisitRecord>):ByteArray = buildString {
        appendLine("id,cache_code,cache_name,source,visit_type,timestamp,actual_latitude,actual_longitude,accuracy_m,published_latitude,published_longitude,note,favorite,photo_count,remote_state,remote_log_id")
        records.forEach{r->appendLine(listOf(r.id,r.cacheCode,r.cacheName,r.source.id,r.visitType.id,r.timestamp,r.actualLatitude,r.actualLongitude,r.accuracyM,r.publishedLatitude,r.publishedLongitude,r.note,r.favorite,r.photoArtifactIds.size,r.remoteState,r.remoteLogId).joinToString(","){csv(it?.toString().orEmpty())})}
    }.toByteArray()

    fun visitGeoJson(records:List<CacheVisitRecord>):ByteArray = JSONObject().apply {
        put("type","FeatureCollection"); put("features",org.json.JSONArray().apply{records.filter{it.actualLatitude!=null&&it.actualLongitude!=null}.forEach{r->put(JSONObject().apply{
            put("type","Feature"); put("geometry",JSONObject().apply{put("type","Point");put("coordinates",org.json.JSONArray(listOf(r.actualLongitude,r.actualLatitude)))})
            put("properties",JSONObject().apply{put("id",r.id);put("cache_code",r.cacheCode);put("cache_name",r.cacheName);put("visit_type",r.visitType.id);put("timestamp",r.timestamp);put("accuracy_m",r.accuracyM);put("favorite",r.favorite)})
        })}})
    }.toString(2).toByteArray()

    fun trackableGeoJson(code:String, records:List<TrackableEventRecord>):ByteArray = JSONObject().apply {
        val pts=records.filter{it.trackableCode.equals(code,true)&&it.latitude!=null&&it.longitude!=null}.sortedBy{it.timestamp}
        put("type","FeatureCollection"); put("features",org.json.JSONArray().apply{
            if(pts.size>=2) put(JSONObject().apply{put("type","Feature");put("geometry",JSONObject().apply{put("type","LineString");put("coordinates",org.json.JSONArray().apply{pts.forEach{put(org.json.JSONArray(listOf(it.longitude,it.latitude)))}})});put("properties",JSONObject().put("trackable_code",code))})
            pts.forEach{e->put(JSONObject().apply{put("type","Feature");put("geometry",JSONObject().apply{put("type","Point");put("coordinates",org.json.JSONArray(listOf(e.longitude,e.latitude)))});put("properties",JSONObject().apply{put("event_type",e.eventType.id);put("timestamp",e.timestamp);put("cache_code",e.cacheCode);put("note",e.note)})})}
        })
    }.toString(2).toByteArray()

    fun gpx(records:List<CacheRecord>):ByteArray = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"MethodMesh\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
        records.forEach{c->append("<wpt lat=\"").append(c.latitude).append("\" lon=\"").append(c.longitude).append("\"><name>").append(xml(c.code)).append("</name><desc>").append(xml(c.name)).append("</desc><type>").append(xml(c.type)).append("</type></wpt>")}
        append("</gpx>")
    }.toByteArray()

    fun geocachingFieldNotes(records:List<CacheVisitRecord>):ByteArray = buildString {
        records.forEach{r->append(r.cacheCode).append(',').append(r.timestamp.substringBefore('T')).append('T').append(r.timestamp.substringAfter('T').take(8)).append('Z').append(',')
            .append(if(r.visitType==VisitType.FOUND) "Found it" else if(r.visitType==VisitType.DNF) "Didn't find it" else "Write note").append(',').append(r.note.replace("\n"," ")).appendLine()}
    }.toByteArray()

    private fun readLines(file:File)=if(file.isFile) file.readLines().filter{it.isNotBlank()} else emptyList()
    private fun append(file:File,line:String){file.parentFile?.mkdirs();file.appendText(line+"\n")}
    private fun writeAll(file:File,lines:List<String>)=atomicWrite(file,lines.joinToString("\n",postfix=if(lines.isEmpty())"" else "\n"))
    private fun atomicWrite(file:File,text:String){ val tmp=File(file.parentFile,file.name+".partial");tmp.writeText(text); if(file.exists()) file.delete(); check(tmp.renameTo(file)) }
    private fun csv(s:String)="\""+s.replace("\"","\"\"")+"\""
    private fun xml(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
}
