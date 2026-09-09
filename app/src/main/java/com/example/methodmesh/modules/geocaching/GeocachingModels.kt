package com.example.methodmesh.modules.geocaching

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

enum class CacheSource(val id: String, val label: String) {
    LOCAL_GPX("local_gpx", "Imported GPX"),
    OPENCACHE_UK("opencache_uk", "OpenCache UK"),
    OPENCACHING_PL("opencaching_pl", "OpenCaching PL"),
    OKAPI("okapi", "OKAPI provider"),
    MANUAL("manual", "Manual"),
    GEOCACHING_COM("geocaching_com", "Geocaching.com");
    companion object { fun from(raw: String?) = entries.firstOrNull { it.id == raw } ?: MANUAL }
}

data class CacheWaypoint(
    val name: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val note: String = ""
) {
    fun toJson() = JSONObject().apply {
        put("name", name); put("type", type); put("latitude", latitude); put("longitude", longitude); put("note", note)
    }
    companion object {
        fun fromJson(o: JSONObject) = CacheWaypoint(
            o.optString("name"), o.optString("type"), o.optDouble("latitude"), o.optDouble("longitude"), o.optString("note")
        )
    }
}

data class CacheRecord(
    val code: String,
    val name: String,
    val source: CacheSource,
    val latitude: Double,
    val longitude: Double,
    val type: String = "Traditional",
    val size: String = "Unknown",
    val difficulty: Double? = null,
    val terrain: Double? = null,
    val owner: String = "",
    val description: String = "",
    val hint: String = "",
    val attributes: List<String> = emptyList(),
    val recentLogs: List<String> = emptyList(),
    val waypoints: List<CacheWaypoint> = emptyList(),
    val providerUrl: String = "",
    val savedAt: String = Instant.now().toString()
) {
    fun toJson() = JSONObject().apply {
        put("code", code); put("name", name); put("source", source.id); put("latitude", latitude); put("longitude", longitude)
        put("type", type); put("size", size); difficulty?.let { put("difficulty", it) }; terrain?.let { put("terrain", it) }
        put("owner", owner); put("description", description); put("hint", hint)
        put("attributes", JSONArray(attributes)); put("recent_logs", JSONArray(recentLogs)); put("provider_url", providerUrl); put("saved_at", savedAt)
        put("waypoints", JSONArray().apply { waypoints.forEach { put(it.toJson()) } })
    }
    companion object {
        fun fromJson(o: JSONObject): CacheRecord = CacheRecord(
            code = o.optString("code"), name = o.optString("name"), source = CacheSource.from(o.optString("source")),
            latitude = o.optDouble("latitude"), longitude = o.optDouble("longitude"), type = o.optString("type", "Traditional"),
            size = o.optString("size", "Unknown"), difficulty = o.optDouble("difficulty").takeUnless { it.isNaN() || it == 0.0 },
            terrain = o.optDouble("terrain").takeUnless { it.isNaN() || it == 0.0 }, owner = o.optString("owner"),
            description = o.optString("description"), hint = o.optString("hint"),
            attributes = o.optJSONArray("attributes").stringList(), recentLogs = o.optJSONArray("recent_logs").stringList(),
            waypoints = buildList { val a=o.optJSONArray("waypoints") ?: JSONArray(); for (i in 0 until a.length()) add(CacheWaypoint.fromJson(a.getJSONObject(i))) },
            providerUrl = o.optString("provider_url"), savedAt = o.optString("saved_at", Instant.now().toString())
        )
    }
}

enum class VisitType(val id: String, val label: String) {
    FOUND("found", "Found it"), DNF("dnf", "Didn't find it"), NOTE("note", "Write note");
    companion object { fun from(raw: String?) = entries.firstOrNull { it.id == raw } ?: FOUND }
}

data class CacheVisitRecord(
    val id: String = UUID.randomUUID().toString(),
    val cacheCode: String,
    val cacheName: String,
    val source: CacheSource,
    val visitType: VisitType,
    val timestamp: String,
    val actualLatitude: Double?,
    val actualLongitude: Double?,
    val accuracyM: Double?,
    val publishedLatitude: Double?,
    val publishedLongitude: Double?,
    val note: String = "",
    val favorite: Boolean = false,
    val photoArtifactIds: List<String> = emptyList(),
    val remoteLogId: String = "",
    val remoteState: String = "local_only",
    val importedFromRemote: Boolean = false
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("cache_code", cacheCode); put("cache_name", cacheName); put("source", source.id); put("visit_type", visitType.id); put("timestamp", timestamp)
        actualLatitude?.let { put("actual_latitude", it) }; actualLongitude?.let { put("actual_longitude", it) }; accuracyM?.let { put("accuracy_m", it) }
        publishedLatitude?.let { put("published_latitude", it) }; publishedLongitude?.let { put("published_longitude", it) }
        put("note", note); put("favorite", favorite); put("photo_artifact_ids", JSONArray(photoArtifactIds)); put("remote_log_id", remoteLogId)
        put("remote_state", remoteState); put("imported_from_remote", importedFromRemote)
    }
    companion object {
        fun fromJson(o: JSONObject) = CacheVisitRecord(
            id=o.optString("id"), cacheCode=o.optString("cache_code"), cacheName=o.optString("cache_name"), source=CacheSource.from(o.optString("source")),
            visitType=VisitType.from(o.optString("visit_type")), timestamp=o.optString("timestamp"), actualLatitude=o.optNullableDouble("actual_latitude"),
            actualLongitude=o.optNullableDouble("actual_longitude"), accuracyM=o.optNullableDouble("accuracy_m"), publishedLatitude=o.optNullableDouble("published_latitude"),
            publishedLongitude=o.optNullableDouble("published_longitude"), note=o.optString("note"), favorite=o.optBoolean("favorite"),
            photoArtifactIds=o.optJSONArray("photo_artifact_ids").stringList(), remoteLogId=o.optString("remote_log_id"), remoteState=o.optString("remote_state", "local_only"),
            importedFromRemote=o.optBoolean("imported_from_remote")
        )
    }
}

