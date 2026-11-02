package ir.persiancalendar.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.data.backup.BackupManager
import ir.persiancalendar.app.data.prefs.AccentPalette
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.data.prefs.ThemeMode
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.notifications.StatusBarDayIcon
import ir.persiancalendar.app.notifications.AlarmScheduler
import ir.persiancalendar.app.ui.components.SectionCard
import ir.persiancalendar.app.ui.components.rememberFileWriters
import ir.persiancalendar.app.ui.theme.EventDotColors
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * صفحه تنظیمات: حساب کاربری، تم، رنگ، ارقام، بکاپ محلی و درباره.
 */
@Composable
fun SettingsScreen(settings: AppSettings, onOpenAccount: () -> Unit, onDevPanel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = AppContainer.settingsRepo(context)

    val (exportFile, importFile) = rememberFileWriters(
        onExport = { uri ->
            scope.launch {
                val events = AppContainer.eventDao(context).getAll()
                val json = BackupManager.exportToJson(events)
                val ok = BackupManager.writeToFile(context, uri, json)
                Toast.makeText(
                    context,
                    if (ok) "پشتیبان با موفقیت ذخیره شد" else "ذخیره پشتیبان ناموفق بود",
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        onImport = { uri ->
            scope.launch {
                val json = BackupManager.readFromFile(context, uri)
                if (json == null) {
                    Toast.makeText(context, "خواندن فایل ناموفق بود", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                runCatching {
                    val events = BackupManager.importFromJson(json)
                    val dao = AppContainer.eventDao(context)
                    dao.deleteAll()
                    events.forEach { dao.insert(it) }
                    AlarmScheduler.rescheduleAllEvents(context)
                    Toast.makeText(context, "${events.size} رویداد بازیابی شد", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "فایل پشتیبان معتبر نیست", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // ---------- حساب کاربری ----------
        val accountRepo = AppContainer.accountRepo(context)
        val accountUser by accountRepo.sessionUser().collectAsState(initial = null)
        val isPremium by accountRepo.isPremium.collectAsState(initial = false)
        SectionCard {
            Row(
                modifier = Modifier
                    .padding(14.dp)
                    .clickable { onOpenAccount() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("حساب کاربری و عضویت ویژه", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            isPremium -> "عضو ویژه — سپاس از حمایت شما"
                            accountUser != null -> accountUser?.name ?: ""
                            else -> "ثبت‌نام کاربر جدید / ورود"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("›", style = MaterialTheme.typography.titleLarge)
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- اعلان همیشگی تاریخ (نسخهٔ ۱.۳) ----------
        val settingsRepo = AppContainer.settingsRepo(context)
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("اعلان همیشگی تاریخ", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "نمایش دائمی تاریخ امروز در نوار وضعیت، با دکمه‌های سریع رویدادها و یادآورها",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.ongoingDateNotif,
                        onCheckedChange = { v ->
                            scope.launch {
                                settingsRepo.setOngoingDateNotif(v)
                                if (v) {
                                    ir.persiancalendar.app.notifications.OngoingDateNotifier.update(context)
                                } else {
                                    ir.persiancalendar.app.notifications.OngoingDateNotifier.cancel(context)
                                }
                            }
                        }
                    )
                }
                // ---------- سبک آیکون نوار وضعیت — نسخهٔ ۱.۱۱.۳۵ (درخواست کاربر: کار ۲) ----------
                // پیش‌فرض: دایرهٔ درشت — دایرهٔ پر سفید که عدد روز ماه با فونت ضخیم
                // وزیرمتن به‌صورت cutout داخلش حک شده (آیکون نوار وضعیت فقط آلفا دارد).
                if (settings.ongoingDateNotif) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(ir.persiancalendar.app.R.string.statusbar_icon_style_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(ir.persiancalendar.app.R.string.statusbar_icon_style_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.statusBarIconStyle == StatusBarDayIcon.STYLE_CIRCLE,
                            onClick = {
                                scope.launch {
                                    settingsRepo.setStatusBarIconStyle(StatusBarDayIcon.STYLE_CIRCLE)
                                    ir.persiancalendar.app.notifications.OngoingDateNotifier.update(context)
                                }
                            },
                            label = { Text(stringResource(ir.persiancalendar.app.R.string.statusbar_icon_style_circle)) }
                        )
                        FilterChip(
                            selected = settings.statusBarIconStyle == StatusBarDayIcon.STYLE_SIMPLE,
                            onClick = {
                                scope.launch {
                                    settingsRepo.setStatusBarIconStyle(StatusBarDayIcon.STYLE_SIMPLE)
                                    ir.persiancalendar.app.notifications.OngoingDateNotifier.update(context)
                                }
                            },
                            label = { Text(stringResource(ir.persiancalendar.app.R.string.statusbar_icon_style_simple)) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- تم ----------
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("تم اپلیکیشن", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { scope.launch { repo.setThemeMode(mode) } },
                            label = { Text(mode.label) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("رنگ اصلی", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccentPalette.entries.forEachIndexed { idx, palette ->
                        val color = EventDotColors[idx % EventDotColors.size]
                        val selected = settings.accent == palette
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(color, CircleShape)
                                .clickable { scope.launch { repo.setAccent(palette) } },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        AccentPalette.entries
                            .firstOrNull { it == settings.accent }?.label ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- نمایش ----------
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("نمایش", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ارقام فارسی", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "نمایش اعداد با ارقام فارسی",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.persianDigits,
                        onCheckedChange = { scope.launch { repo.setPersianDigits(it) } }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("نمایش تاریخ قمری", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "در صفحه جزئیات روز نشان داده شود",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.showHijri,
                        onCheckedChange = { scope.launch { repo.setShowHijri(it) } }
                    )
                }
                // ---------- نمای ماهانهٔ بهبودیافته — نسخهٔ ۱.۱۱.۳۹ ----------
                // هر سوئیچ فوری اعمال می‌شود (جریان DataStore → بازسازی خودکار)
                // و بعد از بستن اپ هم ماندگار است.
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(ir.persiancalendar.app.R.string.month_cell_show_gregorian_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(ir.persiancalendar.app.R.string.month_cell_show_gregorian_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.showGregorianInCells,
                        onCheckedChange = { scope.launch { repo.setShowGregorianInCells(it) } }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(ir.persiancalendar.app.R.string.month_cell_show_hijri_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(ir.persiancalendar.app.R.string.month_cell_show_hijri_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.showHijriInCells,
                        onCheckedChange = { scope.launch { repo.setShowHijriInCells(it) } }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(ir.persiancalendar.app.R.string.month_cell_show_week_number_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(ir.persiancalendar.app.R.string.month_cell_show_week_number_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.showWeekNumber,
                        onCheckedChange = { scope.launch { repo.setShowWeekNumber(it) } }
                    )
                }
                // ---------- حالت تراکم — نسخهٔ ۱.۱۱.۴۰ (کار ۵) ----------
                // رنگ پس‌زمینهٔ هر روز بر اساس تعداد رویدادها (۴ سطح)؛
                // فوری اعمال می‌شود و ماندگار است.
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(ir.persiancalendar.app.R.string.month_density_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(ir.persiancalendar.app.R.string.month_density_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.densityMode,
                        onCheckedChange = { scope.launch { repo.setDensityMode(it) } }
                    )
                }
                // ---------- شروع هفته — نسخهٔ ۱.۱۱.۴۰ (کار ۴) ----------
                // نوار نمای هفتگی از این روز شروع می‌شود (شنبه پیش‌فرض / یکشنبه)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(ir.persiancalendar.app.R.string.week_start_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.weekStartSaturday,
                                onClick = { scope.launch { repo.setWeekStartSaturday(true) } },
                                label = {
                                    Text(stringResource(ir.persiancalendar.app.R.string.week_start_saturday))
                                }
                            )
                            FilterChip(
                                selected = !settings.weekStartSaturday,
                                onClick = { scope.launch { repo.setWeekStartSaturday(false) } },
                                label = {
                                    Text(stringResource(ir.persiancalendar.app.R.string.week_start_sunday))
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- بکاپ ----------
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("پشتیبان‌گیری محلی", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "رویدادها را در یک فایل ذخیره کنید و هر زمان روی گوشی دیگر بازیابی کنید (بدون نیاز به اینترنت)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = {
                        val d = LocalDate.now()
                        exportFile("persian-calendar-backup-${d.year}-${d.monthValue}-${d.dayOfMonth}.json")
                    }) { Text("خروجی فایل پشتیبان") }
                    TextButton(onClick = { importFile() }) { Text("بازیابی از فایل") }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- راه‌اندازی اولیه (نسخه ۱.۱۱.۴) ----------
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("راه‌اندازی اولیه", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "اگر می‌خواهید ۵ گام آغازین (شهر، تاریخ و زمان، اذان‌ها، اعلان‌ها، حساب) دوباره نمایش داده شود، دکمه زیر را بزنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    scope.launch { repo.setOnboardingDone(false) }
                }) { Text("اجرای دوباره راه‌اندازی اولیه") }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- کارت تولد — نسخهٔ ۱.۱۱.۳۵ ----------
        // (سوئیچ «نمایش تبلیغات» حذف شد: تبلیغات صفحه اصلی از این نسخه فقط با فید
        // امضاشدهٔ مدیر کنترل می‌شود و کاربر راهی برای پنهان‌کردن ندارد — بدون ×)
        var showBatteryGuide by remember { mutableStateOf(false) }
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("امکانات صفحه اصلی", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(ir.persiancalendar.app.R.string.birthday_visible_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(ir.persiancalendar.app.R.string.birthday_visible_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.birthdayCardVisible,
                        onCheckedChange = { scope.launch { repo.setBirthdayCardVisible(it) } }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- راهنمای باتری — نسخهٔ ۱.۱۱.۳۴ (نکتهٔ کاربر: شیائومی/هواوی/سامسونگ برنامه‌های پس‌زمینه را می‌کشند) ----------
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text("راهنمای باتری", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "اگر یادآورها و اذان سر وقت نمی‌آیند، گوشی‌های شیائومی، هواوی و سامسونگ برنامه را در پس‌زمینه متوقف می‌کنند — راهنمای کوتاه را ببینید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    TextButton(onClick = { showBatteryGuide = true }) { Text("راهنمای گوشی‌ها") }
                    TextButton(onClick = {
                        // بدون مجوز — فقط باز کردن فهرست بهینه‌سازی باتری سیستم
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }) { Text("تنظیمات باتری") }
                }
            }
        }

        if (showBatteryGuide) {
            BatteryGuideDialog(onDismiss = { showBatteryGuide = false })
        }

        Spacer(Modifier.height(10.dp))

        // ---------- درباره ----------
        // نسخهٔ ۱.۱۱.۲۵ (درخواست کاربر: «میخوام پنل مدیریت رو کسی جز من نبینه»):
        // کارت «پنل مدیریت» حذف شد؛ ورود مخفی: ۵ لمس پشت‌سرهم (با فاصلهٔ کمتر از
        // ۴ ثانیه) روی همین عنوان «درباره بادخزان» — هیچ اثری برای دیگران ندارد و
        // بعد از آن هم صفحهٔ رمز پنل باز می‌شود.
        var devTaps by remember { mutableStateOf(0) }
        var devTapsAt by remember { mutableStateOf(0L) }
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "درباره بادخزان",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable {
                        val now = System.currentTimeMillis()
                        devTaps = if (now - devTapsAt < 4_000L) devTaps + 1 else 1
                        devTapsAt = now
                        // نسخهٔ ۱.۱۱.۳۰: ورود مخفی فقط در فلاور admin (نسخهٔ مدیر) کار می‌کند؛
                        // در نسخهٔ عمومی DevGate.enabled=false و ۵ لمس هیچ کاری نمی‌کند
                        if (devTaps >= 5 && ir.persiancalendar.app.ui.dev.DevGate.enabled) {
                            devTaps = 0
                            onDevPanel()
                        }
                    }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "بادخزان (تقویم فارسی) — نسخه ۱.۱۱.۴۰ — چهار قابلیت تازهٔ تقویم: ① پنل جزئیات روز: ضربه روی هر روز تقویم، پنلی از پایین باز می‌کند با تاریخ کامل شمسی/میلادی/قمری، مناسبت‌ها، رویدادها و اوقات شرعی همان روز و دکمهٔ «افزودن رویداد» — کشیدن به بالا پنل تمام‌صفحه و کشیدن به پایین آن را می‌بندد؛ نگه‌داشتن انگشت روی روز، مستقیم «رویداد جدید» با همان تاریخ باز می‌کند؛ ② نمای هفتگی نو: نوار هفت‌روزه (شنبه تا جمعه یا طبق تنظیم «شروع هفته») با سوایپ افقی بین هفته‌ها و زیر آن agenda رویدادها برای روز انتخاب‌شده یا کل هفته — رویدادهای تمام‌روز بالای فهرست و رویدادهای زمان‌دار به ترتیب ساعت؛ ③ حالت تراکم (اختیاری): رنگ پس‌زمینهٔ هر روز بر اساس تعداد رویدادها در چهار سطح از کم‌رنگ تا پررنگ، بدون آسیب به خوانایی عدد؛ ④ تنظیمات تازهٔ «شروع هفته» (شنبه/یکشنبه) و «حالت تراکم رویدادها» در بخش نمایش — همه فوری و ماندگار؛ (از ۱.۱۱.۳۹:) ضربه روی کارت آبی سربرگ تقویم («برای جابه‌جایی سریع ضربه بزنید») انتخابگر سریع سال و ماه را باز می‌کند — فلش سال قبل/بعد، لمس عدد سال برای تایپ مستقیم با ارقام فارسی یا لاتین (سال بیرون از بازهٔ تقویم غیرفعال است و ورودی نامعتبر فقط پیام خطا می‌دهد)، شبکهٔ ۳×۴ ماه‌ها با هایلایت ماه جاری و نقطهٔ نشانگر «ماه امروز»، دکمهٔ «امروز» و توضیح کامل TalkBack برای هر ماه؛ نمای ماهانه هم بهبود یافت: کشیدن افقی نرم بین ماه‌ها با بارگذاری تنبل و کش، ارتفاع ثابت جدول برای ماه‌های ۵ و ۶ ردیفی (بدون پرش)، عدد روز درشت‌تر، حداکثر ۳ نقطهٔ رنگی رویداد زیر عدد (نقطهٔ سوم در ازدحام کم‌رنگ می‌شود)، تعطیل رسمی قرمز، جمعه با رنگ تعطیلات، امروز با حلقه، روز انتخاب‌شده با دایرهٔ پر، دکمهٔ شناور «امروز» فقط وقتی از ماه جاری دور شده‌اید و سه تنظیم تازه در بخش «نمایش»: «نمایش تاریخ میلادی در خانه‌ها»، «نمایش تاریخ قمری در خانه‌ها» و «نمایش شماره هفته سال» (همه فوری و ماندگار)؛ (از ۱.۱۱.۳۵:) تبلیغات صفحه اصلی به مدل مدیریت‌شده رفت: کارت‌های شبکه‌ای (آیکون + عنوان + نشان «جدید») و بنر تصویری تمام‌عرض زیر تقویم، هر دو فقط با فید امضاشدهٔ مدیر اضافه/ویرایش/حذف می‌شوند و کاربر هیچ راهی برای پنهان‌کردنشان ندارد (بدون ×، بدون منو، بدون سوئیچ)؛ فهرست با هر فید معتبر کامل جایگزین می‌شود و ضدمقاومت بازپخش (sequence) جلوی برگشت تبلیغ حذف‌شده را می‌گیرد؛ روی هر کارت تبلیغی برچسب کوچک «تبلیغ» هست و لمس، فقط لینک https یا مقصدهای داخلی لیست‌سفید را باز می‌کند؛ آیکون عدد روز در نوار وضعیت دوتکه شد: دایرهٔ پر سفید که عدد روز ماه با فونت ضخیم وزیرمتن به‌صورت شفاف داخلش حک شده و هر شب نیمه‌شب عوض می‌شود (سبک «دایرهٔ درشت/ساده» در تنظیمات اعلان همیشگی)؛ کارت «روز تولد شما» با شمارش معکوس روز/ساعت/دقیقه و نوار پیشرفت (قاعدهٔ ۳۰ اسفند در سال غیرکبیسه، گزینهٔ «بدون سال»، تازه‌سازی دقیقه‌ای فقط وقتی صفحه دیده می‌شود، تاریخ تولد فقط محلی و هرگز به شبکه نمی‌رود)؛ صفحهٔ خودکار اذان اکنون پیش‌فرض خاموش است (نکتهٔ Full-Screen Intent اندروید ۱۴+ در بازبینی استور) و از تنظیمات اوقات شرعی روشن می‌شود؛ راهنمای باتری شیائومی/هواوی/سامسونگ هم در تنظیمات اضافه شد؛ و صفحهٔ اذان به سبک اوقات شرعی: هم‌زمان با شروع پخش اذان، صفحهٔ اوقات شرعی (آسمان با خورشید و ابر و نقش اسلیمی، برچسب «اذان … به افق شهر»، نوار سبز همهٔ وقت‌ها با وعدهٔ در حال اذان پررنگ، تاریخ روز، ابزارهای کاربردی و ذکر «یا ذالجلال والاکرام» با دکمهٔ سبز پخش/توقف) به‌صورت تمام‌صفحه بالای قفل باز می‌شود حتی با اپ بسته یا صفحهٔ خاموش؛ اگر گوشی روشن و باز و اپ دیگری جلوی صفحه باشد فقط اعلان سرصفحه‌ای با دکمهٔ «باز کردن» می‌آید مگر «نمایش تمام‌صفحه» را در صفحهٔ اوقات شرعی روشن کنید؛ در حالت بی‌صدا/مزاحم نشوید صدایی پخش نمی‌شود و فقط اعلانِ بی‌صدا می‌آید؛ Audio Focus رعایت می‌شود (تماس = توقف اذان)، با پایان اذان اعلان‌ها کامل جمع می‌شوند، باز شدن اپ دیگر اذان را قطع نمی‌کند (تنظیم «توقف اذان با خروج از صفحه» اختیاری است) و تغییر شهر یا روش محاسبه بلافاصله اذان‌ها را دوباره زمان‌بندی می‌کند؛ و تاریخ قمری با تقویم رسمی هم‌تراز شد (گزارش امروز: ۲۰ جمادی‌الاول ۱۴۴۸) با تقویم رسمی هم‌تراز شد (گزارش امروز: ۲۰ جمادی‌الاول ۱۴۴۸) و از نسخه پیش اعلان همیشگی تاریخ به سبک باد صبا است: تاریخ شمسی درشت و پهن، و زیر آن خط آبی با تاریخ میلادی و قمری به فارسی؛ و مهم‌تر از آن، «تست خودکار تبدیل تاریخ» در خط ساخت اجرا می‌شود که روز اول یک باگ واقعی پیدا کرد — محاسبهٔ سال کبیسه غلط بود و تاریخ‌های دی/بهمن/اسفندِ بعد از ۱ ژانویهٔ بعضی سال‌ها (از جمله زمستان ۱۴۰۳) یک روز جلوتر نمایش داده می‌شد — اصلاح شد و از این پس هیچ نسخه‌ای با تبدیل غلط منتشر نمی‌شود؛ تقویم کامل شمسی با ویزارد راه‌اندازی اولیه ۵ گامی (شهر، تاریخ و زمان، اذان‌ها، اعلان‌ها، حساب)، مناسبت‌های رسمی، رویدادها و یادآوری، موتور یادآور عمومی (آب، دارو، ورزش و کارهای شخصی) با چند نوبت ساعت، روزهای هفته، بازه تاریخ شروع تا پایان، ثبت انجام‌شدن هر نوبت، پیشرفت آب روزانه، اکشن‌های «انجام شد» و «بعداً یادآوری کن» در اعلان و بازیابی خودکار آلارم‌ها پس از ری‌استارت گوشی؛ در ویرایشگر یادآور هنگام خطای عنوان، فرم خودکار به فیلد عنوان برمی‌گردد و آن را پررنگ می‌کند (۱.۱۱.۱)؛ از نسخه ۱.۱۱.۲ منوی سه‌نقطه و زنگ اطلاعیه و سرصفحه همه صفحه‌ها با فاصله استاندارد زیر نوار وضعیت سیستم قرار می‌گیرند؛ از نسخه ۱.۱۱.۳ لمس ساده روزهای تقویم هیچ صفحه‌ای باز نمی‌کند (جزئیات روز فقط با نگه‌داشتن انگشت روی روز) و دکمه برگشت گوشی در تنظیمات و سایر صفحه‌ها به‌جای خروج از برنامه، به صفحه اصلی تقویم برمی‌گردد؛ از نسخه ۱.۱۱.۴ مکان‌یاب GPS با همه سرویس‌های موقعیت و پیام خطای روشن کار می‌کند و قبله‌نما نشانگر کعبه و هم‌راستایی سبز دارد؛ از نسخه ۱.۱۱.۵ منوی سه‌نقطه به شکل پنل زیبا با گوشه‌های گرد و آیکن باز می‌شود و «حساب کاربری» و «اشتراک‌گذاری برنامه» هم دارد، تبلیغات بنری و اینترستیشال به‌طور کامل حذف شده‌اند (حجم نصبی هم کمتر شد)، صفحه حساب کاربری و عضویت ویژه روی همه گوشی‌ها بدون بسته‌شدن برنامه باز می‌شود و باگ «سرویس موقعیت در دسترس نیست» مکان‌یاب رفع شد؛ از نسخه ۱.۱۱.۶ می‌توانید از پنل مدیریت، بنرهای تبلیغ اختصاصی خودتان را — هر چند تا که بخواهید، با تصویر (گالری یا URL)، رنگ، دکمه و لینک دلخواه و ترتیب نمایش — در صفحه اصلی تقویم، پیش از کارت اوقات شرعی بگذارید (اگر بنری نسازید صفحه اصلی بدون هیچ تبلیغی می‌ماند) و پنل منوی سه‌نقطه دقیقاً کنار خود دکمه ⋮ در سمت چپ گوشی باز می‌شود؛ از نسخه ۱.۱۱.۷ اطلاعیه‌ها عکس هم دارند (از پنل مدیریت، عکس از گالری یا URL)، با لمس هر روز تقویم مناسبت‌های همان روز در همان فرم «مناسبت‌های این ماه» زیر تقویم دیده می‌شود، دکمه‌های میان‌بر «قبله‌نما» و «یادآورها» کنار زنگ اطلاعیه در نوار بالا هستند، صدای اذان هنگام رسیدن نوتیف با سرویس پخش اختصاصی حتی با بسته‌بودن برنامه کامل پخش می‌شود و کنار انتخاب «اذان دلخواه» دکمه پخش نمونه اذان هست؛ از نسخه ۱.۱۱.۸ سه نمونه صوتی ساده حذف شدند و دو اذان کامل و واقعی — رحیم مؤذن‌زاده اردبیلی و حسن رضائیان — جایگزین شدند، هم پیش‌نمایش در صفحه اوقات شرعی و هم پخش کامل هنگام رسیدن اعلان اذان (تنظیم قدیمی کاربران هم به اذان معادل منتقل می‌شود)؛ از نسخه ۱.۱۱.۹ نام برنامه به «بادخزان» تغییر کرد و روزهای قمر در عقرب سال ۱۴۰۵ (بر اساس جدول ورود و خروج ماه به برج عقرب) در «مناسبت‌های این ماه» و «مناسبت‌های این روز» ثبت می‌شود — روز ورود با ساعت دقیق ورود، روزهای میانی با «قمر در عقرب» و روز خروج با ساعت دقیق خروج؛ اوقات شرعی شهرستان‌ها با انتخاب استانی/شهرستانی و مکان‌یاب، کارت اوقات شرعی مطابق عکس در پایین صفحه اصلی تقویم و پیش از مناسبت‌های ماه (نوار افقی ۸ وقت + بخش اذان + جدول ماهانه)، دکمه بازگشت به امروز، زنگ اطلاعیه کنار منوی سه‌نقطه با خبر و تبلیغ از پنل مدیریت، اذان دلخواه، قبله‌نما با GPS، منوی سه‌نقطه، بخش پشتیبانی با پیام و ایمیل به مدیر و ویجت صفحه اصلی.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "فونت وزیرمتن با مجوز آزاد SIL OFL 1.1\nالگوریتم تقویم جلالی: Borkowski (jalaali-js)\nتقویم قمری: الگوریتم جدولی (حدود یک روز اختلاف ممکن است)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * راهنمای کوتاه «تنظیم باتری» برای گوشی‌هایی که برنامه‌های پس‌زمینه را
 * می‌کشند — نسخهٔ ۱.۱۱.۳۴ (درخواست کاربر: شیائومی/هواوی/سامسونگ).
 */
@Composable
private fun BatteryGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("فهمیدم") } },
        title = { Text("راهنمای باتری گوشی‌ها") },
        text = {
            Text(
                "شیائومی (MIUI):\n" +
                    "تنظیمات ← برنامه‌ها ← مدیریت برنامه‌ها ← بادخزان ← «شروع خودکار» را روشن کنید و «صرفه‌جویی باتری» را روی «بدون محدودیت» بگذارید؛ در صفحهٔ Recent هم برنامه را قفل کنید (لمس طولانی ← قفل).\n\n" +
                    "هواوی / هونر (EMUI):\n" +
                    "تنظیمات ← باتری ← راه‌اندازی برنامه‌ها ← بادخزان ← «مدیریت خودکار» را خاموش و هر سه گزینهٔ اجازه (شروع خودکار، اجرای غیرمستقیم، اجرا در پس‌زمینه) را روشن کنید.\n\n" +
                    "سامسونگ (One UI):\n" +
                    "تنظیمات ← مراقبت از دستگاه ← باتری ← جزئیات استفاده ← بادخزان ← «محتوای بهینه‌نشده» را انتخاب کنید؛ در «حالت خواب» هم برنامه را از فهرست بیرون بکشید.\n\n" +
                    "همهٔ گوشی‌ها: دکمهٔ «تنظیمات باتری» در همین صفحه فهرست بهینه‌سازی سیستم را باز می‌کند و «بادخزان» را از بهینه‌سازی مستثنا کنید."
            )
        }
    )
}
