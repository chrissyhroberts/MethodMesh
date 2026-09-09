package com.example.methodmesh.modules.geocaching

import androidx.compose.runtime.Composable
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityHostPresentation
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

object GeocachingModule : MethodMeshModule {
    override val moduleId = "geocaching"
    override val displayName = "Geocaching"
    override val summary = "Offline-first geocaching field notebook with live navigation, durable find and trackable ledgers, maps, geotagged media and optional OpenCaching account sync."
    override val iconKey = "location"

    override fun as100Methods() = listOf(
        As100GeocachingDashboardMethod,
        As100CacheLibraryMethod,
        As100NearbyCachesMethod,
        As100NavigateToCacheMethod,
        As100CacheDetailsMethod,
        As100ProjectWaypointMethod,
        As100AverageCoordinatesMethod,
        As100RecordCacheVisitMethod,
        As100CacheHistoryMethod,
        As100TrackableRecordMethod,
        As100TrackableHistoryMethod,
        As100GeocachingAccountMethod,
        As100GeocachingSyncMethod
    )

    override fun capabilityScreens(): List<CapabilityScreenSpec> = screens

    override fun rilBindings() = listOf(
        RilBinding("open geocaching", As100GeocachingDashboardMethod.id, "Open the geocaching field dashboard"),
        RilBinding("browse cache library", As100CacheLibraryMethod.id, "Browse or import offline cache records"),
        RilBinding("find nearby caches", As100NearbyCachesMethod.id, "Find nearby saved or OpenCaching caches"),
        RilBinding("navigate to cache", As100NavigateToCacheMethod.id, "Navigate live to a cache"),
        RilBinding("read cache details", As100CacheDetailsMethod.id, "Read cache listing, hint, waypoints and logs"),
        RilBinding("project geocache waypoint", As100ProjectWaypointMethod.id, "Project a waypoint from bearing and distance"),
        RilBinding("average geocache coordinates", As100AverageCoordinatesMethod.id, "Average repeated GPS fixes"),
        RilBinding("record geocache visit", As100RecordCacheVisitMethod.id, "Record Found, DNF or Note with optional geotagged media"),
        RilBinding("query geocache history", As100CacheHistoryMethod.id, "Query the durable find ledger"),
        RilBinding("record trackable event", As100TrackableRecordMethod.id, "Record a trackable observation or movement"),
        RilBinding("query trackable journey", As100TrackableHistoryMethod.id, "Read a trackable ledger and mapped journey"),
        RilBinding("manage geocaching account", As100GeocachingAccountMethod.id, "Connect or inspect an OpenCaching account"),
        RilBinding("sync geocaching", As100GeocachingSyncMethod.id, "Import remote finds or upload one explicit local visit")
    )

