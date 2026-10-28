package ir.persiancalendar.app.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.JalaliDate
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.core.events.HolidayRepository
import ir.persiancalendar.app.data.db.EventEntity
import ir.persiancalendar.app.data.model.EventRepetition
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.ui.components.JalaliDatePickerDialog
import ir.persiancalendar.app.ui.prayer.PrayerTimesCard
import ir.persiancalendar.app.ui.theme.EventDotColors
import ir.persiancalendar.app.ui.theme.holidayColor
import java.time.LocalDate

/**
 * صفحه تقویم: نمایش ماهانه و هفتگی شمسی.
 */
@Composable
fun CalendarScreen(
    events: List<EventEntity>,
    holidayRepo: HolidayRepository,
    settings: AppSettings,
    persianDigits: Boolean,
    showHijri: Boolean,
    onOpenDay: (JalaliDate) -> Unit,
    onOpenPrayer: () -> Unit = {}
) {
    val todayG = LocalDate.now()
    val todayJ = CalendarConverter.gregorianToJalali(todayG.year, todayG.monthValue, todayG.dayOfMonth)

    var viewJy by remember { mutableStateOf(todayJ.year) }
    var viewJm by remember { mutableStateOf(todayJ.month) }
    var mode by remember { mutableStateOf("ماه") }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedJalali by remember { mutableStateOf(todayJ) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // ---------- سرصفحه ----------
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    // ماه قبل
                    if (viewJm == 1) { viewJm = 12; viewJy -= 1 } else viewJm -= 1
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "ماه قبل",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showDatePicker = true },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = PersianText.monthYear(viewJy, viewJm, persianDigits),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "برای جابه‌جایی سریع ضربه بزنید",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = {
                    if (viewJm == 12) { viewJm = 1; viewJy += 1 } else viewJm += 1
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "ماه بعد",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---------- حالت نمایش ----------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == "ماه",
                onClick = { mode = "ماه" },
                label = { Text("ماهانه") }
            )
            FilterChip(
                selected = mode == "هفته",
                onClick = { mode = "هفته" },
                label = { Text("هفتگی") }
            )
            Spacer(Modifier.weight(1f))
            // ---------- دکمه بازگشت به امروز (نسخه ۱.۶) ----------
            // فقط وقتی کاربر در ماه/روزی غیر از امروز است نمایان می‌شود و با رنگ پر و پالس نرم جلب توجه می‌کند
            val awayFromToday = viewJy != todayJ.year || viewJm != todayJ.month ||
                    selectedJalali != todayJ
            if (awayFromToday) {
                androidx.compose.material3.Button(
                    onClick = {
                        viewJy = todayJ.year; viewJm = todayJ.month; selectedJalali = todayJ
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("برو به امروز", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (mode == "ماه") {
            MonthGrid(
                jy = viewJy, jm = viewJm,
                todayG = todayG, todayJ = todayJ,
                events = events,
                holidayRepo = holidayRepo,
                persianDigits = persianDigits,
                // نسخه ۱.۱۱.۳ (گزارش کاربر): لمس ساده روز فقط انتخاب می‌کند و هیچ صفحه‌ای باز نمی‌شود؛
                // جزئیات روز فقط با نگه‌داشتن انگشت روی روز باز می‌شود
                onDayClick = {
                    selectedJalali = it
                },
                onDayLongClick = {
                    selectedJalali = it
                    onOpenDay(it)
                }
            )
        } else {
            WeekStrip(
                anchor = selectedJalali,
                todayG = todayG,
                todayJ = todayJ,
                events = events,
                holidayRepo = holidayRepo,
                persianDigits = persianDigits,
                onDaySelect = { selectedJalali = it },
                onDayLongClick = { selectedJalali = it; onOpenDay(it) },
                selected = selectedJalali
            )
        }

        Spacer(Modifier.height(10.dp))

        // ---------- کارت اوقات شرعی — نسخه ۱.۱۰: پایین صفحه اصلی تقویم، پیش از مناسبت‌های ماه (مطابق عکس) ----------
        PrayerTimesCard(
            settings = settings,
            persianDigits = persianDigits,
            onOpenPrayer = onOpenPrayer
        )

        Spacer(Modifier.height(10.dp))

        // ---------- مناسبت‌های ماه ----------
        Text("مناسبت‌های این ماه", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        MonthOccasions(jy = viewJy, jm = viewJm, holidayRepo = holidayRepo, persianDigits = persianDigits)

        Spacer(Modifier.height(8.dp))
        // بنر از نسخه ۱.۴ به نوار پایین اسکفولد منتقل شد (محدوده تبلیغات — جای منوی قبلی)
        // نکته نسخه ۱.۱۰: کارت اوقات شرعی (نوار افقی ۸ وقت مطابق عکس) به پایین صفحه اصلی تقویم، پیش از مناسبت‌های ماه بازگشت
    }

    if (showDatePicker) {
        JalaliDatePickerDialog(
            initial = JalaliDate(viewJy, viewJm, 1),
            onDismiss = { showDatePicker = false },
            onSelected = {
                viewJy = it.year
                viewJm = it.month
                selectedJalali = it
                showDatePicker = false
            }
        )
    }
}

/** مشترک: آیا رویداد در این روز رخ می‌دهد؟ */
internal fun eventOccursOn(event: EventEntity, g: LocalDate): Boolean {
    val anchorJ = CalendarConverter.gregorianToJalali(event.gy, event.gm, event.gd)
    return EventRepetition.occursOn(
        LocalDate.of(event.gy, event.gm, event.gd),
        anchorJ,
        ir.persiancalendar.app.data.model.RepeatType.fromOrdinal(event.repeatType),
        g
    )
}

// ---------------- نمایش ماهانه ----------------

@Composable
private fun MonthGrid(
    jy: Int, jm: Int,
    todayG: LocalDate, todayJ: JalaliDate,
    events: List<EventEntity>,
    holidayRepo: HolidayRepository,
    persianDigits: Boolean,
    onDayClick: (JalaliDate) -> Unit,
    onDayLongClick: (JalaliDate) -> Unit
) {
    val monthLen = CalendarConverter.jalaliMonthLength(jy, jm)
    val firstG = CalendarConverter.jalaliToGregorian(jy, jm, 1)
    val firstDate = LocalDate.of(firstG.year, firstG.month, firstG.day)
    val firstIndex = PersianText.weekdayIndexSat0(firstDate) // ۰=شنبه

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // روزهای هفته
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

            // شش ردیف هفته
            var cellIndex = 0
            repeat(6) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) {
                        val dayOffset = cellIndex - firstIndex
                        cellIndex++
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (dayOffset in 0 until monthLen) {
                                val dayNum = dayOffset + 1
                                val j = JalaliDate(jy, jm, dayNum)
                                val g = firstDate.plusDays(dayOffset.toLong())
                                DayCell(
                                    jalali = j,
                                    gregorian = g,
                                    isToday = j == todayJ,
                                    isHoliday = holidayRepo.isHoliday(j, g),
                                    eventColors = events.filter { eventOccursOn(it, g) }
                                        .map { EventDotColors[it.colorIndex % EventDotColors.size] }
                                        .distinct()
                                        .take(3),
                                    persianDigits = persianDigits,
                                    onClick = { onDayClick(j) },
                                    onLongClick = { onDayLongClick(j) }
                                )
                            } else {
                                Spacer(Modifier.aspectRatio(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    jalali: JalaliDate,
    gregorian: LocalDate,
    isToday: Boolean,
    isHoliday: Boolean,
    eventColors: List<androidx.compose.ui.graphics.Color>,
    persianDigits: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val numText = if (persianDigits) PersianText.toPersianDigits(jalali.day.toString())
    else jalali.day.toString()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .aspectRatio(0.9f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .border(
                width = if (isToday) 0.dp else 0.dp,
                color = androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(2.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = numText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = when {
                isToday -> MaterialTheme.colorScheme.onPrimary
                isHoliday -> holidayColor()
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.Center) {
            eventColors.forEach { c ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 1.dp)
                        .size(5.dp)
                        .background(c, CircleShape)
                )
            }
        }
    }
}

// ---------------- نمایش هفتگی ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekStrip(
    anchor: JalaliDate,
    todayG: LocalDate,
    todayJ: JalaliDate,
    events: List<EventEntity>,
    holidayRepo: HolidayRepository,
    persianDigits: Boolean,
    onDaySelect: (JalaliDate) -> Unit,
    onDayLongClick: (JalaliDate) -> Unit,
    selected: JalaliDate
) {
    val anchorG = CalendarConverter.jalaliToGregorian(anchor.year, anchor.month, anchor.day)
    val anchorDate = LocalDate.of(anchorG.year, anchorG.month, anchorG.day)
    val satIndex = PersianText.weekdayIndexSat0(anchorDate)
    val weekStart = anchorDate.minusDays(satIndex.toLong())

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(7) { i ->
                val g = weekStart.plusDays(i.toLong())
                val j = CalendarConverter.gregorianToJalali(g.year, g.monthValue, g.dayOfMonth)
                val isSel = j == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        )
                        .combinedClickable(
                            onClick = { onDaySelect(j) },
                            onLongClick = { onDayLongClick(j) }
                        )
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = PersianText.weekDaysShort[i],
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (persianDigits) PersianText.toPersianDigits(j.day.toString()) else j.day.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = when {
                            isSel -> MaterialTheme.colorScheme.onPrimary
                            j == todayJ -> MaterialTheme.colorScheme.primary
                            holidayRepo.isHoliday(j, g) -> holidayColor()
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // خلاصه هر روز هفته
        repeat(7) { i ->
            val g = weekStart.plusDays(i.toLong())
            val j = CalendarConverter.gregorianToJalali(g.year, g.monthValue, g.dayOfMonth)
            val occasions = holidayRepo.occasionsFor(j, g)
            val dayEvents = events.filter { eventOccursOn(it, g) }
            if (occasions.isNotEmpty() || dayEvents.isNotEmpty()) {
                // نسخه ۱.۱۱.۳: خلاصه روزها فقط نمایشی است — لمس آن هیچ صفحه‌ای باز نمی‌کند
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${PersianText.weekDays[i]} ${if (persianDigits) PersianText.toPersianDigits(j.day.toString()) else j.day.toString()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(84.dp)
                    )
                    Column {
                        occasions.forEach {
                            Text(
                                text = it.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (it.isHoliday) holidayColor() else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        dayEvents.forEach {
                            Text(
                                text = "• ${it.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

/** فهرست مناسبت‌های ماه جاری برای پایین صفحه */
@Composable
private fun MonthOccasions(
    jy: Int, jm: Int,
    holidayRepo: HolidayRepository,
    persianDigits: Boolean
) {
    val len = CalendarConverter.jalaliMonthLength(jy, jm)
    val items = mutableListOf<Pair<Int, List<String>>>()
    for (d in 1..len) {
        val j = JalaliDate(jy, jm, d)
        val g = CalendarConverter.jalaliToGregorian(jy, jm, d)
        val gDate = LocalDate.of(g.year, g.month, g.day)
        val occ = holidayRepo.occasionsFor(j, gDate)
        if (occ.isNotEmpty()) {
            items.add(d to occ.map { o -> if (o.isHoliday) "${o.title} (تعطیل)" else o.title })
        }
    }

    if (items.isEmpty()) {
        Text(
            "مناسبتی در این ماه ثبت نشده است",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column {
        items.forEach { (day, titles) ->
            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                Text(
                    text = if (persianDigits) PersianText.toPersianDigits(day.toString()) else day.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(34.dp),
                    textAlign = TextAlign.Center
                )
                Column {
                    titles.forEach { t ->
                        Text(t, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
