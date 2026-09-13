package br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime

import kotlin.math.cos
import kotlin.math.sin

data class WorldTimeVec3(val x: Float, val y: Float, val z: Float) { fun magnitude() = kotlin.math.sqrt(x*x+y*y+z*z) }
/** Longitude 0 faces +Z; positive longitude turns toward +X. */
object WorldTimeCoordinates { fun latLon(latitude: Float, longitude: Float, radius: Float): WorldTimeVec3 { val lat=Math.toRadians(latitude.toDouble()); val lon=Math.toRadians(longitude.toDouble()); return WorldTimeVec3((radius*cos(lat)*sin(lon)).toFloat(),(radius*sin(lat)).toFloat(),(radius*cos(lat)*cos(lon)).toFloat()) } }
