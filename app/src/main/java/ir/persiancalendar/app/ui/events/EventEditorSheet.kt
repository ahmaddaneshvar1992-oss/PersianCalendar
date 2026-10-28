package ir.persiancalendar.app.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.JalaliDate
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.data.db.EventEntity
import ir.persiancalendar.app.data.model.ReminderOption
import ir.persiancalendar.app.data.model.RepeatType
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.notifications.AlarmScheduler
import ir.persiancalendar.app.ui.components.JalaliDatePickerDialog
import ir.persiancalendar.app.ui.theme.EventDotColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * حالت ویرایشگر رویداد (پایین‌صفحه شیشه‌ای).
 * نسخه ۱.۱۱.۱۱ (گزارش کاربر): انتخابگر ساعت خراب بود — دو فهرست کشویی «ساعت/دقیقه»
 * داخل شیت پایین‌صفحه باز نمی‌شد و برچسب‌شان هم دیده نمی‌شد؛ جایگزین شد با یک دکمه بزرگ
 * «۰۹:۰۰» که دیالوگ انتخابگر ساعت متریال را باز می‌کند + زیرعنوان پویای «تمام‌روز»
 * (وقتی ساعت دارد، همان ساعت را نشان می‌دهد) + چیپ‌های یادآوری/تکرار به‌جای منوی کشویی.
 * @param existing اگر null باشد یعنی رویداد جدید
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditorSheet(
    initialDate: JalaliDate,
    existing: EventEntity?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dao = AppContainer.eventDao(context)

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var date by remember {
        mutableStateOf(
            existing?.let { CalendarConverter.gregorianToJalali(it.gy, it.gm, it.gd) } ?: initialDate
        )
    }
    var allDay by remember { mutableStateOf(existing?.let { it.hour < 0 } ?: true) }
    var hour by remember { mutableStateOf(existing?.takeIf { it.hour >= 0 }?.hour ?: 9) }
    var minute by remember { mutableStateOf(existing?.takeIf { it.hour >= 0 }?.minute ?: 0) }
    var reminder by remember {
        mutableStateOf(ReminderOption.fromMinutes(existing?.reminderMinutesBefore ?: 0))
    }
    var repeat by remember { mutableStateOf(RepeatType.fromOrdinal(existing?.repeatType ?: 0)) }
    var colorIndex by remember { mutableStateOf(existing?.colorIndex ?: 0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                if (existing == null) "رویداد جدید" else "ویرایش رویداد",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it; error = false },
                label = { Text("عنوان رویداد *") },
                isError = error,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (error) {
                Text(
                    "عنوان نمی‌تواند خالی باشد",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("یادداشت (اختیاری)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // تاریخ
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تاریخ شمسی", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Button(onClick = { showDatePicker = true }) {
                    Text(PersianText.jalaliDateShort(date, true))
                }
            }
            Spacer(Modifier.height(10.dp))

            // ساعت — نسخه ۱.۱۱.۱۱: زیرعنوان پویا + دکمه انتخابگر زمان (رفع باگ منوی کشویی در شیت)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("تمام‌روز", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (allDay) "بدون ساعت مشخص"
                        else "ساعت رویداد: " + PersianText.timeHM(hour, minute, true),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = allDay, onCheckedChange = { allDay = it })
            }
            if (!allDay) {
                Button(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("انتخاب ساعت: " + PersianText.timeHM(hour, minute, true))
                }
            }
            Spacer(Modifier.height(10.dp))

            // یادآوری — چیپ‌های قابل لمس به‌جای منوی کشویی (نسخه ۱.۱۱.۱۱)
            Text("یادآوری", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReminderOption.entries.forEach { opt ->
                    FilterChip(
                        selected = reminder == opt,
                        onClick = { reminder = opt },
                        label = { Text(opt.label) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // تکرار — چیپ‌های قابل لمس به‌جای منوی کشویی (نسخه ۱.۱۱.۱۱)
            Text("تکرار", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RepeatType.entries.forEach { rep ->
                    FilterChip(
                        selected = repeat == rep,
                        onClick = { repeat = rep },
                        label = { Text(rep.label) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // رنگ
            Text("رنگ", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EventDotColors.forEachIndexed { idx, c ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(c, CircleShape)
                            .clickable { colorIndex = idx },
                        contentAlignment = Alignment.Center
                    ) {
                        if (colorIndex == idx) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(androidx.compose.ui.graphics.Color.White, CircleShape)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row {
                Button(
                    onClick = {
                        if (title.isBlank()) { error = true; return@Button }
                        val g = CalendarConverter.jalaliToGregorian(date.year, date.month, date.day)
                        val entity = EventEntity(
                            id = existing?.id ?: 0,
                            title = title.trim(),
                            note = note.trim(),
                            jy = date.year, jm = date.month, jd = date.day,
                            gy = g.year, gm = g.month, gd = g.day,
                            hour = if (allDay) -1 else hour,
                            minute = if (allDay) 0 else minute,
                            reminderMinutesBefore = reminder.minutesBefore,
                            repeatType = repeat.ordinal,
                            colorIndex = colorIndex
                        )
                        CoroutineScope(Dispatchers.IO).launch {
                            val id = if (existing == null) dao.insert(entity) else {
                                dao.update(entity); entity.id
                            }
                            AlarmScheduler.scheduleEventReminder(
                                context,
                                entity.copy(id = if (existing == null) id else entity.id)
                            )
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (existing == null) "ذخیره رویداد" else "ذخیره تغییرات") }
            }
        }
    }

    if (showDatePicker) {
        JalaliDatePickerDialog(
            initial = date,
            onDismiss = { showDatePicker = false },
            onSelected = {
                date = it
                showDatePicker = false
            }
        )
    }

    // ---------- انتخابگر ساعت رویداد (نسخه ۱.۱۱.۱۱ — جایگزین دو منوی کشویی خراب) ----------
    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("ساعت رویداد") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    hour = timeState.hour
                    minute = timeState.minute
                    showTimePicker = false
                }) { Text("تأیید") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("انصراف") }
            }
        )
    }
}
