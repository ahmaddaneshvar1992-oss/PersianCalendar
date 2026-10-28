package ir.persiancalendar.app.ui.reminder

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.data.db.DoseLogDao
import ir.persiancalendar.app.data.db.DoseLogEntity
import ir.persiancalendar.app.data.db.ReminderDao
import ir.persiancalendar.app.data.db.ReminderEntity
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.notifications.AlarmScheduler
import ir.persiancalendar.app.notifications.ReminderEngine
import ir.persiancalendar.app.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** رنگ هر نوع یادآور (آب، دارو، ورزش، شخصی) */
internal val typeColors = listOf(
    Color(0xFF2A7BD4), // آب — آبی
    Color(0xFFD64545), // دارو — قرمز
    Color(0xFF1E9E55), // ورزش — سبز
    Color(0xFF7C5CD6)  // شخصی — بنفش
)

/** دقیقه روز → «۱۰:۳۰» (با ارقام فارسی در صورت نیاز) */
internal fun fmtMinute(minute: Int, fa: Boolean): String {
    val h = (minute / 60).coerceIn(0, 23)
    val m = minute % 60
    val s = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    return if (fa) PersianText.toPersianDigits(s) else s
}

/** توضیح تکرار: «هر روز»، «شنبه و دوشنبه»، «یک‌باره» */
internal fun repeatLabel(r: ReminderEntity): String = when (r.repeatMode) {
    ReminderEntity.REPEAT_ONCE -> "یک‌باره"
    ReminderEntity.REPEAT_DAILY -> "هر روز"
    ReminderEntity.REPEAT_WEEKLY -> {
        val days = r.daysSet.sorted()
        if (days.isEmpty()) "هفتگی — روزی انتخاب نشده"
        else days.joinToString(" و ") { PersianText.weekDays[it] }
    }
    else -> ""
}

/** کلید امروز جلالی (jy*10000+jm*100+jd) */
internal fun todayDayKey(): Int {
    val now = LocalDate.now()
    val j = CalendarConverter.gregorianToJalali(now.year, now.monthValue, now.dayOfMonth)
    return ReminderEntity.dayKey(j.year, j.month, j.day)
}

/**
 * صفحه یادآورها — نسخه ۱.۱۱
 * موتور یادآور عمومی برای آب، دارو، ورزش و کارهای شخصی:
 *  - برنامه امروز با ثبت انجام‌شدن هر نوبت (لمس هر ردیف = انجام شد)
 *  - پیشرفت آب روزانه (هدف و لیوان‌های ثبت‌شده)
 *  - بنر راهنما برای مجوز اعلان (اندروید ۱۳+) و آلارم دقیق (اندروید ۱۲+)
 */
