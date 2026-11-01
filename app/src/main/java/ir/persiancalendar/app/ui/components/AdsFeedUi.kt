package ir.persiancalendar.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.persiancalendar.app.R
import ir.persiancalendar.app.core.ads.AdsFeedPolicy
import ir.persiancalendar.app.data.support.AdsFeed
import ir.persiancalendar.app.data.support.AdsFeed.FeedItem
import ir.persiancalendar.app.data.support.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * بخش «تبلیغات مدیریت‌شده» صفحه اصلی — نسخهٔ ۱.۱۱.۳۵ (درخواست کاربر: کار ۱)
 *
 * دو نوع آیتم از فید امضاشدهٔ feed/main_ads.json:
 *  ۱) کارت شبکه‌ای (tile) — در شبکهٔ دو ستونه، همان ظاهر کارت‌های امکانات:
 *     آیکون سمت راست + عنوان + نشان اختیاری قرمز (badge فید) — بدون ⋮
 *  ۲) بنر تمام‌عرض (banner) — تصویر ~۳:۱ در پایین شبکه — بدون ×
 *
 * الزامات محصول:
 *  - کاربر هیچ راهی برای پنهان‌کردن ندارد: بدون ×، بدون منو، بدون سوئیچ؛
 *    جایگاه/حذف فقط با فید (order فید = ترتیب؛ حذف از فید = حذف از اپ).
 *  - برچسب کوچک و خوانای «تبلیغ» روی هر آیتم (شفافیت + قوانین استور) و
 *    کارت تبلیغی عیناً شبیه قابلیت داخلی اپ نیست.
 *  - لمس → لینک https با مرورگر (LinkOpener بدون WebView/intent خام) یا
 *    مقصد داخلی فقط با کلید لیست‌سفید (AdsFeedPolicy.INTERNAL_TARGETS).
 *  - تصویر با ImageLoader موجود (فقط https، سقف بایت)؛ خطای بارگذاری = آیتم
 *    نمایش داده نمی‌شود و جای خالی نمی‌ماند.
 *  - RTL کامل، ارقام/متن‌های فارسی از خود فید و اپ، حالت تاریک از تم،
 *    TalkBack: contentDescription با واژهٔ «تبلیغ».
 */
