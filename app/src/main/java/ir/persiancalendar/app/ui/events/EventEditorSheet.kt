package ir.persiancalendar.app.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import ir.persiancalendar.app.ui.components.SimpleDropdown
import ir.persiancalendar.app.ui.theme.EventDotColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * حالت ویرایشگر رویداد (پایین‌صفحه شیشه‌ای).
 * @param existing اگر null باشد یعنی رویداد جدید
 */
@OptIn(ExperimentalMaterial3Api::class)
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

            // ساعت
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("تمام‌روز", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "بدون ساعت مشخص",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = allDay, onCheckedChange = { allDay = it })
            }
            if (!allDay) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    SimpleDropdown(
                        modifier = Modifier.weight(1f),
                        label = "ساعت",
                        items = (0..23).map { PersianText.toPersianDigits(String.format("%02d", it)) },
                        selectedIndex = hour,
                        onSelected = { hour = it }
                    )
                    SimpleDropdown(
                        modifier = Modifier.weight(1f),
                        label = "دقیقه",
                        items = (0..55 step 5).map { PersianText.toPersianDigits(String.format("%02d", it)) },
                        selectedIndex = minute / 5,
                        onSelected = { minute = it * 5 }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // یادآوری
            SimpleDropdown(
                modifier = Modifier.fillMaxWidth(),
                label = "یادآوری",
                items = ReminderOption.entries.map { it.label },
                selectedIndex = ReminderOption.entries.indexOf(reminder),
                onSelected = { reminder = ReminderOption.entries[it] }
            )
            Spacer(Modifier.height(10.dp))

            // تکرار
            SimpleDropdown(
                modifier = Modifier.fillMaxWidth(),
                label = "تکرار",
                items = RepeatType.entries.map { it.label },
                selectedIndex = RepeatType.entries.indexOf(repeat),
                onSelected = { repeat = RepeatType.entries[it] }
            )
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
}
