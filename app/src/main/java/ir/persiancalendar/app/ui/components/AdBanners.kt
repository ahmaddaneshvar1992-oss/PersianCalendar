package ir.persiancalendar.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.data.support.MainAd
import ir.persiancalendar.app.data.support.MainAdBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
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
 * بخش بنرهای تبلیغات صفحه اصلی — نسخه ۱.۱۱.۶ (درخواست کاربر)
 *
 * هر چند بنر که مدیر در «پنل مدیریت → تبلیغات صفحه اصلی» ساخته باشد، همین‌جا
 * به‌ترتیب به‌صورت کارت‌های تودرتو نمایش داده می‌شود (مطابق نمونه کاربر).
 * اگر هیچ بنری فعال نباشد، این بخش کلاً نمایش داده نمی‌شود — صفحه بدون تبلیغ است.
 */
@Composable
fun MainAdsSection(modifier: Modifier = Modifier, refreshKey: Int = 0) {
    val context = LocalContext.current
    // نسخهٔ ۱.۱۱.۲۱: refreshKey — پس از sync خوراک راه‌دور بنرها، فهرست دوباره خوانده می‌شود
    val ads = remember(refreshKey) { MainAdBox.active(context) }
    if (ads.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        ads.forEach { ad ->
            MainAdCard(ad = ad)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** یک کارت بنر تبلیغ — گرادیان رنگ + نشان «تبلیغ» + عنوان/توضیح + دکمه + تصویر اختیاری */
@Composable
private fun MainAdCard(ad: MainAd) {
    val context = LocalContext.current
    val (c1, c2) = AdPalettes[ad.colorIndex % AdPalettes.size]

    Surface(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                if (ad.link.isNotBlank()) {
                    // نسخهٔ ۱.۱۱.۲۰: باز کردن امن لینک (لینک بدون https هم کار می‌کند)
                    ir.persiancalendar.app.ui.components.LinkOpener.openOrToast(context, ad.link)
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(c1, c2)))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // نشان «تبلیغ»
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.24f)
                ) {
                    Text(
                        "تبلیغ",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    ad.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2
                )
                if (ad.body.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        ad.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 2
                    )
                }
                if (ad.link.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White
                    ) {
                        Text(
                            ad.cta.ifBlank { "مشاهده" },
                            style = MaterialTheme.typography.labelMedium,
                            color = c1,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            if (ad.image.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                AdImage(
                    source = ad.image,
                    modifier = Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }
        }
    }
}

/**
 * بارگذاری تصویر بدون کتابخانه بیرونی — نسخه ۱.۱۱.۶
 * ورودی: مسیر فایل داخلی (تصویر گالری که مدیر انتخاب کرده) یا URL اینترنتی.
 * بارگذاری روی نخ پس‌زمینه انجام می‌شود و در صورت خطا فقط پس‌زمینه خالی می‌ماند.
 * نسخه ۱.۱۱.۷: عمومی شد تا شیت اطلاعیه‌ها هم تصویر اطلاعیه را با همین لودر نشان دهد.
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
                    source.startsWith("http") -> java.net.URL(source).readBytes()
                    else -> java.io.File(source).readBytes()
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
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
