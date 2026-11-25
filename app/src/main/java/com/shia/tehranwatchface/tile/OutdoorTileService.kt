package com.shia.tehranwatchface.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.shia.tehranwatchface.R
import com.shia.tehranwatchface.data.OutdoorMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** One-button tile that flips the outdoor (black background) switch. */
class OutdoorTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                try {
                    var enabled = OutdoorMode.isEnabled(this@OutdoorTileService)
                    if (requestParams.currentState.lastClickableId == TOGGLE_ID) {
                        enabled = !enabled
                        OutdoorMode.set(this@OutdoorTileService, enabled)
                    }
                    completer.set(tile(enabled))
                } catch (error: Throwable) {
                    completer.setException(error)
                }
            }
            "outdoor tile"
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
            "outdoor resources"
        }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun tile(enabled: Boolean): TileBuilders.Tile {
        val label = getString(if (enabled) R.string.tile_outdoor_on else R.string.tile_outdoor_off)
        val button = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(156f))
            .setHeight(dp(68f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId(TOGGLE_ID)
                            .setOnClick(ActionBuilders.LoadAction.Builder().build())
                            .build()
                    )
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(if (enabled) 0xFF42FF44.toInt() else 0xFF26323A.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(34f)).build())
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(label)
                    .setMaxLines(2)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(15f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .setColor(argb(if (enabled) 0xFF000000.toInt() else 0xFFFDEB00.toInt()))
                            .build()
                    )
                    .build()
            )
            .build()
        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .addContent(button)
            .build()
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
    }

    private companion object {
        const val TOGGLE_ID = "toggle_outdoor"
        const val RESOURCES_VERSION = "1"
    }
}
