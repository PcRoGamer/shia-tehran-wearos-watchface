package com.shia.tehranwatchface.data

import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.shia.tehranwatchface.complications.OutdoorModeComplicationService
import com.shia.tehranwatchface.tile.OutdoorTileService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.outdoorStore by preferencesDataStore(name = "tehran_outdoor_mode")

/** The outdoor (black background) switch shared by the app screen, the tile and the face. */
object OutdoorMode {
    private val KEY_ENABLED = booleanPreferencesKey("enabled")

    fun flow(context: Context): Flow<Boolean> =
        context.outdoorStore.data.map { it[KEY_ENABLED] ?: false }

    suspend fun isEnabled(context: Context): Boolean = flow(context).first()

    suspend fun set(context: Context, enabled: Boolean) {
        context.outdoorStore.edit { it[KEY_ENABLED] = enabled }
        // Push the new value to the face right away and redraw the tile.
        ComplicationDataSourceUpdateRequester
            .create(context, ComponentName(context, OutdoorModeComplicationService::class.java))
            .requestUpdateAll()
        TileService.getUpdater(context).requestUpdate(OutdoorTileService::class.java)
    }
}