@Composable
fun RemindersScreen(persianDigits: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = AppContainer.reminderDao(context)
    val logDao = AppContainer.doseLogDao(context)

    val reminders by dao.observeAll().collectAsState(initial = emptyList())
    val logs by logDao.observeDay(todayDayKey()).collectAsState(initial = emptyList())

    var filter by remember { mutableStateOf(-1) } // -1 = همه
    var editorVisible by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ReminderEntity?>(null) }
    var deleting by remember { mutableStateOf<ReminderEntity?>(null) }

    /** ثبت/برداشتن انجام‌شدن یک نوبت امروز */
    fun toggleSlot(reminderId: Long, minute: Int) {
        scope.launch(Dispatchers.IO) {
            val dayKey = todayDayKey()
            val exists = logs.any { it.reminderId == reminderId && it.timeMinute == minute }
            if (exists) {
                logDao.deleteSlot(reminderId, dayKey, minute)
            } else {
                runCatching {
                    logDao.insert(DoseLogEntity(reminderId = reminderId, dayKey = dayKey, timeMinute = minute))
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ---------- مجوزها (چک‌لیست انتشار) ----------
        item { PermissionBanners() }

        // ---------- پیشرفت آب ----------
        val waterRems = reminders.filter { it.enabled && it.type == ReminderEntity.TYPE_WATER }
        if (waterRems.isNotEmpty()) {
            item {
                WaterProgressCard(
                    target = waterRems.sumOf { if (it.targetCount > 0) it.targetCount else 8 },
                    drunk = logs.count { l -> waterRems.any { it.id == l.reminderId } },
                    fa = persianDigits,
                    onAddGlass = {
                        scope.launch(Dispatchers.IO) { addGlass(logDao, waterRems, todayDayKey()) }
                    }
                )
            }
        }

        // ---------- برنامه امروز ----------
        item {
            TodayScheduleCard(
                reminders = reminders,
                logs = logs,
                fa = persianDigits,
                onToggleSlot = { id, minute -> toggleSlot(id, minute) }
            )
        }

        // ---------- فیلتر نوع‌ها ----------
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter == -1,
                    onClick = { filter = -1 },
                    label = { Text("همه") }
                )
                ReminderEntity.typeLabels.forEachIndexed { i, label ->
                    FilterChip(
                        selected = filter == i,
                        onClick = { filter = i },
                        label = { Text(label) }
                    )
                }
            }
        }

        // ---------- فهرست یادآورها ----------
        val shown = reminders.filter { filter == -1 || it.type == filter }
        if (shown.isEmpty()) {
            item {
                SectionCard {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            if (reminders.isEmpty()) "هنوز یادآوری ثبت نشده است"
                            else "این نوع یادآوری ندارید",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "با دکمه سبز «+» یا دکمه پایین، یادآور آب، دارو، ورزش یا کار شخصی بسازید. " +
                                "هر یادآور می‌تواند چند نوبت ساعت، روزهای هفته و بازه تاریخ شروع تا پایان داشته باشد؛ " +
                                "اعلان با دکمه‌های «انجام شد» و «بعداً یادآوری کن» می‌آید و بعد از خاموش‌وشدن " +
                                "گوشی هم خودکار بازیابی می‌شود.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        items(shown, key = { it.id }) { r ->
            ReminderRow(
                r = r,
                fa = persianDigits,
                onToggle = { on ->
                    scope.launch(Dispatchers.IO) {
                        dao.update(r.copy(enabled = on))
                        if (on) ReminderEngine.scheduleNext(context, r.id)
                        else ReminderEngine.cancel(context, r.id)
                    }
                },
                onEdit = { editing = r },
                onDelete = { deleting = r }
            )
        }

        item {
            Button(
                onClick = { editing = null; editorVisible = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("یادآور جدید")
            }
        }
    }

    // ---------- ویرایشگر ----------
    if (editorVisible || editing != null) {
        ReminderEditorSheet(
            existing = editing,
            onDismiss = { editorVisible = false; editing = null }
        )
    }

    // ---------- تأیید حذف ----------
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف یادآور") },
            text = { Text("«${target.title}» و همه ثبت‌هایش حذف شود؟ اعلان‌های بعدی هم لغو می‌شوند.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch(Dispatchers.IO) {
                        logDao.deleteOfReminder(target.id)
                        dao.delete(target)
                        ReminderEngine.cancel(context, target.id)
                    }
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("انصراف") } }
        )
    }
}

/** ثبت یک لیوان آب — با پرش از دقیقه‌های پرشده (ایندکس یکتا) */
private suspend fun addGlass(logDao: DoseLogDao, waterRems: List<ReminderEntity>, dayKey: Int) {
    val target = waterRems.firstOrNull() ?: return
    val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
    for (offset in 0..5) {
        val ok = runCatching {
            logDao.insert(
                DoseLogEntity(
                    reminderId = target.id,
                    dayKey = dayKey,
                    timeMinute = (nowMin + offset) % (24 * 60)
                )
            )
        }.isSuccess
        if (ok) return
    }
}

// ---------------- ردیف یادآور ----------------

