package com.example.methodmesh.modules.geocaching

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.platform.sensors.PhoneSensorRepository
import com.example.methodmesh.transport.workflow.ExternalActionRequest
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

internal object GeocachingCapabilityUi {
    private data class PlaceCandidate(val label:String,val latitude:Double,val longitude:Double)

    @Composable
    fun Render(capabilityId:String, context:CapabilityScreenContext, onBack:()->Unit, onConfirmed:(ExecutionResult)->Unit, onCancel:()->Unit) {
        when(capabilityId){
            As100GeocachingDashboardMethod.id -> Dashboard(context,onBack,onConfirmed,onCancel)
            As100CacheLibraryMethod.id -> Library(context,onBack,onConfirmed,onCancel)
            As100NearbyCachesMethod.id -> Nearby(context,onBack,onConfirmed,onCancel)
            As100NavigateToCacheMethod.id -> Navigate(context,onBack,onConfirmed,onCancel)
            As100CacheDetailsMethod.id -> Details(context,onBack,onConfirmed,onCancel)
            As100ProjectWaypointMethod.id -> Project(context,onBack,onConfirmed,onCancel)
            As100AverageCoordinatesMethod.id -> Average(context,onBack,onConfirmed,onCancel)
            As100RecordCacheVisitMethod.id -> RecordVisit(context,onBack,onConfirmed,onCancel)
            As100CacheHistoryMethod.id -> History(context,onBack,onConfirmed,onCancel)
            As100TrackableRecordMethod.id -> TrackableRecord(context,onBack,onConfirmed,onCancel)
            As100TrackableHistoryMethod.id -> TrackableHistory(context,onBack,onConfirmed,onCancel)
            As100GeocachingAccountMethod.id -> Account(context,onBack,onConfirmed,onCancel)
            As100GeocachingSyncMethod.id -> Sync(context,onBack,onConfirmed,onCancel)
        }
    }

