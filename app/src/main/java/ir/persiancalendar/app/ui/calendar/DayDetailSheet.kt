package ir.persiancalendar.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.R
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.JalaliDate
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.core.events.HolidayRepository
import ir.persiancalendar.app.core.prayer.Cities
import ir.persiancalendar.app.core.prayer.PrayerMethod
import ir.persiancalendar.app.core.prayer.PrayerTimesCalculator
import ir.persiancalendar.app.data.db.EventEntity
import ir.persiancalendar.app.ui.theme.EventDotColors
import ir.persiancalendar.app.ui.theme.holidayColor

/**
 * پنل جزئیات روز (Bottom Sheet) — نسخهٔ ۱.۱۱.۴۰ (کار ۳)
 *
 * ضربه روی هر روز تقویم این پنل را از پایین باز می‌کند:
 *  - تاریخ کامل شمسی + روز هفته + نشان تعطیل/کاری
 *  - تبدیل سه‌گانهٔ تاریخ (شمسی/میلادی/قمری)
 *  - مناسبت‌های روز
 *  - رویدادها و یادآورهای همان روز (هر ردیف با دکمهٔ ویرایش)
 *  - دکمهٔ «افزودن رویداد» با همان تاریخ پیش‌فرض
 *  - اوقات شرعی همان روز
 *
 * کشیدن به بالا پنل را تمام‌صفحه می‌کند و کشیدن به پایین (یا دکمهٔ
 * برگشت گوشی) آن را می‌بندد — رفتار بومی ModalBottomSheet با
 * skipPartiallyExpanded = false (نیمه‌باز → کامل).
 * این پنل جایگزین جریان لمس روز شد؛ DayDetailScreen به‌عنوان فالبک
 * در MainScreen باقی است اما دیگر مسیر فعال نیست.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    jalali: JalaliDate,
    events: List<EventEntity>,
    holidayRepo: HolidayRepository,
    persianDigits: Boolean,
    showHijri: Boolean,
    cityIndex: Int,
    prayerMethod: PrayerMethod,
    onDismiss: () -> Unit,
    onAddEvent: (JalaliDate) -> Unit,
    onEditEvent: (EventEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val g = CalendarConverter.jalaliToGregorian(jalali.year, jalali.month, jalali.day)
    val gDate = java.time.LocalDate.of(g.year, g.month, g.day)
    val h = CalendarConverter.gregorianToHijri(g.year, g.month, g.day)
    val occasions = holidayRepo.occasionsFor(jalali, gDate)
    val isHoliday = holidayRepo.isHoliday(jalali, gDate)
    val dayEvents = WeekAgendaLogic.sortForAgenda(events.filter { eventOccursOn(it, gDate) })

    val city = Cities.list[cityIndex.coerceIn(0, Cities.list.size - 1)]
    val times = PrayerTimesCalculator.compute(gDate, city.lat, city.lng, Cities.IRAN_TZ_HOURS, prayerMethod)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // ---------- سربرگ: روز هفته + تاریخ کامل شمسی + نشان تعطیل ----------
            Text(
                text = PersianText.weekDays.getOrElse(PersianText.weekdayIndexSat0(gDate)) { "" },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = PersianText.jalaliDateFull(jalali, persianDigits),
                style = MaterialTheme.typography.headlineSmall,
                color = if (isHoliday) holidayColor() else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isHoliday) stringResource(R.string.day_sheet_holiday_badge)
                else stringResource(R.string.day_sheet_workday_badge),
                style = MaterialTheme.typography.bodySmall,
                color = if (isHoliday) holidayColor() else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))

            // ---------- تبدیل تاریخ ----------
            Text(stringResource(R.string.day_sheet_dates_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            SheetConversionRow(
                label = stringResource(R.string.day_sheet_cal_jalali),
                value = "${PersianText.jalaliMonths.getOrElse(jalali.month - 1) { "" }} ${pd(jalali.day, persianDigits)}، ${pd(jalali.year, persianDigits)}"
            )
            SheetConversionRow(
                label = stringResource(R.string.day_sheet_cal_gregorian),
                value = "${PersianText.gregorianMonths.getOrElse(g.month - 1) { "" }} ${pd(g.day, persianDigits)}, ${pd(g.year, persianDigits)}"
            )
            if (showHijri) {
                SheetConversionRow(
                    label = stringResource(R.string.day_sheet_cal_hijri),
                    value = PersianText.hijriDateFull(h, persianDigits)
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---------- مناسبت‌ها ----------
            Text(stringResource(R.string.day_sheet_occasions_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            if (occasions.isEmpty()) {
                Text(
                    stringResource(R.string.day_sheet_no_occasion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                occasions.forEach {
                    Text(
                        text = "• ${it.title}" + if (it.isHoliday) " (" + stringResource(R.string.day_sheet_holiday_badge) + ")" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (it.isHoliday) holidayColor() else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---------- رویدادهای من ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.day_sheet_events_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(6.dp))
            if (dayEvents.isEmpty()) {
                Text(
                    stringResource(R.string.day_sheet_no_event),
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
                            } else {
                                Text(
                                    stringResource(R.string.day_sheet_all_day),
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
                        TextButton(onClick = { onEditEvent(e) }) {
                            Text(stringResource(R.string.day_sheet_edit))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---------- دکمهٔ «افزودن رویداد» با همان تاریخ ----------
            val addEventCd = stringResource(R.string.day_sheet_add_event_cd)
            Button(
                onClick = { onAddEvent(jalali) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = addEventCd }
            ) {
                Icon(
                    Icons.Filled.Event,
                    contentDescription = null,
                    modifier = Modifier.height(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.day_sheet_add_event), style = MaterialTheme.typography.titleSmall)
            }

            Spacer(Modifier.height(14.dp))

            // ---------- اوقات شرعی ----------
            Text(
                stringResource(R.string.day_sheet_times_title, city.name),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            val rows = listOf(
                stringResource(R.string.day_sheet_fajr) to times.fajr,
                stringResource(R.string.day_sheet_sunrise) to times.sunrise,
                stringResource(R.string.day_sheet_dhuhr) to times.dhuhr,
                stringResource(R.string.day_sheet_asr) to times.asr,
                stringResource(R.string.day_sheet_sunset) to times.sunset,
                stringResource(R.string.day_sheet_maghrib) to times.maghrib,
                stringResource(R.string.day_sheet_isha) to times.isha
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
}

@Composable
private fun SheetConversionRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

private fun pd(n: Int, persian: Boolean): String =
    if (persian) PersianText.toPersianDigits(n.toString()) else n.toString()