@Composable
fun AdsFeedSection(
    modifier: Modifier = Modifier,
    refreshKey: Int = 0,
    onOpenInternal: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val items = remember(refreshKey) {
        val versionCode = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        }.getOrDefault(0)
        AdsFeed.loadDisplay(context, System.currentTimeMillis(), versionCode)
    }
    val tiles = items.filter { it.type == AdsFeedPolicy.TYPE_TILE }
    val banners = items.filter { it.type == AdsFeedPolicy.TYPE_BANNER }
    if (tiles.isEmpty() && banners.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        // ---------- شبکهٔ دو ستونهٔ کارت‌های تبلیغی (tile) ----------
        // داخل Column با verticalScroll هستیم، پس LazyVerticalGrid مجاز نیست؛
        // ردیف‌های دستی دوتایی همان ظاهر شبکه را می‌سازد.
        tiles.chunked(2).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowTiles.forEach { item ->
                    AdTileItem(
                        item = item,
                        onOpenInternal = onOpenInternal,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
        // ---------- بنرهای تمام‌عرض در پایین شبکه ----------
        banners.forEachIndexed { idx, item ->
            AdBannerItem(item = item)
            if (idx < banners.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

/** توضیح TalkBack آیتم تبلیغ — همیشه با واژهٔ «تبلیغ» (الزام معیار ۴) */
@Composable
private fun adOpenText(item: FeedItem): String {
    val label = item.description.ifBlank { item.title }
    return stringResource(R.string.ad_open_cd, label)
}

/** باز کردن مقصد آیتم — خارجی فقط https با مرورگر؛ داخلی فقط لیست‌سفید */
private fun openAdTarget(context: android.content.Context, item: FeedItem, onOpenInternal: (String) -> Unit) {
    when {
        item.targetType == "external" && item.target.isNotBlank() ->
            LinkOpener.openOrToast(context, item.target)
        item.targetType == "internal" && item.target.isNotBlank() ->
            onOpenInternal(item.target)
        // بدون مقصد معتبر = بدون عمل (کارت فقط نمایشی است)
    }
}

/** حالت بارگذاری تصویر آیتم تبلیغ */
private sealed interface AdImageState {
    data object Loading : AdImageState
    data class Ready(val bitmap: ImageBitmap) : AdImageState
    data object Failed : AdImageState
}

/**
 * بارگذاری محدود تصویر https با ImageLoader موجود — خطا = Failed و فراخوان
 * آیتم را کامل پنهان می‌کند (جای خالی نمی‌ماند). سقف: آیکون ۱MB، بنر ۴MB.
 */
@Composable
private fun rememberAdImage(url: String, maxBytes: Int): AdImageState {
    val state by produceState<AdImageState>(AdImageState.Loading, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = ImageLoader.fetchBounded(url, maxBytes) ?: return@runCatching null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()?.let { AdImageState.Ready(it) } ?: AdImageState.Failed
        }
    }
    return state
}

/** کارت تبلیغی شبکه‌ای (tile) — آیکون + عنوان + نشان «جدید» + برچسب «تبلیغ»، بدون ⋮ */
@Composable
private fun AdTileItem(
    item: FeedItem,
    onOpenInternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val adLabel = stringResource(R.string.ad_label)
    val openCd = adOpenText(item)

    // پنهانی محلی فقط برای خطای تصویر (جای خالی نمی‌ماند) — کاربر پنهان نمی‌کند
    var imageFailed by remember(item.id) { mutableStateOf(false) }
    val imageState = rememberAdImage(item.iconUrl, maxBytes = 1024 * 1024)
    LaunchedEffect(imageState) {
        if (imageState is AdImageState.Failed) imageFailed = true
    }

    AnimatedVisibility(
        visible = !imageFailed,
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = item.target.isNotBlank()) {
                    openAdTarget(context, item, onOpenInternal)
                }
                .semantics { contentDescription = openCd }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // آیکون سمت راست (آغاز RTL) + نشان اختیاری قرمز روی گوشهٔ آن
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when (val s = imageState) {
                                is AdImageState.Ready -> Image(
                                    bitmap = s.bitmap,
                                    contentDescription = null, // توضیح معنایی روی کارت است
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                else -> Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                .copy(alpha = 0.18f),
                                            CircleShape
                                        )
                                )
                            }
                        }
                    }
                    if (item.badge.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE53935),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Text(
                                item.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    // برچسب «تبلیغ» — کوچک ولی واضح (شفافیت و قوانین استور)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                    ) {
                        Text(
                            adLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

/** بنر تصویری تمام‌عرض — مثل بنر قبلی ولی بدون × و بدون هیچ راه پنهانی */
@Composable
private fun AdBannerItem(item: FeedItem) {
    val context = LocalContext.current
    val adLabel = stringResource(R.string.ad_label)
    val openCd = adOpenText(item)

    var imageFailed by remember(item.id) { mutableStateOf(false) }
    val imageState = rememberAdImage(item.imageUrl, maxBytes = 4 * 1024 * 1024)
    LaunchedEffect(imageState) {
        if (imageState is AdImageState.Failed) imageFailed = true
    }

    AnimatedVisibility(
        visible = !imageFailed,
        exit = shrinkVertically() + fadeOut(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = item.target.isNotBlank()) {
                    openAdTarget(context, item, onOpenInternal)
                }
                .semantics { contentDescription = openCd }
        ) {
            when (val s = imageState) {
                is AdImageState.Ready -> Image(
                    bitmap = s.bitmap,
                    contentDescription = null, // توضیح معنایی روی Box است
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                AdImageState.Loading -> {
                    // در حال بارگذاری — فقط زمینهٔ ملایم؛ در خطا کل بنر پنهان می‌شود
                }
                AdImageState.Failed -> {
                    // LaunchedEffect بالا بنر را جمع می‌کند
                }
            }
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
        }
    }
}
