package ir.persiancalendar.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.JalaliDate
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.core.events.HolidayRepository
import ir.persiancalendar.app.core.prayer.Cities
import ir.persiancalendar.app.core.prayer.PrayerMethod
import ir.persiancalendar.app.core.prayer.PrayerTimesCalculator
import ir.persiancalendar.app.data.db.EventEntity
import ir.persiancalendar.app.ui.components.SectionCard
import ir.persiancalendar.app.ui.theme.EventDotColors
import ir.persiancalendar.app.ui.theme.holidayColor
import java.time.LocalDate

/**
 * نمای روزانه: جزئیات کامل یک روز شامل تبدیل تاریخ‌ها، مناسبت‌ها،
 * رویدادها و اوقات شرعی همان روز.
 */
@Composable
fun DayDetailScreen(
    jalali: JalaliDate,
    events: List<EventEntity>,
    holidayRepo: HolidayRepository,
    persianDigits: Boolean,
    showHijri: Boolean,
    cityIndex: Int,
    prayerMethod: PrayerMethod,
    onBack: () -> Unit,
    onAddEvent: (JalaliDate) -> Unit,
    onEditEvent: (EventEntity) -> Unit
) {
    val g = CalendarConverter.jalaliToGregorian(jalali.year, jalali.month, jalali.day)
    val gDate = LocalDate.of(g.year, g.month, g.day)
    val h = CalendarConverter.gregorianToHijri(g.year, g.month, g.day)
    val occasions = holidayRepo.occasionsFor(jalali, gDate)
    val isHoliday = holidayRepo.isHoliday(jalali, gDate)
    val dayEvents = events.filter { eventOccursOn(it, gDate) }

    // اوقات شرعی روز
    val city = Cities.list[cityIndex.coerceIn(0, Cities.list.size - 1)]
    val times = PrayerTimesCalculator.compute(gDate, city.lat, city.lng, Cities.IRAN_TZ_HOURS, prayerMethod)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // ---------- سرصفحه ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "بازگشت",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column {
                Text(
                    text = PersianText.jalaliDateFull(jalali, persianDigits),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isHoliday) holidayColor() else MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${PersianText.weekDays[PersianText.weekdayIndexSat0(gDate)]} — " +
                            if (isHoliday) "تعطیل رسمی" else "روز کاری",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isHoliday) holidayColor() else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            // نسخه ۱.۴: دکمه کوچک «+» از سرصفحه حذف شد — ثبت رویداد با دکمه سبز «+» یا «افزودن» بخش رویدادها
        }

        Spacer(Modifier.height(10.dp))

        // ---------- تبدیل تاریخ‌ها ----------
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("تبدیل تاریخ", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ConversionRow(
                    icon = "🇮🇷",
                    label = "شمسی",
                    value = "${PersianText.jalaliMonths[jalali.month - 1]} ${pd(jalali.day, persianDigits)}، ${pd(jalali.year, persianDigits)}"
                )
                ConversionRow(
                    icon = "🌍",
                    label = "میلادی",
                    value = "${PersianText.gregorianMonths[g.month - 1]} ${pd(g.day, persianDigits)}, ${pd(g.year, persianDigits)}"
                )
                if (showHijri) {
                    ConversionRow(
                        icon = "🌙",
                        label = "قمری",
                        value = PersianText.hijriDateFull(h, persianDigits)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- مناسبت‌ها ----------
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("مناسبت‌ها", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (occasions.isEmpty()) {
                    Text(
                        "مناسبتی برای این روز ثبت نشده است",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    occasions.forEach {
                        Text(
                            text = "• ${it.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (it.isHoliday) holidayColor() else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- رویدادهای من ----------
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("رویدادهای من", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onAddEvent(jalali) }) { Text("افزودن") }
                }
                Spacer(Modifier.height(6.dp))
                if (dayEvents.isEmpty()) {
                    Text(
                        "برای این روز رویدادی ندارید",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    dayEvents.forEach { e ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(36.dp)
                                    .background(
                                        EventDotColors[e.colorIndex % EventDotColors.size],
                                        RoundedCornerShape(3.dp)
                                    )
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(e.title, style = MaterialTheme.typography.bodyLarge)
                                if (e.hour >= 0) {
                                    Text(
                                        PersianText.timeHM(e.hour, e.minute, persianDigits),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (e.note.isNotBlank()) {
                                    Text(
                                        e.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(onClick = { onEditEvent(e) }) { Text("ویرایش") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- اوقات شرعی ----------
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("اوقات شرعی — ${city.name}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val rows = listOf(
                    "اذان صبح" to times.fajr,
                    "طلوع آفتاب" to times.sunrise,
                    "اذان ظهر" to times.dhuhr,
                    "اذان عصر" to times.asr,
                    "غروب آفتاب" to times.sunset,
                    "اذان مغرب" to times.maghrib,
                    "اذان عشاء" to times.isha
                )
                rows.forEach { (label, hourValue) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            text = formatHour(hourValue, persianDigits),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ConversionRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(34.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp))
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun pd(n: Int, persian: Boolean): String =
    if (persian) PersianText.toPersianDigits(n.toString()) else n.toString()

/** ساعت اعشاری را به HH:MM تبدیل می‌کند */
fun formatHour(hourValue: Double, persianDigits: Boolean): String {
    var h = hourValue.toInt()
    var m = Math.round((hourValue - h) * 60)
    if (m >= 60) { h += 1; m = 0 }
    val text = String.format("%02d:%02d", h % 24, m)
    return if (persianDigits) PersianText.toPersianDigits(text) else text
}
