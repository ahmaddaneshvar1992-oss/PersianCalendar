package ir.persiancalendar.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import ir.persiancalendar.app.core.calendar.JalaliDate
import ir.persiancalendar.app.data.db.EventEntity
import ir.persiancalendar.app.data.prefs.AppSettings
import ir.persiancalendar.app.data.support.AnnouncementBox
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.ui.components.AdImage
import ir.persiancalendar.app.ui.account.AccountScreen
import ir.persiancalendar.app.ui.calendar.CalendarScreen
import ir.persiancalendar.app.ui.calendar.DayDetailScreen
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
import kotlinx.coroutines.launch

/** آیتم منوی سه‌نقطه — نسخه ۱.۱۱.۵: هر آیتم tab یا action مشخص دارد */
private data class MenuItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tab: Int = -1,
    val action: String? = null
)

/** سبز دکمه «+» — مطابق طراحی خواسته‌شده کاربر (نسخه ۱.۳) */
private val FabGreen = Color(0xFF1EA84D)

/**
 * بدنه اصلی اپلیکیشن — نسخه ۱.۱۱.۷:
 *  - اطلاعیه‌ها عکس دارند: مدیر از پنل مدیریت عکس (گالری یا URL) ضمیمه می‌کند و
 *    شیت اطلاعیه‌ها همان عکس را نشان می‌دهد (درخواست کاربر).
 *  - دکمه‌های میان‌بر «قبله‌نما» و «یادآورها» کنار زنگ اطلاعیه در نوار بالا (درخواست کاربر).
 *  - پنل منوی سه‌نقطه دقیقاً کنار خود دکمه ⋮ (سمت چپ گوشی) باز می‌شود —
 *    جای‌گذاری قطعی با PopupPositionProvider اختصاصی، مستقل از جهت چیدمان (گزارش کاربر).
 *  - منوی سه‌نقطه (⋮) به سبک «پنل زیبا» باز می‌شود: کارت گوشه‌گرد با آیکن دایره‌ای،
 *    بخش‌بندی (حساب/اشتراک‌گذاری، صفحه‌ها، ابزارها) و هایلایت تب فعلی — مطابق نمونه کاربر.
 *  - آیتم‌های منو: «حساب کاربری» و «اشتراک‌گذاری برنامه».
 *  - نسخه ۱.۱۱.۵: تبلیغات بنری AdMob و اینترستیشال حذف شدند؛ از نسخه ۱.۱۱.۶ بنرهای
 *    اختصاصی مدیر (پنل مدیریت ← تبلیغات صفحه اصلی) در صفحه اصلی تقویم نمایش داده می‌شوند.
 *  - صفحه «یادآورها» (نسخه ۱.۱۱): موتور یادآور عمومی آب/دارو/ورزش/شخصی با Room،
 *    AlarmManager دقیق، بازیابی بعد از ری‌استارت و اکشن‌های «انجام شد / بعداً یادآوری کن».
 *  - کارت اوقات شرعی (نوار افقی ۸ وقت) پایین صفحه اصلی تقویم، پیش از مناسبت‌های ماه.
 *  - زنگ اطلاعیه کنار منوی سه‌نقطه (نسخه ۱.۶): خبر و تبلیغ از پنل مدیریت با نقطه قرمز.
 *  - دکمه سبز «+»: در تقویم/رویدادها ثبت سریع رویداد، در یادآورها ساخت یادآور جدید
 *  - باز شدن مستقیم روی تب از اعلان همیشگی (openTabRequest)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    settings: AppSettings,
    openTabRequest: Int = -1,
    openAnnouncements: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    // روز انتخاب‌شده برای نمای روزانه (null = بسته)
    var dayDetail by remember { mutableStateOf<JalaliDate?>(null) }
    // صفحه حساب کاربری و عضویت ویژه
    var accountVisible by remember { mutableStateOf(false) }
    // پنل مدیریت تبلیغات (نسخه ۱.۲)
    var adminVisible by remember { mutableStateOf(false) }
    // نسخهٔ ۱.۱۱.۲۱: کلید بازسازی بنرها پس از sync خوراک راه‌دور یا تغییر پنل مدیریت
    var adsVersion by remember { mutableStateOf(0) }
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
    // ویرایشگر رویداد
    var editorDate by remember { mutableStateOf<JalaliDate?>(null) }
    var editorExisting by remember { mutableStateOf<EventEntity?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    // ویرایشگر یادآور (نسخه ۱.۱۱)
    var remEditorVisible by remember { mutableStateOf(false) }
    var remEditing by remember { mutableStateOf<ir.persiancalendar.app.data.db.ReminderEntity?>(null) }

    // آیتم‌های منوی سه‌نقطه — نسخه ۱.۱۱.۵: حساب کاربری و اشتراک‌گذاری در بخش اول
    val menuItems = listOf(
        MenuItem("حساب کاربری", Icons.Filled.Person, action = "account"),
        MenuItem("اشتراک‌گذاری برنامه", Icons.Filled.Share, action = "share"),
        MenuItem("تقویم", Icons.Filled.CalendarMonth, tab = 0),
        MenuItem("اوقات شرعی", Icons.Filled.AccessTime, tab = 1),
        MenuItem("قبله‌نما", Icons.Filled.Explore, tab = 2),
        MenuItem("رویدادها", Icons.Filled.EventNote, tab = 3),
        MenuItem("یادآورها", Icons.Filled.Alarm, tab = 4),
        MenuItem("تنظیمات", Icons.Filled.Settings, tab = 5),
        MenuItem("پشتیبانی", Icons.AutoMirrored.Filled.Help, action = "support")
    )
    // نسخهٔ ۱.۱۱.۲۵ (درخواست کاربر: «میخوام پنل مدیریت رو کسی جز من نبینه»):
    // آیتم «پنل مدیریت» از منو حذف شد؛ ورود فقط از راه مخفی در تنظیمات است
    // (۵ لمس پشت‌سرهم روی عنوان «درباره بادخزان») + همان رمز پنل.
    val screenTitles = listOf("بادخزان", "اوقات شرعی", "قبله‌نما", "رویدادها", "یادآورها", "تنظیمات")

    // رویدادها یک بار برای همه صفحه‌ها
    val events by AppContainer.eventDao(context).observeAll().collectAsState(initial = emptyList())
    val holidayRepo = remember { AppContainer.holidayRepo(context) }

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

    // باز شدن مستقیم روی پنل اطلاعیه‌ها — لمس اعلان اطلاعیه (نسخه ۱.۱۱.۱۶)
    LaunchedEffect(openAnnouncements) {
        if (openAnnouncements) {
            annSheetVisible = true
            AnnouncementBox.markSeen(context)
            annUnseen = 0
        }
    }

    // آخرین زمان بررسی خوراک — هم برای باز شدن سرد و هم برای ON_RESUME (۱.۱۱.۲۵)
    var lastFeedSyncAt by remember { mutableStateOf(System.currentTimeMillis()) }

    // بررسی خوراک اطلاعیه‌های همگانی مدیر — در هر باز شدن برنامه (نسخه ۱.۱۱.۱۶)
    LaunchedEffect(Unit) {
        lastFeedSyncAt = System.currentTimeMillis()
        val (freshAnn, freshAds) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val a = ir.persiancalendar.app.data.support.AnnouncementFeed.sync(context)
            val b = ir.persiancalendar.app.data.support.MainAdFeed.sync(context)
            a to b
        }
        if (freshAnn > 0) refreshAnnouncements()
        if (freshAds > 0) adsVersion++
    }

    // ---------- نسخهٔ ۱.۱۱.۲۵ (گزارش کاربر: «تبلیغات دیر آپلود میشه») ----------
    // قبلاً خوراک فقط در باز شدن سرد برنامه گرفته می‌شد و کاربری که برنامه را
    // باز نگه می‌داشت هرگز بنر تازه نمی‌دید. اکنون با هر برگشت به برنامه
    // (ON_RESUME) اگر بیش از ۱۰ دقیقه از آخرین بررسی گذشته باشد، خوراک اطلاعیه
    // و بنر دوباره گرفته می‌شود تا تغییرها در چند ثانیه دیده شوند.
    val hostActivity = context as? androidx.activity.ComponentActivity
    DisposableEffect(hostActivity) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val now = System.currentTimeMillis()
                if (now - lastFeedSyncAt > 10 * 60_000L) {
                    lastFeedSyncAt = now
                    scope.launch(Dispatchers.IO) {
                        val a = ir.persiancalendar.app.data.support.AnnouncementFeed.sync(context)
                        val b = ir.persiancalendar.app.data.support.MainAdFeed.sync(context)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (a > 0) refreshAnnouncements()
                            if (b > 0) adsVersion++
                        }
                    }
                }
            }
        }
        hostActivity?.lifecycle?.addObserver(obs)
        onDispose { hostActivity?.lifecycle?.removeObserver(obs) }
    }

    // به‌روزرسانی اطلاعیه‌ها پس از بستن پنل مدیریت / شیت اطلاعیه (نسخه ۱.۶)
    LaunchedEffect(adminVisible, annSheetVisible, supportVisible) {
        refreshAnnouncements()
        // نسخهٔ ۱.۱۱.۲۱: بنرها هم بعد از بستن پنل مدیریت دوباره خوانده می‌شوند
        adsVersion++
    }

    // ---------- نسخه ۱.۱۱.۳ (گزارش کاربر): دکمه برگشت گوشی ----------
    // در تنظیمات، سایر تب‌ها و صفحه‌های تمام‌صفحه، دکمه برگشت به جای خروج از برنامه
    // به صفحه اصلی تقویم برمی‌گردد؛ فقط روی خود صفحه اصلی تقویم، برگشت برنامه را می‌بندد
    BackHandler(
        enabled = selectedTab != 0 || dayDetail != null ||
                accountVisible || adminVisible || supportVisible
    ) {
        when {
            dayDetail != null -> dayDetail = null
            accountVisible -> accountVisible = false
            adminVisible -> adminVisible = false
            supportVisible -> supportVisible = false
            else -> selectedTab = 0
        }
    }

    Scaffold(
        // ---------- نوار بالا با منوی سه‌نقطه سمت چپ (نسخه ۱.۵) ----------
        topBar = {
            if (!accountVisible && !adminVisible && !supportVisible && dayDetail == null) {
                var menuOpen by remember { mutableStateOf(false) }
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    // نسخه ۱.۱۱.۲: statusBarsPadding — دکمه سه‌نقطه و زنگ اطلاعیه و عنوان
                    // دیگر زیر نوار وضعیت سیستم (ساعت/باتری) نمی‌روند (گزارش کاربر)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            screenTitles.getOrElse(selectedTab) { "بادخزان" },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.weight(1f))
                        // ---------- میان‌برهای کنار زنگ اطلاعیه — نسخه ۱.۱۱.۷ (درخواست کاربر) ----------
                        // قبله‌نما و یادآورها با یک لمس، کنار زنگ اطلاعیه در نوار بالا
                        androidx.compose.material3.IconButton(onClick = { selectedTab = 2 }) {
                            Icon(
                                Icons.Filled.Explore,
                                contentDescription = "قبله‌نما",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        androidx.compose.material3.IconButton(onClick = { selectedTab = 4 }) {
                            Icon(
                                Icons.Filled.Alarm,
                                contentDescription = "یادآورها",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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
                        // نسخه ۱.۱۱.۶: پنل منو داخل Box همین دکمه با Popup اختصاصی باز می‌شود
                        // تا دقیقاً زیر خود دکمه ⋮ (سمت چپ گوشی) و کنارش قرار بگیرد
                        Box {
                            androidx.compose.material3.IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "منوی برنامه")
                            }
                            if (menuOpen) {
                                val density = LocalDensity.current
                                val marginPx = with(density) { 10.dp.roundToPx() }
                                val gapPx = with(density) { 4.dp.roundToPx() }
                                Popup(
                                    popupPositionProvider = remember(marginPx, gapPx) {
                                        LeftAnchoredMenuProvider(marginPx, gapPx)
                                    },
                                    onDismissRequest = { menuOpen = false },
                                    properties = PopupProperties(focusable = true)
                                ) {
                                    // پنل زیبا — کارت گوشه‌گرد با آیکن دایره‌ای و بخش‌بندی
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 8.dp,
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier.widthIn(min = 232.dp, max = 312.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .heightIn(max = 460.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            menuItems.forEachIndexed { index, item ->
                                                // جداکننده بخش‌ها: پیش از «تقویم» و پیش از «تنظیمات»
                                                if (index == 2 || index == 7) {
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(
                                                            horizontal = 10.dp,
                                                            vertical = 4.dp
                                                        ),
                                                        color = MaterialTheme.colorScheme
                                                            .outlineVariant.copy(alpha = 0.5f)
                                                    )
                                                }
                                                MenuRow(
                                                    title = item.title,
                                                    icon = item.icon,
                                                    active = item.tab >= 0 && item.tab == selectedTab,
                                                    primary = MaterialTheme.colorScheme.primary
                                                ) {
                                                    menuOpen = false
                                                    when (item.action) {
                                                        "account" -> accountVisible = true
                                                        "share" -> shareApp(context)
                                                        "support" -> supportVisible = true
                                                        "admin" -> adminVisible = true
                                                        else -> if (item.tab >= 0) selectedTab = item.tab
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        // ---------- نسخه ۱.۱۱.۵: نوار پایین تبلیغات حذف شد — پایین صفحه محتواست ----------
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
                        onOpenPrayer = { selectedTab = 1 },
                        adsRefresh = adsVersion
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
                            // ---------- عکس اطلاعیه — نسخه ۱.۱۱.۷ (درخواست کاربر) ----------
                            if (a.image.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                // نسخهٔ ۱.۱۱.۲۰: لمس بنر اطلاعیه هم لینک را باز می‌کند
                                AdImage(
                                    source = a.image,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(170.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .then(
                                            if (a.link.isNotBlank()) Modifier.clickable {
                                                ir.persiancalendar.app.ui.components.LinkOpener
                                                    .openOrToast(context, a.link)
                                            } else Modifier
                                        )
                                )
                            }
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
                                        // نسخهٔ ۱.۱۱.۲۰: باز کردن امن لینک (بدون https هم کار می‌کند)
                                        ir.persiancalendar.app.ui.components.LinkOpener
                                            .openOrToast(context, a.link)
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

/**
 * جای‌گذار پنل منو — نسخه ۱.۱۱.۶ (گزارش کاربر)
 *
 * پنل همیشه دقیقاً زیر دکمه سه‌نقطه و هم‌تراز لبه دکمه (سمت چپ گوشی) باز می‌شود؛
 * برخلاف DropdownMenu پیش‌فرض، جهت چیدمان (RTL) در محل باز شدن اثر ندارد و
 * اگر پنل از عرض صفحه بزرگ‌تر باشد به داخل صفحه محدود می‌شود.
 */
