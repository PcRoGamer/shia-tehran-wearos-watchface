package com.shia.tehranwatchface.complications

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.shia.tehranwatchface.R
import com.shia.tehranwatchface.data.PrayerPreferences
import com.shia.tehranwatchface.prayer.TehranPrayerCalculator
import com.shia.tehranwatchface.ui.MainActivity
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class NextPrayerComplicationService : SuspendingComplicationDataSourceService() {

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale("ar"))

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val title = PlainComplicationText.Builder("المغرب • 5:20 م").build()
        val text = PlainComplicationText.Builder("بعد 01:15").build()
        val desc = PlainComplicationText.Builder("الصلاة القادمة حسب معهد طهران").build()

        return when (type) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(text, desc)
                .setTitle(title)
                .build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder("5:20 م").build(), desc
            ).setTitle(PlainComplicationText.Builder("المغرب").build()).build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = 65f, min = 0f, max = 100f, contentDescription = desc
            ).setTitle(PlainComplicationText.Builder("المغرب").build())
                .setText(PlainComplicationText.Builder("5:20 م").build())
                .build()
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

        val formattedTime = next.time.format(timeFormatter)
        val totalWindowSeconds = Duration.between(next.previousTime.toInstant(), next.time.toInstant())
            .seconds.coerceAtLeast(1)
        val elapsedSeconds = Duration.between(next.previousTime.toInstant(), now)
            .seconds.coerceIn(0, totalWindowSeconds)
        val progressPercent = (elapsedSeconds.toFloat() / totalWindowSeconds.toFloat()) * 100f

        val countdownText = TimeDifferenceComplicationText.Builder(
            TimeDifferenceStyle.SHORT_DUAL_UNIT,
            CountDownTimeReference(next.time.toInstant())
        ).setMinimumTimeUnit(TimeUnit.MINUTES).build()

        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val icon = MonochromaticImage.Builder(
            Icon.createWithResource(this, R.drawable.ic_crescent)
        ).build()

        val contentDesc = PlainComplicationText.Builder(
            "${next.name.arabicName} $formattedTime"
        ).build()

        return when (request.complicationType) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = countdownText,
                contentDescription = contentDesc
            )
                .setTitle(PlainComplicationText.Builder("${next.name.arabicName} • $formattedTime").build())
                .setMonochromaticImage(icon)
                .setTapAction(tapIntent)
                .build()

            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(formattedTime).build(),
                contentDescription = contentDesc
            )
                .setTitle(PlainComplicationText.Builder(next.name.arabicName).build())
                .setMonochromaticImage(icon)
                .setTapAction(tapIntent)
                .build()

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = progressPercent,
                min = 0f,
                max = 100f,
                contentDescription = contentDesc
            )
                .setTitle(PlainComplicationText.Builder(next.name.arabicName).build())
                .setText(PlainComplicationText.Builder(formattedTime).build())
                .setMonochromaticImage(icon)
                .setTapAction(tapIntent)
                .build()

            else -> null
        }
    }
}
