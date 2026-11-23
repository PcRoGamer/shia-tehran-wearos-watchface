package com.shia.tehranwatchface.complications

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.shia.tehranwatchface.data.PrayerPreferences
import com.shia.tehranwatchface.prayer.TehranPrayerCalculator
import com.shia.tehranwatchface.ui.MainActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DailyScheduleComplicationService : SuspendingComplicationDataSourceService() {

    private val fmt = DateTimeFormatter.ofPattern("HH:mm")

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val text = PlainComplicationText.Builder("فجر 05:10 | ظهر 11:48 | مغرب 17:20").build()
        val desc = PlainComplicationText.Builder("مواقيت اليوم").build()
        return when (type) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(text, desc).build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder("23:15").build(), desc
            ).setTitle(PlainComplicationText.Builder("نصف الليل").build()).build()
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val settings = PrayerPreferences.getSettings(this)
        val times = TehranPrayerCalculator.calculate(
            date = LocalDate.now(),
            latitude = settings.latitude,
            longitude = settings.longitude,
            elevationMeters = settings.elevationMeters
        )

        val summary = "فجر ${times.fajr.format(fmt)} | ظهر ${times.dhuhr.format(fmt)} | مغرب ${times.maghrib.format(fmt)}"
        val midnightStr = times.midnight.format(fmt)

        val tapIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val desc = PlainComplicationText.Builder("$summary • نصف الليل $midnightStr").build()

        return when (request.complicationType) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(summary).build(),
                contentDescription = desc
            ).setTapAction(tapIntent).build()

            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(midnightStr).build(),
                contentDescription = desc
            ).setTitle(PlainComplicationText.Builder("نصف الليل").build())
                .setTapAction(tapIntent)
                .build()

            else -> null
        }
    }
}
