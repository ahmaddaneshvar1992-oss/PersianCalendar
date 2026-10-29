package ir.persiancalendar.app.ui.prayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VolumeOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.JalaliDate
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.core.events.HolidayRepository
import ir.persiancalendar.app.core.prayer.Cities
import ir.persiancalendar.app.core.prayer.PrayerTimesCalculator
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.ui.calendar.formatHour
import ir.persiancalendar.app.ui.components.CityPickerDialog
import ir.persiancalendar.app.ui.components.SectionCard
import ir.persiancalendar.app.ui.theme.holidayColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * کارت اوقات شرعی (نوار افقی ۸ وقت) — نسخه ۱.۱۰:
 * مو‌به‌مو مطابق عکس ارسالی کاربر و به درخواست او در «پایین صفحه اصلی تقویم، پیش از مناسبت‌های ماه»
 * قرار می‌گیرد (پیش‌تر نسخه ۱.۹ فقط در صفحه اوقات شرعی بود). این کامپوزابل کاملاً مستقل است:
 * سرصفحه (آیکون طلوع + «اوقات شرعی + نام شهر» + فلش رفتن به صفحه اوقات شرعی + «بخش اذان» + پخش اذان + منوی سه‌نقطه)،
 * فلش باز/بسته کردن روزهای ماه، نوار افقی ۸ وقت با جداکننده عمودی و هایلایت نزدیک‌ترین وقت،
 * خط پایین (شهر + نزدیک‌ترین وقت + شمارش معکوس زنده هر ۳۰ ثانیه) و پنل «روزهای ماه».
 *
 * نسخه ۱.۱۱.۱۰ (درخواست کاربر):
 * ۱) نام شهرستان منتخب کنار عنوان «اوقات شرعی» نوشته می‌شود («اوقات شرعی تهران»).
 * ۲) جدول ماهانه قدیمی (۳۱ ردیف اسکرول افقی) با «روزهای ماه مثل تقویم» جایگزین شد:
 *    همان شبکه صفحه اصلی (سربرگ ش ی د س چ پ ج، امروز پرشده سبز، جمعه‌ها و تعطیل‌ها رنگ تعطیل)
 *    با این تفاوت که لمس هر روز، نوار ۸ وقتِ همان روز را پایین شبکه نشان می‌دهد.
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
    val todayJ = CalendarConverter.gregorianToJalali(today.year, today.monthValue, today.dayOfMonth)
    val holidayRepo = remember { AppContainer.holidayRepo(context) }
    // روز انتخابی در پنل «روزهای ماه» — پیش‌فرض امروز (نسخه ۱.۱۱.۱۰)
    var selectedDay by remember { mutableStateOf(todayJ.day) }
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
    // نسخه ۱.۱۱.۱۵: وضعیت زندهٔ پخش اذان برای آیکن toggle (هر نیم ثانیه)
    var azanPlayingLive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            azanPlayingLive = ir.persiancalendar.app.notifications.AzanPlaybackService.isPlaying ||
                ir.persiancalendar.app.notifications.AdhanSounds.isPlaying
            delay(500)
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
                    // نسخه ۱.۱۱.۱۰: نام شهرستان منتخب جلوی عنوان (درخواست کاربر)
                    Text(
                        "اوقات شرعی ${city.name}",
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
                            // نسخه ۱.۱۱.۱۵ (درخواست کاربر): «وقتی روش می‌زنم اذان پخش
                            // می‌شود و می‌خوام دوباره روش کلیک کنم متوقف بشه» — toggle
                            val anyPlaying = ir.persiancalendar.app.notifications.AzanPlaybackService.isPlaying ||
                                ir.persiancalendar.app.notifications.AdhanSounds.isPlaying
                            if (anyPlaying) {
                                // توقف هر دو مسیر پخش: پیش‌نمایش و سرویس اعلان
                                ir.persiancalendar.app.notifications.AzanPlaybackService.stop(context)
                                ir.persiancalendar.app.notifications.AdhanSounds.release()
                                android.widget.Toast.makeText(
                                    context, "پخش اذان متوقف شد", android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val ok = ir.persiancalendar.app.notifications.AdhanSounds.play(
                                    context, settings.adhanSound
                                )
                                if (ok) {
                                    android.widget.Toast.makeText(
                                        context, "پخش اذان — ${city.name}", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    android.widget.Toast.makeText(
                                        context, "برای پخش، از تنظیمات اوقات شرعی یکی از اذان‌ها را انتخاب کنید",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (azanPlayingLive) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = if (azanPlayingLive) "توقف پخش اذان" else "پخش اذان",
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
                                text = { Text(if (monthTableVisible) "بستن روزهای ماه" else "روزهای ماه") },
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
                // فلش رو به پایین — باز/بسته کردن روزهای ماه (نسخه ۱.۱۱.۱۰)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { monthTableVisible = !monthTableVisible },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "روزهای ماه و اوقات شرعی",
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

        // ---------- روزهای ماه مثل تقویم + اوقات روز انتخابی — نسخه ۱.۱۱.۱۰ (درخواست کاربر) ----------
        if (monthTableVisible) {
            Spacer(Modifier.height(10.dp))
            SectionCard {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        "اوقات شرعی ${city.name} — ${PersianText.monthYear(todayJ.year, todayJ.month, persianDigits)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    // شبکه روزهای ماه — دقیقاً مثل تقویم صفحه اصلی (ش ی د س چ پ ج)
                    PrayerMonthGrid(
                        jy = todayJ.year,
                        jm = todayJ.month,
                        todayJ = todayJ,
                        selectedDay = selectedDay,
                        holidayRepo = holidayRepo,
                        persianDigits = persianDigits,
                        onSelect = { selectedDay = it }
                    )
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    // اوقات روز انتخابی — همان نوار ۸ وقت
                    val selG = CalendarConverter.jalaliToGregorian(todayJ.year, todayJ.month, selectedDay)
                    val selDate = LocalDate.of(selG.year, selG.month, selG.day)
                    val selTimes = PrayerTimesCalculator.compute(
                        selDate, city.lat, city.lng, Cities.IRAN_TZ_HOURS, settings.prayerMethod
                    )
                    val selValues = listOf(
                        selTimes.fajr, selTimes.sunrise, selTimes.dhuhr, selTimes.asr,
                        selTimes.sunset, selTimes.maghrib, selTimes.isha, selTimes.midnight
                    )
                    val wdIdx = PersianText.weekdayIndexSat0(selDate)
                    val selDayText = if (persianDigits) PersianText.toPersianDigits(selectedDay.toString())
                    else selectedDay.toString()
                    Text(
                        text = "${PersianText.weekDays[wdIdx]} $selDayText " +
                                "${PersianText.jalaliMonths[todayJ.month - 1]} — اوقات شرعی ${city.name}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 4.dp)
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
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(vertical = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    lbl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    shortHour(selValues[i], persianDigits),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "هر روز را لمس کنید تا اوقات شرعی همان روز نمایش داده شود",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
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

/**
 * شبکه روزهای ماه اوقات شرعی — نسخه ۱.۱۱.۱۰ (درخواست کاربر: «می‌خوام مثل عکس
 * روزهای تقویم نمایش داده بشه») — مو‌به‌مو با شبکه صفحه اصلی تقویم:
 * سربرگ ش ی د س چ پ ج (جمعه رنگ تعطیل)، امروز با دکمه سبز پر، تعطیلی‌ها با رنگ تعطیل
 * و روز انتخابی با قاب رنگی؛ لمس هر روز آن را برای نمایش اوقات انتخاب می‌کند.
 */
@Composable
private fun PrayerMonthGrid(
    jy: Int, jm: Int,
    todayJ: JalaliDate,
    selectedDay: Int,
    holidayRepo: HolidayRepository,
    persianDigits: Boolean,
    onSelect: (Int) -> Unit
) {
    val monthLen = CalendarConverter.jalaliMonthLength(jy, jm)
    val firstG = CalendarConverter.jalaliToGregorian(jy, jm, 1)
    val firstDate = LocalDate.of(firstG.year, firstG.month, firstG.day)
    val firstIndex = PersianText.weekdayIndexSat0(firstDate) // ۰=شنبه

    Column(Modifier.fillMaxWidth()) {
        // سربرگ روزهای هفته — مثل تقویم صفحه اصلی
        Row(modifier = Modifier.fillMaxWidth()) {
            PersianText.weekDaysShort.forEach { wd ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = wd,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (wd == "ج") holidayColor() else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // شش ردیف هفته — چینش دقیقاً مثل MonthGrid صفحه اصلی
        var cellIndex = 0
        repeat(6) {
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) {
                    val dayOffset = cellIndex - firstIndex
                    cellIndex++
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (dayOffset in 0 until monthLen) {
                            val dayNum = dayOffset + 1
                            val g = firstDate.plusDays(dayOffset.toLong())
                            val j = JalaliDate(jy, jm, dayNum)
                            val isToday = j == todayJ
                            val isSelected = dayNum == selectedDay
                            val isHoliday = holidayRepo.isHoliday(j, g)
                            val numText = if (persianDigits) PersianText.toPersianDigits(dayNum.toString())
                            else dayNum.toString()
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            isToday -> MaterialTheme.colorScheme.primary
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            else -> Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (isSelected && !isToday) Modifier.border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(10.dp)
                                        ) else Modifier
                                    )
                                    .clickable { onSelect(dayNum) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = numText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isToday -> MaterialTheme.colorScheme.onPrimary
                                        isHoliday -> holidayColor()
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        } else {
                            Spacer(Modifier.aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}
