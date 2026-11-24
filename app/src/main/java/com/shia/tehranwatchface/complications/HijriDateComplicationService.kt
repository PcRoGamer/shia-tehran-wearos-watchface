package com.shia.tehranwatchface.complications

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.shia.tehranwatchface.data.PrayerPreferences
import com.shia.tehranwatchface.prayer.HijriCalendarHelper
import com.shia.tehranwatchface.ui.MainActivity
import java.time.LocalDate

class HijriDateComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val text = PlainComplicationText.Builder("12 جمادى الآخرة").build()
        val desc = PlainComplicationText.Builder("التاريخ الهجري").build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text, desc).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(text, desc).build()
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val settings = PrayerPreferences.getSettings(this)
        val hijri = HijriCalendarHelper.getHijriDate(LocalDate.now(), settings.hijriOffsetDays)

        val shortLabel = "${hijri.day} ${hijri.monthNameArabic}"
        val longLabel = if (hijri.occasion != null) {
            "$shortLabel • ${hijri.occasion}"
        } else {
            hijri.formattedArabic
        }

        val tapIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val desc = PlainComplicationText.Builder(longLabel).build()

        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(shortLabel).build(),
                contentDescription = desc
            ).setTapAction(tapIntent).build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(longLabel).build(),
                contentDescription = desc
            ).setTapAction(tapIntent).build()

            else -> null
        }
    }
}
