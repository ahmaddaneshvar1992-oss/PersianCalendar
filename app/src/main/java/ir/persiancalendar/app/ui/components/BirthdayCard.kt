package ir.persiancalendar.app.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import ir.persiancalendar.app.R
import ir.persiancalendar.app.core.birthday.BirthdayCalculator
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.JalaliDate
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.di.AppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime

/**
 * کارت «روز تولد شما» — نسخهٔ ۱.۱۱.۳۴ (درخواست کاربر، مطابق عکس مرجع)
 *
 *  - آیکون ساعت + عنوان با ایموجی کیک؛ سه دایرهٔ «روز / ساعت / دقیقه» با
 *    ارقام فارسی و برچسب زیر هر دایره؛ زیر آن نوار پیشرفت (۰ تا ۱۰۰٪
 *    فاصلهٔ بین تولد قبلی و تولد بعدی).
 *  - منوی سه‌نقطه: «ویرایش تاریخ تولد» و «پنهان کردن کارت».
 *  - اگر تاریخ تولد ثبت نشده باشد، حالت خالی با دکمهٔ «ثبت تاریخ تولد».
 *  - ورودی با همان پیکر شمسی اپ (JalaliDatePickerDialog) و گزینهٔ «بدون سال»
 *    — بدون سال یعنی شمارش فقط روز و ماه را می‌بیند.
 *  - قاعدهٔ ۳۰ اسفند (سال غیرکبیسه ← ۲۹ اسفند) و پیام ویژهٔ تولد
 *    (BirthdayCalculator — تست‌شده در CI).
 *  - شمارنده‌ها فقط وقتی صفحه دیده می‌شود به‌روز می‌شوند (repeatOnLifecycle
 *    STARTED + رسیور تغییر timezone/تاریخ) — هیچ حلقهٔ پس‌زمینهٔ دائمی نیست.
 *  - حریم خصوصی: تاریخ تولد فقط در DataStore محلی می‌ماند (allowBackup=false
 *    است و تغییر نمی‌کند) و هرگز به شبکه/فید/تبلیغ‌دهنده ارسال نمی‌شود.
 */
