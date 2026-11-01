package ir.persiancalendar.app.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.persiancalendar.app.R
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.JalaliDate
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.core.events.HolidayRepository
import ir.persiancalendar.app.data.db.EventEntity
import ir.persiancalendar.app.data.model.EventRepetition
import ir.persiancalendar.app.data.model.RepeatType
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.ui.components.AdsFeedSection
import ir.persiancalendar.app.ui.components.BirthdayCard
import ir.persiancalendar.app.ui.prayer.PrayerTimesCard
import ir.persiancalendar.app.ui.theme.eventDotColor
import ir.persiancalendar.app.ui.theme.holidayColor
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * صفحه تقویم: نمایش ماهانه و هفتگی شمسی.
 *
 * نسخهٔ ۱.۱۱.۳۹ (درخواست کاربر: «انتخابگر سریع سال و ماه» + «نمای ماهانهٔ بهبودیافته»):
 *  - ضربه روی کارت سربرگ، bottom sheet انتخابگر سال و ماه را باز می‌کند
 *    (MonthYearPickerSheet) — به‌جای دیالوگ قدیمی سه فهرستی که برای
 *    یادآور/تولد/راه‌اندازی همچنان سر جایش است.
 *  - نمای ماهانه روی HorizontalPager ماه‌ها سوار شد: کشیدن افقی نرم بین
 *    ماه‌ها، بارگذاری تنبل ماه‌های مجاور با کش و ارتفاع ثابت ۶ ردیفه
 *    (ماه‌های ۵ و ۶ ردیفی بدون پرش بصری).
 *  - خانه‌های روز: عدد شمسی درشت، زیرعدد اختیاری میلادی/قمری (تنظیمات)،
 *    حداکثر ۳ نقطهٔ رنگی رویداد، تعطیل قرمز، جمعه با رنگ تعطیلات،
 *    امروز با حلقه، روز انتخاب‌شده با دایرهٔ پر و ناحیهٔ لمسی ≥۴۸dp.
 *  - دکمهٔ شناور «امروز» فقط وقتی کاربر از ماه جاری دور شده دیده می‌شود.
 * نمای هفتگی، پنل جزئیات روز و کارت‌های زیر تقویم دست‌نخورده ماندند.
 */
