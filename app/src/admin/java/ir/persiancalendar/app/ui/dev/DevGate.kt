package ir.persiancalendar.app.ui.dev

import androidx.compose.runtime.Composable
import ir.persiancalendar.app.ui.settings.AdminScreen

/**
 * نسخهٔ مدیر (فلاور «admin») — نسخهٔ ۱.۱۱.۳۰.
 * دروازهٔ ورود مخفی (۵ لمس روی «درباره بادخزان») فعال است و پنل مدیریت کامل
 * (بنرها، اطلاعیه‌ها، پشتیبانی، کاربران، انتشار با توکن) همین‌جاست.
 * این فلاور فقط برای دستگاه مدیر ساخته می‌شود؛ APK منتشرشده برای کاربران
 * فلاور «public» است که هیچ‌کدام از این‌ها را ندارد.
 */
object DevGate {
    const val enabled = true
}

@Composable
fun DevPanel(onBack: () -> Unit) = AdminScreen(onBack)
