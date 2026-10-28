package ir.persiancalendar.app.ui.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.JalaliDate
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.data.db.ReminderEntity
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.notifications.ReminderEngine
import ir.persiancalendar.app.ui.components.JalaliDatePickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** ویرایشگر یادآور — نسخه ۱.۱۱ (آب / دارو / ورزش / شخصی) */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderEditorSheet(
    existing: ReminderEntity?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val todayJ = run {
        val n = LocalDate.now()
        CalendarConverter.gregorianToJalali(n.year, n.monthValue, n.dayOfMonth)
    }

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var type by remember { mutableIntStateOf(existing?.type ?: ReminderEntity.TYPE_PERSONAL) }
    var repeatMode by remember { mutableIntStateOf(existing?.repeatMode ?: ReminderEntity.REPEAT_DAILY) }
    var times by remember {
        mutableStateOf(existing?.timesList ?: listOf(600, 720, 840, 960))
    }
    var days by remember {
        mutableStateOf(
            existing?.daysSet?.ifEmpty { setOf(0, 2, 4) } ?: setOf(0, 2, 4)
        )
    }
    var startDate by remember {
        mutableStateOf(
            existing?.takeIf { it.startDate > 0 }?.let {
                JalaliDate(it.startDate / 10000, (it.startDate / 100) % 100, it.startDate % 100)
            } ?: JalaliDate(todayJ.year, todayJ.month, todayJ.day)
        )
    }
    var hasEnd by remember { mutableStateOf(existing != null && existing.endDate > 0) }
    var endDate by remember {
        mutableStateOf(
            existing?.takeIf { it.endDate > 0 }?.let {
                JalaliDate(it.endDate / 10000, (it.endDate / 100) % 100, it.endDate % 100)
            } ?: JalaliDate(todayJ.year, todayJ.month, todayJ.day)
        )
    }
    var target by remember {
        mutableIntStateOf(existing?.targetCount?.takeIf { it > 0 } ?: 8)
    }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }

    var timePickerOpen by remember { mutableStateOf(false) }
    var startPickOpen by remember { mutableStateOf(false) }
    var endPickOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
    val titleFocus = remember { FocusRequester() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp)
        ) {
            Text(
                if (existing == null) "یادآور جدید" else "ویرایش یادآور",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it; error = null },
                label = { Text("عنوان *") },
                placeholder = { Text(when (type) {
                    ReminderEntity.TYPE_WATER -> "مثلا: نوشیدن آب — ۸ لیوان در روز"
                    ReminderEntity.TYPE_MEDICINE -> "مثلا: قرص فشار خون — بعد از غذا"
                    ReminderEntity.TYPE_EXERCISE -> "مثلا: پیاده‌روی ۳۰ دقیقه‌ای"
                    else -> "مثلا: مطالعه کتاب، تماس با دوست…"
                }) },
                isError = error != null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus)
            )
            Spacer(Modifier.height(12.dp))

            // ---------- نوع ----------
            Text("نوع یادآور", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderEntity.typeLabels.forEachIndexed { i, label ->
                    FilterChip(
                        selected = type == i,
                        onClick = { type = i },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------- نوبت‌های ساعت ----------
            // نسخه ۱.۱۱.۱۱ (گزارش کاربر): چیپ‌ها داخل Row معمولی بودند و با چند نوبت،
            // دکمه «+ ساعت» از صفحه بیرون می‌زد و دیده نمی‌شد؛ اکنون FlowRow است و
            // هر تعداد ساعت به سطر بعدی می‌رود و دکمه افزودن همیشه پیدا است.
            Text("ساعت‌های یادآوری *", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                times.sorted().forEach { m ->
                    AssistChip(
                        onClick = { times = times - m },
                        label = { Text(fmtMinute(m, true)) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "حذف ساعت $m",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
                OutlinedButton(onClick = { timePickerOpen = true }) { Text("+ ساعت") }
            }
            if (times.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "هنوز ساعتی اضافه نشده — با دکمه «+ ساعت» نوبت‌های یادآوری را اضافه کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))

            // ---------- حالت تکرار ----------
            Text("تکرار", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ReminderEntity.REPEAT_ONCE to "یک‌باره",
                    ReminderEntity.REPEAT_DAILY to "هر روز",
                    ReminderEntity.REPEAT_WEEKLY to "روزهای هفته"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = repeatMode == mode,
                        onClick = { repeatMode = mode },
                        label = { Text(label) }
                    )
                }
            }

            // ---------- روزهای هفته (فقط حالت هفتگی) ----------
            if (repeatMode == ReminderEntity.REPEAT_WEEKLY) {
                Spacer(Modifier.height(10.dp))
                Text("روزهای تکرار", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PersianText.weekDaysShort.forEachIndexed { i, d ->
                        FilterChip(
                            selected = i in days,
                            onClick = {
                                days = if (i in days) days - i else days + i
                                error = null
                            },
                            label = { Text(d) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------- بازه تاریخ ----------
            Text("بازه تاریخ", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("از", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = { startPickOpen = true }) {
                    Text(jalaliLabel(startDate))
                }
                Spacer(Modifier.weight(1f))
                Text("تا", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("بی‌پایان", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = !hasEnd,
                        onCheckedChange = { hasEnd = !it },
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                if (hasEnd) {
                    Spacer(Modifier.size(6.dp))
                    OutlinedButton(onClick = { endPickOpen = true }) {
                        Text(jalaliLabel(endDate))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------- هدف آب ----------
            if (type == ReminderEntity.TYPE_WATER) {
                Text("هدف روزانه (لیوان)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { if (target > 1) target-- }) { Text("−") }
                    Text(
                        PersianText.toPersianDigits(target.toString()),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    OutlinedButton(onClick = { if (target < 20) target++ }) { Text("+") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "پیشنهاد: ۸ لیوان",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---------- یادداشت ----------
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("یادداشت (اختیاری)") },
                placeholder = { Text("مثلا «بعد از غذا» یا «۲ لیوان بزرگ»") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            // ---------- فعال ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("یادآور فعال است", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Spacer(Modifier.height(14.dp))

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }

            Row {
                Button(
                    onClick = {
                        // ---------- اعتبارسنجی ----------
                        if (title.isBlank()) {
                            error = "عنوان یادآور را وارد کنید"
                            // بازگشت نرم به بالای فرم و فوکوس روی فیلد عنوان (۱.۱۱.۱)
                            scope.launch {
                                scrollState.animateScrollTo(0)
                                titleFocus.requestFocus()
                            }
                            return@Button
                        }
                        if (times.isEmpty()) {
                            error = "حداقل یک ساعت یادآوری انتخاب کنید"
                            return@Button
                        }
                        if (repeatMode == ReminderEntity.REPEAT_WEEKLY && days.isEmpty()) {
                            error = "برای تکرار هفتگی، حداقل یک روز هفته انتخاب کنید"
                            return@Button
                        }
                        val entity = ReminderEntity(
                            id = existing?.id ?: 0,
                            title = title.trim(),
                            type = type,
                            repeatMode = repeatMode,
                            daysOfWeek = days.sorted().joinToString(","),
                            times = times.sorted().joinToString(","),
                            startDate = ReminderEntity.dayKey(startDate.year, startDate.month, startDate.day),
                            endDate = if (hasEnd) ReminderEntity.dayKey(endDate.year, endDate.month, endDate.day) else 0,
                            targetCount = if (type == ReminderEntity.TYPE_WATER) target else 0,
                            note = note.trim(),
                            enabled = enabled,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis()
                        )
                        scope.launch(Dispatchers.IO) {
                            val id = AppContainer.reminderDao(context).insert(entity)
                            // بستن فوری آلارم نوبت بعدی (لغو قدیمی + بستن جدید)
                            ReminderEngine.scheduleNext(context, id)
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("ذخیره یادآور") }
                if (existing != null) {
                    Spacer(Modifier.size(10.dp))
                    OutlinedButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            AppContainer.doseLogDao(context).deleteOfReminder(existing.id)
                            AppContainer.reminderDao(context).delete(existing)
                            ReminderEngine.cancel(context, existing.id)
                        }
                        onDismiss()
                    }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    // ---------- انتخاب ساعت ----------
    if (timePickerOpen) {
        val state = rememberTimePickerState(initialHour = 10, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timePickerOpen = false },
            title = { Text("ساعت یادآوری") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val m = state.hour * 60 + state.minute
                    if (m !in times) times = (times + m)
                    timePickerOpen = false
                }) { Text("افزودن") }
            },
            dismissButton = {
                TextButton(onClick = { timePickerOpen = false }) { Text("انصراف") }
            }
        )
    }

    if (startPickOpen) {
        JalaliDatePickerDialog(
            initial = startDate,
            onDismiss = { startPickOpen = false },
            onSelected = { startDate = it; startPickOpen = false }
        )
    }
    if (endPickOpen) {
        JalaliDatePickerDialog(
            initial = endDate,
            onDismiss = { endPickOpen = false },
            onSelected = { endDate = it; endPickOpen = false }
        )
    }
}

private fun jalaliLabel(d: JalaliDate): String =
    "${PersianText.toPersianDigits(d.day.toString())} " +
        PersianText.jalaliMonths.getOrElse(d.month - 1) { "" } + " " +
        PersianText.toPersianDigits(d.year.toString())