@Composable
private fun ReminderRow(
    r: ReminderEntity,
    fa: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    SectionCard {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(
                            typeColors.getOrElse(r.type) { MaterialTheme.colorScheme.primary },
                            CircleShape
                        )
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    r.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = r.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(4.dp))
            val times = r.timesList.sorted().joinToString("  ·  ") { fmtMinute(it, fa) }
            // نسخه ۱.۱۱.۱۱ (گزارش کاربر): ساعت‌های نوبت قبلاً داخل یک متن خاکستری کوچک گم می‌شدند؛
            // اکنون در یک سطر مجزا و با رنگ اصلی و درشت نمایش داده می‌شوند.
            if (times.isNotEmpty()) {
                Text(
                    times,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
            }
            val schedule = buildString {
                append(repeatLabel(r))
                if (r.type == ReminderEntity.TYPE_WATER && r.targetCount > 0) {
                    append(" · هدف ${PersianText.toPersianDigits(r.targetCount.toString())} لیوان")
                }
                if (r.note.isNotBlank()) append(" · ${r.note}")
            }
            if (schedule.isNotBlank()) {
                Text(
                    schedule,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ReminderEntity.typeLabels.getOrElse(r.type) { "شخصی" },
                    style = MaterialTheme.typography.labelSmall,
                    color = typeColors.getOrElse(r.type) { MaterialTheme.colorScheme.primary }
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit, Modifier.size(34.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "ویرایش",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, Modifier.size(34.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "حذف",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ---------------- پیشرفت آب ----------------

@Composable
private fun WaterProgressCard(target: Int, drunk: Int, fa: Boolean, onAddGlass: () -> Unit) {
    SectionCard {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.WaterDrop,
                    contentDescription = null,
                    tint = Color(0xFF2A7BD4),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("آب امروز", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    if (fa) "${PersianText.toPersianDigits(drunk.toString())} از " +
                        PersianText.toPersianDigits(target.toString()) + " لیوان"
                    else "$drunk از $target لیوان",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF2A7BD4)
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (drunk.toFloat() / target.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Color(0xFF2A7BD4),
                trackColor = Color(0xFF2A7BD4).copy(alpha = 0.14f)
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAddGlass) {
                Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("یک لیوان نوشیدم")
            }
        }
    }
}

// ---------------- برنامه امروز ----------------

private data class Slot(val reminderId: Long, val title: String, val type: Int, val minute: Int)

@Composable
private fun TodayScheduleCard(
    reminders: List<ReminderEntity>,
    logs: List<DoseLogEntity>,
    fa: Boolean,
    onToggleSlot: (Long, Int) -> Unit
) {
    val today = LocalDate.now()
    val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }

    val slots = reminders
        .filter { it.enabled && ReminderEngine.occursOnDate(it, today) }
        .flatMap { r -> r.timesList.map { Slot(r.id, r.title, r.type, it) } }
        .sortedBy { it.minute }
    if (slots.isEmpty()) return

    val nextSlot = slots.firstOrNull { s ->
        s.minute >= nowMin && !logs.any { it.reminderId == s.reminderId && it.timeMinute == s.minute }
    }

    SectionCard {
        Column(Modifier.padding(14.dp)) {
            Text("برنامه امروز", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            slots.forEach { slot ->
                val done = logs.any { it.reminderId == slot.reminderId && it.timeMinute == slot.minute }
                val isNext = nextSlot != null && nextSlot == slot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSlot(slot.reminderId, slot.minute) }
                        .padding(vertical = 5.dp)
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(
                                when {
                                    done -> Color(0xFF1E9E55)
                                    isNext -> MaterialTheme.colorScheme.primary
                                    slot.minute < nowMin -> MaterialTheme.colorScheme.outline
                                    else -> typeColors.getOrElse(slot.type) { MaterialTheme.colorScheme.primary }
                                },
                                CircleShape
                            )
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        fmtMinute(slot.minute, fa),
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = if (done) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        slot.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    if (isNext && !done) Text(
                        "بعدی",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "لمس هر ردیف یعنی «انجام شد»؛ دوباره لمس کنید تا برداشته شود.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------- بنرهای مجوز (چک‌لیست انتشار) ----------------

@Composable
private fun PermissionBanners() {
    val context = LocalContext.current

    // مجوز اعلان — اندروید ۱۳ به بالا
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            SectionCard {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("اجازه اعلان لازم است", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "برای دیدن یادآوری‌های آب، دارو، ورزش و کارهای شخصی، اجازه اعلان را بدهید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        if (context is Activity) runCatching {
                            androidx.core.app.ActivityCompat.requestPermissions(
                                context,
                                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                1002
                            )
                        }
                    }) { Text("اجازه می‌دهم") }
                }
            }
        }
    }

    // آلارم دقیق — اندروید ۱۲ به بالا (در ۱۴ سخت‌گیرانه‌تر)
    if (!AlarmScheduler.canExact(context)) {
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("آلارم دقیق غیرفعال است", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "بدون مجوز «آلارم‌ها و یادآورها»، یادآوری‌ها ممکن است با چند دقیقه تأخیر برسند. " +
                        "از تنظیمات اندروید اجازه بدهید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    }.onFailure {
                        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    }
                }) { Text("باز کردن تنظیمات") }
            }
        }
    }

    // بهینه‌سازی باتری — نسخه ۱.۱۱.۱۱: روی گوشی‌های شیائومی/سامسونگ و… بدون استثنا شدن از
    // بهینه‌سازی، آلارم‌ها گاهی اجرا نمی‌شوند و همین دلیل اصلی «اعلان نمی‌آید» است.
    if (!isIgnoringBattery(context)) {
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("بهینه‌سازی باتری مانع اعلان‌هاست", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "برای رسیدن حتماً و سرِ وقت یادآوری‌ها، اذان و اعلان تاریخ، برنامه را از " +
                        "بهینه‌سازی باتری مستثنا کنید. در گوشی‌های شیائومی، گزینه «Autostart» را هم روشن کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { requestIgnoreBattery(context) }) {
                    Text("استثنا کردن از بهینه‌سازی باتری")
                }
            }
        }
    }
}

/** آیا برنامه از بهینه‌سازی باتری مستثنا شده است؟ */
internal fun isIgnoringBattery(context: android.content.Context): Boolean {
    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/** درخواست استثنا شدن از بهینه‌سازی باتری (دیالوگ سیستمی مستقیم؛ در نبودش صفحه تنظیمات) */
internal fun requestIgnoreBattery(context: android.content.Context) {
    val ok = runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    if (!ok) runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