@Composable
fun CalendarScreen(
    events: List<EventEntity>,
    holidayRepo: HolidayRepository,
    settings: AppSettings,
    persianDigits: Boolean,
    showHijri: Boolean,
    onOpenDay: (JalaliDate) -> Unit,
    onOpenPrayer: () -> Unit = {},
    adsRefresh: Int = 0, // نسخهٔ ۱.۱۱.۲۱: بازسازی کارت‌ها پس از sync خوراک راه‌دور/پنل مدیریت
    // نسخهٔ ۱.۱۱.۳۵: مقصدهای داخلی آیتم‌های تبلیغ فید — فقط کلیدهای لیست‌سفید
    // (AdsFeedPolicy.INTERNAL_TARGETS) از این مسیر به تب/صفحهٔ خودشان می‌روند
    onOpenInternal: (String) -> Unit = {}
) {
    val todayG = LocalDate.now()
    val todayJ = CalendarConverter.gregorianToJalali(todayG.year, todayG.monthValue, todayG.dayOfMonth)

    var viewJy by remember { mutableStateOf(todayJ.year) }
    var viewJm by remember { mutableStateOf(todayJ.month) }
    var mode by remember { mutableStateOf("ماه") }
    var showMonthPicker by remember { mutableStateOf(false) }
    var selectedJalali by remember { mutableStateOf(todayJ) }

    // ---------- نمای ماهانهٔ بهبودیافته (۱.۱۱.۳۹): Pager ماه‌ها ----------
    // هر ماه یک صفحه؛ صفحهٔ آغازین = ماه امروز؛ مجموع صفحات = بازهٔ موتور تقویم
    val pagerState = rememberPagerState(
        initialPage = MonthYearPickerLogic.pageOf(todayJ.year, todayJ.month)
    ) { MonthYearPickerLogic.PAGE_COUNT }
    val scope = rememberCoroutineScope()

    // صفحهٔ تثبیت‌شدهٔ Pager → (سال، ماه) سربرگ و مناسبت‌های ماه
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            viewJy = MonthYearPickerLogic.yearOfPage(page)
            viewJm = MonthYearPickerLogic.monthOfPage(page)
        }
    }

    fun goToMonth(jy: Int, jm: Int) {
        val target = MonthYearPickerLogic.pageOf(jy, jm)
        scope.launch {
            runCatching { pagerState.animateScrollToPage(target) }
        }
    }

    fun goToPrevMonth() {
        val page = (MonthYearPickerLogic.pageOf(viewJy, viewJm) - 1).coerceAtLeast(0)
        goToMonth(MonthYearPickerLogic.yearOfPage(page), MonthYearPickerLogic.monthOfPage(page))
    }

    fun goToNextMonth() {
        val page = (MonthYearPickerLogic.pageOf(viewJy, viewJm) + 1)
            .coerceAtMost(MonthYearPickerLogic.PAGE_COUNT - 1)
        goToMonth(MonthYearPickerLogic.yearOfPage(page), MonthYearPickerLogic.monthOfPage(page))
    }

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
                IconButton(
                    onClick = { goToPrevMonth() },
                    enabled = MonthYearPickerLogic.canGoToPrevMonth(viewJy, viewJm)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.picker_prev_month),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                val openPickerLabel = stringResource(R.string.header_open_picker_hint)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClickLabel = openPickerLabel) { showMonthPicker = true },
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
                IconButton(
                    onClick = { goToNextMonth() },
                    enabled = MonthYearPickerLogic.canGoToNextMonth(viewJy, viewJm)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.picker_next_month),
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
            // ---------- دکمه بازگشت به امروز (نسخه ۱.۶) — فقط در نمای هفتگی ----------
            // (در نمای ماهانه، دکمهٔ شناور «امروز» جای آن را گرفته — ۱.۱۱.۳۹)
            // فقط وقتی کاربر در ماه/روزی غیر از امروز است نمایان می‌شود و با رنگ پر و پالس نرم جلب توجه می‌کند
            val awayFromToday = viewJy != todayJ.year || viewJm != todayJ.month ||
                    selectedJalali != todayJ
            if (mode == "هفته" && awayFromToday) {
                Button(
                    onClick = {
                        viewJy = todayJ.year; viewJm = todayJ.month; selectedJalali = todayJ
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
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
            Box(modifier = Modifier.fillMaxWidth()) {
                MonthPagerGrid(
                    pagerState = pagerState,
                    todayJ = todayJ,
                    events = events,
                    holidayRepo = holidayRepo,
                    settings = settings,
                    persianDigits = persianDigits,
                    selected = selectedJalali,
                    onDayClick = {
                        // نسخه ۱.۱۱.۳ (گزارش کاربر): لمس ساده روز فقط انتخاب می‌کند
                        selectedJalali = it
                    },
                    onDayLongClick = {
                        selectedJalali = it
                        onOpenDay(it)
                    }
                )
                // ---------- دکمهٔ شناور «امروز» (۱.۱۱.۳۹) ----------
                // فقط وقتی کاربر از ماه جاری دور شده نمایش داده می‌شود
                val showTodayFab = MonthGridLogic.isAwayFromCurrentMonth(
                    viewJy, viewJm, todayJ.year, todayJ.month
                )
                val todayFabCd = stringResource(R.string.today_fab_cd)
                AnimatedVisibility(
                    visible = showTodayFab,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                ) {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.today_fab)) },
                        icon = {
                            Icon(
                                Icons.Filled.Today,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            goToMonth(todayJ.year, todayJ.month)
                            selectedJalali = todayJ
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.semantics { contentDescription = todayFabCd }
                    )
                }
            }
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

        // ---------- تبلیغات مدیریت‌شده (tile + بنر بدون ×) — نسخهٔ ۱.۱۱.۳۵ (درخواست کاربر: کار ۱) ----------
        // جای قرارگیری: زیر جدول تقویم، بالای کارت‌های «روز تولد/مناسبت‌های روز».
        // کاربر هیچ راهی برای پنهان‌کردن ندارد (بدون × و بدون ⋮)؛ فقط مدیر با فید
        // امضاشده (sequence بالاتر) آیتم‌ها را اضافه/ویرایش/حذف می‌کند و فهرست کامل
        // جایگزین می‌شود. برچسب «تبلیغ» روی هر آیتم؛ لینک https/مقصد داخلی لیست‌سفید.
        AdsFeedSection(
            refreshKey = adsRefresh,
            onOpenInternal = onOpenInternal
        )

        Spacer(Modifier.height(10.dp))

        // ---------- کارت «روز تولد شما» — نسخهٔ ۱.۱۱.۳۴ (درخواست کاربر) ----------
        // شمارش معکوس تولد بعدی (روز/ساعت/دقیقه با ارقام فارسی) + نوار پیشرفت؛
        // قاعدهٔ ۳۰ اسفند و پیام ویژهٔ تولد؛ فقط دادهٔ محلی — هرگز به شبکه نمی‌رود.
        if (settings.birthdayCardVisible) {
            BirthdayCard(settings = settings)
            Spacer(Modifier.height(10.dp))
        }

        // ---------- مناسبت‌های روز انتخاب‌شده — نسخه ۱.۱۱.۷ (درخواست کاربر) ----------
        // با لمس هر روز تقویم، مناسبت‌های همان روز با همان فرم «مناسبت‌های این ماه»
        // (شماره روز + عنوان مناسبت‌ها) همین‌جا زیر تقویم دیده می‌شود.
        SelectedDayOccasions(
            selected = selectedJalali,
            events = events,
            holidayRepo = holidayRepo,
            persianDigits = persianDigits
        )

        Spacer(Modifier.height(10.dp))

        // ---------- کارت اوقات شرعی — نسخه ۱.۱۰: پایین صفحه اصلی تقویم، پیش از مناسبت‌های ماه (مطابق عکس) ----------
        // نسخهٔ ۱.۱۱.۱۶ (درخواست کاربر): با لمس هر روز، اوقات شرعی همان روز نمایش داده می‌شود
        PrayerTimesCard(
            settings = settings,
            persianDigits = persianDigits,
            selectedDate = CalendarConverter.jalaliToGregorian(
                selectedJalali.year, selectedJalali.month, selectedJalali.day
            ).let { LocalDate.of(it.year, it.month, it.day) },
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

    if (showMonthPicker) {
        MonthYearPickerSheet(
            viewJy = viewJy,
            viewJm = viewJm,
            todayJy = todayJ.year,
            todayJm = todayJ.month,
            persianDigits = persianDigits,
            onDismiss = { showMonthPicker = false },
            onPick = { jy, jm ->
                showMonthPicker = false
                // انتخاب ماه با انیمیشن کوتاه Pager به همان ماه می‌رود
                goToMonth(jy, jm)
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
        RepeatType.fromOrdinal(event.repeatType),
        g
    )
}

// ---------------- نمایش ماهانه بهبودیافته (۱.۱۱.۳۹) ----------------

/**
 * مدل آمادهٔ هر خانهٔ روز — یک بار برای کل صفحه محاسبه و کش می‌شود
 * تا هنگام سوایپ هیچ محاسبهٔ سنگینی روی نخ اصلی رخ ندهد.
 */
internal data class MonthCellData(
    val jalali: JalaliDate,
    val gDate: LocalDate,
    val isToday: Boolean,
    val isSelected: Boolean,
    val isHoliday: Boolean,
    val isFriday: Boolean,
    /** اندیس‌های رنگ دسته‌بندی رویدادهای این روز (متمایز) */
    val eventColorIndices: List<Int>,
    /** نقشهٔ نقطه‌ها (حداکثر ۳ نقطه، نقطهٔ سوم کم‌رنگ در ازدحام) */
    val dotPlan: MonthGridLogic.EventDotPlan,
    val eventCount: Int,
    /** زیرعدد میلادی/قمری — فقط وقتی تنظیمات مربوطه روشن باشد */
    val gregorianDayText: String?,
    val gregorianA11y: String?,
    val hijriDayText: String?,
    val hijriA11y: String?
)

/** ساخت مدل‌های همهٔ روزهای ماه — تابع محض (بدون Compose) برای تست‌پذیری */
internal fun buildMonthCells(
    jy: Int,
    jm: Int,
    monthLen: Int,
    firstDate: LocalDate,
    todayJ: JalaliDate,
    selected: JalaliDate,
    events: List<EventEntity>,
    holidayRepo: HolidayRepository,
    showGregorian: Boolean,
    showHijri: Boolean
): List<MonthCellData> {
    val out = ArrayList<MonthCellData>(monthLen)
    for (dayOffset in 0 until monthLen) {
        val dayNum = dayOffset + 1
        val j = JalaliDate(jy, jm, dayNum)
        val g = firstDate.plusDays(dayOffset.toLong())
        val dayEvents = events.filter { eventOccursOn(it, g) }
        val distinctColors = dayEvents.map { it.colorIndex }.distinct()
        val gregorianDayText: String?
        val gregorianA11y: String?
        if (showGregorian) {
            gregorianDayText = g.dayOfMonth.toString()
            gregorianA11y = "${g.dayOfMonth} ${PersianText.gregorianMonthsFa.getOrElse(g.monthValue - 1) { "" }}"
        } else {
            gregorianDayText = null
            gregorianA11y = null
        }
        val hijriDayText: String?
        val hijriA11y: String?
        if (showHijri) {
            val h = CalendarConverter.hijriFromJalali(jy, jm, dayNum)
            hijriDayText = h.day.toString()
            hijriA11y = "${h.day} ${PersianText.hijriMonths.getOrElse(h.month - 1) { "" }}"
        } else {
            hijriDayText = null
            hijriA11y = null
        }
        out.add(
            MonthCellData(
                jalali = j,
                gDate = g,
                isToday = j == todayJ,
                isSelected = j == selected,
                isHoliday = holidayRepo.isHoliday(j, g),
                isFriday = PersianText.weekdayIndexSat0(g) == 6,
                eventColorIndices = distinctColors.take(MonthGridLogic.MAX_EVENT_DOTS),
                dotPlan = MonthGridLogic.eventDotPlan(distinctColors.size),
                eventCount = dayEvents.size,
                gregorianDayText = gregorianDayText,
                gregorianA11y = gregorianA11y,
                hijriDayText = hijriDayText,
                hijriA11y = hijriA11y
            )
        )
    }
    return out
}

@Composable
private fun MonthPagerGrid(
    pagerState: androidx.compose.foundation.pager.PagerState,
    todayJ: JalaliDate,
    events: List<EventEntity>,
    holidayRepo: HolidayRepository,
    settings: AppSettings,
    persianDigits: Boolean,
    selected: JalaliDate,
    onDayClick: (JalaliDate) -> Unit,
    onDayLongClick: (JalaliDate) -> Unit
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth(),
        // ماه‌های مجاور به‌صورت تنبل و با کش از پیش ساخته می‌شوند تا سوایپ نرم باشد
        beyondViewportPageCount = 1
    ) { page ->
        val jy = MonthYearPickerLogic.yearOfPage(page)
        val jm = MonthYearPickerLogic.monthOfPage(page)
        MonthPage(
            jy = jy,
            jm = jm,
            todayJ = todayJ,
            events = events,
            holidayRepo = holidayRepo,
            settings = settings,
            persianDigits = persianDigits,
            selected = selected,
            onDayClick = onDayClick,
            onDayLongClick = onDayLongClick
        )
    }
}

@Composable
private fun MonthPage(
    jy: Int,
    jm: Int,
    todayJ: JalaliDate,
    events: List<EventEntity>,
    holidayRepo: HolidayRepository,
    settings: AppSettings,
    persianDigits: Boolean,
    selected: JalaliDate,
    onDayClick: (JalaliDate) -> Unit,
    onDayLongClick: (JalaliDate) -> Unit
) {
    val showGregorian = settings.showGregorianInCells
    val showHijri = settings.showHijriInCells
    val showWeekNumber = settings.showWeekNumber

    val monthLen = remember(jy, jm) { CalendarConverter.jalaliMonthLength(jy, jm) }
    val firstG = remember(jy, jm) { CalendarConverter.jalaliToGregorian(jy, jm, 1) }
    val firstDate = remember(firstG) { LocalDate.of(firstG.year, firstG.month, firstG.day) }
    val firstIndex = remember(firstDate) { PersianText.weekdayIndexSat0(firstDate) }

    // مدل‌های خانه‌ها یک بار برای هر صفحه محاسبه و کش می‌شوند (سوایپ روان)
    val cells = remember(
        jy, jm, events, holidayRepo, selected, todayJ, showGregorian, showHijri
    ) {
        buildMonthCells(
            jy, jm, monthLen, firstDate, todayJ, selected, events, holidayRepo,
            showGregorian, showHijri
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // روزهای هفته — با نام کامل (نسخه ۱.۱۱۷ به درخواست کاربر: شنبه، یکشنبه، …)
            Row(modifier = Modifier.fillMaxWidth()) {
                if (showWeekNumber) {
                    Spacer(Modifier.width(24.dp))
                }
                PersianText.weekDays.forEachIndexed { i, wd ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = wd,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            color = if (i == 6) holidayColor() else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // شش ردیف ثابت — ماه‌های ۵ ردیفی ردیف خالی آخر را هم رزرو می‌کنند
            // تا ارتفاع صفحه بین ماه‌ها بدون پرش ثابت بماند (۱.۱۱.۳۹)
            repeat(MonthGridLogic.FIXED_ROW_COUNT) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (showWeekNumber) {
                        val weekNo = MonthGridLogic.weekNumber(jy, jm, firstIndex, row)
                        Text(
                            text = if (persianDigits) PersianText.toPersianDigits(weekNo.toString())
                            else weekNo.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .width(24.dp)
                                .align(Alignment.CenterVertically)
                        )
                    }
                    repeat(7) { col ->
                        val idx = row * 7 + col
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            val cell = cells.getOrNull(idx)
                            if (cell != null) {
                                DayCell(
                                    data = cell,
                                    persianDigits = persianDigits,
                                    onClick = { onDayClick(cell.jalali) },
                                    onLongClick = { onDayLongClick(cell.jalali) }
                                )
                            } else {
                                Spacer(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = MonthGridLogic.MIN_CELL_HEIGHT_DP.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    data: MonthCellData,
    persianDigits: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val holiday = holidayColor()
    val numText = if (persianDigits) PersianText.toPersianDigits(data.jalali.day.toString())
    else data.jalali.day.toString()

    // متن TalkBack کامل — «آبان، ۱۰، یکشنبه، تعطیل رسمی، ۲ رویداد»
    val cd = MonthGridLogic.dayContentDescription(
        monthName = PersianText.jalaliMonths.getOrElse(data.jalali.month - 1) { "" },
        day = data.jalali.day,
        weekdayName = PersianText.weekDays.getOrElse(PersianText.weekdayIndexSat0(data.gDate)) { "" },
        isHoliday = data.isHoliday,
        isFriday = data.isFriday,
        isToday = data.isToday,
        isSelected = data.isSelected,
        eventCount = data.eventCount,
        gregorianLabel = data.gregorianA11y,
        hijriLabel = data.hijriA11y
    )

    // روز انتخاب‌شده: دایرهٔ پر — امروز: حلقه دور عدد — هر دو: پر + حلقه
    val circleFill = when {
        data.isSelected -> MaterialTheme.colorScheme.primary
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val ringColor = if (data.isToday && !data.isSelected) MaterialTheme.colorScheme.primary
    else androidx.compose.ui.graphics.Color.Transparent
    val numColor = when {
        data.isSelected -> MaterialTheme.colorScheme.onPrimary
        data.isToday -> MaterialTheme.colorScheme.primary
        data.isHoliday || data.isFriday -> holiday
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MonthGridLogic.MIN_CELL_HEIGHT_DP.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics { contentDescription = cd }
            .padding(vertical = 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            // دایرهٔ متن‌پیرو: با بزرگ‌شدن فونت تا ۲۰۰٪ هم عدد داخل دایره جا می‌شود
            modifier = Modifier
                .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                .background(circleFill, CircleShape)
                .border(width = if (data.isToday && !data.isSelected) 2.dp else 0.dp, ringColor, CircleShape)
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = numText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = numColor
                )
                // نقطه‌های رویداد — فضایشان همیشه رزرو می‌شود تا ارتفاع ثابت بماند
                Row(
                    modifier = Modifier.height(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val plan = data.dotPlan
                    data.eventColorIndices.forEachIndexed { i, colorIndex ->
                        val dim = plan.lastDotDimmed && i == plan.dotCount - 1
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 1.dp)
                                .size(5.dp)
                                .background(
                                    eventDotColor(colorIndex).copy(alpha = if (dim) 0.45f else 1f),
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }
        // زیرعدد اختیاری میلادی/قمری (تنظیمات — پیش‌فرض خاموش)
        if (data.gregorianDayText != null || data.hijriDayText != null) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(13.dp)
            ) {
                data.gregorianDayText?.let { gTxt ->
                    Text(
                        text = if (persianDigits) PersianText.toPersianDigits(gTxt) else gTxt,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.5.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
                data.hijriDayText?.let { hTxt ->
                    Text(
                        text = if (persianDigits) PersianText.toPersianDigits(hTxt)
                        else PersianText.toArabicDigits(hTxt),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.5.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}

// ---------------- نمایش هفتگی (دست‌نخورده) ----------------

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
                        text = PersianText.weekDays[i],
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        maxLines = 1,
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

/**
 * مناسبت‌های روز لمس‌شده تقویم — نسخه ۱.۱۱.۷ (درخواست کاربر)
 *
 * کاربر می‌خواست «وقتی روی روزهای تقویم میزنم مناسبت‌های اون روز رو در فرم
 * مناسبت‌های این ماه ببینم» — پس همین‌جا زیر تقویم، یک کارت با همان فرم
 * (شماره روز سمت راست + عنوان مناسبت‌ها) برای روز انتخاب‌شده نشان داده می‌شود؛
 * شامل مناسبت‌های رسمی (تعطیل با رنگ قرمز) و رویدادهای شخصی کاربر (با «•»).
 */
@Composable
private fun SelectedDayOccasions(
    selected: JalaliDate,
    events: List<EventEntity>,
    holidayRepo: HolidayRepository,
    persianDigits: Boolean
) {
    val g = CalendarConverter.jalaliToGregorian(selected.year, selected.month, selected.day)
    val gDate = LocalDate.of(g.year, g.month, g.day)
    val occ = holidayRepo.occasionsFor(selected, gDate)
    val dayEvents = events.filter { eventOccursOn(it, gDate) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "مناسبت‌های این روز — " +
                        PersianText.weekDays.getOrElse(
                            PersianText.weekdayIndexSat0(gDate)
                        ) { "" } + " " +
                        (if (persianDigits) PersianText.toPersianDigits(selected.day.toString())
                         else selected.day.toString()) + " " +
                        PersianText.jalaliMonths.getOrElse(selected.month - 1) { "" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            if (occ.isEmpty() && dayEvents.isEmpty()) {
                Text(
                    "مناسبتی برای این روز ثبت نشده است",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // همان فرم «مناسبت‌های این ماه»: شماره روز + عنوان‌ها
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(
                        text = if (persianDigits) PersianText.toPersianDigits(selected.day.toString())
                        else selected.day.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(34.dp),
                        textAlign = TextAlign.Center
                    )
                    Column {
                        occ.forEach { o ->
                            Text(
                                text = if (o.isHoliday) "${o.title} (تعطیل)" else o.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (o.isHoliday) holidayColor()
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        dayEvents.forEach { e ->
                            Text(
                                text = "• ${e.title}",
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