private class LeftAnchoredMenuProvider(
    private val marginPx: Int,
    private val gapPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        // لبه شروع پنل = لبه دکمه سه‌نقطه؛ خروج از صفحه با حاشیه امن محدود می‌شود
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = anchorBounds.left.coerceIn(marginPx, maxX)
        val maxY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(0)
        val y = (anchorBounds.bottom + gapPx).coerceIn(0, maxY)
        return IntOffset(x, y)
    }
}

/**
 * ردیف منوی سه‌نقطه — سبک پنل نمونه کاربر: آیکن در دایره ملایم + عنوان؛
 * تب فعلی با رنگ اصلی هایلایت می‌شود (نسخه ۱.۱۱.۵)
 */
@Composable
private fun MenuRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    primary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    if (active) primary.copy(alpha = 0.16f) else primary.copy(alpha = 0.09f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** اشتراک‌گذاری برنامه از منوی سه‌نقطه — با لانچر امن (نسخه ۱.۱۱.۵) */
private fun shareApp(context: android.content.Context) {
    val ok = runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "بادخزان (تقویم فارسی) — تقویم کامل شمسی با اوقات شرعی، قبله‌نما و یادآور\n" +
                        "https://github.com/ahmaddaneshvar1992-oss/PersianCalendar/releases"
            )
        }
        context.startActivity(Intent.createChooser(send, "اشتراک‌گذاری برنامه"))
    }.isSuccess
    if (!ok) {
        Toast.makeText(context, "برنامه‌ای برای اشتراک‌گذاری پیدا نشد", Toast.LENGTH_SHORT).show()
    }
}
