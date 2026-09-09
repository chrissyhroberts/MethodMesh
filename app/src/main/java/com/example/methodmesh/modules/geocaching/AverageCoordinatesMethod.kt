package com.example.methodmesh.modules.geocaching

object As100AverageCoordinatesMethod:GeocachingMethodBase("geocache.average_coordinates","Average coordinates","Average a set of GPS observations into a more stable coordinate.",GeocachingContracts.average){
    override fun calculate(settings:Map<String,String>):Map<String,String>{
        val points=settings.gc("samples")?.split(';','\n')?.mapNotNull{row->val p=row.trim().split(',', '|');if(p.size<2)null else p[0].trim().toDoubleOrNull()?.let{lat->p[1].trim().toDoubleOrNull()?.let{lon->Triple(lat,lon,p.getOrNull(2)?.trim()?.toDoubleOrNull())}}}.orEmpty();if(points.isEmpty())error("At least one latitude,longitude sample is required.")
        val lat=points.map{it.first}.average();val lon=GeocachingMath.circularMeanLongitude(points.map{it.second});val acc=points.mapNotNull{it.third}
        return success(mapOf("geocache_average_result" to "${lat.gcFmt()}, ${lon.gcFmt()}","geocache_average_sample_count" to points.size.toString(),"geocache_average_mean_latitude" to lat.gcFmt(),"geocache_average_mean_longitude" to lon.gcFmt(),"geocache_average_mean_accuracy_m" to acc.takeIf{it.isNotEmpty()}?.average()?.gcFmt(1).orEmpty(),"geocache_average_min_accuracy_m" to acc.minOrNull()?.gcFmt(1).orEmpty(),"geocache_average_max_accuracy_m" to acc.maxOrNull()?.gcFmt(1).orEmpty()))
    }
}
