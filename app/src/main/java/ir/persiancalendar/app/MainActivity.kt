package ir.persiancalendar.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.notifications.NotificationHelper
import ir.persiancalendar.app.notifications.OngoingDateNotifier
import ir.persiancalendar.app.ui.MainScreen
import ir.persiancalendar.app.ui.onboarding.OnboardingScreen
import ir.persiancalendar.app.ui.theme.PersianCalendarTheme
import kotlinx.coroutines.flow.first

/**
 * اکتیویتی اصلی — کل اپلیکیشن با Jetpack Compose و راست‌به‌چپ کامل.
 *
 * نسخه ۱.۳: پشتیبانی از باز شدن مستقیم روی تب مشخص از دکمه‌های
 * «اعلان همیشگی تاریخ» (لیست اعلان‌ها / تنظیم یادآورها) با extra «open_tab».
 * نسخه ۱.۱۱: تب یادآورها (۴).
 * نسخه ۱.۱۱.۴: ویزارد راه‌اندازی اولیه ۵ گامی (افق، تاریخ/زمان، اذان‌ها، اعلان‌ها، حساب)
 * قبل از ورود به برنامه نمایش داده می‌شود؛ درخواست مجوز اعلان از onStart حذف و
 * به گام چهارم ویزارد منتقل شد.
 */
class MainActivity : ComponentActivity() {

    /** تب درخواستی از اعلان (۰=تقویم ۱=اوقات شرعی ۲=قبله ۳=رویدادها ۴=یادآورها ۵=تنظیمات؛ ‎-1‎ = هیچ) */
    private val openTabRequest = mutableIntStateOf(-1)

    /** باز شدن برنامه روی پنل اطلاعیه‌ها — لمس اعلان اطلاعیه (نسخه ۱.۱۱.۱۶) */
    private val openAnnouncements = mutableStateOf(false)

    /**
     * نسخه ۱.۱۱.۱۱ (گزارش کاربر: «اعلان‌ها فعال نیست») — اگر کاربر آنبوردینگ را قبلاً کامل
     * کرده باشد اما مجوز اعلان هنوز داده نشده باشد (مثلاً ارتقا از نسخه‌های قدیمی یا رد شدن
     * در گام چهارم)، بلافاصله پس از اجرا یک بار سیستم همان دیالوگ مجوز اعلان را نشان می‌دهد.
     * اگر کاربر «دیگر نپرس» زده باشد، سیستم بی‌صدا رد می‌کند و آزاردهنده نیست.
     */
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // نسخه ۱.۱۱.۱۲: در صورت رد شدن، کاربر با پیام روشن راهنمایی می‌شود
        // (برای کاربران ارتقایی که راه‌اندازی اولیه را قبلاً کامل کرده‌اند)
        if (!granted) Toast.makeText(
            this,
            "اعلان‌ها فعال نیست — یادآورها، اذان و اعلان تاریخ نمی‌رسند؛ " +
                "از تنظیمات ← اعلان‌ها فعالش کنید",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openTabRequest.intValue = tabFromIntent(intent)
        openAnnouncements.value =
            intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_ANNOUNCEMENTS, false) ?: false

        val settingsRepo = AppContainer.settingsRepo(this)
        // خواندن اولیه همگام برای جلوگیری از فلش ویزارد در هر اجرای مجدد
        val initialSettings = runCatching {
            kotlinx.coroutines.runBlocking { settingsRepo.settings.first() }
        }.getOrDefault(ir.persiancalendar.app.data.prefs.AppSettings())

        // ---------- درخواست یک‌باره مجوز اعلان برای کاربران ارتقایی (نسخه ۱.۱۱.۱۱) ----------
        if (initialSettings.onboardingDone &&
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val settings by settingsRepo.settings.collectAsState(initial = initialSettings)

            PersianCalendarTheme(themeMode = settings.themeMode, accent = settings.accent) {
                // راست‌به‌چپ برای همه صفحه‌ها
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        if (settings.onboardingDone) {
                            MainScreen(
                                settings = settings,
                                openTabRequest = openTabRequest.intValue,
                                openAnnouncements = openAnnouncements.value
                            )
                        } else {
                            // ---------- ویزارد راه‌اندازی اولیه (نسخه ۱.۱۱.۴) ----------
                            OnboardingScreen(settings = settings)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // اگر اپ باز باشد و از اعلان دوباره آمده باشد (launchMode=singleTop)
        tabFromIntent(intent).takeIf { it >= 0 }?.let { openTabRequest.intValue = it }
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_ANNOUNCEMENTS, false) == true) {
            openAnnouncements.value = true
        }
    }

    override fun onResume() {
        super.onResume()
        // نسخه ۱.۱۱.۱۴: اگر اذان در حال پخش است، با باز شدن اپ متوقف شود —
        // دیگر لازم نیست کاربر برنامه را بکشد تا صدا قطع شود
        if (ir.persiancalendar.app.notifications.AzanPlaybackService.isPlaying) {
            ir.persiancalendar.app.notifications.AzanPlaybackService.stop(this)
            Toast.makeText(this, "پخش اذان متوقف شد", Toast.LENGTH_SHORT).show()
        }
        // نسخه ۱.۱۱.۱۴: اعلان همیشگی تاریخ با هر باز شدن اپ دوباره راه می‌افتد
        // (سرویس Foreground — مثل برنامه‌های ADM/VPN همیشه در نوار وضعیت می‌ماند)
        runCatching { OngoingDateNotifier.update(this) }
        // به‌روزرسانی ویجت هنگام باز شدن اپ
        ir.persiancalendar.app.widget.CalendarWidgetProvider.updateAll(this)
    }

    private fun tabFromIntent(intent: Intent?): Int = when (intent?.getStringExtra(OngoingDateNotifier.EXTRA_OPEN_TAB)) {
        OngoingDateNotifier.TAB_EVENTS -> 3 // ترتیب تب‌ها از نسخه ۱.۱۱: ۰=تقویم ۱=اوقات ۲=قبله ۳=رویدادها ۴=یادآورها ۵=تنظیمات
        OngoingDateNotifier.TAB_PRAYER -> 1
        OngoingDateNotifier.TAB_REMINDERS -> 4
        else -> -1
    }
}
