package com.example.methodmesh.modules.geocaching

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.settings.SettingsState
import org.json.JSONObject
import java.time.Instant
import java.util.Locale

internal const val GEOCACHING_VERSION = "1.1.4"

internal object GeocachingContracts {
    val dashboard = listOf("geocache_dashboard_status","geocache_dashboard_result","geocache_dashboard_active_hunt_code","geocache_dashboard_active_hunt_name","geocache_dashboard_cache_count","geocache_dashboard_visit_count","geocache_dashboard_found_count","geocache_dashboard_dnf_count","geocache_dashboard_photo_count","geocache_dashboard_followed_trackable_count","geocache_dashboard_opencache_connected","geocache_dashboard_opencache_username","geocache_dashboard_pending_upload_count","geocache_dashboard_captured_time_iso","geocache_dashboard_audit_json","geocache_dashboard_error")
    val library = listOf("geocache_library_status","geocache_library_result","geocache_library_cache_count","geocache_library_cache_codes","geocache_library_selected_cache_code","geocache_library_selected_cache_name","geocache_library_source","geocache_library_gpx_export_uri","geocache_library_captured_time_iso","geocache_library_audit_json","geocache_library_error")
    val nearby = listOf("geocache_nearby_status","geocache_nearby_result","geocache_nearby_current_latitude","geocache_nearby_current_longitude","geocache_nearby_radius_km","geocache_nearby_source","geocache_nearby_cache_count","geocache_nearby_caches_json","geocache_nearby_selected_cache_code","geocache_nearby_selected_distance_m","geocache_nearby_selected_bearing_deg","geocache_nearby_captured_time_iso","geocache_nearby_audit_json","geocache_nearby_error")
    val navigate = listOf("geocache_navigate_status","geocache_navigate_result","geocache_navigate_cache_code","geocache_navigate_cache_name","geocache_navigate_target_latitude","geocache_navigate_target_longitude","geocache_navigate_current_latitude","geocache_navigate_current_longitude","geocache_navigate_accuracy_m","geocache_navigate_distance_m","geocache_navigate_bearing_deg","geocache_navigate_heading_deg","geocache_navigate_relative_bearing_deg","geocache_navigate_arrived","geocache_navigate_arrival_radius_m","geocache_navigate_started_at_iso","geocache_navigate_captured_time_iso","geocache_navigate_audit_json","geocache_navigate_error")
    val details = listOf("geocache_details_status","geocache_details_result","geocache_details_code","geocache_details_name","geocache_details_source","geocache_details_latitude","geocache_details_longitude","geocache_details_type","geocache_details_size","geocache_details_difficulty","geocache_details_terrain","geocache_details_owner","geocache_details_description","geocache_details_hint","geocache_details_attributes_json","geocache_details_recent_logs_json","geocache_details_waypoints_json","geocache_details_provider_url","geocache_details_captured_time_iso","geocache_details_audit_json","geocache_details_error")
    val project = listOf("geocache_project_status","geocache_project_result","geocache_project_origin_latitude","geocache_project_origin_longitude","geocache_project_bearing_deg","geocache_project_distance_m","geocache_project_projected_latitude","geocache_project_projected_longitude","geocache_project_captured_time_iso","geocache_project_audit_json","geocache_project_error")
    val average = listOf("geocache_average_status","geocache_average_result","geocache_average_sample_count","geocache_average_mean_latitude","geocache_average_mean_longitude","geocache_average_mean_accuracy_m","geocache_average_min_accuracy_m","geocache_average_max_accuracy_m","geocache_average_captured_time_iso","geocache_average_audit_json","geocache_average_error")
    val visit = listOf("geocache_visit_status","geocache_visit_result","geocache_visit_id","geocache_visit_cache_code","geocache_visit_cache_name","geocache_visit_type","geocache_visit_timestamp","geocache_visit_actual_latitude","geocache_visit_actual_longitude","geocache_visit_accuracy_m","geocache_visit_published_latitude","geocache_visit_published_longitude","geocache_visit_published_offset_m","geocache_visit_note","geocache_visit_favorite","geocache_visit_photo_count","geocache_visit_photo_uri","geocache_visit_persisted_to_ledger","geocache_visit_remote_state","geocache_visit_captured_time_iso","geocache_visit_audit_json","geocache_visit_error")
    val history = listOf("geocache_history_status","geocache_history_result","geocache_history_record_count","geocache_history_found_count","geocache_history_dnf_count","geocache_history_note_count","geocache_history_photo_count","geocache_history_records_json","geocache_history_selected_visit_id","geocache_history_selected_photo_uri","geocache_history_csv_export_uri","geocache_history_geojson_export_uri","geocache_history_field_notes_export_uri","geocache_history_captured_time_iso","geocache_history_audit_json","geocache_history_error")
    val trackableRecord = listOf("geocache_trackable_record_status","geocache_trackable_record_result","geocache_trackable_record_event_id","geocache_trackable_record_trackable_code","geocache_trackable_record_trackable_name","geocache_trackable_record_event_type","geocache_trackable_record_timestamp","geocache_trackable_record_cache_code","geocache_trackable_record_cache_name","geocache_trackable_record_latitude","geocache_trackable_record_longitude","geocache_trackable_record_accuracy_m","geocache_trackable_record_note","geocache_trackable_record_photo_count","geocache_trackable_record_photo_uri","geocache_trackable_record_persisted_to_ledger","geocache_trackable_record_captured_time_iso","geocache_trackable_record_audit_json","geocache_trackable_record_error")
    val trackableHistory = listOf("geocache_trackable_history_status","geocache_trackable_history_result","geocache_trackable_history_trackable_code","geocache_trackable_history_trackable_name","geocache_trackable_history_event_count","geocache_trackable_history_journey_distance_m","geocache_trackable_history_current_state","geocache_trackable_history_events_json","geocache_trackable_history_geojson_export_uri","geocache_trackable_history_captured_time_iso","geocache_trackable_history_audit_json","geocache_trackable_history_error")
    val account = listOf("geocache_account_status","geocache_account_result","geocache_account_provider","geocache_account_connected","geocache_account_username","geocache_account_user_uuid","geocache_account_profile_url","geocache_account_caches_found","geocache_account_last_refresh_iso","geocache_account_authorization_url","geocache_account_action","geocache_account_captured_time_iso","geocache_account_audit_json","geocache_account_error")
    val sync = listOf("geocache_sync_status","geocache_sync_result","geocache_sync_provider","geocache_sync_operation","geocache_sync_local_visit_id","geocache_sync_remote_log_id","geocache_sync_imported_count","geocache_sync_pending_upload_count","geocache_sync_state","geocache_sync_captured_time_iso","geocache_sync_audit_json","geocache_sync_error")
}

