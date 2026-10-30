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
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.data.backup.BackupManager
import ir.persiancalendar.app.data.prefs.AccentPalette
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.data.prefs.ThemeMode
import ir.persiancalendar.app.di.AppContainer
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
fun SettingsScreen(settings: AppSettings, onOpenAccount: () -> Unit, onOpenAdmin: () -> Unit) {
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

        // ---------- درباره ----------
        // نسخهٔ ۱.۱۱.۲۵ (درخواست کاربر: «میخوام پنل مدیریت رو کسی جز من نبینه»):
        // کارت «پنل مدیریت» حذف شد؛ ورود مخفی: ۵ لمس پشت‌سرهم (با فاصلهٔ کمتر از
        // ۴ ثانیه) روی همین عنوان «درباره بادخزان» — هیچ اثری برای دیگران ندارد و
        // بعد از آن هم صفحهٔ رمز پنل باز می‌شود.
        var adminTaps by remember { mutableStateOf(0) }
        var adminTapsAt by remember { mutableStateOf(0L) }
        SectionCard {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "درباره بادخزان",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable {
                        val now = System.currentTimeMillis()
                        adminTaps = if (now - adminTapsAt < 4_000L) adminTaps + 1 else 1
                        adminTapsAt = now
                        if (adminTaps >= 5) {
                            adminTaps = 0
                            onOpenAdmin()
                        }
                    }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "بادخزان (تقویم فارسی) — نسخه ۱.۱۱.۲۹ — تقویم کامل شمسی با ویزارد راه‌اندازی اولیه ۵ گامی (شهر، تاریخ و زمان، اذان‌ها، اعلان‌ها، حساب)، مناسبت‌های رسمی، رویدادها و یادآوری، موتور یادآور عمومی (آب، دارو، ورزش و کارهای شخصی) با چند نوبت ساعت، روزهای هفته، بازه تاریخ شروع تا پایان، ثبت انجام‌شدن هر نوبت، پیشرفت آب روزانه، اکشن‌های «انجام شد» و «بعداً یادآوری کن» در اعلان و بازیابی خودکار آلارم‌ها پس از ری‌استارت گوشی؛ در ویرایشگر یادآور هنگام خطای عنوان، فرم خودکار به فیلد عنوان برمی‌گردد و آن را پررنگ می‌کند (۱.۱۱.۱)؛ از نسخه ۱.۱۱.۲ منوی سه‌نقطه و زنگ اطلاعیه و سرصفحه همه صفحه‌ها با فاصله استاندارد زیر نوار وضعیت سیستم قرار می‌گیرند؛ از نسخه ۱.۱۱.۳ لمس ساده روزهای تقویم هیچ صفحه‌ای باز نمی‌کند (جزئیات روز فقط با نگه‌داشتن انگشت روی روز) و دکمه برگشت گوشی در تنظیمات و سایر صفحه‌ها به‌جای خروج از برنامه، به صفحه اصلی تقویم برمی‌گردد؛ از نسخه ۱.۱۱.۴ مکان‌یاب GPS با همه سرویس‌های موقعیت و پیام خطای روشن کار می‌کند و قبله‌نما نشانگر کعبه و هم‌راستایی سبز دارد؛ از نسخه ۱.۱۱.۵ منوی سه‌نقطه به شکل پنل زیبا با گوشه‌های گرد و آیکن باز می‌شود و «حساب کاربری» و «اشتراک‌گذاری برنامه» هم دارد، تبلیغات بنری و اینترستیشال به‌طور کامل حذف شده‌اند (حجم نصبی هم کمتر شد)، صفحه حساب کاربری و عضویت ویژه روی همه گوشی‌ها بدون بسته‌شدن برنامه باز می‌شود و باگ «سرویس موقعیت در دسترس نیست» مکان‌یاب رفع شد؛ از نسخه ۱.۱۱.۶ می‌توانید از پنل مدیریت، بنرهای تبلیغ اختصاصی خودتان را — هر چند تا که بخواهید، با تصویر (گالری یا URL)، رنگ، دکمه و لینک دلخواه و ترتیب نمایش — در صفحه اصلی تقویم، پیش از کارت اوقات شرعی بگذارید (اگر بنری نسازید صفحه اصلی بدون هیچ تبلیغی می‌ماند) و پنل منوی سه‌نقطه دقیقاً کنار خود دکمه ⋮ در سمت چپ گوشی باز می‌شود؛ از نسخه ۱.۱۱.۷ اطلاعیه‌ها عکس هم دارند (از پنل مدیریت، عکس از گالری یا URL)، با لمس هر روز تقویم مناسبت‌های همان روز در همان فرم «مناسبت‌های این ماه» زیر تقویم دیده می‌شود، دکمه‌های میان‌بر «قبله‌نما» و «یادآورها» کنار زنگ اطلاعیه در نوار بالا هستند، صدای اذان هنگام رسیدن نوتیف با سرویس پخش اختصاصی حتی با بسته‌بودن برنامه کامل پخش می‌شود و کنار انتخاب «اذان دلخواه» دکمه پخش نمونه اذان هست؛ از نسخه ۱.۱۱.۸ سه نمونه صوتی ساده حذف شدند و دو اذان کامل و واقعی — رحیم مؤذن‌زاده اردبیلی و حسن رضائیان — جایگزین شدند، هم پیش‌نمایش در صفحه اوقات شرعی و هم پخش کامل هنگام رسیدن اعلان اذان (تنظیم قدیمی کاربران هم به اذان معادل منتقل می‌شود)؛ از نسخه ۱.۱۱.۹ نام برنامه به «بادخزان» تغییر کرد و روزهای قمر در عقرب سال ۱۴۰۵ (بر اساس جدول ورود و خروج ماه به برج عقرب) در «مناسبت‌های این ماه» و «مناسبت‌های این روز» ثبت می‌شود — روز ورود با ساعت دقیق ورود، روزهای میانی با «قمر در عقرب» و روز خروج با ساعت دقیق خروج؛ اوقات شرعی شهرستان‌ها با انتخاب استانی/شهرستانی و مکان‌یاب، کارت اوقات شرعی مطابق عکس در پایین صفحه اصلی تقویم و پیش از مناسبت‌های ماه (نوار افقی ۸ وقت + بخش اذان + جدول ماهانه)، دکمه بازگشت به امروز، زنگ اطلاعیه کنار منوی سه‌نقطه با خبر و تبلیغ از پنل مدیریت، اذان دلخواه، قبله‌نما با GPS، منوی سه‌نقطه، بخش پشتیبانی با پیام و ایمیل به مدیر و ویجت صفحه اصلی.",
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
