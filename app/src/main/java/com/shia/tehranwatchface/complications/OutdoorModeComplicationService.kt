package com.shia.tehranwatchface.complications

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.shia.tehranwatchface.R
import com.shia.tehranwatchface.data.OutdoorMode

/** Hidden provider for the face's slot 10: value 1 while the outdoor switch is on, else 0. */
class OutdoorModeComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = null

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        if (request.complicationType != ComplicationType.RANGED_VALUE) return NoDataComplicationData()
        val label = PlainComplicationText.Builder(getString(R.string.complication_outdoor_label)).build()
        val value = if (OutdoorMode.isEnabled(this)) 1f else 0f
        return RangedValueComplicationData.Builder(value, 0f, 1f, label).setText(label).build()
    }
}