abstract class GeocachingMethodBase(
    final override val id:String,
    private val methodName:String,
    private val methodDescription:String,
    outputs:List<String>,
    maturity:String = "DEVELOPMENT",
    connectivity:String = "OFFLINE",
    methodType:MethodObjectType = MethodObjectType.Calculation
):As100Method {
    final override val ref=ArchitectureRef(ArchitectureId(id),"Method",methodName)
    final override val descriptor=MethodDescriptor(
        id=ArchitectureId(id), methodType=methodType, name=methodName, version=GEOCACHING_VERSION,
        description=methodDescription, outputs=outputs, graphOutputs=listOf(id),
        parameters=mapOf("category" to "Geocaching","status" to "Development","maturity" to maturity,"connectivity" to connectivity,"interaction_lifecycle" to "live_working_result_commit","icon_key" to "location")
    )
    final override val contract=MethodContract(method=ref,producedKnowledgeTypes=listOf(KnowledgeObjectType.Observation),producedFields=descriptor.outputs,producedGraphOutputs=descriptor.graphOutputs)
    final override fun request(action:String,context:Map<String,String>,signals:List<Signal>,inputs:List<ArchitectureRef>)=As100ExecutionEngine.request(action=action,method=ref,context=context,signals=signals,inputs=inputs)
    override fun execute(request:ExecutionRequest,settingsState:SettingsState?,transport:String?):ExecutionResult {
        val invocation=InvocationContext.from(request.context)
        val values=runCatching{calculate(request.context)}.getOrElse{failure(it.message?:"Operation failed.")}
        return result(request,values,invocation)
    }
    protected open fun calculate(settings:Map<String,String>):Map<String,String> = failure("This capability requires its native MethodMesh interaction surface.")
    fun result(request:ExecutionRequest,values:Map<String,String>,invocation:InvocationContext?):ExecutionResult {
        val statusKey=descriptor.outputs.firstOrNull{it.endsWith("_status")}.orEmpty();val errorKey=descriptor.outputs.firstOrNull{it.endsWith("_error")}.orEmpty();val ok=values[statusKey]=="succeeded"
        val provenance=ProvenanceContext("methodmesh.geocaching",id,GEOCACHING_VERSION)
        val observation=Observation(phenomenon=id,values=values,temporalContext=request.temporalContext,provenance=provenance)
        val transformation=Transformation(action=id,method=ref,outputs=listOf(ArchitectureRef(observation.id,observation.objectType,observation.phenomenon)),status=if(ok)TransformationStatus.Succeeded else TransformationStatus.Failed,temporalContext=request.temporalContext,provenance=provenance)
        return As100ExecutionEngine.complete(request=request,status=if(ok)TransformationStatus.Succeeded else TransformationStatus.Failed,observations=listOf(observation),transformations=listOf(transformation),diagnostics=if(ok)emptyMap() else mapOf(errorKey to values[errorKey].orEmpty())).withInvocationContext(invocation)
    }
    internal fun captured(base:Map<String,String>):Map<String,String> = success(base)
    internal fun rejected(message:String):Map<String,String> = failure(message)

    protected fun success(base:Map<String,String>):Map<String,String>{
        val status=descriptor.outputs.first{it.endsWith("_status")};val error=descriptor.outputs.first{it.endsWith("_error")};val audit=descriptor.outputs.firstOrNull{it.endsWith("_audit_json")};val time=descriptor.outputs.firstOrNull{it.endsWith("_captured_time_iso")}
        val out=descriptor.outputs.associateWith{""}.toMutableMap();out.putAll(base);out[status]="succeeded";out[error]="";time?.let{out[it]=out[it].orEmpty().ifBlank{Instant.now().toString()}};audit?.let{out[it]=out[it].orEmpty().ifBlank{JSONObject().apply{put("method_id",id);put("method_version",GEOCACHING_VERSION);put("captured_time_iso",out[time])}.toString()}};return out
    }
    protected fun failure(message:String):Map<String,String>{
        val status=descriptor.outputs.first{it.endsWith("_status")};val error=descriptor.outputs.first{it.endsWith("_error")};val audit=descriptor.outputs.firstOrNull{it.endsWith("_audit_json")};val time=descriptor.outputs.firstOrNull{it.endsWith("_captured_time_iso")}
        return descriptor.outputs.associateWith{""}.toMutableMap().apply{this[status]="failed";this[error]=message;time?.let{this[it]=Instant.now().toString()};audit?.let{this[it]=JSONObject().apply{put("method_id",id);put("method_version",GEOCACHING_VERSION);put("error",message)}.toString()}}
    }
}

internal fun Map<String,String>.gc(key:String):String? = (this[key]?:this["input_$key"])?.takeIf{it.isNotBlank()}
internal fun Number.gcFmt(decimals:Int=6)=String.format(Locale.US,"%.${decimals}f",this.toDouble())
