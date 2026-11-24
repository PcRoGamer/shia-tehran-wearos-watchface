package com.shia.tehranwatchface.complications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

class SkyPositionTest {

    @Test
    fun calendarDayCanBeDarkInMelbourneWhileLondonHasDaylight() {
        val now = Instant.parse("2025-06-01T08:00:00Z")
        val melbourne = SkyPosition.packed(now, -37.8136, 144.9631, 31.0)
        val london = SkyPosition.packed(now, 51.5072, -0.1276, 11.0)
        assertTrue(sunAltitude(melbourne) < -6)
        assertTrue(sunAltitude(london) > 15)
    }

    @Test
    fun solarAndLunarPositionsAlignDuringTheDallasEclipse() {
        val now = Instant.parse("2024-04-08T18:42:00Z")
        val frame = SkyPosition.packed(now, 32.7767, -96.797, 131.0)
        assertTrue(frame in 0..SkyPosition.MAX_VALUE)
        assertEquals(frame, frame.toFloat().toInt())
        assertTrue(sunAltitude(frame) > 45)
        assertTrue(abs(sunAltitude(frame) - ((frame and 63) * 2 - 36)) <= 2)
        assertTrue(abs((frame shr 18) - ((frame shr 6) and 63)) <= 1)
    }

    private fun sunAltitude(frame: Int) = ((frame shr 12) and 63) * 2 - 36
}
