package com.shia.tehranwatchface.complications

import org.shredzone.commons.suncalc.MoonPosition
import org.shredzone.commons.suncalc.SunPosition
import java.time.Instant
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal object SkyPosition {

    const val MAX_VALUE = 16_777_215

    fun packed(time: Instant, latitude: Double, longitude: Double, elevation: Double): Int {
        val sun = SunPosition.compute().on(time).at(latitude, longitude).elevation(elevation).execute()
        val moon = MoonPosition.compute().on(time).at(latitude, longitude).elevation(elevation).execute()
        return (screenX(sun.azimuth, sun.trueAltitude) shl 18) or
            (encodeAltitude(sun.trueAltitude) shl 12) or
            (screenX(moon.azimuth, moon.altitude) shl 6) or encodeAltitude(moon.altitude)
    }

    // East appears on the left and west on the right. Altitude compresses the horizon circle
    // toward the center as an object approaches the zenith.
    private fun screenX(azimuth: Double, altitude: Double): Int {
        val horizontal = sin(Math.toRadians(azimuth)) * cos(Math.toRadians(altitude.coerceAtLeast(0.0)))
        return ((1.0 - horizontal) * 31.5).roundToInt().coerceIn(0, 63)
    }

    // Four six-bit integers remain lossless when passed through a ranged complication float.
    private fun encodeAltitude(altitude: Double): Int =
        ((altitude + 36.0) / 2.0).roundToInt().coerceIn(0, 63)
}
