package com.example.methodmesh.modules.geocaching

object As100NavigateToCacheMethod:GeocachingMethodBase("geocache.navigate","Navigate to cache","Navigate live to a cache using GPS, distance, bearing and device heading.",GeocachingContracts.navigate,connectivity="OFFLINE",methodType=com.example.methodmesh.core.methodmesh.MethodObjectType.SignalInterpreter)
