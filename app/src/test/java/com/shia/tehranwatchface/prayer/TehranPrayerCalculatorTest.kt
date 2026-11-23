package com.shia.tehranwatchface.prayer

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class TehranPrayerCalculatorTest {

    @Test
    fun `tehran method produces valid chronological times and shia maghrib after sunset`() {
        val tehranZone = ZoneId.of("Asia/Tehran")
        val date = LocalDate.of(2026, 11, 23)
        val times = TehranPrayerCalculator.calculate(
            date = date,
            latitude = 35.6892,
            longitude = 51.3890,
            elevationMeters = 1189.0,
            zoneId = tehranZone
        )

        assertTrue(times.imsak.isBefore(times.fajr))
        assertTrue(times.fajr.isBefore(times.sunrise))
        assertTrue(times.sunrise.isBefore(times.dhuhr))
        assertTrue(times.dhuhr.isBefore(times.asr))
        assertTrue(times.asr.isBefore(times.sunset))

        // Tehran method: Maghrib is at 4.5° below horizon (~18-25 min after sunset)
        val maghribDiffMin = Duration.between(times.sunset, times.maghrib).toMinutes()
        assertTrue("Expected Maghrib 15-30m after sunset, was $maghribDiffMin", maghribDiffMin in 15..30)
        assertTrue(times.maghrib.isBefore(times.isha))
    }

    @Test
    fun `shia midnight is midpoint between sunset and next fajr`() {
        val najafZone = ZoneId.of("Asia/Baghdad")
        val date = LocalDate.of(2026, 11, 23)
        val today = TehranPrayerCalculator.calculate(date, 32.0, 44.336, 53.0, najafZone)
        val tomorrow = TehranPrayerCalculator.calculate(date.plusDays(1), 32.0, 44.336, 53.0, najafZone)

        val sunsetToMidnight = Duration.between(today.sunset, today.midnight).seconds
        val midnightToFajr = Duration.between(today.midnight, tomorrow.fajr).seconds
        assertTrue(kotlin.math.abs(sunsetToMidnight - midnightToFajr) <= 2)
    }
}
