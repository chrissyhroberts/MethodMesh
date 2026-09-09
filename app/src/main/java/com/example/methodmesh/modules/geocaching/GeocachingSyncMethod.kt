package com.example.methodmesh.modules.geocaching

object As100GeocachingSyncMethod:GeocachingMethodBase("geocache.sync","Geocaching sync","Refresh an OpenCaching account, import remote logs or deliberately upload one selected local visit.",GeocachingContracts.sync,connectivity="ONLINE_ONLY")
