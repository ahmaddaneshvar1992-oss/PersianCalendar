package ir.persiancalendar.app.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import ir.persiancalendar.app.R
import ir.persiancalendar.app.core.ads.DayIconMath
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.PersianText
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * آیکون «دایرهٔ سفید با عدد روز» نوار وضعیت — نسخهٔ ۱.۱۱.۳۵ (درخواست کاربر: کار ۲)
 *
 * درخواست: «آیکون دایره‌ای با عدد درشت»: یک دایرهٔ پر سفید که عدد روز ماه شمسی
 * با فونت ضخیم به‌صورت cutout (شفاف) داخلش حک شده است.
 *
 * چرا سفید + شفاف؟ آیکون نوار وضعیت تک‌رنگ است و سیستم فقط «کانال آلفا» را با
 * رنگ مناسب خودش (روشن/تاریک) رنگ می‌کند؛ پس دایره آلفای کامل (سفید رسم می‌شود)
 * و عدد آلفای صفر (PorterDuff.Mode.CLEAR) می‌گیرد — روی نوار وضعیت روشن و تاریک
 * هر دو خواناست.
 *
 * اندازه‌گیری فونت: بزرگ‌ترین سایزی که عدد ۱ یا ۲ رقمی در جعبهٔ داخل دایره جا شود
 * (DayIconMath.fitFontSize با اندازه‌گیری واقعی Paint).
 * فونت: وزیرمتن Bold (res/font/vazirmatn_bold.ttf — ارقام فارسی دارد)؛ در نبودش
 * DEFAULT_BOLD سیستم.
 *
 * حافظه: بیت‌مپ برای (روز، اندازه، سبک) یک‌بار ساخته و «بازاستفاده» می‌شود؛
 * اعلان همیشگی هر دقیقه بازسازی می‌شود و بدون این کش هر دقیقه یک بیت‌مپ می‌ساخت.
 * بیت‌مپ قبلی recycle نمی‌شود چون هنوز ممکن است به اعلانِ در حال نمایش اشاره
 * کند؛ با تعویض مرجع، GC حافظهٔ native آن را آزاد می‌کند (بیت‌مپ ۹۶×۹۶ = ~۳۶KB).
 *
 * فالبک: اگر هر چیزی در ساخت بیت‌مپ شکست خورد (حافظه، فونت، …)، خروجی null است
 * و OngoingDateNotifier به آیکون vector برنامه برمی‌گردد — بدون کرش.
 * (توجه: «رد شدن بیت‌مپ توسط برخی OEMها» از بیرون قابل تشخیص نیست؛ در آن حالت
 * سیستم خودش آیکون را ساده/فشرده رندر می‌کند — ریسک ثبت‌شده در سند ریسک‌ها.)
 */
object StatusBarDayIcon {

    /** سبک آیکون نوار وضعیت — هم‌ارز تنظیمات */
    const val STYLE_CIRCLE = 0
    const val STYLE_SIMPLE = 1

    /** اندازهٔ مرجع آیکون نوار وضعیت — ۲۴dp؛ برای هر تراکم با density مقیاس می‌شود */
    private const val BASE_DP = 24

    @Volatile private var cachedBitmap: Bitmap? = null
    @Volatile private var cachedKey: String = ""
    @Volatile private var cachedTypeface: Typeface? = null

    /** عدد امروز به شمسی با ارقام فارسی — مثلاً «۱۷» */
    fun todayDayText(today: LocalDate = LocalDate.now()): String {
        val j = CalendarConverter.gregorianToJalali(today.year, today.monthValue, today.dayOfMonth)
        return PersianText.toPersianDigits(j.day.toString())
    }

    /** فونت ضخیم فارسی از منابع پروژه (وزیرمتن Bold) — یک‌بار لود و کش می‌شود */
    private fun boldTypeface(context: Context): Typeface? {
        cachedTypeface?.let { return it }
        return runCatching {
            ResourcesCompat.getFont(context, R.font.vazirmatn_bold)
        }.getOrNull()?.also { cachedTypeface = it }
    }

    /**
     * بیت‌مپ آیکون نوار وضعیت برای امروز — null یعنی فالبک به آیکون vector.
     * style: STYLE_CIRCLE (دایرهٔ پر + عدد حک‌شده) یا STYLE_SIMPLE (فقط عدد).
     */
    fun bitmapFor(
        context: Context,
        style: Int,
        today: LocalDate = LocalDate.now(),
        density: Float = context.resources.displayMetrics.density
    ): Bitmap? = runCatching {
        val text = todayDayText(today)
        val sizePx = (BASE_DP * density).roundToInt().coerceIn(24, 192)
        val key = "$text|$sizePx|$style"
        cachedBitmap?.takeIf { cachedKey == key }?.let { return it }

        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val typeface = boldTypeface(context) ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        if (style == STYLE_SIMPLE) {
            // ---------- سبک ساده (رفتار ۱.۱۱.۱۳): عدد سفید روی زمینهٔ شفاف ----------
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = typeface
                textAlign = Paint.Align.CENTER
                textSize = if (text.length <= 1) sizePx * 0.76f else sizePx * 0.62f
            }
            val baseline = sizePx / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(text, sizePx / 2f, baseline, paint)
        } else {
            // ---------- سبک دایرهٔ درشت (پیش‌فرض): دایرهٔ پر + cutout عدد ----------
            val diameter = DayIconMath.circleDiameter(sizePx)
            val cx = sizePx / 2f
            val cy = sizePx / 2f

            // ۱) دایرهٔ پر سفید (آلفای کامل)
            val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, diameter / 2f, circle)

            // ۲) اندازهٔ فونت عدد — با اندازه‌گیری واقعی این فونت، تا حداکثر درشتی
            val maxW = DayIconMath.glyphMaxWidth(diameter)
            val maxH = DayIconMath.glyphMaxHeight(diameter)
            val probe = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = typeface }
            val textSize = DayIconMath.fitFontSize(
                text = text,
                maxWidth = maxW,
                maxHeight = maxH,
                measureWidth = { t, s ->
                    probe.textSize = s
                    probe.measureText(t)
                },
                measureHeight = { s ->
                    probe.textSize = s
                    (probe.fontMetrics.descent - probe.fontMetrics.ascent)
                }
            )

            // ۳) حک کردن عدد به‌صورت شفاف (CLEAR روی کانال آلفا)
            val carve = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = typeface
                textSize = textSize
                textAlign = Paint.Align.CENTER
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            val baseline = cy - (carve.descent() + carve.ascent()) / 2f
            canvas.drawText(text, cx, baseline, carve)
        }

        cachedBitmap = bmp
        cachedKey = key
        bmp
    }.getOrNull()
}
