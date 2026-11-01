package ir.persiancalendar.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.R
import ir.persiancalendar.app.core.ads.AdPolicy
import ir.persiancalendar.app.data.support.AdDismissStore
import ir.persiancalendar.app.data.support.FeedFetcher
import ir.persiancalendar.app.data.support.ImageLoader
import ir.persiancalendar.app.data.support.MainAd
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
 * بخش بنرهای تبلیغات صفحه اصلی — نسخهٔ ۱.۱۱.۳۴ (درخواست کاربر: بنرهای قابل حذف)
 *
 * نسخهٔ ۱.۱۱.۶: نمایش بنرهای پنل مدیریت (پیش‌فرض صفر بنر = بدون تبلیغ).
 * نسخهٔ ۱.۱۱.۳۴:
 *  - جای قرارگیری به «زیر جدول تقویم، بالای کارت‌های مناسبت‌ها» منتقل شد.
 *  - حداکثر ۲ بنر هم‌زمان، انتخاب بر اساس priority و بازهٔ startAt/endAt
 *    و دروازهٔ minVersionCode (AdPolicy — تست‌شده در CI).
 *  - دکمهٔ × با ناحیهٔ لمسی ۴۸dp و دایرهٔ نیمه‌شفاف؛ با لمس، بنر با انیمیشن
 *    کوتاه حذف و شناسه‌اش تا dismissDays فید (پیش‌فرض ۷ روز؛ ۰ = همیشه)
 *    در حافظهٔ محلی پنهان می‌ماند — بعد از بستن و باز کردن اپ برنمی‌گردد.
 *  - کلید قطع اضطراری فید (adsEnabled در config.json امضاشده) + سوئیچ کاربر.
 *  - برچسب «تبلیغ» روی هر بنر (الزام قوانین انتشار) — بنر شبیه محتوای خود
 *    اپ یا کنترل سیستمی نیست.
 *  - لینک فقط https (AdPolicy.isSafeLink) و فقط با LinkOpener/مرورگر باز
 *    می‌شود؛ هیچ WebView و هیچ intent خامی از فید اجرا نمی‌شود.
 *  - اگر تصویر بنر بارگذاری نشود، خود بنر نمایش داده نمی‌شود (جای خالی نمی‌ماند).
 *  - RTL کامل، ارقام فارسی از متن‌های خود اپ، حالت تاریک از تم اپ.
 */
@Composable
fun MainAdsSection(
    modifier: Modifier = Modifier,
    refreshKey: Int = 0,
    userAdsEnabled: Boolean = true // سوئیچ «نمایش تبلیغات» در تنظیمات
) {
    val context = LocalContext.current
    // نسخهٔ ۱.۱۱.۲۱: refreshKey — پس از sync خوراک راه‌دور بنرها، فهرست دوباره خوانده می‌شود
    val allAds = remember(refreshKey) { MainAdBox.active(context) }
    // پنهان‌شده‌های × — پس از هر حذف، dismissedVersion فهرست را دوباره می‌سازد
    var dismissedVersion by remember(refreshKey) { mutableStateOf(0) }
    val dismissed = remember(refreshKey, dismissedVersion) { AdDismissStore.load(context) }

    val ads = remember(refreshKey, dismissedVersion, userAdsEnabled) {
        val versionCode = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        }.getOrDefault(0)
        AdPolicy.select(
            ads = allAds,
            record = { it.toRecord() },
            nowMs = System.currentTimeMillis(),
            versionCode = versionCode,
            feedAdsEnabled = FeedFetcher.configAdsEnabled,
            userAdsEnabled = userAdsEnabled,
            dismissedUntil = dismissed
        )
    }
    if (ads.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        ads.forEach { ad ->
            // پنهانی محلی برای انیمیشن: با × یا خطای تصویر، بنر جمع می‌شود
            var locallyHidden by remember(ad.id, dismissedVersion) { mutableStateOf(false) }
            AnimatedVisibility(
                visible = !locallyHidden,
                exit = shrinkVertically() + fadeOut()
            ) {
                MainAdCard(
                    ad = ad,
                    onDismiss = {
                        // ثبت پنهانی دائمی بر اساس dismissDays فید (۰ = همیشه)
                        val until = AdPolicy.dismissedUntilMs(ad.dismissDays, System.currentTimeMillis())
                        AdDismissStore.put(context, ad.id, until)
                        dismissedVersion++ // از فهرست انتخاب هم حذف شود
                    },
                    onHide = { locallyHidden = true } // فقط این بار (مثلاً خطای تصویر)
                )
            }
            // فاصلهٔ بین بنرها فقط وقتی بنر فعلی نمایان است
            if (!locallyHidden) Spacer(Modifier.height(8.dp))
        }
    }
}

/** تبدیل MainAd به رکورد خالص برای AdPolicy */
private fun MainAd.toRecord() = AdPolicy.AdRecord(
    id = id,
    priority = priority,
    startAt = startAt,
    endAt = endAt,
    dismissDays = dismissDays,
    minVersionCode = minVersionCode
)

