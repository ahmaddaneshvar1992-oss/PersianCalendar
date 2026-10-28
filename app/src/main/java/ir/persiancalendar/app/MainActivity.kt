package ir.persiancalendar.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.notifications.OngoingDateNotifier
import ir.persiancalendar.app.ui.MainScreen
import ir.persiancalendar.app.ui.theme.PersianCalendarTheme

/**
 * اکتیویتی اصلی — کل اپلیکیشن با Jetpack Compose و راست‌به‌چپ کامل.
 *
 * نسخه ۱.۳: پشتیبانی از باز شدن مستقیم روی تب مشخص از دکمه‌های
 * «اعلان همیشگی تاریخ» (لیست اعلان‌ها / تنظیم یادآورها) با extra «open_tab».
 * نسخه ۱.۱۱: تب یادآورها (۴) + درخواست مجوز اعلان در اندروید ۱۳ به بالا
 * (یک بار در اولین اجرا — چک‌لیست موتور یادآور عمومی).
 */
class MainActivity : ComponentActivity() {

    /** تب درخواستی از اعلان (۰=تقویم ۱=اوقات شرعی ۲=قبله ۳=رویدادها ۴=یادآورها ۵=تنظیمات؛ ‎-1‎ = هیچ) */
    private val openTabRequest = mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openTabRequest.intValue = tabFromIntent(intent)
        askNotificationPermissionOnce()

        setContent {
            val settingsRepo = AppContainer.settingsRepo(this)
            val settings by settingsRepo.settings.collectAsState(initial = ir.persiancalendar.app.data.prefs.AppSettings())

            PersianCalendarTheme(themeMode = settings.themeMode, accent = settings.accent) {
                // راست‌به‌چپ برای همه صفحه‌ها
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MainScreen(
                            settings = settings,
                            openTabRequest = openTabRequest.intValue
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // اگر اپ باز باشد و از اعلان دوباره آمده باشد (launchMode=singleTop)
        tabFromIntent(intent).takeIf { it >= 0 }?.let { openTabRequest.intValue = it }
    }

    override fun onResume() {
        super.onResume()
        // به‌روزرسانی ویجت هنگام باز شدن اپ
        ir.persiancalendar.app.widget.CalendarWidgetProvider.updateAll(this)
    }

    private fun tabFromIntent(intent: Intent?): Int = when (intent?.getStringExtra(OngoingDateNotifier.EXTRA_OPEN_TAB)) {
        OngoingDateNotifier.TAB_EVENTS -> 3 // ترتیب تب‌ها از نسخه ۱.۱۱: ۰=تقویم ۱=اوقات ۲=قبله ۳=رویدادها ۴=یادآورها ۵=تنظیمات
        OngoingDateNotifier.TAB_PRAYER -> 1
        OngoingDateNotifier.TAB_REMINDERS -> 4
        else -> -1
    }

    /**
     * درخواست مجوز اعلان در اندروید ۱۳ به بالا — فقط یک بار در اولین اجرا
     * (چک‌لیست موتور یادآور: «درخواست مجوز اعلان در اندروید ۱۳ به بالا»).
     * در نسخه‌های قدیمی‌تر مجوز در نص خودکار اعطا می‌شود.
     */
    private fun askNotificationPermissionOnce() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences("pc_flags", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("notif_asked", false)) return
        prefs.edit().putBoolean("notif_asked", true).apply()
        runCatching {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }
}
