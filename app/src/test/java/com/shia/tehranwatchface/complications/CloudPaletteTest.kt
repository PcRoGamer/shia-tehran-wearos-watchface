package com.shia.tehranwatchface.complications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudPaletteTest {

    private fun hour(low: Double, mid: Double, high: Double, code: Int = 2, vis: Double? = 30_000.0, horizon: Double? = null) =
        CloudPalette.Hour(low, mid, high, code, vis, 60.0, horizon)

    @Test
    fun scatteredHighCloudWithClearHorizonIsVivid() {
        val r = CloudPalette.evaluate(hour(low = 5.0, mid = 30.0, high = 40.0, horizon = 0.0))
        assertTrue(r.vivid > 0.8)
        assertTrue(r.gloom < 0.15)
    }

    @Test
    fun lowDeckOnTheSunsetHorizonKillsColour() {
        val r = CloudPalette.evaluate(hour(low = 10.0, mid = 30.0, high = 40.0, horizon = 80.0))
        assertTrue(r.vivid < 0.2)
    }

    @Test
    fun thunderstormIsGloomy() {
        val r = CloudPalette.evaluate(hour(low = 90.0, mid = 80.0, high = 60.0, code = 95))
        assertTrue(r.gloom > 0.95)
        assertTrue(r.vivid < 0.05)
    }

    @Test
    fun hazeMutesColour() {
        val clean = CloudPalette.evaluate(hour(5.0, 40.0, 40.0, vis = 30_000.0, horizon = 0.0)).vivid
        val hazy = CloudPalette.evaluate(hour(5.0, 40.0, 40.0, vis = 6_000.0, horizon = 0.0)).vivid
        assertTrue(hazy < clean * 0.5)
    }

    @Test
    fun codePacksBothValuesLosslessly() {
        val r = CloudPalette.Result(gloom = 1.0, vivid = 1.0)
        assertEquals(256, r.code)
        assertEquals(1, CloudPalette.Result(0.0, 0.0).code)
        val c = CloudPalette.Result(0.4, 0.6).code - 1
        assertEquals(9, c % 16)
        assertEquals(6, c / 16)
    }
}
