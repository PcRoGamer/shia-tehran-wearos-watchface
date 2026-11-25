package com.shia.tehranwatchface.complications

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Turns one hour of cloud forecast into two cloud-colour inputs for the face:
 *
 * - gloom (0..1): how dark cloud bases look. Thick low cloud and precipitation mean optically
 *   thick cloud that lets little light through, so the bases read grey-blue to slate.
 * - vivid (0..1): the potential for coloured sunrise/sunset cloud (gold, then pink/purple).
 *   Colour needs mid/high cloud to catch unfiltered low sun, an open low-cloud horizon toward
 *   the sun so light can reach their undersides, and clean, dry air (Corfidi, NOAA SPC;
 *   weather.com sunset explainer). Rain at that hour mutes it.
 *
 * The face combines these with its own sun elevation (gold around +4 deg, pink/purple around
 * -2.5 deg, i.e. just after sunset / before sunrise) and falls back to its built-in weather
 * condition when no forecast is available.
 */
object CloudPalette {

    data class Hour(
        val lowPct: Double,
        val midPct: Double,
        val highPct: Double,
        val weatherCode: Int,
        val visibilityM: Double?,
        val humidityPct: Double?,
        /** Low cloud at a point ~120 km toward the sun (west in the evening, east in the morning). */
        val horizonLowPct: Double?
    )

    data class Result(val gloom: Double, val vivid: Double) {
        /** 1..256: 1 + vivid(0..15) + 16 * gloom(0..15). Sent as the sky complication's -min. */
        val code: Int get() = 1 + (vivid * 15).roundToInt() + 16 * (gloom * 15).roundToInt()
    }

    fun evaluate(h: Hour): Result {
        val low = h.lowPct.coerceIn(0.0, 100.0) / 100
        val mid = h.midPct.coerceIn(0.0, 100.0) / 100
        val high = h.highPct.coerceIn(0.0, 100.0) / 100
        val code = h.weatherCode

        val precipitation = when (code) {
            in 95..99 -> 0.5
            65, 67, 82 -> 0.4
            in 61..67, in 80..81 -> 0.3
            in 71..77, 85, 86 -> 0.2
            in 51..57 -> 0.15
            45, 48 -> 0.25
            else -> 0.0
        }
        val gloom = (0.6 * low + 0.2 * mid + precipitation).coerceIn(0.0, 1.0)

        // Combined mid+high cover: best between ~30% and ~75%; a solid deck dulls the show.
        val upper = 1 - (1 - mid) * (1 - high)
        val cover = (upper / 0.3).coerceIn(0.0, 1.0) *
            if (upper > 0.75) (1 - (upper - 0.75) / 0.25 * 0.7).coerceIn(0.0, 1.0) else 1.0
        // Low cloud overhead and on the sun-side horizon blocks the light reaching the layer.
        val blocking = 0.4 * low + 0.6 * ((h.horizonLowPct ?: h.lowPct).coerceIn(0.0, 100.0) / 100)
        val open = (1 - blocking / 0.6).coerceIn(0.0, 1.0).pow(1.2)
        // Haze and moist air mute colours (Mie scattering); clean air keeps them saturated.
        val clean = h.visibilityM?.let { ((it - 5_000) / 15_000).coerceIn(0.4, 1.0) } ?: 1.0
        val dry = if ((h.humidityPct ?: 0.0) > 90) 0.8 else 1.0
        val wet = if (code >= 51) 0.3 else 1.0
        val vivid = (cover * open * clean * dry * wet).coerceIn(0.0, 1.0)
        return Result(gloom, vivid)
    }
}