    @Composable
    private fun Dashboard(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val app=LocalContext.current;var revision by rememberSaveable{mutableIntStateOf(0)};var child by rememberSaveable{mutableStateOf<String?>(null)};var frozenJson by rememberSaveable{mutableStateOf<String?>(null)};val frozen=frozenJson?.let(::jsonToValues)
        val repo=remember(app,revision){GeocachingRepository(app)};val providers=remember(app,revision){GeocachingProviderRegistry(app)};val accounts=remember(app){GeocachingAccountStore(app)};val defaultProvider=remember(revision){providers.profile(providers.defaultProviderId())};val caches=remember(revision){repo.caches()};val visits=remember(revision){repo.visits()};val events=remember(revision){repo.trackableEvents()};val hunt=remember(revision){repo.activeHunt()};val account=remember(revision,defaultProvider?.id){defaultProvider?.let{accounts.account(it.id)}?:ProviderAccount("",false)}
        val uploaded=remember(revision){repo.receipts().filter{it.status=="succeeded"}.map{it.localVisitId}.toSet()};val pending=visits.count{it.visitType!=VisitType.NOTE&&it.id !in uploaded};val found=visits.count{it.visitType==VisitType.FOUND};val dnf=visits.count{it.visitType==VisitType.DNF};val photos=visits.sumOf{it.photoArtifactIds.size};val followed=events.map{it.trackableCode.uppercase()}.distinct().size
        val values=mapOf("geocache_dashboard_result" to (hunt?.let{"Hunting ${it.cacheName}"}?:"Ready to hunt"),"geocache_dashboard_active_hunt_code" to hunt?.cacheCode.orEmpty(),"geocache_dashboard_active_hunt_name" to hunt?.cacheName.orEmpty(),"geocache_dashboard_cache_count" to caches.size.toString(),"geocache_dashboard_visit_count" to visits.size.toString(),"geocache_dashboard_found_count" to found.toString(),"geocache_dashboard_dnf_count" to dnf.toString(),"geocache_dashboard_photo_count" to photos.toString(),"geocache_dashboard_followed_trackable_count" to followed.toString(),"geocache_dashboard_opencache_connected" to account.connected.toString(),"geocache_dashboard_opencache_username" to account.username,"geocache_dashboard_pending_upload_count" to pending.toString())
        LaunchedEffect(Unit){if(context.submitsImmediately){commitResult(context,As100GeocachingDashboardMethod,values,onConfirmed)}}
        if (child != null) {
            val target = child!!
            val action = ExternalActionRequest(target, target, emptyMap())
            Render(
                target,
                context.copy(action = action, stepNumber = 2, totalSteps = 2),
                { child = null },
                { child = null; revision++ },
                { child = null }
            )
            return
        }
        MethodSurface("Geocaching","Field notebook, active hunt, finds, trackables and optional sync.",context,onBack,onCancel,frozen!=null,onDone=frozen?.let{{onConfirmed(commitResult(context,As100GeocachingDashboardMethod,it,onConfirmed))}},committedKey=frozen,committedResultFactory=frozen?.let { committedValues -> { commitResult(context,As100GeocachingDashboardMethod,committedValues,onConfirmed) } },onDoneResult=onConfirmed){
            HeroPanel{Text(if(hunt==null)"READY TO HUNT" else "ACTIVE HUNT",style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary);Text(hunt?.cacheName?:"Find something nearby",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);hunt?.let{CopyableValue(it.cacheCode,"Cache");Text("Started ${friendlyTime(it.startedAt)}");PrimaryAction("Continue hunt"){child=As100NavigateToCacheMethod.id}}?:PrimaryAction("Nearby caches"){child=As100NearbyCachesMethod.id}}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){StatTile(found.toString(),"Found",Modifier.weight(1f));StatTile(photos.toString(),"Photos",Modifier.weight(1f));StatTile(followed.toString(),"Trackables",Modifier.weight(1f))}
            SectionTitle("FIELD NOTEBOOK");DashboardAction("Find history","$found finds · $dnf DNFs · ${visits.size} records"){child=As100CacheHistoryMethod.id};DashboardAction("Trackable journeys","$followed followed · ${events.size} observations"){child=As100TrackableHistoryMethod.id};DashboardAction("Cache library","${caches.size} saved offline"){child=As100CacheLibraryMethod.id}
            SectionTitle("PROVIDERS & SYNC");DashboardAction(defaultProvider?.label ?: "Providers","${if(defaultProvider!=null && accounts.consumerKey(defaultProvider.id).isNotBlank()) "Public API key saved" else "Needs OKAPI application key"}${if(account.connected) " · ${account.username.ifBlank{"account connected"}}" else " · sign-in optional"}"){child=As100GeocachingAccountMethod.id};if(account.connected)DashboardAction("Synchronise","$pending local logs waiting · import remote history or upload one chosen visit"){child=As100GeocachingSyncMethod.id}
            SectionTitle("TOOLS");Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){MiniAction("Project\nwaypoint",Modifier.weight(1f)){child=As100ProjectWaypointMethod.id};MiniAction("Average\ncoordinates",Modifier.weight(1f)){child=As100AverageCoordinatesMethod.id};MiniAction("Record\nvisit",Modifier.weight(1f)){child=As100RecordCacheVisitMethod.id}}
            CommitButton(frozen!=null){frozenJson=valuesToJson(values)}
        }
    }

    @Composable
    private fun Library(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val app=LocalContext.current;val repo=remember(app){GeocachingRepository(app)};val scope=rememberCoroutineScope();var revision by rememberSaveable{mutableIntStateOf(0)};var selected by rememberSaveable{mutableStateOf(context.action.setting("cache_code").orEmpty())};var query by rememberSaveable{mutableStateOf("")};var status by rememberSaveable{mutableStateOf("")};var frozenJson by rememberSaveable{mutableStateOf<String?>(null)};val frozen=frozenJson?.let(::jsonToValues)
        var transientImported by remember{mutableStateOf<List<CacheRecord>>(emptyList())};val gpxText=context.action.setting("gpx_text")
        LaunchedEffect(gpxText){if(context.submitsImmediately&&gpxText!=null){transientImported=runCatching{GeocachingGpx.parse(ByteArrayInputStream(gpxText.toByteArray()))}.getOrDefault(emptyList())}}
        val stored=remember(revision){repo.caches().sortedBy{it.name.lowercase()}};val caches=if(transientImported.isNotEmpty())transientImported else stored;val selectedCache=caches.firstOrNull{it.code.equals(selected,true)};val filtered=caches.filter{query.isBlank()||it.name.contains(query,true)||it.code.contains(query,true)}
        fun values(exportUri:String="")=mapOf("geocache_library_result" to "${caches.size} caches available","geocache_library_cache_count" to caches.size.toString(),"geocache_library_cache_codes" to caches.joinToString("|"){it.code},"geocache_library_selected_cache_code" to selectedCache?.code.orEmpty(),"geocache_library_selected_cache_name" to selectedCache?.name.orEmpty(),"geocache_library_source" to if(transientImported.isNotEmpty())"supplied_gpx" else "local_library","geocache_library_gpx_export_uri" to exportUri)
        LaunchedEffect(transientImported.size){if(context.submitsImmediately){val uri=if(context.action.setting("export_gpx")=="true")materialiseBytes(app,"geocache_library.gpx",repo.gpx(caches))else "";commitResult(context,As100CacheLibraryMethod,values(uri),onConfirmed)}}
        val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{runCatching{withContext(Dispatchers.IO){app.contentResolver.openInputStream(uri)?.use(GeocachingGpx::parse)?:error("Cannot open GPX")}}.onSuccess{repo.saveCaches(it);revision++;status="Imported ${it.size} caches."}.onFailure{status=it.message.orEmpty()}}}
        MethodSurface("Cache library","Offline cache shelf and GPX exchange.",context,onBack,onCancel,frozen!=null,onDone=frozen?.let{{onConfirmed(commitResult(context,As100CacheLibraryMethod,it,onConfirmed))}},committedKey=frozen,committedResultFactory=frozen?.let { committedValues -> { commitResult(context,As100CacheLibraryMethod,committedValues,onConfirmed) } },onDoneResult=onConfirmed){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={importer.launch(arrayOf("application/gpx+xml","application/xml","text/xml","*/*"))},modifier=Modifier.weight(1f)){Text("Import GPX")};OutlinedButton(onClick={repo.savePersistentBytes("MethodMesh geocache library.gpx","application/gpx+xml",repo.gpx(stored));status="GPX snapshot saved to Files."},enabled=stored.isNotEmpty(),modifier=Modifier.weight(1f)){Text("Save GPX")}}
            if(status.isNotBlank())Text(status,color=MaterialTheme.colorScheme.primary);OutlinedTextField(query,{query=it},label={Text("Search caches")},modifier=Modifier.fillMaxWidth(),singleLine=true);Text("${filtered.size} caches",fontWeight=FontWeight.Bold);filtered.take(100).forEach{CacheRow(it,selected==it.code){selected=it.code}}
            selectedCache?.let{HeroPanel{Text(it.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black);CopyableValue(it.code,"Cache");CopyableValue("${it.latitude.gcFmt()}, ${it.longitude.gcFmt()}","Coordinates");Text("${it.type} · D ${it.difficulty?:"—"} / T ${it.terrain?:"—"} · ${it.size}")}}
            CommitButton(frozen!=null){frozenJson=valuesToJson(values())}
        }
    }

    @Composable
    private fun Nearby(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val app=LocalContext.current
        val repo=remember(app){GeocachingRepository(app)}
        val accounts=remember(app){GeocachingAccountStore(app)}
        var providerRevision by rememberSaveable { mutableIntStateOf(0) }
        var manageProviders by rememberSaveable { mutableStateOf(false) }
        var providerToManage by rememberSaveable { mutableStateOf("") }
        var openProviderEditor by rememberSaveable { mutableStateOf(false) }
        val providers=remember(app,providerRevision){GeocachingProviderRegistry(app)}
        val providerProfiles=remember(providerRevision){providers.profiles()}
        val scope=rememberCoroutineScope()

        val requestedSource=context.action.setting("source")
        val defaultProviderId=providers.defaultProviderId()
        val preferredSource = when {
            requestedSource=="library" -> "library"
            requestedSource!=null && providers.profile(requestedSource)!=null -> requestedSource
            accounts.consumerKey(defaultProviderId).isNotBlank() -> defaultProviderId
            else -> "library"
        }
        var lat by rememberSaveable{mutableStateOf(context.action.setting("latitude").orEmpty())}
        var lon by rememberSaveable{mutableStateOf(context.action.setting("longitude").orEmpty())}
        var radius by rememberSaveable{mutableStateOf(context.action.setting("radius_km")?:"10")}
        var source by rememberSaveable{mutableStateOf(preferredSource)}
        var searchOrigin by rememberSaveable{mutableStateOf(if(lat.isNotBlank()&&lon.isNotBlank()) "supplied" else "current")}
        var searchCenterLabel by rememberSaveable{mutableStateOf(if(lat.isNotBlank()&&lon.isNotBlank()) "Supplied coordinates" else "Current location")}
        var placePickerOpen by rememberSaveable{mutableStateOf(false)}
        var placeQuery by rememberSaveable{mutableStateOf("")}
        var placeCandidatesJson by rememberSaveable{mutableStateOf("[]")}
        var placeLookupStatus by rememberSaveable{mutableStateOf("")}
        var placeLookupLoading by remember{mutableStateOf(false)}
        var manualLat by rememberSaveable{mutableStateOf("")}
        var manualLon by rememberSaveable{mutableStateOf("")}
        var remoteJson by rememberSaveable{mutableStateOf("")}
        var selected by rememberSaveable{mutableStateOf("")}
        var status by rememberSaveable{mutableStateOf(if(lat.isBlank()||lon.isBlank()) "Locating…" else "Using supplied position.")}
        var loading by remember{mutableStateOf(false)}
        var locationAttempted by rememberSaveable{mutableStateOf(lat.isNotBlank()&&lon.isNotBlank())}
        var searchAttempted by rememberSaveable{mutableStateOf(false)}
        var queryState by rememberSaveable{mutableStateOf("idle")}
        var verifiedProviderId by rememberSaveable{mutableStateOf("")}
        var queryRecordCount by rememberSaveable{mutableIntStateOf(0)}
        var queryMore by rememberSaveable{mutableStateOf(false)}
        var queryError by rememberSaveable{mutableStateOf("")}
        var frozenJson by rememberSaveable{mutableStateOf<String?>(null)}
        val frozen=frozenJson?.let(::jsonToValues)
        val placeCandidates=remember(placeCandidatesJson){
            runCatching{
                val a=JSONArray(placeCandidatesJson)
                List(a.length()){i->
                    val o=a.getJSONObject(i)
                    PlaceCandidate(o.optString("label"),o.getDouble("latitude"),o.getDouble("longitude"))
                }
            }.getOrDefault(emptyList())
        }

        val la=lat.toDoubleOrNull()
        val lo=lon.toDoubleOrNull()
        val local=remember(lat,lon,radius){
            if(la==null||lo==null) emptyList() else repo.caches()
                .map{it to GeocachingMath.distanceM(la,lo,it.latitude,it.longitude)}
                .filter{it.second<=(radius.toDoubleOrNull()?:10.0)*1000}
                .sortedBy{it.second}.map{it.first}
        }
        val remote=remember(remoteJson){jsonCaches(remoteJson)}
        val results=if(source=="library")local else remote
        val chosen=results.firstOrNull{it.code==selected}

        fun setLocation(l:Location){
            lat=l.latitude.gcFmt()
            lon=l.longitude.gcFmt()
            searchOrigin="current"
            searchCenterLabel="Current location ±${l.accuracy.roundToInt()} m"
            placePickerOpen=false
            locationAttempted=true
            searchAttempted = source=="library"
            status=if(source=="library") {
                val saved=repo.caches().size
                if(saved==0) "Position acquired (±${l.accuracy.roundToInt()} m). No offline caches are saved yet — import GPX or configure an OKAPI provider."
                else "Position acquired (±${l.accuracy.roundToInt()} m). Ready to search saved caches."
            } else "Position acquired (±${l.accuracy.roundToInt()} m). Loading nearby caches…"
        }

        fun load(sourceId:String=source, searchLat:Double?=lat.toDoubleOrNull(), searchLon:Double?=lon.toDoubleOrNull()){
            val currentLat=searchLat; val currentLon=searchLon
            if(currentLat==null||currentLon==null){status="Choose a search centre or use your current location first.";return}
            searchAttempted=true
            if(sourceId=="library"){
                val total=repo.caches().size
                val localAtCenter=repo.caches()
                    .map{it to GeocachingMath.distanceM(currentLat,currentLon,it.latitude,it.longitude)}
                    .filter{it.second<=(radius.toDoubleOrNull()?:10.0)*1000}
                status=when{
                    total==0 -> "No offline caches are saved yet. Import a GPX in Cache library, or choose an OpenCaching source."
                    localAtCenter.isEmpty() -> "No saved caches within ${radius} km of ${searchCenterLabel}. Increase the radius or choose an OpenCaching source."
                    else -> "${localAtCenter.size} saved caches within ${radius} km of ${searchCenterLabel}."
                }
                return
            }
            val profile=providers.profile(sourceId)
            if(profile==null){status="Unknown provider profile. Open Providers to choose or add an OKAPI site.";return}
            val inst=profile.installation()
            val key=accounts.consumerKey(profile.id); val secret=accounts.consumerSecret(profile.id)
            if(key.isBlank()){
                queryState="unconfigured";queryError="";verifiedProviderId=""
                status="${profile.label} needs an OKAPI consumer key. Tap the provider above to configure it."
                return
            }
            loading=true
            queryState="loading";queryError="";queryRecordCount=0;queryMore=false
            status="Contacting ${inst.label} and validating the public API key…"
            scope.launch{
                runCatching{
                    withContext(Dispatchers.IO){
                        OkapiClient(inst,key,secret).nearby(
                            currentLat,currentLon,radius.toDoubleOrNull()?:10.0,
                            context.action.setting("limit")?.toIntOrNull()?:50,
                            accounts.accessToken(profile.id).takeIf{it.isNotBlank() && secret.isNotBlank()},
                            accounts.accessSecret(profile.id).takeIf{it.isNotBlank() && secret.isNotBlank()}
                        )
                    }
                }.onSuccess{reply:OkapiNearbyResult->
                    remoteJson=JSONArray().apply{reply.caches.forEach{c:CacheRecord->put(c.toJson())}}.toString()
                    queryRecordCount=reply.providerRecordCount;queryMore=reply.more
                    if(reply.providerRecordCount>0 && reply.caches.isEmpty()){
                        queryState="parse_error";verifiedProviderId=profile.id
                        queryError="${inst.label} returned ${reply.providerRecordCount} cache records, but MethodMesh could not parse them. The API key is valid; this is a response-format problem."
                        status="${inst.label} verified, but its cache data could not be parsed."
                    }else{
                        queryState="success";verifiedProviderId=profile.id
                        status=if(reply.caches.isEmpty()) {
                            "${inst.label} accepted the API key and completed the search: 0 caches within ${radius} km."
                        } else {
                            "${inst.label} verified · ${reply.caches.size} caches returned${if(reply.providerRecordCount!=reply.caches.size) " of ${reply.providerRecordCount} provider records" else ""}${if(reply.more) " · more available" else ""}."
                        }
                    }
                }.onFailure{error:Throwable->
                    queryState="error";verifiedProviderId="";queryRecordCount=0;queryMore=false
                    queryError=error.message.orEmpty().ifBlank{"Nearby-cache query failed."}
                    status="${inst.label} search failed. See provider feedback below."
                }
                loading=false
            }
        }

        val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            locationAttempted=true
            if(hasLocation(app)) acquireLocation(app,{l->setLocation(l);if(source!="library")load(source,l.latitude,l.longitude)},{status=it})
            else status="Location permission is required for Near me. You can still call this capability with supplied coordinates."
        }

        fun acquireNow(){
            placePickerOpen=false
            status="Locating…"
            if(hasLocation(app)) acquireLocation(app,{l->setLocation(l);if(source!="library")load(source,l.latitude,l.longitude)},{status=it;locationAttempted=true})
            else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        fun applySearchCenter(candidate:PlaceCandidate, origin:String="place"){
            lat=candidate.latitude.gcFmt()
            lon=candidate.longitude.gcFmt()
            searchOrigin=origin
            searchCenterLabel=candidate.label.ifBlank{"Chosen location"}
            locationAttempted=true
            remoteJson=""
            selected=""
            searchAttempted=false
            queryState="idle";queryError="";queryRecordCount=0;queryMore=false
            status="Search centre set to ${searchCenterLabel}."
            load(source,candidate.latitude,candidate.longitude)
        }

        fun findPlace(){
            val q=placeQuery.trim()
            if(q.isBlank()){placeLookupStatus="Enter a place, postcode or address.";return}
            placeLookupLoading=true
            placeLookupStatus="Looking up $q…"
            scope.launch{
                runCatching{
                    withContext(Dispatchers.IO){
                        if(!Geocoder.isPresent()) error("This device has no geocoding service. Enter coordinates instead.")
                        @Suppress("DEPRECATION")
                        val matches=Geocoder(app).getFromLocationName(q,5).orEmpty()
                        matches.mapNotNull{a->
                            val label=a.getAddressLine(0)?.takeIf{it.isNotBlank()}
                                ?: listOfNotNull(a.featureName,a.locality,a.adminArea,a.countryName).distinct().joinToString(", ")
                            if(label.isBlank()) null else PlaceCandidate(label,a.latitude,a.longitude)
                        }
                    }
                }.onSuccess{matches->
                    placeCandidatesJson=JSONArray().apply{matches.forEach{c->put(JSONObject().put("label",c.label).put("latitude",c.latitude).put("longitude",c.longitude))}}.toString()
                    placeLookupStatus=if(matches.isEmpty()) "No matching places found. Try a fuller name/postcode, or enter coordinates." else "Choose the matching place below."
                }.onFailure{placeLookupStatus=it.message.orEmpty().ifBlank{"Place lookup failed. Enter coordinates instead."}}
                placeLookupLoading=false
            }
        }

        fun values():Map<String,String>{
            val d=if(la!=null&&lo!=null&&chosen!=null)GeocachingMath.distanceM(la,lo,chosen.latitude,chosen.longitude)else null
            val b=if(la!=null&&lo!=null&&chosen!=null)GeocachingMath.bearingDeg(la,lo,chosen.latitude,chosen.longitude)else null
            return mapOf(
                "geocache_nearby_result" to "${results.size} caches in ${radius} km around ${searchCenterLabel}",
                "geocache_nearby_current_latitude" to lat,
                "geocache_nearby_current_longitude" to lon,
                "geocache_nearby_radius_km" to radius,
                "geocache_nearby_source" to source,
                "geocache_nearby_cache_count" to results.size.toString(),
                "geocache_nearby_caches_json" to JSONArray().apply{results.forEach{put(it.toJson())}}.toString(),
                "geocache_nearby_selected_cache_code" to chosen?.code.orEmpty(),
                "geocache_nearby_selected_distance_m" to d?.gcFmt(1).orEmpty(),
                "geocache_nearby_selected_bearing_deg" to b?.gcFmt(1).orEmpty()
            )
        }

        LaunchedEffect(Unit){
            if(context.submitsImmediately){
                if(la!=null&&lo!=null){
                    if(source=="library") commitResult(context,As100NearbyCachesMethod,values(),onConfirmed) else load(source)
                }
            } else if(la==null||lo==null) {
                acquireNow()
            } else {
                searchAttempted=true
                if(source!="library") load(source)
            }
        }
        LaunchedEffect(remoteJson){
            if(context.submitsImmediately&&source!="library"&&remoteJson.isNotBlank()&&queryState=="success") commitResult(context,As100NearbyCachesMethod,values(),onConfirmed)
        }

        val canCommit = la!=null && lo!=null && searchAttempted && !loading && results.isNotEmpty()

        if (manageProviders) {
            val target = As100GeocachingAccountMethod.id
            val managedProvider = providerToManage.ifBlank { if(source=="library") providers.defaultProviderId() else source }
            val childSettings = buildMap {
                put("provider", managedProvider)
                if(openProviderEditor) put("open_editor", "true")
            }
            val action = ExternalActionRequest(target, target, childSettings)
            fun closeProviderManager(){
                manageProviders=false
                openProviderEditor=false
                providerRevision++
                remoteJson=""
                selected=""
                searchAttempted=false
                queryState="idle";queryError="";queryRecordCount=0;queryMore=false
                if(source!="library" && lat.toDoubleOrNull()!=null && lon.toDoubleOrNull()!=null){
                    load(source)
                }
            }
            Render(
                target,
                context.copy(action=action, stepNumber=context.stepNumber+1, totalSteps=context.totalSteps+1),
                ::closeProviderManager,
                { closeProviderManager() },
                ::closeProviderManager
            )
            return
        }

        MethodSurface("Nearby caches","Live nearby-cache map and ranked list.",context,onBack,onCancel,frozen!=null,onDone=frozen?.let{{onConfirmed(commitResult(context,As100NearbyCachesMethod,it,onConfirmed))}},committedKey=frozen,committedResultFactory=frozen?.let { committedValues -> { commitResult(context,As100NearbyCachesMethod,committedValues,onConfirmed) } },onDoneResult=onConfirmed){
            ProviderSourcePicker(
                providerProfiles,
                source,
                accounts,
                providers.defaultProviderId(),
                verifiedProviderId,
                onManage={
                    providerToManage=if(source=="library") providers.defaultProviderId() else source
                    openProviderEditor=false
                    manageProviders=true
                },
                onConfigure={providerId->
                    source=providerId
                    providerToManage=providerId
                    openProviderEditor=true
                    remoteJson=""
                    selected=""
                    searchAttempted=false
                    queryState="idle";queryError="";queryRecordCount=0;queryMore=false
                    manageProviders=true
                }
            ){providerId->
                source=providerId
                remoteJson=""
                selected=""
                searchAttempted=false
                queryState="idle";queryError="";queryRecordCount=0;queryMore=false
                if(lat.toDoubleOrNull()!=null&&lon.toDoubleOrNull()!=null) load(providerId)
            }
            SectionTitle("SEARCH CENTRE")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                if(searchOrigin=="current"&&!placePickerOpen) Button(onClick=::acquireNow,modifier=Modifier.weight(1f)){Text("My location")}
                else OutlinedButton(onClick=::acquireNow,modifier=Modifier.weight(1f)){Text("My location")}
                if(placePickerOpen||searchOrigin=="place"||searchOrigin=="manual") Button(onClick={placePickerOpen=true},modifier=Modifier.weight(1f)){Text("Choose place")}
                else OutlinedButton(onClick={placePickerOpen=true},modifier=Modifier.weight(1f)){Text("Choose place")}
            }
            if(la!=null&&lo!=null){
                HeroPanel{
                    Text(searchCenterLabel,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    CopyableValue("${la.gcFmt()}, ${lo.gcFmt()}","Search centre")
                }
            }
            if(placePickerOpen){
                OutlinedTextField(placeQuery,{placeQuery=it},label={Text("Place, postcode or address")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                Button(onClick=::findPlace,enabled=placeQuery.isNotBlank()&&!placeLookupLoading,modifier=Modifier.fillMaxWidth()){Text(if(placeLookupLoading)"Looking up…" else "Find place")}
                if(placeLookupStatus.isNotBlank())Text(placeLookupStatus,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                placeCandidates.forEach{candidate->
                    DashboardAction(candidate.label,"${candidate.latitude.gcFmt()}, ${candidate.longitude.gcFmt()}"){applySearchCenter(candidate,"place")}
                }
                Text("Or use coordinates",fontWeight=FontWeight.Bold)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(manualLat,{manualLat=it},label={Text("Latitude")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Text),modifier=Modifier.weight(1f),singleLine=true)
                    OutlinedTextField(manualLon,{manualLon=it},label={Text("Longitude")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Text),modifier=Modifier.weight(1f),singleLine=true)
                }
                OutlinedButton(onClick={
                    val ml=manualLat.toDoubleOrNull();val mn=manualLon.toDoubleOrNull()
                    if(ml==null||mn==null||ml !in -90.0..90.0||mn !in -180.0..180.0) placeLookupStatus="Enter valid latitude (−90…90) and longitude (−180…180)."
                    else applySearchCenter(PlaceCandidate("Chosen coordinates",ml,mn),"manual")
                },modifier=Modifier.fillMaxWidth()){Text("Use these coordinates")}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={load(source)},enabled=la!=null&&lo!=null&&!loading,modifier=Modifier.fillMaxWidth()){Text(if(loading)"Loading…" else "Refresh search")}
            }
            SliderCard("Radius","${radius.toDoubleOrNull()?.gcFmt(0)?:"10"} km",(radius.toFloatOrNull()?:10f).coerceIn(.1f,100f),.1f..100f){
                radius=it.toDouble().gcFmt(1); searchAttempted=false
            }
            Text(status,style=MaterialTheme.typography.bodySmall,color=if(canCommit)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)

            if(la==null||lo==null){
                HeroPanel{
                    Text("LOCATION NEEDED",fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary)
                    Text(if(locationAttempted)"MethodMesh could not obtain a location yet." else "Finding your position…",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text("Nearby caches is a live tool. Commit is unavailable until a position has been acquired and the selected source has been queried.")
                }
            } else if(source!="library" && queryState=="loading") {
                HeroPanel{
                    Text("CONTACTING PROVIDER",fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary)
                    Text("Validating the API key and running the nearby-cache query…",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text("Provider: ${providers.profile(source)?.label?:source} · radius ${radius.toDoubleOrNull()?.gcFmt(1)?:radius} km")
                }
            } else if(source!="library" && queryState=="parse_error") {
                HeroPanel{
                    Text("PROVIDER DATA ERROR",fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.error)
                    Text("The API key worked, but MethodMesh could not read the returned cache records.",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text(queryError)
                    Text("This is not an empty search and not an authentication failure.")
                }
            } else if(source!="library" && queryState=="error") {
                HeroPanel{
                    Text("PROVIDER ERROR",fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.error)
                    Text("The provider did not complete this search.",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text(queryError.ifBlank{"No diagnostic message was returned."})
                    Text("Your key is saved, but it has not been verified by a successful request. Tap the provider row to edit it, or Refresh to retry.")
                }
            } else if(source!="library" && queryState=="success" && results.isEmpty()) {
                HeroPanel{
                    Text("PROVIDER RESPONDED",fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary)
                    Text("The request succeeded, but 0 caches were returned.",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text("${providers.profile(source)?.label?:source} accepted the API key. Search radius: ${radius.toDoubleOrNull()?.gcFmt(1)?:radius} km · provider records: $queryRecordCount${if(queryMore) " · more available" else ""}.")
                    Text("This is a genuine empty result, not an authentication failure.")
                }
            } else if(searchAttempted && results.isEmpty()) {
                HeroPanel{
                    Text("NO CACHES SHOWN",fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary)
                    Text(if(source=="library"&&repo.caches().isEmpty())"Your offline cache library is empty." else "Nothing was found in the current search.",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text(if(source=="library")"Import a GPX in Cache library, increase the radius, or switch to a configured OKAPI provider." else "Refresh or check the provider configuration.")
                }
            }

            if(la!=null&&lo!=null&&results.isNotEmpty()){
                Radar(results,la,lo,selected){selected=it}
                GeoMap(listOf(MapPoint(if(searchOrigin=="current") "You" else searchCenterLabel,la,lo,"__me__"))+results.map{MapPoint(it.name,it.latitude,it.longitude,it.code)})
                SectionTitle("NEARBY")
            }
            results.take(60).forEach{c->
                CacheRow(c,c.code==selected,if(la!=null&&lo!=null)GeocachingMath.distanceM(la,lo,c.latitude,c.longitude)else null){selected=c.code}
            }
            chosen?.let{
                HeroPanel{
                    Text(it.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black)
                    CopyableValue(it.code)
                    CopyableValue("${it.latitude.gcFmt()}, ${it.longitude.gcFmt()}")
                    val d=if(la!=null&&lo!=null)GeocachingMath.distanceM(la,lo,it.latitude,it.longitude)else null
                    d?.let{distance->Text("${distanceLabel(distance)} away")}
                    OutlinedButton(onClick={repo.saveCache(it);status="Saved offline."},modifier=Modifier.fillMaxWidth()){Text("Save selected offline")}
                }
            }
            if(canCommit) CommitButton(frozen!=null){frozenJson=valuesToJson(values())}
            else Text(if(results.isEmpty() && searchAttempted) "Nothing to commit yet — at least one cache must be returned." else "Commit becomes available after location and a populated search result are ready.",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun Navigate(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val app=LocalContext.current;val repo=remember(app){GeocachingRepository(app)};var code by rememberSaveable{mutableStateOf(context.action.setting("cache_code").orEmpty())};val stored=remember(code){repo.caches().firstOrNull{it.code.equals(code,true)}};var name by rememberSaveable{mutableStateOf(context.action.setting("cache_name")?:stored?.name.orEmpty())};var targetLat by rememberSaveable{mutableStateOf(context.action.setting("target_latitude")?:stored?.latitude?.gcFmt().orEmpty())};var targetLon by rememberSaveable{mutableStateOf(context.action.setting("target_longitude")?:stored?.longitude?.gcFmt().orEmpty())};var arrival by rememberSaveable{mutableStateOf(context.action.setting("arrival_radius_m")?:"15")};var current by remember{mutableStateOf<Location?>(null)};var status by rememberSaveable{mutableStateOf("Waiting for GPS…")};var frozenJson by rememberSaveable{mutableStateOf<String?>(null)};val frozen=frozenJson?.let(::jsonToValues);var started by rememberSaveable{mutableStateOf(repo.activeHunt()?.takeIf{it.cacheCode==code}?.startedAt?:Instant.now().toString())}
        val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(!it.values.any{g->g})status="Location permission is required."};DisposableEffect(app){PhoneSensorRepository.start(app);onDispose{PhoneSensorRepository.stop()}};LiveLocation(app,hasLocation(app),onLocation={current=it;status="GPS ±${it.accuracy.roundToInt()} m"})
        LaunchedEffect(Unit){if(!hasLocation(app))permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
        val tl=targetLat.toDoubleOrNull();val to=targetLon.toDoubleOrNull();val d=if(current!=null&&tl!=null&&to!=null)GeocachingMath.distanceM(current!!.latitude,current!!.longitude,tl,to)else null;val b=if(current!=null&&tl!=null&&to!=null)GeocachingMath.bearingDeg(current!!.latitude,current!!.longitude,tl,to)else null;val h=PhoneSensorRepository.headingDegrees?.toDouble();val rel=if(b!=null&&h!=null)GeocachingMath.relativeBearing(b,h)else null;val arrived=d!=null&&d<=(arrival.toDoubleOrNull()?:15.0)
        fun values()=mapOf("geocache_navigate_result" to (if(arrived)"Arrived at ${name.ifBlank{code}}" else d?.let{"${distanceLabel(it)} to ${name.ifBlank{code}}"}?:"Waiting for navigation fix"),"geocache_navigate_cache_code" to code,"geocache_navigate_cache_name" to name,"geocache_navigate_target_latitude" to targetLat,"geocache_navigate_target_longitude" to targetLon,"geocache_navigate_current_latitude" to current?.latitude?.gcFmt().orEmpty(),"geocache_navigate_current_longitude" to current?.longitude?.gcFmt().orEmpty(),"geocache_navigate_accuracy_m" to current?.accuracy?.toDouble()?.gcFmt(1).orEmpty(),"geocache_navigate_distance_m" to d?.gcFmt(1).orEmpty(),"geocache_navigate_bearing_deg" to b?.gcFmt(1).orEmpty(),"geocache_navigate_heading_deg" to h?.gcFmt(1).orEmpty(),"geocache_navigate_relative_bearing_deg" to rel?.gcFmt(1).orEmpty(),"geocache_navigate_arrived" to arrived.toString(),"geocache_navigate_arrival_radius_m" to arrival,"geocache_navigate_started_at_iso" to started)
        MethodSurface("Navigate","Compass first. Distance and target immediately below.",context,onBack,onCancel,frozen!=null,onDone=frozen?.let{{onConfirmed(commitResult(context,As100NavigateToCacheMethod,it,onConfirmed))}},committedKey=frozen,committedResultFactory=frozen?.let { committedValues -> { commitResult(context,As100NavigateToCacheMethod,committedValues,onConfirmed) } },onDoneResult=onConfirmed){NavigationCompass(rel,d,h,arrived);Text(status,style=MaterialTheme.typography.bodySmall);d?.let{CopyableValue(it.gcFmt(1),"Distance metres")};tl?.let{CopyableValue("${it.gcFmt()}, ${to?.gcFmt().orEmpty()}","Target")};current?.let{CopyableValue("${it.latitude.gcFmt()}, ${it.longitude.gcFmt()}","Current location")};if(tl==null||to==null){OutlinedTextField(code,{code=it},label={Text("Cache code")},modifier=Modifier.fillMaxWidth());OutlinedTextField(targetLat,{targetLat=it},label={Text("Target latitude")},modifier=Modifier.fillMaxWidth());OutlinedTextField(targetLon,{targetLon=it},label={Text("Target longitude")},modifier=Modifier.fillMaxWidth())};SliderCard("Arrival radius","${arrival.toDoubleOrNull()?.gcFmt(0)?:"15"} m",(arrival.toFloatOrNull()?:15f).coerceIn(1f,200f),1f..200f){arrival=it.roundToInt().toString()};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={if(tl!=null&&to!=null){started=Instant.now().toString();repo.setActiveHunt(ActiveHunt(code,name,stored?.source?:CacheSource.MANUAL,tl,to,started,arrival.toDoubleOrNull()?:15.0));status="Hunt saved across app restarts."}},modifier=Modifier.weight(1f)){Text("Keep active")};OutlinedButton(onClick={repo.setActiveHunt(null);status="Active hunt cleared."},modifier=Modifier.weight(1f)){Text("Stop hunt")}};CommitButton(frozen!=null){frozenJson=valuesToJson(values());if(context.presentationMode.name=="Dashboard"&&arrived)repo.setActiveHunt(null)}}
    }

    @Composable
    private fun Details(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val app=LocalContext.current;val repo=remember(app){GeocachingRepository(app)};val accounts=remember(app){GeocachingAccountStore(app)};val scope=rememberCoroutineScope();var code by rememberSaveable{mutableStateOf(context.action.setting("cache_code").orEmpty())};var cacheJson by rememberSaveable{mutableStateOf("")};var revealHint by rememberSaveable{mutableStateOf(false)};var status by rememberSaveable{mutableStateOf("")};var frozenJson by rememberSaveable{mutableStateOf<String?>(null)};val frozen=frozenJson?.let(::jsonToValues)
        val local=remember(code){repo.caches().firstOrNull{it.code.equals(code,true)}};val fetched=remember(cacheJson){runCatching{if(cacheJson.isBlank())null else CacheRecord.fromJson(JSONObject(cacheJson))}.getOrNull()};val supplied=remember(context.action.settings){suppliedCache(context.action)};val cache=fetched?:local?:supplied
        fun values(c:CacheRecord?)=if(c==null)mapOf("geocache_details_result" to "Cache not found","geocache_details_code" to code)else mapOf("geocache_details_result" to "${c.code} · ${c.name}","geocache_details_code" to c.code,"geocache_details_name" to c.name,"geocache_details_source" to c.source.id,"geocache_details_latitude" to c.latitude.gcFmt(),"geocache_details_longitude" to c.longitude.gcFmt(),"geocache_details_type" to c.type,"geocache_details_size" to c.size,"geocache_details_difficulty" to c.difficulty?.gcFmt(1).orEmpty(),"geocache_details_terrain" to c.terrain?.gcFmt(1).orEmpty(),"geocache_details_owner" to c.owner,"geocache_details_description" to c.description,"geocache_details_hint" to c.hint,"geocache_details_attributes_json" to JSONArray(c.attributes).toString(),"geocache_details_recent_logs_json" to JSONArray(c.recentLogs).toString(),"geocache_details_waypoints_json" to JSONArray().apply{c.waypoints.forEach{put(it.toJson())}}.toString(),"geocache_details_provider_url" to c.providerUrl)
        fun online(){val providers=GeocachingProviderRegistry(app);val src=context.action.setting("source")?.takeIf{providers.profile(it)!=null}?:providers.defaultProviderId();val profile=providers.profile(src)?:run{status="Provider not configured.";return};val inst=profile.installation();val key=accounts.consumerKey(profile.id);val secret=accounts.consumerSecret(profile.id);if(key.isBlank()){status="${profile.label} needs an OKAPI consumer key in Providers & accounts.";return};scope.launch{runCatching{withContext(Dispatchers.IO){OkapiClient(inst,key,secret).cache(code,accounts.accessToken(profile.id).takeIf{it.isNotBlank() && secret.isNotBlank()},accounts.accessSecret(profile.id).takeIf{it.isNotBlank() && secret.isNotBlank()})}}.onSuccess{cacheJson=it.toJson().toString();status="Loaded from ${profile.label}."}.onFailure{status=it.message.orEmpty()}}}
        LaunchedEffect(cache?.code){if(context.submitsImmediately&&cache!=null)commitResult(context,As100CacheDetailsMethod,values(cache),onConfirmed)}
        MethodSurface("Cache details","Readable listing rather than a settings form.",context,onBack,onCancel,frozen!=null,onDone=frozen?.let{{onConfirmed(commitResult(context,As100CacheDetailsMethod,it,onConfirmed))}},committedKey=frozen,committedResultFactory=frozen?.let { committedValues -> { commitResult(context,As100CacheDetailsMethod,committedValues,onConfirmed) } },onDoneResult=onConfirmed){if(cache==null){OutlinedTextField(code,{code=it},label={Text("Cache code")},modifier=Modifier.fillMaxWidth(),singleLine=true);Button(onClick=::online,modifier=Modifier.fillMaxWidth()){Text("Load cache")};Text(status)}else{HeroPanel{Text(cache.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);CopyableValue(cache.code);CopyableValue("${cache.latitude.gcFmt()}, ${cache.longitude.gcFmt()}","Coordinates");Text("${cache.type} · ${cache.size} · D ${cache.difficulty?:"—"} / T ${cache.terrain?:"—"}")};if(cache.owner.isNotBlank())CopyableValue(cache.owner,"Owner");if(cache.description.isNotBlank()){SectionTitle("DESCRIPTION");Text(cache.description)};if(cache.hint.isNotBlank()){OutlinedButton(onClick={revealHint=!revealHint},modifier=Modifier.fillMaxWidth()){Text(if(revealHint)"Hide hint" else "Reveal hint")};if(revealHint)Text(cache.hint)};if(cache.waypoints.isNotEmpty()){SectionTitle("ADDITIONAL WAYPOINTS");cache.waypoints.forEach{CopyableValue("${it.latitude.gcFmt()}, ${it.longitude.gcFmt()}",it.name)}};if(cache.recentLogs.isNotEmpty()){SectionTitle("RECENT LOGS");cache.recentLogs.take(10).forEach{Text(it,style=MaterialTheme.typography.bodySmall)}};CommitButton(frozen!=null){frozenJson=valuesToJson(values(cache))}}}
    }

    @Composable
    private fun Project(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        var lat by rememberSaveable{mutableStateOf(context.action.setting("origin_latitude").orEmpty())};var lon by rememberSaveable{mutableStateOf(context.action.setting("origin_longitude").orEmpty())};var bearing by rememberSaveable{mutableStateOf(context.action.setting("bearing_deg")?:"0")};var distance by rememberSaveable{mutableStateOf(context.action.setting("distance_m")?:"100")};var frozenJson by rememberSaveable{mutableStateOf<String?>(null)};val frozen=frozenJson?.let(::jsonToValues)
        val p=runCatching{GeocachingMath.project(lat.toDouble(),lon.toDouble(),bearing.toDouble(),distance.toDouble())}.getOrNull();fun values()=mapOf("geocache_project_result" to p?.let{"${it.first.gcFmt()}, ${it.second.gcFmt()}"}.orEmpty(),"geocache_project_origin_latitude" to lat,"geocache_project_origin_longitude" to lon,"geocache_project_bearing_deg" to bearing,"geocache_project_distance_m" to distance,"geocache_project_projected_latitude" to p?.first?.gcFmt().orEmpty(),"geocache_project_projected_longitude" to p?.second?.gcFmt().orEmpty())
        LaunchedEffect(Unit){if(context.submitsImmediately&&p!=null)commitResult(context,As100ProjectWaypointMethod,values(),onConfirmed)}
        MethodSurface("Project waypoint","Projected coordinate first; controls underneath.",context,onBack,onCancel,frozen!=null,onDone=frozen?.let{{onConfirmed(commitResult(context,As100ProjectWaypointMethod,it,onConfirmed))}},committedKey=frozen,committedResultFactory=frozen?.let { committedValues -> { commitResult(context,As100ProjectWaypointMethod,committedValues,onConfirmed) } },onDoneResult=onConfirmed){HeroPanel{Text("PROJECTED",fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary);CopyableValue(p?.let{"${it.first.gcFmt()}, ${it.second.gcFmt()}"}?:"Enter a valid origin, bearing and distance")};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(lat,{lat=it},label={Text("Latitude")},modifier=Modifier.weight(1f));OutlinedTextField(lon,{lon=it},label={Text("Longitude")},modifier=Modifier.weight(1f))};OutlinedTextField(bearing,{bearing=it},label={Text("Bearing °")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Text));OutlinedTextField(distance,{distance=it},label={Text("Distance m")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Text));CommitButton(frozen!=null){if(p!=null)frozenJson=valuesToJson(values())}}
    }

    @Composable
    private fun Average(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val app=LocalContext.current;var samplesJson by rememberSaveable{mutableStateOf(context.action.setting("samples").orEmpty())};var samples by rememberSaveable{mutableStateOf(parseSampleTriples(samplesJson))};var status by rememberSaveable{mutableStateOf("")};var frozenJson by rememberSaveable{mutableStateOf<String?>(null)};val frozen=frozenJson?.let(::jsonToValues);val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(it.values.any{g->g})freshLocation(app,{l->samples=samples+Triple(l.latitude,l.longitude,l.accuracy.toDouble());status="Added GPS fix ±${l.accuracy.roundToInt()} m"},{status=it})}
        val mean=if(samples.isEmpty())null else GeocachingMath.meanCoordinate(samples.map{it.first to it.second});val acc=samples.map{it.third};fun values()=mapOf("geocache_average_result" to mean?.let{"${it.first.gcFmt()}, ${it.second.gcFmt()}"}.orEmpty(),"geocache_average_sample_count" to samples.size.toString(),"geocache_average_mean_latitude" to mean?.first?.gcFmt().orEmpty(),"geocache_average_mean_longitude" to mean?.second?.gcFmt().orEmpty(),"geocache_average_mean_accuracy_m" to acc.takeIf{it.isNotEmpty()}?.average()?.gcFmt(1).orEmpty(),"geocache_average_min_accuracy_m" to acc.minOrNull()?.gcFmt(1).orEmpty(),"geocache_average_max_accuracy_m" to acc.maxOrNull()?.gcFmt(1).orEmpty())
        LaunchedEffect(Unit){if(context.submitsImmediately&&samples.isNotEmpty())commitResult(context,As100AverageCoordinatesMethod,values(),onConfirmed)}
        MethodSurface("Average coordinates","Repeated high-accuracy fixes, averaged into one field coordinate.",context,onBack,onCancel,frozen!=null,onDone=frozen?.let{{onConfirmed(commitResult(context,As100AverageCoordinatesMethod,it,onConfirmed))}},committedKey=frozen,committedResultFactory=frozen?.let { committedValues -> { commitResult(context,As100AverageCoordinatesMethod,committedValues,onConfirmed) } },onDoneResult=onConfirmed){HeroPanel{Text("CURRENT AVERAGE",fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary);CopyableValue(mean?.let{"${it.first.gcFmt()}, ${it.second.gcFmt()}"}?:"No samples yet");Text("${samples.size} samples${acc.minOrNull()?.let{" · best ±${it.gcFmt(1)} m"}.orEmpty()}")};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={if(hasLocation(app))freshLocation(app,{l->samples=samples+Triple(l.latitude,l.longitude,l.accuracy.toDouble());status="Added GPS fix ±${l.accuracy.roundToInt()} m"},{status=it})else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},modifier=Modifier.weight(1f)){Text("Add GPS fix")};OutlinedButton(onClick={if(samples.isNotEmpty())samples=samples.dropLast(1)},enabled=samples.isNotEmpty(),modifier=Modifier.weight(1f)){Text("Undo")};OutlinedButton(onClick={samples=emptyList()},enabled=samples.isNotEmpty(),modifier=Modifier.weight(1f)){Text("Clear")}};if(status.isNotBlank())Text(status);if(!context.startsImmediately){OutlinedTextField(samples.joinToString("\n"){"${it.first},${it.second},${it.third}"},{text->samples=parseSampleTriples(text)},label={Text("Paste coordinate samples")},modifier=Modifier.fillMaxWidth(),minLines=3)};CommitButton(frozen!=null){if(samples.isNotEmpty())frozenJson=valuesToJson(values())}}
    }

    @Composable
    private fun RecordVisit(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val app=LocalContext.current;val repo=remember(app){GeocachingRepository(app)};var code by rememberSaveable{mutableStateOf(context.action.setting("cache_code")?:repo.activeHunt()?.cacheCode.orEmpty())};val cache=remember(code){repo.caches().firstOrNull{it.code.equals(code,true)}};var name by rememberSaveable{mutableStateOf(context.action.setting("cache_name")?:cache?.name.orEmpty())};var type by rememberSaveable{mutableStateOf(context.action.setting("visit_type")?:"found")};var note by rememberSaveable{mutableStateOf(context.action.setting("note").orEmpty())};var favorite by rememberSaveable{mutableStateOf(context.action.setting("favorite")?.toBooleanStrictOrNull()?:false)};var location by remember{mutableStateOf<Location?>(null)};var photoArtifact by rememberSaveable{mutableStateOf("")};var photoOutput by rememberSaveable{mutableStateOf("")};var status by rememberSaveable{mutableStateOf("")};var frozenJson by rememberSaveable{mutableStateOf<String?>(null)};val frozen=frozenJson?.let(::jsonToValues);var pendingPhoto by remember{mutableStateOf<File?>(null)}
        val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(it.values.any{g->g})freshLocation(app,{location=it;status="Geotag ±${it.accuracy.roundToInt()} m"},{status=it})};LaunchedEffect(Unit){context.action.setting("actual_latitude")?.toDoubleOrNull()?.let{la->context.action.setting("actual_longitude")?.toDoubleOrNull()?.let{lo->location=Location("supplied").apply{latitude=la;longitude=lo;accuracy=context.action.setting("accuracy_m")?.toFloatOrNull()?:0f}}}}
        fun persistPhoto(file:File){location?.let{runCatching{ExifInterface(file.absolutePath).apply{setExifGps(it.latitude,it.longitude);setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss",java.util.Locale.US).format(java.util.Date()));saveAttributes()}}};photoArtifact=repo.savePersistentBytes("Geocache ${code.ifBlank{"visit"}} ${System.currentTimeMillis()}.jpg","image/jpeg",file.readBytes()).id;photoOutput=materialiseArtifact(app,repo,photoArtifact,"visit-photo.jpg");status="Photo saved to Files with visit geotag."}
        val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->pendingPhoto?.let{if(ok)persistPhoto(it)else it.delete()};pendingPhoto=null};val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){val f=File(app.cacheDir,"geocache-picked-${System.currentTimeMillis()}.jpg");app.contentResolver.openInputStream(uri)?.use{input->f.outputStream().use{input.copyTo(it)}};persistPhoto(f)}}
        fun capturePhoto(){val f=File(app.cacheDir,"geocache-camera-${System.currentTimeMillis()}.jpg");pendingPhoto=f;camera.launch(FileProvider.getUriForFile(app,"${app.packageName}.fileprovider",f))}
        fun makeRecord():CacheVisitRecord{val id=context.action.setting("visit_id")?:UUID.randomUUID().toString();val ts=context.action.setting("timestamp")?:Instant.now().toString();val suppliedLat=context.action.setting("actual_latitude")?.toDoubleOrNull();val suppliedLon=context.action.setting("actual_longitude")?.toDoubleOrNull();val actualLat=location?.latitude?:suppliedLat;val actualLon=location?.longitude?:suppliedLon;return CacheVisitRecord(id,code,name,cache?.source?:CacheSource.from(context.action.setting("source")),VisitType.from(type),ts,actualLat,actualLon,location?.accuracy?.toDouble()?:context.action.setting("accuracy_m")?.toDoubleOrNull(),cache?.latitude?:context.action.setting("published_latitude")?.toDoubleOrNull(),cache?.longitude?:context.action.setting("published_longitude")?.toDoubleOrNull(),note,favorite,listOfNotNull(photoArtifact.takeIf{it.isNotBlank()}),remoteState="local_only")}
        fun values(r:CacheVisitRecord,persisted:Boolean):Map<String,String> = mapOf("geocache_visit_result" to "${r.visitType.label} · ${r.cacheName.ifBlank{r.cacheCode}}","geocache_visit_id" to r.id,"geocache_visit_cache_code" to r.cacheCode,"geocache_visit_cache_name" to r.cacheName,"geocache_visit_type" to r.visitType.id,"geocache_visit_timestamp" to r.timestamp,"geocache_visit_actual_latitude" to r.actualLatitude?.gcFmt().orEmpty(),"geocache_visit_actual_longitude" to r.actualLongitude?.gcFmt().orEmpty(),"geocache_visit_accuracy_m" to r.accuracyM?.gcFmt(1).orEmpty(),"geocache_visit_published_latitude" to r.publishedLatitude?.gcFmt().orEmpty(),"geocache_visit_published_longitude" to r.publishedLongitude?.gcFmt().orEmpty(),"geocache_visit_published_offset_m" to publishedOffset(r)?.gcFmt(1).orEmpty(),"geocache_visit_note" to r.note,"geocache_visit_favorite" to r.favorite.toString(),"geocache_visit_photo_count" to r.photoArtifactIds.size.toString(),"geocache_visit_photo_uri" to photoOutput,"geocache_visit_persisted_to_ledger" to persisted.toString(),"geocache_visit_remote_state" to r.remoteState)
        MethodSurface("Record visit","Your actual field observation, not merely the published cache point.",context,onBack,onCancel,frozen!=null,onDone=frozen?.let{{onConfirmed(commitResult(context,As100RecordCacheVisitMethod,it,onConfirmed))}},committedKey=frozen,committedResultFactory=frozen?.let { committedValues -> { commitResult(context,As100RecordCacheVisitMethod,committedValues,onConfirmed) } },onDoneResult=onConfirmed){Segmented(VisitType.entries.map{it.id to it.label},type){type=it};HeroPanel{Text(name.ifBlank{code.ifBlank{"CACHE VISIT"}},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black);if(code.isNotBlank())CopyableValue(code,"Cache");location?.let{CopyableValue("${it.latitude.gcFmt()}, ${it.longitude.gcFmt()}","Actual GPS position");Text("±${it.accuracy.roundToInt()} m")};cache?.let{Text("Published point ${it.latitude.gcFmt()}, ${it.longitude.gcFmt()}")}}
            if(code.isBlank())OutlinedTextField(code,{code=it},label={Text("Cache code")},modifier=Modifier.fillMaxWidth());if(name.isBlank())OutlinedTextField(name,{name=it},label={Text("Cache name")},modifier=Modifier.fillMaxWidth());OutlinedTextField(note,{note=it},label={Text("Field note")},modifier=Modifier.fillMaxWidth(),minLines=3);Row(verticalAlignment=Alignment.CenterVertically){Switch(favorite,{favorite=it});Spacer(Modifier.width(8.dp));Text("Favourite")};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={if(hasLocation(app))freshLocation(app,{location=it;status="Geotag ±${it.accuracy.roundToInt()} m"},{status=it})else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},modifier=Modifier.weight(1f)){Text("Use GPS")};OutlinedButton(onClick=::capturePhoto,modifier=Modifier.weight(1f)){Text(if(photoArtifact.isBlank())"Take photo" else "Retake")};OutlinedButton(onClick={picker.launch(arrayOf("image/*"))},modifier=Modifier.weight(1f)){Text("Choose photo")}};if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall);if(photoArtifact.isNotBlank())Text("1 geotagged photo saved in Files",fontWeight=FontWeight.SemiBold)
            CommitButton(frozen!=null){if(code.isBlank()){status="Cache code is required."}else{val r=makeRecord();val persist=if(context.presentationMode.name=="Dashboard")true else context.action.setting("persist_to_ledger")?.toBooleanStrictOrNull()?:false;if(persist)repo.appendVisit(r);if(persist&&(r.visitType==VisitType.FOUND||r.visitType==VisitType.DNF))repo.setActiveHunt(null);frozenJson=valuesToJson(values(r,persist))}}
        }
    }

    @Composable
    private fun History(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val app=LocalContext.current;val repo=remember(app){GeocachingRepository(app)};var query by rememberSaveable{mutableStateOf(context.action.setting("query").orEmpty())};var kind by rememberSaveable{mutableStateOf(context.action.setting("visit_type")?:"all")};var selected by rememberSaveable{mutableStateOf(context.action.setting("selected_visit_id").orEmpty())};var export by rememberSaveable{mutableStateOf(context.action.setting("export_format")?:"none")};var status by rememberSaveable{mutableStateOf("")};var frozenJson by rememberSaveable{mutableStateOf<String?>(null)};val frozen=frozenJson?.let(::jsonToValues);val all=remember{repo.visits().sortedByDescending{it.timestamp}}
        val filtered=all.filter{(kind=="all"||it.visitType.id==kind)&&(query.isBlank()||it.cacheCode.contains(query,true)||it.cacheName.contains(query,true)||it.note.contains(query,true))&&context.action.setting("cache_code")?.let{c->it.cacheCode.equals(c,true)}?:true&&context.action.setting("since_iso")?.let{s->it.timestamp>=s}?:true&&context.action.setting("until_iso")?.let{u->it.timestamp<=u}?:true};val chosen=filtered.firstOrNull{it.id==selected};fun exports():Triple<String,String,String>{var csv="";var geo="";var notes="";when(export){"csv"->csv=materialiseBytes(app,"geocache_history.csv",repo.visitCsv(filtered));"geojson"->geo=materialiseBytes(app,"geocache_history.geojson",repo.visitGeoJson(filtered));"field_notes"->notes=materialiseBytes(app,"geocache_visits.txt",repo.geocachingFieldNotes(filtered))};return Triple(csv,geo,notes)}
        fun values():Map<String,String>{val (csv,geo,notes)=exports();return mapOf("geocache_history_result" to "${filtered.size} visit records","geocache_history_record_count" to filtered.size.toString(),"geocache_history_found_count" to filtered.count{it.visitType==VisitType.FOUND}.toString(),"geocache_history_dnf_count" to filtered.count{it.visitType==VisitType.DNF}.toString(),"geocache_history_note_count" to filtered.count{it.visitType==VisitType.NOTE}.toString(),"geocache_history_photo_count" to filtered.sumOf{it.photoArtifactIds.size}.toString(),"geocache_history_records_json" to jsonVisits(filtered),"geocache_history_selected_visit_id" to chosen?.id.orEmpty(),"geocache_history_selected_photo_uri" to chosen?.photoArtifactIds?.firstOrNull()?.let{materialiseArtifact(app,repo,it,"find-photo.jpg")}.orEmpty(),"geocache_history_csv_export_uri" to csv,"geocache_history_geojson_export_uri" to geo,"geocache_history_field_notes_export_uri" to notes)}
        LaunchedEffect(Unit){if(context.submitsImmediately)commitResult(context,As100CacheHistoryMethod,values(),onConfirmed)}
        MethodSurface("Find history","Persistent field ledger: search it, map it, export it.",context,onBack,onCancel,frozen!=null,onDone=frozen?.let{{onConfirmed(commitResult(context,As100CacheHistoryMethod,it,onConfirmed))}},committedKey=frozen,committedResultFactory=frozen?.let { committedValues -> { commitResult(context,As100CacheHistoryMethod,committedValues,onConfirmed) } },onDoneResult=onConfirmed){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){StatTile(filtered.count{it.visitType==VisitType.FOUND}.toString(),"Found",Modifier.weight(1f));StatTile(filtered.count{it.visitType==VisitType.DNF}.toString(),"DNF",Modifier.weight(1f));StatTile(filtered.sumOf{it.photoArtifactIds.size}.toString(),"Photos",Modifier.weight(1f))};OutlinedTextField(query,{query=it},label={Text("Search finds")},modifier=Modifier.fillMaxWidth());Segmented(listOf("all" to "All","found" to "Found","dnf" to "DNF","note" to "Notes"),kind){kind=it};val pts=filtered.mapNotNull{if(it.actualLatitude!=null&&it.actualLongitude!=null)MapPoint(it.cacheName.ifBlank{it.cacheCode},it.actualLatitude,it.actualLongitude,it.id)else null};if(pts.isNotEmpty())GeoMap(pts);filtered.take(100).forEach{v->DashboardAction("${v.visitType.label} · ${v.cacheName.ifBlank{v.cacheCode}}","${friendlyTime(v.timestamp)}${v.actualLatitude?.let{" · actual GPS"}.orEmpty()}${if(v.photoArtifactIds.isNotEmpty())" · 📷 ${v.photoArtifactIds.size}" else ""}"){selected=v.id}};chosen?.let{HeroPanel{Text(it.cacheName.ifBlank{it.cacheCode},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black);CopyableValue(it.cacheCode);CopyableValue(it.timestamp,"Visit time");it.actualLatitude?.let{la->CopyableValue("${la.gcFmt()}, ${it.actualLongitude?.gcFmt().orEmpty()}","Actual find position")};publishedOffset(it)?.let{o->Text("${o.gcFmt(1)} m from published cache point")};if(it.note.isNotBlank())Text(it.note)}};SectionTitle("EXPORT");Segmented(listOf("none" to "None","csv" to "CSV","geojson" to "GeoJSON","field_notes" to "Field notes"),export){export=it};OutlinedButton(onClick={when(export){"csv"->repo.savePersistentBytes("Geocache find history.csv","text/csv",repo.visitCsv(filtered));"geojson"->repo.savePersistentBytes("Geocache find history.geojson","application/geo+json",repo.visitGeoJson(filtered));"field_notes"->repo.savePersistentBytes("geocache_visits.txt","text/plain",repo.geocachingFieldNotes(filtered));else->return@OutlinedButton};status="Export saved to Files."},enabled=export!="none",modifier=Modifier.fillMaxWidth()){Text("Save export to Files")};if(status.isNotBlank())Text(status);CommitButton(frozen!=null){frozenJson=valuesToJson(values())}}
    }

    @Composable
    private fun TrackableRecord(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val app=LocalContext.current;val repo=remember(app){GeocachingRepository(app)};var code by rememberSaveable{mutableStateOf(context.action.setting("trackable_code").orEmpty())};var name by rememberSaveable{mutableStateOf(context.action.setting("trackable_name").orEmpty())};var event by rememberSaveable{mutableStateOf(context.action.setting("event_type")?:"discover")};var cacheCode by rememberSaveable{mutableStateOf(context.action.setting("cache_code").orEmpty())};var cacheName by rememberSaveable{mutableStateOf(context.action.setting("cache_name").orEmpty())};var note by rememberSaveable{mutableStateOf(context.action.setting("note").orEmpty())};var loc by remember{mutableStateOf<Location?>(null)};var photoArtifact by rememberSaveable{mutableStateOf("")};var photoUri by rememberSaveable{mutableStateOf("")};var frozenJson by rememberSaveable{mutableStateOf<String?>(null)};val frozen=frozenJson?.let(::jsonToValues);var status by rememberSaveable{mutableStateOf("")};var pendingPhoto by remember{mutableStateOf<File?>(null)}
        val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(it.values.any{g->g})freshLocation(app,{loc=it},{status=it})};fun persistPhoto(f:File){loc?.let{l->runCatching{ExifInterface(f.absolutePath).apply{setExifGps(l.latitude,l.longitude);saveAttributes()}}};photoArtifact=repo.savePersistentBytes("Trackable ${code.ifBlank{"event"}} ${System.currentTimeMillis()}.jpg","image/jpeg",f.readBytes()).id;photoUri=materialiseArtifact(app,repo,photoArtifact,"trackable-photo.jpg")};val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->pendingPhoto?.let{if(ok)persistPhoto(it)else it.delete()};pendingPhoto=null};fun takePhoto(){val f=File(app.cacheDir,"trackable-${System.currentTimeMillis()}.jpg");pendingPhoto=f;camera.launch(FileProvider.getUriForFile(app,"${app.packageName}.fileprovider",f))}
        fun record()=TrackableEventRecord(context.action.setting("event_id")?:UUID.randomUUID().toString(),code,name,TrackableEventType.from(event),context.action.setting("timestamp")?:Instant.now().toString(),cacheCode,cacheName,loc?.latitude?:context.action.setting("latitude")?.toDoubleOrNull(),loc?.longitude?:context.action.setting("longitude")?.toDoubleOrNull(),loc?.accuracy?.toDouble()?:context.action.setting("accuracy_m")?.toDoubleOrNull(),note,listOfNotNull(photoArtifact.takeIf{it.isNotBlank()}))
        fun values(r:TrackableEventRecord,persisted:Boolean)=mapOf("geocache_trackable_record_result" to "${r.eventType.label} · ${r.trackableName.ifBlank{r.trackableCode}}","geocache_trackable_record_event_id" to r.id,"geocache_trackable_record_trackable_code" to r.trackableCode,"geocache_trackable_record_trackable_name" to r.trackableName,"geocache_trackable_record_event_type" to r.eventType.id,"geocache_trackable_record_timestamp" to r.timestamp,"geocache_trackable_record_cache_code" to r.cacheCode,"geocache_trackable_record_cache_name" to r.cacheName,"geocache_trackable_record_latitude" to r.latitude?.gcFmt().orEmpty(),"geocache_trackable_record_longitude" to r.longitude?.gcFmt().orEmpty(),"geocache_trackable_record_accuracy_m" to r.accuracyM?.gcFmt(1).orEmpty(),"geocache_trackable_record_note" to r.note,"geocache_trackable_record_photo_count" to r.photoArtifactIds.size.toString(),"geocache_trackable_record_photo_uri" to photoUri,"geocache_trackable_record_persisted_to_ledger" to persisted.toString())
        MethodSurface("Trackable event","Follow a public trackable reference across time. Private tracking codes are never stored.",context,onBack,onCancel,frozen!=null,onDone=frozen?.let{{onConfirmed(commitResult(context,As100TrackableRecordMethod,it,onConfirmed))}},committedKey=frozen,committedResultFactory=frozen?.let { committedValues -> { commitResult(context,As100TrackableRecordMethod,committedValues,onConfirmed) } },onDoneResult=onConfirmed){HeroPanel{Text(name.ifBlank{code.ifBlank{"TRACKABLE"}},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black);if(code.isNotBlank())CopyableValue(code,"Public reference")};OutlinedTextField(code,{code=it},label={Text("Trackable reference")},modifier=Modifier.fillMaxWidth());if(name.isBlank())OutlinedTextField(name,{name=it},label={Text("Name")},modifier=Modifier.fillMaxWidth());Segmented(TrackableEventType.entries.take(5).map{it.id to it.label},event){event=it};Segmented(TrackableEventType.entries.drop(5).map{it.id to it.label},event){event=it};OutlinedTextField(cacheCode,{cacheCode=it},label={Text("Cache code (optional)")},modifier=Modifier.fillMaxWidth());OutlinedTextField(note,{note=it},label={Text("Note")},modifier=Modifier.fillMaxWidth(),minLines=2);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={if(hasLocation(app))freshLocation(app,{loc=it},{status=it})else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},modifier=Modifier.weight(1f)){Text("Use GPS")};OutlinedButton(onClick=::takePhoto,modifier=Modifier.weight(1f)){Text("Add photo")}};loc?.let{CopyableValue("${it.latitude.gcFmt()}, ${it.longitude.gcFmt()}","Observation position")};CommitButton(frozen!=null){if(code.isBlank())status="Trackable reference is required." else {val r=record();val persist=if(context.presentationMode.name=="Dashboard")true else context.action.setting("persist_to_ledger")?.toBooleanStrictOrNull()?:false;if(persist)repo.appendTrackable(r);frozenJson=valuesToJson(values(r,persist))}};if(status.isNotBlank())Text(status)}
    }

    @Composable
    private fun TrackableHistory(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val app=LocalContext.current;val repo=remember(app){GeocachingRepository(app)};val all=remember{repo.trackableEvents().sortedBy{it.timestamp}};val codes=all.map{it.trackableCode}.filter{it.isNotBlank()}.distinct();var code by rememberSaveable{mutableStateOf(context.action.setting("trackable_code")?:codes.firstOrNull().orEmpty())};var frozenJson by rememberSaveable{mutableStateOf<String?>(null)};val frozen=frozenJson?.let(::jsonToValues);var status by rememberSaveable{mutableStateOf("")};val events=all.filter{it.trackableCode.equals(code,true)};val name=events.lastOrNull{it.trackableName.isNotBlank()}?.trackableName.orEmpty();val distance=journeyDistance(events);fun values():Map<String,String>{val uri=if(context.action.setting("export_geojson")=="true")materialiseBytes(app,"trackable_${code}.geojson",repo.trackableGeoJson(code,events))else "";return mapOf("geocache_trackable_history_result" to "${events.size} events · ${distanceLabel(distance)} recorded journey","geocache_trackable_history_trackable_code" to code,"geocache_trackable_history_trackable_name" to name,"geocache_trackable_history_event_count" to events.size.toString(),"geocache_trackable_history_journey_distance_m" to distance.gcFmt(1),"geocache_trackable_history_current_state" to trackableState(events),"geocache_trackable_history_events_json" to jsonTrackables(events),"geocache_trackable_history_geojson_export_uri" to uri)}
        LaunchedEffect(Unit){if(context.submitsImmediately)commitResult(context,As100TrackableHistoryMethod,values(),onConfirmed)}
        MethodSurface("Trackable journey","A durable ledger and mapped journey through time.",context,onBack,onCancel,frozen!=null,onDone=frozen?.let{{onConfirmed(commitResult(context,As100TrackableHistoryMethod,it,onConfirmed))}},committedKey=frozen,committedResultFactory=frozen?.let { committedValues -> { commitResult(context,As100TrackableHistoryMethod,committedValues,onConfirmed) } },onDoneResult=onConfirmed){if(codes.isEmpty()){Text("No followed trackables yet.")}else{if(codes.size>1)OutlinedTextField(code,{code=it},label={Text("Trackable reference")},modifier=Modifier.fillMaxWidth());HeroPanel{Text(name.ifBlank{code},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black);CopyableValue(code,"Public reference");Text("${events.size} events · ${distanceLabel(distance)} recorded journey · ${trackableState(events)}")};val pts=events.mapNotNull{if(it.latitude!=null&&it.longitude!=null)MapPoint(it.eventType.label,it.latitude,it.longitude,it.id)else null};if(pts.isNotEmpty())GeoMap(pts,pts);events.sortedByDescending{it.timestamp}.forEach{e->DashboardAction(e.eventType.label,"${friendlyTime(e.timestamp)}${e.cacheCode.takeIf{it.isNotBlank()}?.let{" · $it"}.orEmpty()}"){} };OutlinedButton(onClick={repo.savePersistentBytes("Trackable ${code} journey.geojson","application/geo+json",repo.trackableGeoJson(code,events));status="Journey saved to Files."},enabled=events.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("Save journey GeoJSON")};if(status.isNotBlank())Text(status);CommitButton(frozen!=null){frozenJson=valuesToJson(values())}}}
    }

    @Composable
    private fun Account(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val app=LocalContext.current
        val store=remember(app){GeocachingAccountStore(app)}
        val registry=remember(app){GeocachingProviderRegistry(app)}
        val scope=rememberCoroutineScope()
        var revision by rememberSaveable{mutableIntStateOf(0)}
        val profiles=remember(revision){registry.profiles()}
        var provider by rememberSaveable{mutableStateOf(context.action.setting("provider")?.takeIf{registry.profile(it)!=null}?:registry.defaultProviderId())}
        val profile=remember(provider,revision){registry.profile(provider)?:registry.profile(registry.defaultProviderId())!!}
        var consumerKey by rememberSaveable(provider,revision){mutableStateOf(context.action.setting("consumer_key")?:store.consumerKey(provider))}
        var consumerSecret by rememberSaveable(provider,revision){mutableStateOf(context.action.setting("consumer_secret").orEmpty())}
        var verifier by rememberSaveable(provider){mutableStateOf(context.action.setting("verifier").orEmpty())}
        var authUrl by rememberSaveable(provider){mutableStateOf("")}
        var status by rememberSaveable{mutableStateOf("")}
        var editing by rememberSaveable{mutableStateOf(context.action.setting("open_editor")?.toBooleanStrictOrNull() ?: false)}
        var adding by rememberSaveable{mutableStateOf(false)}
        var editLabel by rememberSaveable{mutableStateOf(profile.label)}
        var editUrl by rememberSaveable{mutableStateOf(profile.baseUrl)}
        var frozenJson by rememberSaveable{mutableStateOf<String?>(null)}
        val frozen=frozenJson?.let(::jsonToValues)
        val account=remember(provider,revision){store.account(provider)}

        fun selectProvider(id:String){
            provider=id;consumerKey=store.consumerKey(id);consumerSecret="";verifier="";authUrl="";status=""
            registry.profile(id)?.let{editLabel=it.label;editUrl=it.baseUrl};editing=false;adding=false
        }
        fun values(action:String=context.action.setting("action")?:"status")=mapOf(
            "geocache_account_result" to if(account.connected)"Connected as ${account.username}" else "${profile.label}${if(store.consumerKey(provider).isNotBlank())" public API key saved" else " without a public API key"}",
            "geocache_account_provider" to provider,
            "geocache_account_connected" to account.connected.toString(),
            "geocache_account_username" to account.username,
            "geocache_account_user_uuid" to account.userUuid,
            "geocache_account_profile_url" to account.profileUrl,
            "geocache_account_caches_found" to account.cachesFound?.toString().orEmpty(),
            "geocache_account_last_refresh_iso" to account.lastRefreshIso,
            "geocache_account_authorization_url" to authUrl,
            "geocache_account_action" to action
        )
        LaunchedEffect(Unit){if(context.submitsImmediately&&(context.action.setting("action")?:"status")=="status")commitResult(context,As100GeocachingAccountMethod,values(),onConfirmed)}

        fun saveProvider(){
            runCatching{
                val existingId=if(adding)null else provider
                val saved=registry.save(existingId,editLabel,editUrl)
                val keyToSave=consumerKey.ifBlank{store.consumerKey(saved.id)}
                val secretToSave=consumerSecret.ifBlank{store.consumerSecret(saved.id)}
                if(keyToSave.isNotBlank()||secretToSave.isNotBlank())store.saveConsumer(saved.id,keyToSave,secretToSave)
                provider=saved.id;consumerKey=keyToSave;consumerSecret="";registry.setDefault(saved.id);editing=false;adding=false;revision++
                status="Saved ${saved.label} and made it the default provider."
            }.onFailure{status=it.message.orEmpty()}
        }
        fun startConnect(){
            val key=consumerKey.ifBlank{store.consumerKey(provider)}
            val secret=consumerSecret.ifBlank{store.consumerSecret(provider)}
            if(key.isBlank()||secret.isBlank()){status="Configure this provider's OKAPI consumer key and secret first.";return}
            store.saveConsumer(provider,key,secret)
            scope.launch{runCatching{withContext(Dispatchers.IO){OkapiClient(profile.installation(),key,secret).requestToken()}}.onSuccess{
                store.savePending(provider,it.token,it.secret);authUrl=it.authorizationUrl;app.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(authUrl)));status="Authorize in the provider page, then enter the PIN here."
            }.onFailure{status=it.message.orEmpty()}}
        }
        fun finishConnect(){
            val pending=store.pending(provider);if(pending==null){status="Start sign-in first.";return}
            scope.launch{runCatching{withContext(Dispatchers.IO){
                val client=OkapiClient(profile.installation(),store.consumerKey(provider),store.consumerSecret(provider))
                val access=client.accessToken(OAuthRequestToken(pending.first,pending.second,""),verifier)
                store.saveAccess(provider,access.token,access.secret);store.clearPending(provider)
                val p=client.account(access.token,access.secret);store.saveProfile(provider,p.username,p.userUuid,p.profileUrl,p.cachesFound);p
            }}.onSuccess{revision++;status="Connected as ${it.username}."}.onFailure{status=it.message.orEmpty()}}
        }

        MethodSurface("Providers & accounts","Any OKAPI installation can be configured. Account sign-in is optional and separate from provider credentials.",context,onBack,onCancel,frozen!=null,onDone=frozen?.let{{onConfirmed(commitResult(context,As100GeocachingAccountMethod,it,onConfirmed))}},committedKey=frozen,committedResultFactory=frozen?.let { committedValues -> { commitResult(context,As100GeocachingAccountMethod,committedValues,onConfirmed) } },onDoneResult=onConfirmed){
            if(!(adding||editing)){
                SectionTitle("PROVIDERS")
                profiles.forEach{p->
                    val a=store.account(p.id);val default=p.id==registry.defaultProviderId()
                    DashboardAction(p.label,"${p.baseUrl}${if(default)" · DEFAULT" else ""}${if(a.connected)" · ${a.username.ifBlank{"CONNECTED"}}" else if(store.consumerKey(p.id).isNotBlank())" · key saved" else " · needs key"}"){selectProvider(p.id)}
                }
                OutlinedButton(onClick={adding=true;editing=true;editLabel="";editUrl="https://";consumerKey="";consumerSecret="";status=""},modifier=Modifier.fillMaxWidth()){Text("+ Add OKAPI provider")}
            }

            if(adding||editing){
                SectionTitle(if(adding)"ADD PROVIDER" else "EDIT PROVIDER")
                OutlinedTextField(editLabel,{editLabel=it},label={Text("Provider name")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(editUrl,{editUrl=it},label={Text("OKAPI site base URL")},supportingText={Text("Example: https://opencaching.example")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(consumerKey,{consumerKey=it},label={Text("OKAPI consumer key")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(consumerSecret,{consumerSecret=it},label={Text(if(store.consumerSecret(provider).isBlank())"OKAPI consumer secret (account sign-in only)" else "New consumer secret (blank keeps current)")},supportingText={Text("Public cache browsing needs only the consumer key. The secret is required for OAuth account connection.")},modifier=Modifier.fillMaxWidth(),singleLine=true,visualTransformation=PasswordVisualTransformation())
                val signupBase = editUrl.trim().trimEnd('/')
                if(signupBase.startsWith("https://")){
                    OutlinedButton(
                        onClick={app.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("$signupBase/okapi/signup.html")))},
                        modifier=Modifier.fillMaxWidth()
                    ){Text("Create API key on provider website")}
                }
                OutlinedButton(onClick={
                    val testKey=consumerKey.ifBlank{store.consumerKey(provider)}
                    val testUrl=editUrl.trim().trimEnd('/')
                    if(testKey.isBlank()) status="Enter a consumer key first."
                    else scope.launch{
                        status="Testing public API access…"
                        runCatching{withContext(Dispatchers.IO){OkapiClient(OpenCachingInstallation(provider,editLabel.ifBlank{profile.label},testUrl),testKey).testPublicAccess()}}
                            .onSuccess{check:OkapiPublicCheck->status="Public API key verified. ${editLabel.ifBlank{profile.label}} accepted the request${if(check.sampleCount>0) " and returned a sample cache" else ""}."}
                            .onFailure{status=it.message.orEmpty().ifBlank{"Public API key test failed."}}
                    }
                },enabled=consumerKey.isNotBlank()||store.consumerKey(provider).isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Test public API key")}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(onClick=::saveProvider,modifier=Modifier.weight(1f)){Text("Save provider")}
                    OutlinedButton(onClick={editing=false;adding=false},modifier=Modifier.weight(1f)){Text("Cancel")}
                }
            }else{
                SectionTitle("${profile.label.uppercase()} · PROVIDER")
                HeroPanel{
                    Text(profile.label,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black)
                    CopyableValue(profile.baseUrl,"Base URL")
                    Text(if(store.consumerKey(provider).isBlank())"Public API key not configured." else "Public API key saved. Run a search or use Test public API key to verify it.")
                    if(provider==registry.defaultProviderId())Text("Default nearby-search provider",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                }
                if(store.consumerKey(provider).isBlank()){
                    Button(
                        onClick={app.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("${profile.baseUrl.trimEnd('/')}/okapi/signup.html")))},
                        modifier=Modifier.fillMaxWidth()
                    ){Text("Create API key on ${profile.label}")}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={editing=true;editLabel=profile.label;editUrl=profile.baseUrl;consumerKey=store.consumerKey(provider);consumerSecret=""},modifier=Modifier.weight(1f)){Text("Edit")}
                    OutlinedButton(onClick={registry.setDefault(provider);revision++;status="${profile.label} is now the default provider."},modifier=Modifier.weight(1f)){Text("Make default")}
                }
                if(!profile.builtIn){OutlinedButton(onClick={store.clearAccount(provider);if(registry.delete(provider)){revision++;selectProvider(registry.defaultProviderId());status="Provider removed."}},modifier=Modifier.fillMaxWidth()){Text("Remove provider")}}

                SectionTitle("ACCOUNT")
                if(account.connected){
                    HeroPanel{Text(account.username.ifBlank{"CONNECTED"},style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);Text("${profile.label}${account.cachesFound?.let{" · $it finds"}.orEmpty()}");account.profileUrl.takeIf{it.isNotBlank()}?.let{CopyableValue(it,"Profile")}}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Button(onClick={scope.launch{runCatching{withContext(Dispatchers.IO){OkapiClient(profile.installation(),store.consumerKey(provider),store.consumerSecret(provider)).account(store.accessToken(provider),store.accessSecret(provider))}}.onSuccess{store.saveProfile(provider,it.username,it.userUuid,it.profileUrl,it.cachesFound);revision++;status="Account refreshed."}.onFailure{status=it.message.orEmpty()}}},modifier=Modifier.weight(1f)){Text("Refresh")}
                        OutlinedButton(onClick={store.clearAccount(provider);revision++;status="Signed out."},modifier=Modifier.weight(1f)){Text("Sign out")}
                    }
                }else{
                    HeroPanel{Text("Optional sign-in",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black);Text("Public nearby searches use the provider's application key. OAuth is only needed for your account, history and log uploads.")}
                    Button(onClick=::startConnect,enabled=store.consumerKey(provider).isNotBlank()&&store.consumerSecret(provider).isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Open provider sign-in")}
                    if(authUrl.isNotBlank()){
                        OutlinedTextField(verifier,{verifier=it},label={Text("Authorization PIN")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                        Button(onClick=::finishConnect,enabled=verifier.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Finish connection")}
                    }
                }
            }
            SectionTitle("GEOCACHING.COM")
            HeroPanel{Text("Official API connection",fontWeight=FontWeight.Black);Text("Designed in, but sign-in remains disabled until Geocaching HQ issues MethodMesh approved API credentials.")}
            if(status.isNotBlank())Text(status,color=MaterialTheme.colorScheme.primary)
            CommitButton(frozen!=null){frozenJson=valuesToJson(values())}
        }
    }

    @Composable
    private fun Sync(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val app=LocalContext.current;val repo=remember(app){GeocachingRepository(app)};val store=remember(app){GeocachingAccountStore(app)};val registry=remember(app){GeocachingProviderRegistry(app)};val profiles=remember{registry.profiles()};val scope=rememberCoroutineScope()
        var provider by rememberSaveable{mutableStateOf(context.action.setting("provider")?.takeIf{registry.profile(it)!=null}?:registry.defaultProviderId())};var operation by rememberSaveable{mutableStateOf(context.action.setting("operation")?:"refresh_account")};var visitId by rememberSaveable{mutableStateOf(context.action.setting("local_visit_id").orEmpty())};var status by rememberSaveable{mutableStateOf("")};var resultValuesJson by rememberSaveable{mutableStateOf<String?>(null)};val resultValues=resultValuesJson?.let(::jsonToValues);var running by remember{mutableStateOf(false)}
        val profile=registry.profile(provider)?:registry.profile(registry.defaultProviderId())!!;val account=store.account(provider);val uploaded=repo.receipts().filter{it.provider==provider&&it.status=="succeeded"}.map{it.localVisitId}.toSet();val pending=repo.visits().filter{it.id !in uploaded&&it.visitType!=VisitType.NOTE};val selected=pending.firstOrNull{it.id==visitId}?:pending.firstOrNull();LaunchedEffect(selected?.id){if(visitId.isBlank())visitId=selected?.id.orEmpty()}
        fun base(state:String,remoteId:String="",imported:Int=0)=mapOf("geocache_sync_result" to status,"geocache_sync_provider" to provider,"geocache_sync_operation" to operation,"geocache_sync_local_visit_id" to visitId,"geocache_sync_remote_log_id" to remoteId,"geocache_sync_imported_count" to imported.toString(),"geocache_sync_pending_upload_count" to pending.size.toString(),"geocache_sync_state" to state)
        fun runSync(){if(!account.connected){status="Connect ${profile.label} first.";return};val key=store.consumerKey(provider);val secret=store.consumerSecret(provider);if(key.isBlank()||secret.isBlank()){status="${profile.label} application credentials are missing.";return};val token=store.accessToken(provider);val tokenSecret=store.accessSecret(provider);running=true;scope.launch{runCatching{withContext(Dispatchers.IO){val client=OkapiClient(profile.installation(),key,secret);when(operation){"refresh_account"->{val p=client.account(token,tokenSecret);store.saveProfile(provider,p.username,p.userUuid,p.profileUrl,p.cachesFound);Triple("account_refreshed","",0)};"import_history"->{val logs=client.userLogs(token,tokenSecret,store.userUuid(provider),context.action.setting("limit")?.toIntOrNull()?:500);var n=0;logs.forEach{l->val c=l.optString("cache_code");val local=repo.caches().firstOrNull{it.code.equals(c,true)};val t=when(l.optString("type").lowercase()){ "found it","found"->VisitType.FOUND;"didn't find it","not found","dnf"->VisitType.DNF;else->VisitType.NOTE};val ts=l.optString("date_created").ifBlank{l.optString("date").let{if('T' in it)it else "${it}T12:00:00Z"}};val r=CacheVisitRecord(cacheCode=c,cacheName=local?.name.orEmpty(),source=cacheSourceForProvider(provider),visitType=t,timestamp=ts,actualLatitude=null,actualLongitude=null,accuracyM=null,publishedLatitude=local?.latitude,publishedLongitude=local?.longitude,note=l.optString("comment"),remoteLogId=l.optString("uuid"),remoteState="remote_only",importedFromRemote=true);val before=repo.visits().size;repo.upsertImportedVisit(r);if(repo.visits().size>before)n++};Triple("history_imported","",n)};else->{val v=repo.findVisit(visitId)?:error("Choose a local visit to upload.");repo.successfulUploadFor(v.id,provider)?.let{error("That visit already has a successful upload receipt for ${profile.label}.")};try{val res=client.submitVisit(token,tokenSecret,v);repo.appendReceipt(SyncReceipt(v.id,provider,res.first,"succeeded",Instant.now().toString(),res.second));Triple("uploaded",res.first,0)}catch(e:Exception){repo.appendReceipt(SyncReceipt(v.id,provider,"","failed",Instant.now().toString(),e.message.orEmpty()));throw e}}}}}.onSuccess{(state,remote,n)->status=when(state){"account_refreshed"->"Account refreshed.";"history_imported"->"Imported $n remote logs.";else->"Visit uploaded."};resultValuesJson=valuesToJson(base(state,remote,n))}.onFailure{status=it.message.orEmpty();resultValuesJson=valuesToJson(base("failed"))};running=false}}
        LaunchedEffect(resultValues){if(context.submitsImmediately)resultValues?.let{commitResult(context,As100GeocachingSyncMethod,it,onConfirmed)}}
        MethodSurface("Synchronise","Explicit provider-by-provider sync — never a hidden all-or-nothing merge.",context,onBack,onCancel,resultValues!=null,onDone=resultValues?.let{{onConfirmed(commitResult(context,As100GeocachingSyncMethod,it,onConfirmed))}},committedKey=resultValues,committedResultFactory=resultValues?.let { committedValues -> { commitResult(context,As100GeocachingSyncMethod,committedValues,onConfirmed) } },onDoneResult=onConfirmed){
            SectionTitle("PROVIDER")
            ProviderAccountPicker(profiles,provider,store){provider=it;visitId="";status="";resultValuesJson=null}
            if(!account.connected)HeroPanel{Text("Not connected",fontWeight=FontWeight.Black);Text("Connect ${profile.label} in Providers & accounts first.")}else{
                HeroPanel{Text(account.username.ifBlank{"CONNECTED"},fontWeight=FontWeight.Black);Text(profile.label)}
                Segmented(listOf("refresh_account" to "Refresh","import_history" to "Import finds","upload_visit" to "Upload visit"),operation){operation=it}
                if(operation=="upload_visit"){Text("${pending.size} local visits without a successful upload receipt to ${profile.label}.",style=MaterialTheme.typography.bodySmall);pending.take(30).forEach{v->DashboardAction(v.cacheName.ifBlank{v.cacheCode},"${v.visitType.label} · ${friendlyTime(v.timestamp)}"){visitId=v.id}};selected?.let{CopyableValue(it.id,"Selected visit")}}
                Button(onClick=::runSync,enabled=!running&&(operation!="upload_visit"||visitId.isNotBlank()),modifier=Modifier.fillMaxWidth()){Text(if(running)"Working…" else when(operation){"refresh_account"->"Refresh account";"import_history"->"Import remote history";else->"Upload selected visit"})}
            }
            if(status.isNotBlank())Text(status,color=MaterialTheme.colorScheme.primary)
        }
    }

}

@Composable
private fun ProviderSourcePicker(
    profiles:List<GeocachingProviderProfile>,
    selected:String,
    store:GeocachingAccountStore,
    defaultProviderId:String,
    verifiedProviderId:String,
    onManage:()->Unit,
    onConfigure:(String)->Unit,
    onSelected:(String)->Unit
){
    SectionTitle("CACHE SOURCE")
    HeroPanel {
        Text("Where should MethodMesh look?",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Black)
        Text("Offline uses caches already saved on this device. Online providers use their own OKAPI application credentials; signing into an account is optional.")
    }
    if(selected=="library") Button(onClick={onSelected("library")},modifier=Modifier.fillMaxWidth()){Text("Offline library · local only")}
    else OutlinedButton(onClick={onSelected("library")},modifier=Modifier.fillMaxWidth()){Text("Offline library · local only")}
    profiles.forEach{p->
        val configured=store.consumerKey(p.id).isNotBlank()
        val connected=store.account(p.id).connected
        val suffix=buildString {
            if(p.id==defaultProviderId) append(" · DEFAULT")
            append(if(p.id==verifiedProviderId) " · VERIFIED" else if(configured) " · KEY SET" else " · NEEDS KEY")
            if(connected) append(" · SIGNED IN")
        }
        when {
            !configured -> OutlinedButton(onClick={onConfigure(p.id)},modifier=Modifier.fillMaxWidth()){Text(p.label+suffix)}
            selected==p.id -> Button(onClick={onSelected(p.id)},modifier=Modifier.fillMaxWidth()){Text(p.label+suffix)}
            else -> OutlinedButton(onClick={onSelected(p.id)},modifier=Modifier.fillMaxWidth()){Text(p.label+suffix)}
        }
    }
    OutlinedButton(onClick=onManage,modifier=Modifier.fillMaxWidth()){Text("Manage providers / add another OKAPI site") }
}

@Composable
private fun ProviderAccountPicker(profiles:List<GeocachingProviderProfile>,selected:String,store:GeocachingAccountStore,onSelected:(String)->Unit){
    profiles.forEach{p->
        val a=store.account(p.id)
        DashboardAction(p.label,if(a.connected)"${a.username.ifBlank{"Connected"}} · account connected" else if(store.consumerKey(p.id).isNotBlank())"Public API key saved · no account" else "Provider key not configured"){onSelected(p.id)}
    }
}

private fun cacheSourceForProvider(providerId:String)=when(providerId){
    GeocachingProviderRegistry.UK.id->CacheSource.OPENCACHE_UK
    GeocachingProviderRegistry.PL.id->CacheSource.OPENCACHING_PL
    else->CacheSource.OKAPI
}

@Composable
private fun LiveLocation(context:Context,enabled:Boolean,onLocation:(Location)->Unit){
    val client=remember(context){LocationServices.getFusedLocationProviderClient(context)};val callback=remember{object:LocationCallback(){override fun onLocationResult(result:LocationResult){result.lastLocation?.let(onLocation)}}}
    DisposableEffect(enabled){if(enabled){val req=LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,1000L).setMinUpdateIntervalMillis(500L).build();runCatching{client.requestLocationUpdates(req,callback,android.os.Looper.getMainLooper())}};onDispose{client.removeLocationUpdates(callback)}}
}

private fun hasLocation(context:Context)=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
private fun acquireLocation(context:Context,onLocation:(Location)->Unit,onError:(String)->Unit)=freshLocation(context,onLocation,onError)
private fun freshLocation(context:Context,onLocation:(Location)->Unit,onError:(String)->Unit){if(!hasLocation(context)){onError("Location permission is required.");return};val c=LocationServices.getFusedLocationProviderClient(context);val token=CancellationTokenSource();runCatching{c.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,token.token).addOnSuccessListener{it?.let(onLocation)?:onError("No GPS fix available.")}.addOnFailureListener{onError(it.message?:"GPS request failed.")}}.onFailure{onError(it.message?:"GPS request failed.")}}

private fun parseSampleTriples(raw:String):List<Triple<Double,Double,Double>> = raw.split(';','\n').mapNotNull{row->val p=row.trim().split(',', '|');if(p.size<2)null else{val a=p[0].trim().toDoubleOrNull();val b=p[1].trim().toDoubleOrNull();if(a==null||b==null)null else Triple(a,b,p.getOrNull(2)?.trim()?.toDoubleOrNull()?:0.0)}}
private fun suppliedCache(action:ExternalActionRequest):CacheRecord?{val code=action.setting("cache_code")?:return null;val lat=action.setting("latitude")?.toDoubleOrNull()?:return null;val lon=action.setting("longitude")?.toDoubleOrNull()?:return null;return CacheRecord(code,action.setting("cache_name").orEmpty(),CacheSource.from(action.setting("source")),lat,lon,action.setting("type")?:"Traditional",action.setting("size")?:"Unknown",action.setting("difficulty")?.toDoubleOrNull(),action.setting("terrain")?.toDoubleOrNull(),action.setting("owner").orEmpty(),action.setting("description").orEmpty(),action.setting("hint").orEmpty(),runCatching{val a=JSONArray(action.setting("attributes_json")?:"[]");buildList{for(i in 0 until a.length())add(a.optString(i))}}.getOrDefault(emptyList()),runCatching{val a=JSONArray(action.setting("recent_logs_json")?:"[]");buildList{for(i in 0 until a.length())add(a.optString(i))}}.getOrDefault(emptyList()),providerUrl=action.setting("provider_url").orEmpty())}


private fun ExifInterface.setExifGps(latitude: Double, longitude: Double) {
    fun dms(value: Double): String {
        val abs = kotlin.math.abs(value)
        val degrees = abs.toInt()
        val minutesFull = (abs - degrees) * 60.0
        val minutes = minutesFull.toInt()
        val seconds = (minutesFull - minutes) * 60.0
        val denominator = 10_000L
        val numerator = kotlin.math.round(seconds * denominator).toLong()
        return "$degrees/1,$minutes/1,$numerator/$denominator"
    }
    setAttribute(ExifInterface.TAG_GPS_LATITUDE, dms(latitude))
    setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (latitude >= 0.0) "N" else "S")
    setAttribute(ExifInterface.TAG_GPS_LONGITUDE, dms(longitude))
    setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (longitude >= 0.0) "E" else "W")
}
