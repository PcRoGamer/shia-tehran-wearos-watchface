package com.shia.tehranwatchface.data

import android.content.Context
import android.util.Log
import com.shia.tehranwatchface.complications.CloudPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos

/**
 * Hourly cloud layers from Open-Meteo (free, no key): local low/mid/high cover, weather code,
 * visibility and humidity, plus low cloud ~120 km west and east as a proxy for the sunset and
 * sunrise horizon. Cached for an hour; on failure the last cache (if still covering the hour)
 * or nothing is returned and the face uses its own weather model.
 */
object CloudForecast {
    private const val TAG = "CloudForecast"
    private const val PREFS = "tehran_cloud_forecast"
    private const val MAX_AGE_MS = 60 * 60 * 1000L
    private const val HORIZON_KM = 120.0

    class Forecast(private val local: JSONObject, private val west: JSONObject?, private val east: JSONObject?) {
        private val times = local.getJSONArray("time")

        /** Hour data for [time]; [evening] picks the west horizon, otherwise east. */
        fun hourAt(time: Instant, evening: Boolean): CloudPalette.Hour? {
            val t = time.epochSecond
            var best = -1
            var bestGap = Long.MAX_VALUE
            for (i in 0 until times.length()) {
                val gap = abs(times.getLong(i) + 1800 - t)
                if (gap < bestGap) { bestGap = gap; best = i }
            }
            if (best < 0 || bestGap > 5400) return null
            fun num(o: JSONObject?, key: String): Double? =
                o?.optJSONArray(key)?.let { if (best < it.length() && !it.isNull(best)) it.getDouble(best) else null }
            val horizon = if (evening) west else east
            return CloudPalette.Hour(
                lowPct = num(local, "cloud_cover_low") ?: return null,
                midPct = num(local, "cloud_cover_mid") ?: 0.0,
                highPct = num(local, "cloud_cover_high") ?: 0.0,
                weatherCode = num(local, "weather_code")?.toInt() ?: 0,
                visibilityM = num(local, "visibility"),
                humidityPct = num(local, "relative_humidity_2m"),
                horizonLowPct = num(horizon, "cloud_cover_low")
            )
        }
    }

    suspend fun get(context: Context, latitude: Double, longitude: Double): Forecast? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = String.format(Locale.US, "%.2f,%.2f", latitude, longitude)
        val cached = prefs.getString("body", null)?.takeIf { prefs.getString("key", null) == key }
        val fresh = System.currentTimeMillis() - prefs.getLong("at", 0) < MAX_AGE_MS
        val body = if (cached != null && fresh) cached else {
            fetch(latitude, longitude)?.also {
                prefs.edit().putString("body", it).putString("key", key)
                    .putLong("at", System.currentTimeMillis()).apply()
            } ?: cached
        }
        return body?.let { parse(it) }
    }

    private fun parse(body: String): Forecast? = runCatching {
        val arr = JSONArray(body)
        Forecast(arr.getJSONObject(0).getJSONObject("hourly"),
            arr.optJSONObject(1)?.optJSONObject("hourly"),
            arr.optJSONObject(2)?.optJSONObject("hourly"))
    }.onFailure { Log.w(TAG, "parse failed", it) }.getOrNull()

    private suspend fun fetch(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(12_000) {
            runCatching {
                val dLon = HORIZON_KM / (111.32 * cos(Math.toRadians(latitude)).coerceAtLeast(0.2))
                val lats = List(3) { latitude }
                val lons = listOf(longitude, longitude - dLon, longitude + dLon)
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=" + lats.joinToString(",") { String.format(Locale.US, "%.4f", it) } +
                        "&longitude=" + lons.joinToString(",") { String.format(Locale.US, "%.4f", ((it + 540) % 360) - 180) } +
                        "&hourly=cloud_cover_low,cloud_cover_mid,cloud_cover_high,weather_code,visibility,relative_humidity_2m" +
                        "&forecast_days=2&timezone=GMT&timeformat=unixtime"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                try {
                    if (conn.responseCode != 200) null
                    else conn.inputStream.bufferedReader().use { it.readText() }
                } finally { conn.disconnect() }
            }.onFailure { Log.w(TAG, "fetch failed", it) }.getOrNull()
        }
    }
}
