package com.example.methodmesh.modules.geocaching

import org.json.JSONArray

object As100CacheDetailsMethod:GeocachingMethodBase("geocache.details","Cache details","Return cache listing details, hint, recent logs and additional waypoints.",GeocachingContracts.details,connectivity="ONLINE_OFFLINE"){
    override fun calculate(settings:Map<String,String>):Map<String,String>{
        val code=settings.gc("cache_code")?:error("Cache code is required."); val name=settings.gc("cache_name").orEmpty(); val lat=settings.gc("latitude").orEmpty(); val lon=settings.gc("longitude").orEmpty()
        return success(mapOf("geocache_details_result" to listOf(code,name).filter{it.isNotBlank()}.joinToString(" · "),"geocache_details_code" to code,"geocache_details_name" to name,"geocache_details_source" to settings.gc("source").orEmpty(),"geocache_details_latitude" to lat,"geocache_details_longitude" to lon,"geocache_details_type" to settings.gc("type").orEmpty(),"geocache_details_size" to settings.gc("size").orEmpty(),"geocache_details_difficulty" to settings.gc("difficulty").orEmpty(),"geocache_details_terrain" to settings.gc("terrain").orEmpty(),"geocache_details_owner" to settings.gc("owner").orEmpty(),"geocache_details_description" to settings.gc("description").orEmpty(),"geocache_details_hint" to settings.gc("hint").orEmpty(),"geocache_details_attributes_json" to (settings.gc("attributes_json")?:JSONArray().toString()),"geocache_details_recent_logs_json" to (settings.gc("recent_logs_json")?:JSONArray().toString()),"geocache_details_waypoints_json" to (settings.gc("waypoints_json")?:JSONArray().toString()),"geocache_details_provider_url" to settings.gc("provider_url").orEmpty()))
    }
}