enum class TrackableEventType(val id: String, val label: String) {
    FOLLOW("follow", "Follow"), DISCOVER("discover", "Discover"), RETRIEVE("retrieve", "Retrieve"), GRAB("grab", "Grab"),
    DROP("drop", "Drop"), VISIT("visit", "Visit cache"), NOTE("note", "Note"), OWN("own", "Own / collection"), RELEASE("release", "Release");
    companion object { fun from(raw: String?) = entries.firstOrNull { it.id == raw } ?: NOTE }
}

data class TrackableEventRecord(
    val id: String = UUID.randomUUID().toString(),
    val trackableCode: String,
    val trackableName: String,
    val eventType: TrackableEventType,
    val timestamp: String,
    val cacheCode: String = "",
    val cacheName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Double? = null,
    val note: String = "",
    val photoArtifactIds: List<String> = emptyList(),
    val source: String = "methodmesh"
) {
    fun toJson() = JSONObject().apply {
        put("id",id); put("trackable_code",trackableCode); put("trackable_name",trackableName); put("event_type",eventType.id); put("timestamp",timestamp)
        put("cache_code",cacheCode); put("cache_name",cacheName); latitude?.let{put("latitude",it)}; longitude?.let{put("longitude",it)}; accuracyM?.let{put("accuracy_m",it)}
        put("note",note); put("photo_artifact_ids",JSONArray(photoArtifactIds)); put("source",source)
    }
    companion object { fun fromJson(o:JSONObject)=TrackableEventRecord(
        id=o.optString("id"), trackableCode=o.optString("trackable_code"), trackableName=o.optString("trackable_name"), eventType=TrackableEventType.from(o.optString("event_type")),
        timestamp=o.optString("timestamp"), cacheCode=o.optString("cache_code"), cacheName=o.optString("cache_name"), latitude=o.optNullableDouble("latitude"), longitude=o.optNullableDouble("longitude"),
        accuracyM=o.optNullableDouble("accuracy_m"), note=o.optString("note"), photoArtifactIds=o.optJSONArray("photo_artifact_ids").stringList(), source=o.optString("source","methodmesh")
    ) }
}

data class ActiveHunt(
    val cacheCode: String,
    val cacheName: String,
    val source: CacheSource,
    val latitude: Double,
    val longitude: Double,
    val startedAt: String = Instant.now().toString(),
    val arrivalRadiusM: Double = 15.0
) {
    fun toJson()=JSONObject().apply{put("cache_code",cacheCode);put("cache_name",cacheName);put("source",source.id);put("latitude",latitude);put("longitude",longitude);put("started_at",startedAt);put("arrival_radius_m",arrivalRadiusM)}
    companion object { fun fromJson(o:JSONObject)=ActiveHunt(o.optString("cache_code"),o.optString("cache_name"),CacheSource.from(o.optString("source")),o.optDouble("latitude"),o.optDouble("longitude"),o.optString("started_at"),o.optDouble("arrival_radius_m",15.0)) }
}

data class SyncReceipt(
    val localVisitId: String,
    val provider: String,
    val remoteLogId: String,
    val status: String,
    val attemptedAt: String,
    val message: String = ""
) {
    fun toJson()=JSONObject().apply{put("local_visit_id",localVisitId);put("provider",provider);put("remote_log_id",remoteLogId);put("status",status);put("attempted_at",attemptedAt);put("message",message)}
    companion object { fun fromJson(o:JSONObject)=SyncReceipt(o.optString("local_visit_id"),o.optString("provider"),o.optString("remote_log_id"),o.optString("status"),o.optString("attempted_at"),o.optString("message")) }
}

internal fun JSONArray?.stringList(): List<String> = buildList { val a=this@stringList ?: return@buildList; for(i in 0 until a.length()) add(a.optString(i)) }
internal fun JSONObject.optNullableDouble(key:String):Double? = if(has(key) && !isNull(key)) optDouble(key).takeUnless{it.isNaN()} else null
