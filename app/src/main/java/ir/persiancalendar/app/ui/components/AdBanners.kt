package ir.persiancalendar.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ابزارهای مشترک تصویر بنر/اطلاعیه — نسخهٔ ۱.۱۱.۳۵
 *
 * بخش «بنرهای تبلیغاتی قابل حذف» نسخهٔ ۱.۱۱.۳۴ (MainAdsSection با دکمهٔ × و
 * AdDismissStore) از این نسخه حذف شد — طبق نیاز جدید محصول، تبلیغات صفحه اصلی
 * «بدون ضربدر» و فقط از راه فید امضاشدهٔ مدیریت می‌شود (AdsFeed + AdsFeedSection
 * در AdsFeedUi.kt). پالت رنگ و AdImage برای پنل مدیریت و شیت اطلاعیه‌ها حفظ شده‌اند.
 *
 * پالت رنگ بنرهای تبلیغ — نسخه ۱.۱۱.۶
 * هر بنر یک رنگ از این فهرست دارد (جفت رنگ برای گرادیان افقی).
 */
val AdPalettes: List<Pair<Color, Color>> = listOf(
    Color(0xFF1E88E5) to Color(0xFF1565C0), // آبی
    Color(0xFF43A047) to Color(0xFF2E7D32), // سبز
    Color(0xFFFB8C00) to Color(0xFFEF6C00), // نارنجی
    Color(0xFF8E24AA) to Color(0xFF6A1B9A), // بنفش
    Color(0xFFE53935) to Color(0xFFB71C1C), // قرمز
    Color(0xFF00897B) to Color(0xFF00695C), // فیروزه‌ای
    Color(0xFF3949AB) to Color(0xFF283593), // نیلی
    Color(0xFFD81B60) to Color(0xFFAD1457)  // سرخابی
)

/** نام پالت‌ها برای فهرست انتخاب رنگ در پنل مدیریت */
val AdColorNames: List<String> = listOf(
    "آبی", "سبز", "نارنجی", "بنفش", "قرمز", "فیروزه‌ای", "نیلی", "سرخابی"
)

/**
 * بارگذاری تصویر بدون کتابخانه بیرونی — نسخه ۱.۱۱.۶
 * ورودی: مسیر فایل داخلی (تصویر گالری مدیر) یا URL اینترنتی یا data URI.
 * بارگذاری روی نخ پس‌زمینه انجام می‌شود و در صورت خطا فقط پس‌زمینه خالی می‌ماند.
 * نسخه ۱.۱۱.۳۴: فقط https/data/فایل محلی — سقف ۴ مگابایت دانلود (ImageLoader).
 */
@Composable
fun AdImage(source: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = when {
                    // نسخهٔ ۱.۱۱.۲۱: عکس جاسازی‌شده در خوراک راه‌دور (بنر گالری مدیر)
                    source.startsWith("data:") -> android.util.Base64.decode(
                        source.substringAfter("base64,"), android.util.Base64.DEFAULT
                    )
                    source.startsWith("https://") -> ImageLoader.fetchBounded(
                        source, maxBytes = 4 * 1024 * 1024
                    )
                    else -> java.io.File(source).readBytes()
                }
                bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier.background(Color.White.copy(alpha = 0.20f)),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
