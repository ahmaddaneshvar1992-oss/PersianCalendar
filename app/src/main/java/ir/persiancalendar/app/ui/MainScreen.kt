package ir.persiancalendar.app.ui

import android.app.Activity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.ads.AdManager
import ir.persiancalendar.app.core.calendar.JalaliDate
import ir.persiancalendar.app.data.db.EventEntity
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.data.support.AnnouncementBox
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.ui.account.AccountScreen
import ir.persiancalendar.app.ui.calendar.CalendarScreen
import ir.persiancalendar.app.ui.calendar.DayDetailScreen
import ir.persiancalendar.app.ui.components.BannerAd
import ir.persiancalendar.app.ui.events.EventEditorSheet
import ir.persiancalendar.app.ui.events.EventsScreen
import ir.persiancalendar.app.ui.prayer.PrayerScreen
import ir.persiancalendar.app.ui.qibla.QiblaScreen
import ir.persiancalendar.app.ui.reminder.ReminderEditorSheet
import ir.persiancalendar.app.ui.reminder.RemindersScreen
import ir.persiancalendar.app.ui.settings.AdminScreen
import ir.persiancalendar.app.ui.settings.SettingsScreen
import ir.persiancalendar.app.ui.support.SupportScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class MenuItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/** سبز دکمه «+» — مطابق طراحی خواسته‌شده کاربر (نسخه ۱.۳) */
private val FabGreen = Color(0xFF1EA84D)

