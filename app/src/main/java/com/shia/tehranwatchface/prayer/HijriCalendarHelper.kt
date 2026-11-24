package com.shia.tehranwatchface.prayer

import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

object HijriCalendarHelper {

    data class HijriDateInfo(
        val year: Int,
        val month: Int, // 1..12
        val day: Int,
        val monthNameArabic: String,
        val formattedArabic: String,
        val occasion: String?
    )

    private val arabicMonths = listOf(
        "محرم",
        "صفر",
        "ربيع الأول",
        "ربيع الآخر",
        "جمادى الأولى",
        "جمادى الثانية",
        "رجب",
        "شعبان",
        "رمضان",
        "شوال",
        "ذو القعدة",
        "ذو الحجة"
    )

    // Key Shia Islamic occasions (month 1..12, day)
    private val shiaOccasions = mapOf(
        (1 to 1) to "رأس السنة الهجرية",
        (1 to 9) to "تاسوعاء الحسين (ع)",
        (1 to 10) to "عاشوراء الحسين (ع)",
        (1 to 25) to "شهادة السجاد (ع)",
        (2 to 20) to "أربعين الإمام الحسين (ع)",
        (2 to 28) to "وفاة النبي (ص) والحسن (ع)",
        (2 to 30) to "شهادة الرضا (ع)",
        (3 to 8) to "شهادة العسكري (ع)",
        (3 to 17) to "ولادة النبي (ص) والصادق (ع)",
        (5 to 5) to "ولادة السيدة زينب (ع)",
        (6 to 3) to "شهادة الزهراء (ع)",
        (6 to 20) to "ولادة الزهراء (ع)",
        (7 to 1) to "ولادة الباقر (ع)",
        (7 to 13) to "ولادة أمير المؤمنين (ع)",
        (7 to 15) to "وفاة السيدة زينب (ع)",
        (7 to 25) to "شهادة الكاظم (ع)",
        (7 to 27) to "المبعث النبوي الشريف",
        (8 to 3) to "ولادة الإمام الحسين (ع)",
        (8 to 4) to "ولادة أبي الفضل العباس (ع)",
        (8 to 15) to "ولادة الإمام المهدي (عج)",
        (9 to 15) to "ولادة الإمام الحسن (ع)",
        (9 to 19) to "ليلة القدر الأولى",
        (9 to 21) to "شهادة أمير المؤمنين (ع)",
        (9 to 23) to "ليلة القدر الكبرى",
        (10 to 1) to "عيد الفطر المبارك",
        (10 to 25) to "شهادة الصادق (ع)",
        (11 to 11) to "ولادة الإمام الرضا (ع)",
        (11 to 29) to "شهادة الجواد (ع)",
        (12 to 10) to "عيد الأضحى المبارك",
        (12 to 18) to "عيد الغدير الأغر",
        (12 to 24) to "يوم المباهلة"
    )

    fun getHijriDate(date: LocalDate, dayOffset: Int = 0): HijriDateInfo {
        val adjustedDate = date.plusDays(dayOffset.toLong())
        val cal = IslamicCalendar(ULocale("ar@calendar=islamic-umalqura"))
        cal.time = Date.from(adjustedDate.atStartOfDay(ZoneId.systemDefault()).toInstant())

        val year = cal.get(IslamicCalendar.YEAR)
        val monthZeroBased = cal.get(IslamicCalendar.MONTH) // 0..11
        val month = monthZeroBased + 1
        val day = cal.get(IslamicCalendar.DAY_OF_MONTH)
        val monthName = arabicMonths.getOrElse(monthZeroBased) { "" }
        val occasion = shiaOccasions[month to day]

        return HijriDateInfo(
            year = year,
            month = month,
            day = day,
            monthNameArabic = monthName,
            formattedArabic = "$day $monthName $year",
            occasion = occasion
        )
    }
}
