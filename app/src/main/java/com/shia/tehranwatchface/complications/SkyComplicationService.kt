package com.shia.tehranwatchface.complications

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import com.shia.tehranwatchface.R
import com.shia.tehranwatchface.data.PrayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

class SkyComplicationService : SuspendingTimelineComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = null

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline {
        if (request.complicationType != ComplicationType.RANGED_VALUE) {
            return ComplicationDataTimeline(NoDataComplicationData(), emptyList())
        }

        val description = PlainComplicationText.Builder(getString(R.string.complication_sky_label)).build()
        // A different maximum tells the face to use its own sky after the last frame expires.
        val fallback = RangedValueComplicationData.Builder(0f, 0f, 1f, description)
            .setText(description)
            .build()
        val settings = PrayerPreferences.getSettings(this)
        if (!settings.locationConfigured) {
            return ComplicationDataTimeline(fallback, emptyList())
        }

        val start = Instant.now().truncatedTo(ChronoUnit.MINUTES)
        val entries = withContext(Dispatchers.Default) {
            List(60) { index ->
                val from = start.plusSeconds(index * 120L)
                val until = from.plusSeconds(120)
                val position = SkyPosition.packed(
                    time = from.plusSeconds(60),
                    latitude = settings.latitude,
                    longitude = settings.longitude,
                    elevation = settings.elevationMeters.coerceAtLeast(0.0)
                )
                TimelineEntry(
                    validity = TimeInterval(from, until),
                    complicationData = RangedValueComplicationData.Builder(
                        value = position.toFloat(),
                        min = 0f,
                        max = SkyPosition.MAX_VALUE.toFloat(),
                        contentDescription = description
                    ).setText(description).build()
                )
            }
        }
        return ComplicationDataTimeline(fallback, entries)
    }
}
