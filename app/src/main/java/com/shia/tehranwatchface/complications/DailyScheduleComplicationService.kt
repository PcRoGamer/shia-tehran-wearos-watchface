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
import java.time.format.DateTimeFormatter
import java.util.Locale

class DailyScheduleComplicationService : SuspendingComplicationDataSourceService() {

    private val fmt12AmPm = DateTimeFormatter.ofPattern("hh:mm a", Locale("ar"))

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val text = PlainComplicationText.Builder("11:35 • نصف الليل").build()
        val desc = PlainComplicationText.Builder("نصف الليل 11:35 م، ثم الفجر 4:12 ص").build()
        return when (type) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(text, desc)
                .setTitle(PlainComplicationText.Builder("04:12 • الفجر").build())
                .build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder("11:35 م").build(), desc
            ).setTitle(PlainComplicationText.Builder("يليها نصف الليل").build()).build()
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val settings = PrayerPreferences.getSettings(this)
        val (_, second, third) = TehranPrayerCalculator.getUpcomingPrayers(
            now = Instant.now(),
            latitude = settings.latitude,
            longitude = settings.longitude,
            elevationMeters = settings.elevationMeters
        )

        val secondTime = second.time.format(fmt12AmPm)
        val thirdTime = third.time.format(fmt12AmPm)
        val secondName = second.name.watchLabel
        val thirdName = third.name.watchLabel

        val tapIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return when (request.complicationType) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("${secondTime.take(5)} • $secondName").build(),
                contentDescription = PlainComplicationText.Builder("$secondName $secondTime، ثم $thirdName $thirdTime").build()
            ).setTitle(PlainComplicationText.Builder("${thirdTime.take(5)} • $thirdName").build())
                .setTapAction(tapIntent)
                .build()

            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(secondTime).build(),
                contentDescription = PlainComplicationText.Builder("$secondName $secondTime").build()
            ).setTitle(PlainComplicationText.Builder("يليها $secondName").build())
                .setTapAction(tapIntent)
                .build()

            else -> null
        }
    }
}
