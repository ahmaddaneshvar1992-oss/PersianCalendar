package ir.persiancalendar.app.ui.prayer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.core.prayer.Cities
import ir.persiancalendar.app.core.prayer.PrayerTimesCalculator
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.ui.calendar.formatHour
import ir.persiancalendar.app.ui.components.CityPickerDialog
import ir.persiancalendar.app.ui.components.SectionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * کارت اوقات شرعی (نوار افقی ۸ وقت) — نسخه ۱.۱۰:
 * مو‌به‌مو مطابق عکس ارسالی کاربر و به درخواست او در «پایین صفحه اصلی تقویم، پیش از مناسبت‌های ماه»
 * قرار می‌گیرد (پیش‌تر نسخه ۱.۹ فقط در صفحه اوقات شرعی بود). این کامپوزابل کاملاً مستقل است:
 * سرصفحه (آیکون طلوع + عنوان + فلش رفتن به صفحه اوقات شرعی + «بخش اذان» + پخش اذان + منوی سه‌نقطه)،
 * فلش باز/بسته کردن جدول ماهانه، نوار افقی ۸ وقت با جداکننده عمودی و هایلایت نزدیک‌ترین وقت،
 * خط پایین (شهر + نزدیک‌ترین وقت + شمارش معکوس زنده هر ۳۰ ثانیه) و جدول ماهانه.
 */
