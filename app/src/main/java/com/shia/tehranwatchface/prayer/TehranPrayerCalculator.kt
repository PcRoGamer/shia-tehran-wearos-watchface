package com.shia.tehranwatchface.prayer

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.*

/**
 * Astronomical Prayer Time Calculator implementing the
 * Institute of Geophysics, University of Tehran (مؤسسة الجيوفيزياء بجامعة طهران) method.
 *
 * Parameters:
 * - Fajr Angle: 17.7° below horizon
 * - Maghrib Angle: 4.5° below horizon (Shia Jafari criterion: disappearance of eastern redness)
 * - Isha Angle: 14.0° below horizon
 * - Asr: Standard (1x shadow)
 * - Midnight (نصف الليل الشرعي): Midpoint between Sunset and next Fajr (Jafari rule)
 * - Imsak: 10 minutes before Fajr
 */
object TehranPrayerCalculator {

    private const val FAJR_ANGLE = 17.7
    private const val MAGHRIB_ANGLE = 4.5
    private const val ISHA_ANGLE = 14.0
    private const val IMSAK_OFFSET_MINUTES = 10L

    data class PrayerTimes(
        val date: LocalDate,
        val imsak: ZonedDateTime,
        val fajr: ZonedDateTime,
        val sunrise: ZonedDateTime,
        val dhuhr: ZonedDateTime,
        val asr: ZonedDateTime,
        val sunset: ZonedDateTime,
        val maghrib: ZonedDateTime,
        val isha: ZonedDateTime,
        val midnight: ZonedDateTime
    )

    enum class PrayerName(val arabicName: String) {
        IMSAK("الإمساك"),
        FAJR("الفجر"),
        SUNRISE("الشروق"),
        DHUHR("الظهر"),
        ASR("العصر"),
        SUNSET("الغروب"),
        MAGHRIB("المغرب"),
        ISHA("العشاء"),
        MIDNIGHT("نصف الليل");

        val watchLabel: String
            get() = when (this) {
                SUNRISE -> "آخر الفجر"
                SUNSET -> "آخر العصر"
                MIDNIGHT -> "آخر العشاء"
                else -> arabicName
            }
    }

    data class NextPrayerInfo(
        val name: PrayerName,
        val time: ZonedDateTime,
        val previousTime: ZonedDateTime
    )

    fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        elevationMeters: Double = 0.0,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): PrayerTimes {
        val julianDay = toJulianDate(date) - longitude / (15.0 * 24.0)
        val timezoneHours = zoneId.rules.getOffset(
            date.atTime(12, 0).atZone(zoneId).toInstant()
        ).totalSeconds / 3600.0

        val dhuhrHour = computeMidDay(julianDay, timezoneHours, longitude)
        val sunriseAngle = 0.833 + 0.0347 * sqrt(max(0.0, elevationMeters))

        val fajrHour = sunAngleTime(julianDay, latitude, longitude, timezoneHours, FAJR_ANGLE, isMorning = true)
        val sunriseHour = sunAngleTime(julianDay, latitude, longitude, timezoneHours, sunriseAngle, isMorning = true)
        val asrHour = asrTime(julianDay, latitude, longitude, timezoneHours, factor = 1.0)
        val sunsetHour = sunAngleTime(julianDay, latitude, longitude, timezoneHours, sunriseAngle, isMorning = false)
        val maghribHour = sunAngleTime(julianDay, latitude, longitude, timezoneHours, MAGHRIB_ANGLE, isMorning = false)
        val ishaHour = sunAngleTime(julianDay, latitude, longitude, timezoneHours, ISHA_ANGLE, isMorning = false)

        val fajr = hoursToZonedDateTime(date, fajrHour, zoneId)
        val imsak = fajr.minusMinutes(IMSAK_OFFSET_MINUTES)
        val sunrise = hoursToZonedDateTime(date, sunriseHour, zoneId)
        val dhuhr = hoursToZonedDateTime(date, dhuhrHour, zoneId)
        val asr = hoursToZonedDateTime(date, asrHour, zoneId)
        val sunset = hoursToZonedDateTime(date, sunsetHour, zoneId)
        val maghrib = hoursToZonedDateTime(date, maghribHour, zoneId)
        val isha = hoursToZonedDateTime(date, ishaHour, zoneId)

        // Next day's Fajr for Shia Shar'i Midnight (midpoint between Sunset and Fajr)
        val nextDate = date.plusDays(1)
        val nextJulianDay = toJulianDate(nextDate) - longitude / (15.0 * 24.0)
        val nextTzHours = zoneId.rules.getOffset(
            nextDate.atTime(12, 0).atZone(zoneId).toInstant()
        ).totalSeconds / 3600.0
        val nextFajrHour = sunAngleTime(nextJulianDay, latitude, longitude, nextTzHours, FAJR_ANGLE, isMorning = true)
        val nextFajr = hoursToZonedDateTime(nextDate, nextFajrHour, zoneId)

        val nightDurationSeconds = java.time.Duration.between(sunset, nextFajr).seconds
        val midnight = sunset.plusSeconds(nightDurationSeconds / 2)

        return PrayerTimes(
            date = date,
            imsak = imsak,
            fajr = fajr,
            sunrise = sunrise,
            dhuhr = dhuhr,
            asr = asr,
            sunset = sunset,
            maghrib = maghrib,
            isha = isha,
            midnight = midnight
        )
    }

    fun getNextPrayer(
        now: Instant,
        latitude: Double,
        longitude: Double,
        elevationMeters: Double = 0.0,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): NextPrayerInfo = getUpcomingPrayers(now, latitude, longitude, elevationMeters, zoneId).first()

    fun getUpcomingPrayers(
        now: Instant,
        latitude: Double,
        longitude: Double,
        elevationMeters: Double = 0.0,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<NextPrayerInfo> {
        val today = now.atZone(zoneId).toLocalDate()
        return (-1L..1L).flatMap { days ->
            val times = calculate(today.plusDays(days), latitude, longitude, elevationMeters, zoneId)
            listOf(
                PrayerName.FAJR to times.fajr,
                PrayerName.SUNRISE to times.sunrise,
                PrayerName.DHUHR to times.dhuhr,
                PrayerName.SUNSET to times.sunset,
                PrayerName.MAGHRIB to times.maghrib,
                PrayerName.MIDNIGHT to times.midnight
            )
        }.sortedBy { (_, time) -> time.toInstant() }
            .zipWithNext { previous, (name, time) -> NextPrayerInfo(name, time, previous.second) }
            .filter { it.time.toInstant().isAfter(now) }
            .take(3)
    }

    private fun toJulianDate(date: LocalDate): Double {
        var year = date.year
        var month = date.monthValue
        val day = date.dayOfMonth
        if (month <= 2) {
            year -= 1
            month += 12
        }
        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + b - 1524.5
    }

    private data class SunPosition(val declinationDeg: Double, val equationOfTimeHours: Double)

    private fun sunPosition(jd: Double): SunPosition {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sinDeg(g) + 0.020 * sinDeg(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = atan2Deg(cosDeg(e) * sinDeg(l), cosDeg(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = asinDeg(sinDeg(e) * sinDeg(l))
        return SunPosition(decl, eqt)
    }

    private fun computeMidDay(jd: Double, timezoneHours: Double, longitude: Double): Double {
        val pos = sunPosition(jd + 0.5)
        return fixHour(12.0 + timezoneHours - longitude / 15.0 - pos.equationOfTimeHours)
    }

    private fun sunAngleTime(
        jd: Double,
        latitude: Double,
        longitude: Double,
        timezoneHours: Double,
        angle: Double,
        isMorning: Boolean
    ): Double {
        val approxDayFraction = if (isMorning) 0.25 else 0.75
        val pos = sunPosition(jd + approxDayFraction)
        val midDay = fixHour(12.0 + timezoneHours - longitude / 15.0 - pos.equationOfTimeHours)
        val cosT = (-sinDeg(angle) - sinDeg(pos.declinationDeg) * sinDeg(latitude)) /
            (cosDeg(pos.declinationDeg) * cosDeg(latitude))
        val clampedCosT = cosT.coerceIn(-1.0, 1.0)
        val t = acosDeg(clampedCosT) / 15.0
        return midDay + if (isMorning) -t else t
    }

    private fun asrTime(
        jd: Double,
        latitude: Double,
        longitude: Double,
        timezoneHours: Double,
        factor: Double
    ): Double {
        val pos = sunPosition(jd + 0.5)
        val angle = -acotDeg(factor + tanDeg(abs(latitude - pos.declinationDeg)))
        return sunAngleTime(jd, latitude, longitude, timezoneHours, angle, isMorning = false)
    }

    private fun hoursToZonedDateTime(date: LocalDate, rawHours: Double, zoneId: ZoneId): ZonedDateTime {
        val hours = fixHour(rawHours)
        val totalSeconds = (hours * 3600.0).roundToLong()
        val h = ((totalSeconds / 3600) % 24).toInt()
        val m = ((totalSeconds % 3600) / 60).toInt()
        val s = (totalSeconds % 60).toInt()
        return ZonedDateTime.of(date, LocalTime.of(h, m, s), zoneId)
    }

    private fun fixAngle(a: Double): Double = a - 360.0 * floor(a / 360.0)
    private fun fixHour(a: Double): Double = a - 24.0 * floor(a / 24.0)
    private fun sinDeg(d: Double): Double = sin(Math.toRadians(d))
    private fun cosDeg(d: Double): Double = cos(Math.toRadians(d))
    private fun tanDeg(d: Double): Double = tan(Math.toRadians(d))
    private fun asinDeg(x: Double): Double = Math.toDegrees(asin(x))
    private fun acosDeg(x: Double): Double = Math.toDegrees(acos(x))
    private fun atan2Deg(y: Double, x: Double): Double = Math.toDegrees(atan2(y, x))
    private fun acotDeg(x: Double): Double = Math.toDegrees(atan(1.0 / x))
}
