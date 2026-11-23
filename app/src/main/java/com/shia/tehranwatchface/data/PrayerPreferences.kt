package com.shia.tehranwatchface.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "tehran_prayer_prefs")

data class PrayerSettings(
    val cityName: String = "النجف الأشرف",
    val latitude: Double = 32.0000,
    val longitude: Double = 44.3360,
    val elevationMeters: Double = 53.0,
    val hijriOffsetDays: Int = 0
)

data class CityPreset(
    val nameArabic: String,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double
)

object PrayerPreferences {

    private val KEY_CITY_NAME = stringPreferencesKey("city_name")
    private val KEY_LATITUDE = doublePreferencesKey("latitude")
    private val KEY_LONGITUDE = doublePreferencesKey("longitude")
    private val KEY_ELEVATION = doublePreferencesKey("elevation")
    private val KEY_HIJRI_OFFSET = intPreferencesKey("hijri_offset")

    val presets = listOf(
        CityPreset("النجف الأشرف", 32.0000, 44.3360, 53.0),
        CityPreset("كربلاء المقدسة", 32.6160, 44.0249, 28.0),
        CityPreset("طهران", 35.6892, 51.3890, 1189.0),
        CityPreset("قم المقدسة", 34.6416, 50.8746, 928.0),
        CityPreset("مشهد المقدسة", 36.2605, 59.6168, 995.0),
        CityPreset("بغداد", 33.3152, 44.3661, 34.0),
        CityPreset("بيروت", 33.8938, 35.5018, 55.0),
        CityPreset("المنامة", 26.2285, 50.5860, 5.0),
        CityPreset("ملبورن", -37.8136, 144.9631, 31.0),
        CityPreset("لندن", 51.5072, -0.1276, 11.0)
    )

    fun settingsFlow(context: Context): Flow<PrayerSettings> =
        context.dataStore.data.map { prefs ->
            PrayerSettings(
                cityName = prefs[KEY_CITY_NAME] ?: "النجف الأشرف",
                latitude = prefs[KEY_LATITUDE] ?: 32.0000,
                longitude = prefs[KEY_LONGITUDE] ?: 44.3360,
                elevationMeters = prefs[KEY_ELEVATION] ?: 53.0,
                hijriOffsetDays = prefs[KEY_HIJRI_OFFSET] ?: 0
            )
        }

    suspend fun getSettings(context: Context): PrayerSettings =
        settingsFlow(context).first()

    suspend fun updateLocation(
        context: Context,
        cityName: String,
        latitude: Double,
        longitude: Double,
        elevationMeters: Double = 0.0
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CITY_NAME] = cityName
            prefs[KEY_LATITUDE] = latitude
            prefs[KEY_LONGITUDE] = longitude
            prefs[KEY_ELEVATION] = elevationMeters
        }
    }

    suspend fun updateHijriOffset(context: Context, offsetDays: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HIJRI_OFFSET] = offsetDays.coerceIn(-2, 2)
        }
    }
}
