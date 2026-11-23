package com.shia.tehranwatchface.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.shia.tehranwatchface.complications.DailyScheduleComplicationService
import com.shia.tehranwatchface.complications.HijriDateComplicationService
import com.shia.tehranwatchface.complications.NextPrayerComplicationService
import com.shia.tehranwatchface.data.PrayerPreferences
import com.shia.tehranwatchface.data.PrayerSettings
import com.shia.tehranwatchface.prayer.HijriCalendarHelper
import com.shia.tehranwatchface.prayer.TehranPrayerCalculator
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colors = Colors(
                        primary = Color(0xFFD4AF37),
                        primaryVariant = Color(0xFFB38F28),
                        secondary = Color(0xFF0B3529),
                        background = Color(0xFF041E17),
                        surface = Color(0xFF0B3529),
                        onPrimary = Color.Black,
                        onBackground = Color(0xFFF5EFE0),
                        onSurface = Color(0xFFF5EFE0)
                    )
                ) {
                    TehranWatchFaceApp()
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun TehranWatchFaceApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    val settings by PrayerPreferences.settingsFlow(context).collectAsState(initial = PrayerSettings())
    var gpsStatus by remember { mutableStateOf<String?>(null) }

    fun refreshComplications() {
        listOf(
            NextPrayerComplicationService::class.java,
            HijriDateComplicationService::class.java,
            DailyScheduleComplicationService::class.java
        ).forEach { serviceClass ->
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, serviceClass))
                .requestUpdateAll()
        }
    }

    val fetchGps: () -> Unit = {
        scope.launch {
            try {
                gpsStatus = "جاري تحديد الموقع..."
                val client = LocationServices.getFusedLocationProviderClient(context)
                val loc = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                if (loc != null) {
                    PrayerPreferences.updateLocation(
                        context = context,
                        cityName = "موقعي الحالي (GPS)",
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        elevationMeters = loc.altitude
                    )
                    refreshComplications()
                    gpsStatus = "تم تحديث الموقع بنجاح"
                } else {
                    gpsStatus = "تعذر الحصول على الإحداثيات"
                }
            } catch (e: Exception) {
                gpsStatus = "تعذر الوصول إلى GPS"
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) fetchGps() else gpsStatus = "يرجى منح صلاحية الموقع"
    }

    val today = remember { LocalDate.now() }
    val times = remember(settings) {
        TehranPrayerCalculator.calculate(
            date = today,
            latitude = settings.latitude,
            longitude = settings.longitude,
            elevationMeters = settings.elevationMeters
        )
    }
    val hijri = remember(settings.hijriOffsetDays) {
        HijriCalendarHelper.getHijriDate(today, settings.hijriOffsetDays)
    }
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }

    val prayerRows = remember(times) {
        listOf(
            "الإمساك" to times.imsak.format(fmt),
            "الفجر (17.7°)" to times.fajr.format(fmt),
            "الشروق" to times.sunrise.format(fmt),
            "الظهر" to times.dhuhr.format(fmt),
            "العصر" to times.asr.format(fmt),
            "المغرب (4.5°)" to times.maghrib.format(fmt),
            "العشاء (14°)" to times.isha.format(fmt),
            "نصف الليل الشرعي" to times.midnight.format(fmt)
        )
    }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp)
        ) {
            item {
                Text(
                    text = "معهد الجيوفيزياء - طهران",
                    color = MaterialTheme.colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            item {
                Text(
                    text = "${settings.cityName}\n${hijri.formattedArabic}",
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                )
            }

            if (hijri.occasion != null) {
                item {
                    Chip(
                        onClick = {},
                        label = {
                            Text(
                                text = hijri.occasion,
                                fontSize = 11.sp,
                                color = MaterialTheme.colors.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF0E4535)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )
                }
            }

            items(prayerRows) { (label, timeStr) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onBackground
                    )
                    Text(
                        text = timeStr,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Chip(
                    onClick = {
                        val hasFine = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasFine || hasCoarse) {
                            fetchGps()
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    label = { Text("تحديث الموقع عبر GPS", fontSize = 12.sp) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (gpsStatus != null) {
                item {
                    Text(
                        text = gpsStatus!!,
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            item {
                Text(
                    text = "تعديل الهلال (${settings.hijriOffsetDays} يوم)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onBackground,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactButton(onClick = {
                        scope.launch {
                            PrayerPreferences.updateHijriOffset(context, settings.hijriOffsetDays - 1)
                            refreshComplications()
                        }
                    }) { Text("-") }

                    CompactButton(onClick = {
                        scope.launch {
                            PrayerPreferences.updateHijriOffset(context, settings.hijriOffsetDays + 1)
                            refreshComplications()
                        }
                    }) { Text("+") }
                }
            }

            item {
                Text(
                    text = "مدن سريعة",
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
            }

            items(PrayerPreferences.presets) { preset ->
                Chip(
                    onClick = {
                        scope.launch {
                            PrayerPreferences.updateLocation(
                                context,
                                preset.nameArabic,
                                preset.latitude,
                                preset.longitude,
                                preset.elevationMeters
                            )
                            refreshComplications()
                        }
                    },
                    label = { Text(preset.nameArabic, fontSize = 12.sp) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}
