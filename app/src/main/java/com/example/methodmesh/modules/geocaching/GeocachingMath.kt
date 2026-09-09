package com.example.methodmesh.modules.geocaching

import kotlin.math.*

object GeocachingMath {
    private const val EARTH_M = 6371008.8
    fun distanceM(lat1:Double, lon1:Double, lat2:Double, lon2:Double):Double {
        val p1=Math.toRadians(lat1); val p2=Math.toRadians(lat2); val dP=Math.toRadians(lat2-lat1); val dL=Math.toRadians(lon2-lon1)
        val a=sin(dP/2).pow(2)+cos(p1)*cos(p2)*sin(dL/2).pow(2)
        return EARTH_M*2*atan2(sqrt(a),sqrt(1-a))
    }
    fun bearingDeg(lat1:Double,lon1:Double,lat2:Double,lon2:Double):Double {
        val p1=Math.toRadians(lat1); val p2=Math.toRadians(lat2); val dL=Math.toRadians(lon2-lon1)
        val y=sin(dL)*cos(p2); val x=cos(p1)*sin(p2)-sin(p1)*cos(p2)*cos(dL)
        return (Math.toDegrees(atan2(y,x))+360.0)%360.0
    }
    fun relativeBearingDeg(target:Double, heading:Double):Double = ((target-heading+540.0)%360.0)-180.0
    fun relativeBearing(target:Double, heading:Double):Double = relativeBearingDeg(target, heading)
    fun project(lat:Double,lon:Double,bearingDeg:Double,distanceM:Double):Pair<Double,Double>{
        val d=distanceM/EARTH_M; val b=Math.toRadians(bearingDeg); val p1=Math.toRadians(lat); val l1=Math.toRadians(lon)
        val p2=asin(sin(p1)*cos(d)+cos(p1)*sin(d)*cos(b)); val l2=l1+atan2(sin(b)*sin(d)*cos(p1),cos(d)-sin(p1)*sin(p2))
        return Math.toDegrees(p2) to ((Math.toDegrees(l2)+540.0)%360.0-180.0)
    }
    fun circularMeanLongitude(values:List<Double>):Double{
        if(values.isEmpty()) return Double.NaN
        val x=values.sumOf{cos(Math.toRadians(it))}; val y=values.sumOf{sin(Math.toRadians(it))}; return Math.toDegrees(atan2(y,x))
    }
    fun meanCoordinate(points:List<Pair<Double,Double>>):Pair<Double,Double>{
        require(points.isNotEmpty())
        val xyz=points.map{(lat,lon)->val p=Math.toRadians(lat);val l=Math.toRadians(lon);Triple(cos(p)*cos(l),cos(p)*sin(l),sin(p))}
        val x=xyz.sumOf{it.first}/xyz.size;val y=xyz.sumOf{it.second}/xyz.size;val z=xyz.sumOf{it.third}/xyz.size
        val lon=atan2(y,x);val hyp=sqrt(x*x+y*y);val lat=atan2(z,hyp)
        return Math.toDegrees(lat) to Math.toDegrees(lon)
    }
}