@Composable
fun PrayerTimesCard(
    settings: AppSettings,
    persianDigits: Boolean,
    modifier: Modifier = Modifier,
    onOpenPrayer: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cityPickerVisible by remember { mutableStateOf(false) }
    var monthTableVisible by remember { mutableStateOf(false) }
    var cardMenuOpen by remember { mutableStateOf(false) }

    val city = Cities.list[settings.cityIndex.coerceIn(0, Cities.list.size - 1)]
    val today = remember { LocalDate.now() }
    val times = PrayerTimesCalculator.compute(
        today, city.lat, city.lng, Cities.IRAN_TZ_HOURS, settings.prayerMethod
    )

    val timeLabels = listOf(
        "اذان صبح", "طلوع آفتاب", "اذان ظهر", "اذان عصر",
        "غروب آفتاب", "اذان مغرب", "اذان عشاء", "نیمه‌شب شرعی"
    )
    val timeValues = listOf(
        times.fajr, times.sunrise, times.dhuhr, times.asr,
        times.sunset, times.maghrib, times.isha, times.midnight
    )
    // برچسب‌های کوتاه نوار افقی — دقیقاً مطابق عکس ارسالی
    val shortLabels = listOf("صبح", "طلوع", "ظهر", "عصر", "غروب", "مغرب", "عشا", "نیمه‌شب")

    // شمارش معکوس زنده — به‌روزرسانی هر ۳۰ ثانیه (مانند نسخه ۱.۸)
    var nowTick by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowTick = LocalDateTime.now()
        }
    }
    val nowH = nowTick.let { it.hour + it.minute / 60.0 }
    val nextIndex = timeValues.indexOfFirst { it > nowH }

    val nextLabel = if (nextIndex >= 0) timeLabels[nextIndex] else "اذان صبح (فردا)"
    val nextDiffMin = if (nextIndex >= 0) {
        ((timeValues[nextIndex] - nowH) * 60).toInt()
    } else {
        (((24.0 - nowH) + times.fajr) * 60).toInt()
    }
    val nextDiffText = if (nextDiffMin >= 60)
        "${nextDiffMin / 60} ساعت و ${nextDiffMin % 60} دقیقه"
    else "$nextDiffMin دقیقه"

    Column(modifier = modifier.fillMaxWidth()) {
        SectionCard {
            Column(Modifier.fillMaxWidth()) {
                // سرصفحه کارت: آیکون طلوع + عنوان + فلش صفحه اوقات شرعی | «بخش اذان» + بلندگو + منوی سه‌نقطه
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.WbTwilight,
                        contentDescription = null,
                        tint = Color(0xFFE0A010),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "اوقات شرعی",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onOpenPrayer, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "رفتن به صفحه اوقات شرعی",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "بخش اذان",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = {
                            ir.persiancalendar.app.notifications.AdhanSounds.play(
                                context, settings.adhanSound
                            )
                            android.widget.Toast.makeText(
                                context, "پخش اذان — ${city.name}", android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.VolumeUp,
                            contentDescription = "پخش اذان",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { cardMenuOpen = true }, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "گزینه‌های اوقات شرعی",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        DropdownMenu(expanded = cardMenuOpen, onDismissRequest = { cardMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("تغییر شهرستان") },
                                onClick = { cardMenuOpen = false; cityPickerVisible = true }
                            )
                            DropdownMenuItem(
                                text = { Text(if (monthTableVisible) "بستن جدول ماهانه" else "جدول ماهانه") },
                                onClick = { cardMenuOpen = false; monthTableVisible = !monthTableVisible }
                            )
                            DropdownMenuItem(
                                text = { Text("اشتراک‌گذاری اوقات") },
                                onClick = {
                                    cardMenuOpen = false
                                    val txt = buildString {
                                        append("اوقات شرعی ${city.name} — ")
                                        shortLabels.forEachIndexed { i, s ->
                                            append(s); append(' '); append(formatHour(timeValues[i], persianDigits)); append("  ")
                                        }
                                    }
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(android.content.Intent.EXTRA_TEXT, txt)
                                    context.startActivity(
                                        android.content.Intent.createChooser(send, "اشتراک‌گذاری اوقات شرعی")
                                    )
                                }
                            )
                        }
                    }
                }
                HorizontalDivider()
                // فلش رو به پایین — باز/بسته کردن جدول ماهانه (مانند عکس)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { monthTableVisible = !monthTableVisible },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "جدول ماهانه اوقات شرعی",
                            tint = if (monthTableVisible) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer { rotationZ = if (monthTableVisible) 180f else 0f }
                        )
                    }
                }
                HorizontalDivider()
                // نوار افقی ۸ وقت با جداکننده عمودی — دقیقاً مطابق عکس
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 12.dp)
                ) {
                    shortLabels.forEachIndexed { i, lbl ->
                        if (i > 0) {
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                        val isNext = i == nextIndex
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    if (isNext) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                lbl,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isNext) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                shortHour(timeValues[i], persianDigits),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                // خط زیر نوار: شهر + نزدیک‌ترین وقت
                Text(
                    text = "${city.name} (${city.province}) — نزدیک‌ترین وقت: $nextLabel · مانده $nextDiffText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // ---------- جدول ماهانه (فلش کارت — باز/بسته) ----------
        if (monthTableVisible) {
            Spacer(Modifier.height(10.dp))
            SectionCard {
                Column(Modifier.padding(10.dp)) {
                    val todayJ = CalendarConverter.gregorianToJalali(
                        today.year, today.monthValue, today.dayOfMonth
                    )
                    Text(
                        "اوقات شرعی ${city.name} — ${PersianText.monthYear(todayJ.year, todayJ.month, persianDigits)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        // سربرگ جدول
                        Row {
                            TableCell("روز", 46.dp, header = true)
                            shortLabels.forEach { TableCell(it, 56.dp, header = true) }
                        }
                        val days = CalendarConverter.jalaliMonthLength(todayJ.year, todayJ.month)
                        for (d in 1..days) {
                            val g = CalendarConverter.jalaliToGregorian(todayJ.year, todayJ.month, d)
                            val t = PrayerTimesCalculator.compute(
                                LocalDate.of(g.year, g.month, g.day), city.lat, city.lng,
                                Cities.IRAN_TZ_HOURS, settings.prayerMethod
                            )
                            val vals = listOf(
                                t.fajr, t.sunrise, t.dhuhr, t.asr,
                                t.sunset, t.maghrib, t.isha, t.midnight
                            )
                            Row(
                                modifier = if (d == todayJ.day) Modifier.background(
                                    MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp)
                                ) else Modifier
                            ) {
                                TableCell(
                                    if (persianDigits) PersianText.toPersianDigits(d.toString()) else d.toString(),
                                    46.dp, highlight = d == todayJ.day
                                )
                                vals.forEach { TableCell(formatHour(it, persianDigits), 56.dp, highlight = d == todayJ.day) }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- دیالوگ تغییر شهرستان (منوی سه‌نقطه) ----------
    if (cityPickerVisible) {
        CityPickerDialog(
            cities = Cities.list,
            selectedIndex = settings.cityIndex,
            onDismiss = { cityPickerVisible = false },
            onSelected = { idx ->
                cityPickerVisible = false
                scope.launch { AppContainer.settingsRepo(context).setCityIndex(idx) }
            }
        )
    }
}

/** ساعت کوتاه بدون صفر ابتدایی — دقیقاً مطابق عکس (۵:۱۱) */
private fun shortHour(hourValue: Double, persianDigits: Boolean): String {
    var h = hourValue.toInt()
    var m = Math.round((hourValue - h) * 60)
    if (m >= 60) { h += 1; m = 0 }
    val text = "${h % 24}:${m.toString().padStart(2, '0')}"
    return if (persianDigits) PersianText.toPersianDigits(text) else text
}

/** خانه جدول ماهانه اوقات شرعی */
@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    header: Boolean = false,
    highlight: Boolean = false
) {
    Box(
        modifier = Modifier.width(width),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (header || highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (header) MaterialTheme.colorScheme.onSurfaceVariant
            else if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 5.dp)
        )
    }
}