/** دکمهٔ × حذف بنر — ناحیهٔ لمسی ۴۸dp با دایرهٔ نیمه‌شفاف (کنتراست روی هر تصویری) */
@Composable
private fun AdDismissButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cd = stringResource(R.string.ad_dismiss_cd)
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
        )
        Icon(
            Icons.Filled.Close,
            contentDescription = null, // توضیح روی ناحیهٔ لمسی ۴۸dp است (TalkBack)
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** یک کارت بنر تبلیغ — نسخهٔ ۱.۱۱.۳۴: تصویری (نسبت ~۳:۱) یا متنی */
@Composable
private fun MainAdCard(ad: MainAd, onDismiss: () -> Unit, onHide: () -> Unit) {
    val context = LocalContext.current
    val adLabel = stringResource(R.string.ad_label)
    val openCd = stringResource(R.string.ad_open_cd, ad.title)

    // نسخهٔ ۱.۱۱.۳۴: لینک فقط با https — لینک ناامن مثل نبودنش رفتار می‌شود (معیار ۳)
    val safeLink = ad.link.takeIf { AdPolicy.isSafeLink(it) } ?: ""

    if (ad.image.isNotBlank() && AdPolicy.isSafeImage(ad.image) && !ad.image.startsWith("data:")) {
        // ---------- بنر تصویری: تصویر تمام‌عرض ~۳:۱ + نشان «تبلیغ» + × ----------
        // تک‌بارگذاری با حالت صریح؛ خطا = پنهان‌شدن کامل بنر (جای خالی نمی‌ماند)
        var state by produceState<ImageResult>(ImageResult.Loading, ad.image) {
            value = withContext(Dispatchers.IO) {
                val bytes = when {
                    ad.image.startsWith("https://") ->
                        ImageLoader.fetchBounded(ad.image, maxBytes = 4 * 1024 * 1024)
                    else -> // مسیر فایل داخلی دستگاه مدیر
                        runCatching { java.io.File(ad.image).readBytes() }.getOrNull()
                }
                bytes?.let {
                    runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                        .getOrNull()
                }?.let { ImageResult.Ready(it) } ?: ImageResult.Failed
            }
        }
        when (val s = state) {
            is ImageResult.Ready -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(enabled = safeLink.isNotBlank()) {
                            LinkOpener.openOrToast(context, safeLink)
                        }
                        .semantics { contentDescription = openCd }
                ) {
                    Image(
                        bitmap = s.bitmap,
                        contentDescription = null, // توضیح معنایی روی Box با title فید است
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // نشان «تبلیغ» — گوشهٔ پایین-ابتدایی (RTL)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            adLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    // دکمهٔ × — گوشهٔ بالا-ابتدایی
                    AdDismissButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart))
                }
            }
            ImageResult.Loading -> {
                // در حال بارگذاری — هنوز چیزی رندر نمی‌شود تا جای خالی نماند
            }
            ImageResult.Failed -> {
                // نسخهٔ ۱.۱۱.۳۴: خطای تصویر → بنر نمایش داده نمی‌شود (معیار کاربر)
                LaunchedEffect(ad.id) { onHide() }
            }
        }
    } else {
        // ---------- بنر متنی: گرادیان رنگ + نشان «تبلیغ» + عنوان/توضیح + دکمه + × ----------
        val (c1, c2) = AdPalettes[ad.colorIndex % AdPalettes.size]
        Surface(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(enabled = safeLink.isNotBlank()) {
                    LinkOpener.openOrToast(context, safeLink)
                }
                .semantics { contentDescription = openCd }
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(c1, c2)))
                        // فاصلهٔ انتهایی بیشتر تا متن زیر × نرود (کلیک ناخواسته ممنوع)
                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 60.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // نشان «تبلیغ»
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.24f)
                        ) {
                            Text(
                                adLabel,
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
                        if (safeLink.isNotBlank()) {
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
                    if (ad.image.isNotBlank() && ad.image.startsWith("data:")) {
                        // تصویر جاسازی‌شدهٔ گالری مدیر در بنر متنی (رفتار قبلی)
                        Spacer(Modifier.width(10.dp))
                        AdImage(
                            source = ad.image,
                            modifier = Modifier
                                .size(86.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }
                }
                // دکمهٔ × — بالا-ابتدایی، بیرون از ناحیهٔ متن
                AdDismissButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                )
            }
        }
    }
}

/** حالت بارگذاری تصویر بنر */
private sealed interface ImageResult {
    data object Loading : ImageResult
    data class Ready(val bitmap: ImageBitmap) : ImageResult
    data object Failed : ImageResult
}

/**
 * بارگذاری تصویر بدون کتابخانه بیرونی — نسخه ۱.۱۱.۶
 * ورودی: مسیر فایل داخلی (تصویر گالری مدیر) یا URL اینترنتی.
 * بارگذاری روی نخ پس‌زمینه انجام می‌شود و در صورت خطا فقط پس‌زمینه خالی می‌ماند.
 * نسخه ۱.۱۱.۷: عمومی شد تا شیت اطلاعیه‌ها هم تصویر اطلاعیه را با همین لودر نشان دهد.
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