@Composable
fun BirthdayCard(
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = AppContainer.settingsRepo(context)

    // ---------- لحظهٔ فعلی + تیکر دقیقه (فقط وقتی صفحه دیده می‌شود) ----------
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    val lifecycle = LocalLifecycleOwner.current

    // ۱) تیکر دقیقه — فقط در STARTED؛ وقتی کاربر اپ را می‌بندد به‌طور خودکار لغو می‌شود
    // (هیچ حلقهٔ پس‌زمینهٔ دائمی نیست — معیار کاربر)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = ZonedDateTime.now()
                // تیک در مرز دقیقه — به‌جای چک‌های بی‌وقفه
                delay(1_000L * (60 - ZonedDateTime.now().second) + 250L)
            }
        }
    }

    // ۲) بازمحاسبهٔ فوری روی ON_RESUME (برگشت به اپ، تعویض تاریخ/timezone)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) now = ZonedDateTime.now()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    // ۳) رسیور تغییر ساعت/منطقهٔ زمانی/تاریخ دستگاه (الزام رفتار)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                now = ZonedDateTime.now()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // ---------- وضعیت کارت ----------
    var showEditDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val birth = remember(settings.profileBirth) { parseBirth(settings.profileBirth) }

    Box(modifier = modifier.fillMaxWidth()) {
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                // ---------- سرصفحه: آیکون ساعت + عنوان + منوی سه‌نقطه ----------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.birthday_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.birthday_menu_cd)
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.birthday_edit)) },
                                onClick = {
                                    showMenu = false
                                    showEditDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.birthday_hide)) },
                                onClick = {
                                    showMenu = false
                                    scope.launch { repo.setBirthdayCardVisible(false) }
                                }
                            )
                        }
                    }
                }

                if (birth == null) {
                    // ---------- حالت خالی: تاریخ تولد ثبت نشده ----------
                    Text(
                        stringResource(R.string.birthday_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { showEditDialog = true },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(R.string.birthday_set_button))
                    }
                } else {
                    val state = remember(birth, now) {
                        BirthdayCalculator.compute(now, birth.second, birth.third, birth.first)
                    }
                    if (state == null) {
                        // ورودی خراب — کارت بدون شمارنده (بدون کرش)
                        Text(
                            stringResource(R.string.birthday_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        if (state.isToday) {
                            // ---------- پیام ویژهٔ روز تولد ----------
                            Text(
                                stringResource(R.string.birthday_today_msg),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        // ---------- سه دایره: روز / ساعت / دقیقه ----------
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CountdownCircle(
                                value = PersianText.toPersianDigits(state.days.toString()),
                                label = stringResource(R.string.birthday_days)
                            )
                            CountdownCircle(
                                value = PersianText.toPersianDigits(state.hours.toString()),
                                label = stringResource(R.string.birthday_hours)
                            )
                            CountdownCircle(
                                value = PersianText.toPersianDigits(state.minutes.toString()),
                                label = stringResource(R.string.birthday_minutes)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        // ---------- نوار پیشرفت: چند درصد از فاصلهٔ دو تولد گذشته ----------
                        val pct = (state.progress * 100).toInt().coerceIn(0, 100)
                        LinearProgressIndicator(
                            progress = { state.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.birthday_progress_fmt, PersianText.toPersianDigits(pct.toString())),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ---------- دیالوگ ویرایش تاریخ تولد (با گزینهٔ «بدون سال») ----------
        if (showEditDialog) {
            BirthdayEditDialog(
                initial = birth,
                onDismiss = { showEditDialog = false },
                onSave = { jy, jm, jd ->
                    showEditDialog = false
                    scope.launch {
                        // حریم خصوصی: فقط DataStore محلی — هرگز شبکه
                        repo.setProfileBirth(jy, jm, jd)
                    }
                }
            )
        }
    }
}

/** سه‌تایی سال/ماه/روز تولد — سال ۰ یعنی «بدون سال» */
private typealias BirthTriple = Triple<Int, Int, Int>

/** پارس "jy/jm/jd" — ورودی خراب = null (بدون کرش) */
private fun parseBirth(raw: String): BirthTriple? {
    val parts = raw.trim().split("/")
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    if (m !in 1..12 || d !in 1..31) return null
    if (y != 0 && y !in 1300..1500) return null
    return BirthTriple(y, m, d)
}

/**
 * ثبت/ویرایش تاریخ تولد — همان پیکر شمسی اپ (JalaliDatePickerDialog) با
 * اسلات extraContent برای چک‌باکس «بدون سال» — بدون دیالوگ تودرتو.
 */
@Composable
private fun BirthdayEditDialog(
    initial: BirthTriple?,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int) -> Unit
) {
    val todayJ = remember {
        val g = java.time.LocalDate.now()
        CalendarConverter.gregorianToJalali(g.year, g.monthValue, g.dayOfMonth)
    }
    var noYear by remember { mutableStateOf(initial?.first == 0 || initial == null) }
    var picked by remember {
        mutableStateOf(
            JalaliDate(
                year = initial?.first?.takeIf { it != 0 } ?: todayJ.year,
                month = initial?.second ?: todayJ.month,
                day = initial?.third ?: todayJ.day
            )
        )
    }

    JalaliDatePickerDialog(
        initial = picked,
        onDismiss = onDismiss,
        // بازهٔ سال: تا ۱۱۰ سال قبل — تولد در آینده بی‌معناست
        minYear = todayJ.year - 110,
        maxYear = todayJ.year,
        title = stringResource(R.string.birthday_edit),
        onSelected = {
            // سالِ «بدون سال» = ۰ ذخیره می‌شود تا شمارش فقط روز/ماه را ببیند
            onSave(if (noYear) 0 else it.year, it.month, it.day)
        },
        extraContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(
                    checked = noYear,
                    onCheckedChange = { noYear = it }
                )
                Text(stringResource(R.string.birthday_no_year))
            }
        }
    )
}

/** یک دایرهٔ شمارنده با عدد فارسی داخل و برچسب زیر (مطابق عکس مرجع) */
@Composable
private fun CountdownCircle(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