/**
 * بدنه اصلی اپلیکیشن — نسخه ۱.۱۱:
 *  - صفحه جدید «یادآورها» (نسخه ۱.۱۱): موتور یادآور عمومی آب/دارو/ورزش/شخصی با Room،
 *    AlarmManager دقیق، بازیابی بعد از ری‌استارت و اکشن‌های «انجام شد / بعداً یادآوری کن».
 *  - کارت اوقات شرعی (نوار افقی ۸ وقت مطابق عکس) در «پایین صفحه اصلی تقویم،
 *    پیش از مناسبت‌های ماه»؛ فلش سرصفحه کارت به صفحه اوقات شرعی می‌رود.
 *  - ناوبری با «منوی سه‌نقطه» سمت چپ نوار بالا (⋮): تقویم، اوقات شرعی، قبله‌نما، رویدادها،
 *    یادآورها، تنظیمات، پشتیبانی و پنل مدیریت.
 *  - زنگ اطلاعیه کنار منوی سه‌نقطه (نسخه ۱.۶): خبر و تبلیغ از پنل مدیریت با نقطه قرمز.
 *  - صفحه جدید «پشتیبانی»: کاربر درخواست تبلیغ/مشکل/پیشنهاد برای مدیر می‌فرستد.
 *  - فضای پایین = محدوده تبلیغات (بنرهای تصویری نامحدود چرخشی یا AdMob)
 *  - دکمه سبز «+»: در تقویم/رویدادها ثبت سریع رویداد، در یادآورها ساخت یادآور جدید
 *  - باز شدن مستقیم روی تب از اعلان همیشگی (openTabRequest)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(settings: AppSettings, openTabRequest: Int = -1) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    // روز انتخاب‌شده برای نمای روزانه (null = بسته)
    var dayDetail by remember { mutableStateOf<JalaliDate?>(null) }
    // صفحه حساب کاربری و عضویت ویژه
    var accountVisible by remember { mutableStateOf(false) }
    // پنل مدیریت تبلیغات (نسخه ۱.۲)
    var adminVisible by remember { mutableStateOf(false) }
    // صفحه پشتیبانی (نسخه ۱.۵)
    var supportVisible by remember { mutableStateOf(false) }
    // زنگ اطلاعیه (نسخه ۱.۶) — خبر و تبلیغ از پنل مدیریت
    var annSheetVisible by remember { mutableStateOf(false) }
    var annList by remember { mutableStateOf(AnnouncementBox.active(context)) }
    var annUnseen by remember { mutableStateOf(AnnouncementBox.unseenCount(context)) }
    fun refreshAnnouncements() {
        annList = AnnouncementBox.active(context)
        annUnseen = AnnouncementBox.unseenCount(context)
    }
    // وضعیت عضویت ویژه — برای حذف تبلیغات
    val isPremium by AppContainer.accountRepo(context).isPremium
        .collectAsState(initial = false)
    // ویرایشگر رویداد
    var editorDate by remember { mutableStateOf<JalaliDate?>(null) }
    var editorExisting by remember { mutableStateOf<EventEntity?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    // ویرایشگر یادآور (نسخه ۱.۱۱)
    var remEditorVisible by remember { mutableStateOf(false) }
    var remEditing by remember { mutableStateOf<ir.persiancalendar.app.data.db.ReminderEntity?>(null) }

    // آیتم‌های منوی سه‌نقطه — یادآورها بعد از رویدادها (نسخه ۱.۱۱)
    val menuItems = listOf(
        MenuItem("تقویم", Icons.Filled.CalendarMonth),
        MenuItem("اوقات شرعی", Icons.Filled.AccessTime),
        MenuItem("قبله‌نما", Icons.Filled.Explore),
        MenuItem("رویدادها", Icons.Filled.EventNote),
        MenuItem("یادآورها", Icons.Filled.Alarm),
        MenuItem("تنظیمات", Icons.Filled.Settings),
        MenuItem("پشتیبانی", Icons.AutoMirrored.Filled.Help),
        MenuItem("پنل مدیریت تبلیغات", Icons.Filled.Shield)
    )
    val screenTitles = listOf("تقویم فارسی", "اوقات شرعی", "قبله‌نما", "رویدادها", "یادآورها", "تنظیمات")

    // رویدادها یک بار برای همه صفحه‌ها
    val events by AppContainer.eventDao(context).observeAll().collectAsState(initial = emptyList())
    val holidayRepo = remember { AppContainer.holidayRepo(context) }

    // پیش‌بارگذاری اینترستیشال (فقط وقتی تبلیغات روشن و کاربر غیرویژه باشد)
    LaunchedEffect(Unit, isPremium) {
        val s = AppContainer.settingsRepo(context).settings.first()
        AdManager.syncConfig(
            adsEnabled = s.adsEnabled,
            interstitialEnabled = s.interstitialEnabled,
            testMode = s.adTestMode,
            bannerUnitId = s.bannerUnitId,
            interstitialUnitId = s.interstitialUnitId
        )
        if (!isPremium) AdManager.loadInterstitial(context)
    }

    // باز شدن مستقیم روی تب از دکمه‌های اعلان همیشگی
    LaunchedEffect(openTabRequest) {
        if (openTabRequest in 0..5) {
            dayDetail = null
            accountVisible = false
            adminVisible = false
            supportVisible = false
            selectedTab = openTabRequest
        }
    }

    // به‌روزرسانی اطلاعیه‌ها پس از بستن پنل مدیریت / شیت اطلاعیه (نسخه ۱.۶)
    LaunchedEffect(adminVisible, annSheetVisible, supportVisible) {
        refreshAnnouncements()
    }

    // اینترستیشال هنگام باز کردن جزئیات روز — تناوب قابل تنظیم از پنل مدیریت (نسخه ۱.۲)
    LaunchedEffect(dayDetail) {
        if (dayDetail != null && !isPremium) {
            val repo = AppContainer.settingsRepo(context)
            val s = repo.settings.first()
            AdManager.syncConfig(
                adsEnabled = s.adsEnabled,
                interstitialEnabled = s.interstitialEnabled,
                testMode = s.adTestMode,
                bannerUnitId = s.bannerUnitId,
                interstitialUnitId = s.interstitialUnitId
            )
            val count = s.interstitialCounter + 1
            scope.launch(Dispatchers.IO) { repo.incrementInterstitialCounter() }
            if (s.adsEnabled && s.interstitialEnabled &&
                count % s.interstitialEvery == 0 && context is Activity
            ) {
                AdManager.showInterstitialIfReady(context)
            }
            AdManager.loadInterstitial(context)
        }
    }

    Scaffold(
        // ---------- نوار بالا با منوی سه‌نقطه سمت چپ (نسخه ۱.۵) ----------
        topBar = {
            if (!accountVisible && !adminVisible && !supportVisible && dayDetail == null) {
                var menuOpen by remember { mutableStateOf(false) }
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            screenTitles.getOrElse(selectedTab) { "تقویم فارسی" },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.weight(1f))
                        // زنگ اطلاعیه با نقطه قرمز — خبر و تبلیغ مدیر (نسخه ۱.۶)
                        androidx.compose.material3.IconButton(onClick = {
                            annSheetVisible = true
                            AnnouncementBox.markSeen(context)
                            annUnseen = 0
                        }) {
                            BadgedBox(
                                badge = {
                                    if (annUnseen > 0) {
                                        Badge(containerColor = Color(0xFFE53935))
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Filled.Notifications,
                                    contentDescription = "اطلاعیه‌ها — خبر و تبلیغ"
                                )
                            }
                        }
                        // سه‌نقطه در سمت چپ (در RTL پایان سطر = چپ)
                        androidx.compose.material3.IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "منوی برنامه")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            menuItems.forEachIndexed { index, item ->
                                DropdownMenuItem(
                                    text = { Text(item.title) },
                                    leadingIcon = {
                                        Icon(
                                            item.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        when (item.title) {
                                            "پشتیبانی" -> supportVisible = true
                                            "پنل مدیریت تبلیغات" -> adminVisible = true
                                            else -> selectedTab = index
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        // ---------- محدوده تبلیغات پایین — بنرهای تصویری نامحدود یا AdMob (نسخه ۱.۵) ----------
        bottomBar = {
            if (!accountVisible && !adminVisible && !supportVisible) BannerAd()
        },
        // ---------- دکمه سبز «+» — رویداد جدید در تقویم/رویدادها، یادآور جدید در یادآورها ----------
        floatingActionButton = {
            if (!accountVisible && !adminVisible && !supportVisible && dayDetail == null &&
                (selectedTab == 0 || selectedTab == 3 || selectedTab == 4)
            ) {
                FloatingActionButton(
                    onClick = {
                        if (selectedTab == 4) {
                            remEditing = null
                            remEditorVisible = true
                        } else {
                            editorExisting = null
                            editorDate = null
                            editorVisible = true
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = FabGreen,
                    contentColor = Color.White
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = if (selectedTab == 4) "یادآور جدید" else "ثبت رویداد جدید"
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (adminVisible) {
                // ---------- پنل مدیریت تبلیغات (نسخه ۱.۲) ----------
                AdminScreen(onBack = { adminVisible = false })
            } else if (accountVisible) {
                // ---------- حساب کاربری و عضویت ویژه ----------
                AccountScreen(onBack = { accountVisible = false })
            } else if (supportVisible) {
                // ---------- پشتیبانی — ارسال پیام به مدیر (نسخه ۱.۵) ----------
                SupportScreen(onBack = { supportVisible = false })
            } else if (dayDetail != null) {
                // ---------- نمای روزانه ----------
                DayDetailScreen(
                    jalali = dayDetail!!,
                    events = events,
                    holidayRepo = holidayRepo,
                    persianDigits = settings.persianDigits,
                    showHijri = settings.showHijri,
                    cityIndex = settings.cityIndex,
                    prayerMethod = settings.prayerMethod,
                    onBack = { dayDetail = null },
                    onAddEvent = { d ->
                        editorDate = d
                        editorExisting = null
                        editorVisible = true
                    },
                    onEditEvent = { e ->
                        editorDate = null
                        editorExisting = e
                        editorVisible = true
                    }
                )
            } else {
                when (selectedTab) {
                    0 -> CalendarScreen(
                        events = events,
                        holidayRepo = holidayRepo,
                        settings = settings,
                        persianDigits = settings.persianDigits,
                        showHijri = settings.showHijri,
                        onOpenDay = { dayDetail = it },
                        onOpenPrayer = { selectedTab = 1 }
                    )

                    1 -> PrayerScreen(
                        settings = settings,
                        persianDigits = settings.persianDigits,
                        onSettingsChange = { /* تنظیمات از طریق DataStore جریان می‌یابد */ }
                    )

                    2 -> QiblaScreen(settings = settings)

                    3 -> EventsScreen(
                        events = events,
                        persianDigits = settings.persianDigits,
                        onEdit = { e ->
                            editorExisting = e
                            editorDate = null
                            editorVisible = true
                        },
                        onAdd = {
                            editorExisting = null
                            editorDate = null
                            editorVisible = true
                        },
                        onDelete = { e ->
                            scope.launch(Dispatchers.IO) {
                                AppContainer.eventDao(context).delete(e)
                                ir.persiancalendar.app.notifications.AlarmScheduler
                                    .cancelEventReminder(context, e.id)
                            }
                        }
                    )

                    4 -> RemindersScreen(persianDigits = settings.persianDigits)

                    5 -> SettingsScreen(
                        settings = settings,
                        onOpenAccount = { accountVisible = true },
                        onOpenAdmin = { adminVisible = true }
                    )
                }
            }
        }
    }

    // ---------- شیت اطلاعیه‌ها — زنگ کنار منوی سه‌نقطه (نسخه ۱.۶) ----------
    if (annSheetVisible) {
        ModalBottomSheet(onDismissRequest = { annSheetVisible = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 26.dp)
            ) {
                Text("اطلاعیه‌ها", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                if (annList.isEmpty()) {
                    Text(
                        "اطلاعیه‌ای فعال نیست",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                annList.asReversed().forEach { a ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                a.typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (a.type == "ad") Color(0xFFB8860B)
                                else MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(a.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                a.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (a.link.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(a.link)
                                                )
                                            )
                                        }
                                    }
                                ) { Text("مشاهده") }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- ویرایشگر یادآور (نسخه ۱.۱۱) ----------
    if (remEditorVisible || remEditing != null) {
        ReminderEditorSheet(
            existing = remEditing,
            onDismiss = {
                remEditorVisible = false
                remEditing = null
            }
        )
    }

    // ---------- ویرایشگر رویداد ----------
    if (editorVisible) {
        EventEditorSheet(
            initialDate = editorDate
                ?: editorExisting?.let {
                    ir.persiancalendar.app.core.calendar.CalendarConverter
                        .gregorianToJalali(it.gy, it.gm, it.gd)
                }
                ?: ir.persiancalendar.app.core.calendar.CalendarConverter.let { cc ->
                    val now = java.time.LocalDate.now()
                    cc.gregorianToJalali(now.year, now.monthValue, now.dayOfMonth)
                },
            existing = editorExisting,
            onDismiss = {
                editorVisible = false
                editorExisting = null
                editorDate = null
            }
        )
    }
}
