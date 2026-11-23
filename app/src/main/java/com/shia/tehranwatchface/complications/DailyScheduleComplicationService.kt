package com.shia.tehranwatchface.complications

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.shia.tehranwatchface.data.PrayerPreferences
import com.shia.tehranwatchface.prayer.TehranPrayerCalculator
import com.shia.tehranwatchface.ui.MainActivity
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DailyScheduleComplicationService : SuspendingComplicationDataSourceService() {

    private val fmt12AmPm = DateTimeFormatter.ofPattern("h:mm a", Locale("ar"))

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val text = PlainComplicationText.Builder("يليها: العشاء • 6:35 م").build()
        val desc = PlainComplicationText.Builder("الصلاة التي تلي القادمة").build()
        return when (type) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(text, desc).build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder("6:35 م").build(), desc
            ).setTitle(PlainComplicationText.Builder("يليها العشاء").build()).build()
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val settings = PrayerPreferences.getSettings(this)
        val now = Instant.now()
        val next = TehranPrayerCalculator.getNextPrayer(
            now = now,
            latitude = settings.latitude,
            longitude = settings.longitude,
            elevationMeters = settings.elevationMeters
        )
        // Find the milestone immediately following the next prayer
        val afterNext = TehranPrayerCalculator.getNextPrayer(
            now = next.time.toInstant().plusSeconds(60),
            latitude = settings.latitude,
            longitude = settings.longitude,
            elevationMeters = settings.elevationMeters
        )

        val afterTimeStr = afterNext.time.format(fmt12AmPm)
        val label = "يليها: ${afterNext.name.arabicName} • $afterTimeStr"

        val tapIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val desc = PlainComplicationText.Builder(label).build()

        return when (request.complicationType) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(label).build(),
                contentDescription = desc
            ).setTapAction(tapIntent).build()

            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(afterTimeStr).build(),
                contentDescription = desc
            ).setTitle(PlainComplicationText.Builder("يليها ${afterNext.name.arabicName}").build())
                .setTapAction(tapIntent)
                .build()

            else -> null
        }
    }
}
