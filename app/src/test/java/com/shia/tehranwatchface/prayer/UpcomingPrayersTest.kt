package com.shia.tehranwatchface.prayer

import com.shia.tehranwatchface.prayer.TehranPrayerCalculator.PrayerName
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class UpcomingPrayersTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private val date = LocalDate.of(2026, 11, 25)
    private val times = TehranPrayerCalculator.calculate(date, 35.6892, 51.389, 1189.0, zone)

    private fun upcoming(now: Instant) =
        TehranPrayerCalculator.getUpcomingPrayers(now, 35.6892, 51.389, 1189.0, zone)

    @Test
    fun `sunrise marks the end of fajr`() {
        val events = upcoming(times.fajr.toInstant())
        assertEquals(listOf(PrayerName.SUNRISE, PrayerName.DHUHR, PrayerName.SUNSET), events.map { it.name })
        assertEquals(listOf("الشروق", "الظهر والعصر", "الغروب"), events.map { it.name.watchLabel })
        assertEquals(times.fajr, events.first().previousTime)
        assertEquals(times.sunrise, events.first().time)
    }

    @Test
    fun `actual sunset and maghrib are separate milestones`() {
        val events = upcoming(times.sunset.toInstant().minusSeconds(1))
        assertEquals(listOf(PrayerName.SUNSET, PrayerName.MAGHRIB, PrayerName.MIDNIGHT), events.map { it.name })
        assertEquals(listOf("الغروب", "المغرب والعشاء", "نصف الليل الشرعي"), events.map { it.name.watchLabel })
        assertEquals(listOf(times.sunset, times.maghrib, times.midnight), events.map { it.time })
    }

    @Test
    fun `schedule advances exactly at islamic midnight and includes tomorrow`() {
        val events = upcoming(times.midnight.toInstant())
        assertEquals(listOf(PrayerName.FAJR, PrayerName.SUNRISE, PrayerName.DHUHR), events.map { it.name })
        assertEquals(times.midnight, events.first().previousTime)
        assertEquals(date.plusDays(1), events.first().time.toLocalDate())
    }
}
