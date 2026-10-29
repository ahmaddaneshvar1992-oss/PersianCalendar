package ir.persiancalendar.app.ui.components

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.persiancalendar.app.core.location.LocationHelper

/**
 * فعال‌ساز خودکار سرویس موقعیت (GPS) — نسخه ۱.۱۱۷ (درخواست کاربر مطابق عکس)
 *
 * قبلاً وقتی سرویس موقعیت گوشی خاموش بود، فقط پیام «از تنظیمات اندروید
 * روشنش کنید» نمایش داده می‌شد و کاربر باید خودش مسیر تنظیمات را پیدا
 * می‌کرد. اکنون با لمس دکمهٔ GPS:
 *
 *  ۱) اگر پلی‌سرویس گوگل موجود باشد، دیالوگ سیستمی یک‌لمسی «فعال‌سازی
 *     موقعیت» باز می‌شود؛ با تأیید کاربر سرویس همان‌جا روشن و جست‌وجوی
 *     موقعیت خودکار ادامه می‌یابد — بدون رفتن به تنظیمات.
 *  ۲) روی گوشی‌های بدون پلی‌سرویس، مستقیم صفحهٔ تنظیمات موقعیت گوشی باز
 *     می‌شود و به‌محض بازگشت کاربر (بعد از روشن‌کردن کلید) جست‌وجو به‌طور
 *     خودکار ادامه می‌یابد.
 *
 * استفاده:
 * ```
 * val enableLocationService = rememberGpsServiceEnabler(
 *     onServiceReady = { findGps(...) },
 *     onFailed = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
 * )
 * // در لمس دکمهٔ GPS وقتی سرویس خاموش است:
 * enableLocationService()
 * ```
 *
 * خروجی: لامبدای «روشن‌کردن سرویس و ادامهٔ خودکار» — idempotent.
 */
@Composable
fun rememberGpsServiceEnabler(
    onServiceReady: () -> Unit,
    onFailed: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val activity = context as? Activity

    // ۱) نتیجهٔ دیالوگ سیستمی یک‌لمسی گوگل — RESULT_OK یعنی سرویس روشن شد
    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { st: ActivityResult ->
        if (st.resultCode == Activity.RESULT_OK && LocationHelper.isServiceEnabled(context)) {
            onServiceReady()
        } else {
            onFailed("سرویس موقعیت روشن نشد — لطفاً دوباره تلاش کنید")
        }
    }

    // ۲) بازگشت از صفحهٔ تنظیمات موقعیت (گوشی‌های بدون پلی‌سرویس) —
    //    اگر کاربر کلید را روشن کرده باشد، خودکار ادامه می‌دهیم
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (LocationHelper.isServiceEnabled(context)) {
            onServiceReady()
        } else {
            onFailed("سرویس موقعیت هنوز خاموش است — بعد از روشن‌کردن دوباره لمس کنید")
        }
    }

    return remember(onServiceReady, onFailed) {
        {
            val act = activity
            if (act == null) {
                // context مثل Service قابل اجرای دیالوگ نیست
                onFailed("فعال‌سازی خودکار سرویس موقعیت ممکن نشد")
            } else {
                LocationHelper.tryEnableService(act) { sender ->
                    if (sender != null) {
                        resolutionLauncher.launch(
                            IntentSenderRequest.Builder(sender).build()
                        )
                    } else {
                        settingsLauncher.launch(
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        )
                    }
                }
            }
        }
    }
}