    override fun capabilitySettings(): Map<String, List<MethodSetting>> = mapOf(
        As100GeocachingDashboardMethod.id to emptyList(),
        As100CacheLibraryMethod.id to listOf(
            text("cache_code", "Selected cache code", "Optional cache to select after opening."),
            text("gpx_text", "GPX text", "Advanced direct/ODK import payload.", "Advanced"),
            bool("export_gpx", "Return GPX export", false, "Advanced")
        ),
        As100NearbyCachesMethod.id to listOf(
            text("source", "Source / provider ID", "Use library or a provider ID configured in Geocaching → Providers & accounts."),
            text("latitude", "Latitude", "Optional supplied current latitude."),
            text("longitude", "Longitude", "Optional supplied current longitude."),
            float("radius_km", "Radius", 10f, 0.1f, 100f, "km"),
            int("limit", "Maximum results", 50, 1, 200)
        ),
        As100NavigateToCacheMethod.id to listOf(
            text("cache_code", "Cache code"), text("cache_name", "Cache name"),
            text("target_latitude", "Target latitude"), text("target_longitude", "Target longitude"),
            float("arrival_radius_m", "Arrival radius", 15f, 1f, 200f, "m")
        ),
        As100CacheDetailsMethod.id to listOf(
            text("cache_code", "Cache code"), text("cache_name", "Cache name"),
            text("latitude", "Latitude", group="Advanced"), text("longitude", "Longitude", group="Advanced"),
            text("type", "Cache type", group="Advanced"), text("size", "Size", group="Advanced"),
            text("difficulty", "Difficulty", group="Advanced"), text("terrain", "Terrain", group="Advanced"),
            text("owner", "Owner", group="Advanced"), text("description", "Description", group="Advanced"),
            text("hint", "Hint", group="Advanced"), text("attributes_json", "Attributes JSON", group="Advanced"),
            text("recent_logs_json", "Recent logs JSON", group="Advanced"), text("waypoints_json", "Waypoints JSON", group="Advanced"),
            text("provider_url", "Provider URL", group="Advanced"), text("source", "Source", group="Advanced")
        ),
        As100ProjectWaypointMethod.id to listOf(
            text("origin_latitude", "Origin latitude"), text("origin_longitude", "Origin longitude"),
            float("bearing_deg", "Bearing", 0f, 0f, 359.99f, "°"), float("distance_m", "Distance", 100f, 0f, 1_000_000f, "m")
        ),
        As100AverageCoordinatesMethod.id to listOf(
            text("samples", "Coordinate samples", "Semicolon/newline separated latitude,longitude[,accuracy] observations.")
        ),
        As100RecordCacheVisitMethod.id to listOf(
            text("visit_id", "Visit ID", group="Advanced"), text("cache_code", "Cache code"), text("cache_name", "Cache name"),
            choice("source", "Cache source", "manual", CacheSource.entries.map { it.id }),
            choice("visit_type", "Visit", "found", VisitType.entries.map { it.id }), text("timestamp", "Visit time", group="Advanced"),
            text("actual_latitude", "Actual latitude", group="Location"), text("actual_longitude", "Actual longitude", group="Location"),
            text("accuracy_m", "GPS accuracy", group="Location"), text("published_latitude", "Published latitude", group="Location"),
            text("published_longitude", "Published longitude", group="Location"), text("note", "Field note"),
            bool("favorite", "Favourite", false), text("photo_uri", "Photo input URI", group="Advanced"),
            bool("persist_to_ledger", "Persist to MethodMesh ledger", false, "Advanced")
        ),
        As100CacheHistoryMethod.id to listOf(
            text("query", "Search"), choice("visit_type", "Visit type", "all", listOf("all", "found", "dnf", "note")),
            text("cache_code", "Cache code"), text("since_iso", "Since", group="Advanced"), text("until_iso", "Until", group="Advanced"),
            text("selected_visit_id", "Selected visit ID", group="Advanced"),
            choice("export_format", "Return attachment", "none", listOf("none", "csv", "geojson", "field_notes"), group="Advanced")
        ),
        As100TrackableRecordMethod.id to listOf(
            text("event_id", "Event ID", group="Advanced"), text("trackable_code", "Trackable reference"), text("trackable_name", "Trackable name"),
            choice("event_type", "Event", "discover", TrackableEventType.entries.map { it.id }), text("timestamp", "Time", group="Advanced"),
            text("cache_code", "Cache code"), text("cache_name", "Cache name"), text("latitude", "Latitude", group="Location"),
            text("longitude", "Longitude", group="Location"), text("accuracy_m", "GPS accuracy", group="Location"), text("note", "Note"),
            text("photo_uri", "Photo input URI", group="Advanced"), bool("persist_to_ledger", "Persist to MethodMesh ledger", false, "Advanced")
        ),
        As100TrackableHistoryMethod.id to listOf(
            text("trackable_code", "Trackable reference"), text("selected_event_id", "Selected event ID", group="Advanced"),
            bool("export_geojson", "Return journey GeoJSON", false, "Advanced")
        ),
        As100GeocachingAccountMethod.id to listOf(
            text("provider", "Provider ID", "Provider ID from Geocaching → Providers & accounts."),
            choice("action", "Action", "status", listOf("status", "connect", "refresh", "disconnect")),
            text("consumer_key", "OKAPI consumer key", group="Provider setup"), text("consumer_secret", "OKAPI consumer secret", group="Provider setup"),
            text("verifier", "OAuth PIN", group="Provider setup")
        ),
        As100GeocachingSyncMethod.id to listOf(
            text("provider", "Provider ID", "Provider ID from Geocaching → Providers & accounts."),
            choice("operation", "Operation", "refresh_account", listOf("refresh_account", "import_history", "upload_visit")),
            text("local_visit_id", "Local visit ID"), int("limit", "Import limit", 500, 1, 1000)
        )
    )

    private val screens = listOf(
        screen(As100GeocachingDashboardMethod, "Geocaching", "Field dashboard, active hunt, history, trackables and account state."),
        screen(As100CacheLibraryMethod, "Cache library", "Browse and import an offline cache library."),
        screen(As100NearbyCachesMethod, "Nearby caches", "Find caches around the current or supplied position."),
        screen(As100NavigateToCacheMethod, "Navigate", "Live compass-and-distance navigation to a cache."),
        screen(As100CacheDetailsMethod, "Cache details", "Read cache details, hint, logs and waypoints."),
        screen(As100ProjectWaypointMethod, "Project waypoint", "Project a coordinate from bearing and distance."),
        screen(As100AverageCoordinatesMethod, "Average coordinates", "Collect repeated fixes and calculate a stable coordinate."),
        screen(As100RecordCacheVisitMethod, "Record visit", "Record a find, DNF or note with geotagged media."),
        screen(As100CacheHistoryMethod, "Find history", "Search, map and export the persistent visit ledger."),
        screen(As100TrackableRecordMethod, "Trackable event", "Record an observation or movement for a trackable."),
        screen(As100TrackableHistoryMethod, "Trackable journey", "View a trackable ledger and map its journey."),
        screen(As100GeocachingAccountMethod, "Accounts", "Connect an OpenCaching account without giving MethodMesh the provider password."),
        screen(As100GeocachingSyncMethod, "Sync", "Refresh account state, import remote finds or upload one chosen local visit.")
    )

    private fun screen(method: GeocachingMethodBase, title: String, description: String) = object : CapabilityScreenSpec {
        override val capabilityId = method.id
        override val title = title
        override val description = description
        override val hostPresentation = CapabilityHostPresentation.Immersive
        @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
            GeocachingCapabilityUi.Render(capabilityId, context, onBack, onConfirmed, onCancel)
        }
    }

    private fun text(id:String,label:String,description:String?=null,group:String?=null)=MethodSetting.TextSetting(id,label,description,group,"")
    private fun bool(id:String,label:String,default:Boolean,group:String?=null)=MethodSetting.BooleanSetting(id,label,null,group,default)
    private fun int(id:String,label:String,default:Int,min:Int?=null,max:Int?=null,group:String?=null)=MethodSetting.IntSetting(id,label,null,group,default,min,max)
    private fun float(id:String,label:String,default:Float,min:Float?=null,max:Float?=null,unit:String?=null,group:String?=null)=MethodSetting.FloatSetting(id,label,null,group,default,min,max,1f,unit,1)
    private fun choice(id:String,label:String,default:String,choices:List<String>,group:String?=null)=MethodSetting.ChoiceSetting(id,label,null,group,default,choices)
}
