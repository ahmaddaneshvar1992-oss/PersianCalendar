package ir.persiancalendar.app.core.ads

/**
 * ریاضی خالص «رسم عدد روز در آیکون نوار وضعیت» — نسخهٔ ۱.۱۱.۳۵ (درخواست کاربر: کار ۲)
 *
 * آیکون نوار وضعیت تک‌رنگ است و سیستم فقط کانال آلفا را رنگ می‌کند؛ پس دایره
 * سفید رسم می‌شود و عدد با xfermode CLEAR از آن «حک» می‌شود (شفاف می‌شود).
 * این شیء فقط محاسبات اندازه است تا بدون دستگاه اندرویدی با تست واحد
 * (DayIconMathTest در CI) راستی‌آزمایی شود؛ نقاشی واقعی در StatusBarDayIcon.
 */
object DayIconMath {

    /**
     * قطر دایره برای بوم مربعی sizePx — حاشیهٔ خیلی کوچک (٪۱٫۵ هر سمت) تا
     * لبهٔ دایره در رندرهای لبه‌به‌لبهٔ برخی OEMها بریده نشود.
     */
    fun circleDiameter(sizePx: Int): Float = sizePx * (1f - 2f * CIRCLE_INSET_FRACTION)

    /**
     * حداکثر جعبهٔ متن عدد داخل دایره: عرض ≈ ٪۵۸ قطر، ارتفاع ≈ ٪۵۴ قطر
     * (ارتفاع کمتر از عرض است تا بالای «۱» و دُم «۲» داخل دایره بماند).
     */
    fun glyphMaxWidth(diameter: Float): Float = diameter * 0.58f
    fun glyphMaxHeight(diameter: Float): Float = diameter * 0.54f

    /**
     * انتخاب اندازهٔ فونت: بزرگ‌ترین textSize که عرض متن (با measure در همان
     * سایز) و ارتفاع گلیف در جعبه جا شود.
     *
     * عرض متن و متریک‌های فونت هر دو نسبت به textSize «خطی» اند، پس جواب دقیق
     * حل بسته است: سایز = min(بودجهٔ عرض ÷ عرضِ واحد، بودجهٔ ارتفاع ÷ ارتفاعِ واحد)
     * (جست‌وجوی دودویی با شروعِ محتاط تا ۲۰٪ کوچک‌تر از بهینه می‌ماند — این‌جا حذف شد.)
     */
    fun fitFontSize(
        text: String,
        maxWidth: Float,
        maxHeight: Float,
        measureWidth: (String, Float) -> Float,
        measureHeight: (Float) -> Float
    ): Float {
        if (text.isEmpty() || maxWidth <= 0f || maxHeight <= 0f) return 1f
        val perUnitW = measureWidth(text, 1f)   // عرض متن در سایز ۱ — کسری از سایز
        val perUnitH = measureHeight(1f)        // ارتفاع گلیف در سایز ۱
        val capByW = if (perUnitW > 0f) maxWidth / perUnitW else Float.MAX_VALUE
        val capByH = if (perUnitH > 0f) maxHeight / perUnitH else Float.MAX_VALUE
        val best = minOf(capByW, capByH)
        return if (best.isInfinite() || best.isNaN() || best <= 0f) 1f else best
    }

    /**
     * اندازهٔ فونت عدد روز (۱ یا ۲ رقم فارسی) — نام مستعار راحت برای فراخوان اندروید.
     * برای رقم‌های بیشتر هم درست کار می‌کند (فونت کوچک‌تر می‌شود).
     */
    fun dayGlyphFontSize(dayText: String, diameter: Float, densityScale: Float = 1f): Float =
        fitFontSize(
            text = dayText,
            maxWidth = glyphMaxWidth(diameter),
            maxHeight = glyphMaxHeight(diameter),
            measureWidth = { t, size -> t.length * size * PROPORTIONAL_DIGIT_WIDTH / densityScale },
            measureHeight = { size -> size * DIGIT_HEIGHT_RATIO }
        )

    /**
     * تقریب عرض/ارتفاع نسبی رقم‌های فارسی وزیرمتن برای تخمین سریع (روی دستگاه
     * اندازهٔ واقعی Paint معیار است؛ این تقریب برای پیش‌فرض‌های سیستمی امن است —
     * رقم فارسی Naskh ≈ ۰٫۵۶ برابر em پهن و ≈ ۰٫۷۲ em بلند).
     */
    const val PROPORTIONAL_DIGIT_WIDTH = 0.56f
    const val DIGIT_HEIGHT_RATIO = 0.72f

    /** حاشیهٔ دایره از لبهٔ بوم (هر سمت، کسری از اندازه) */
    const val CIRCLE_INSET_FRACTION = 0.015f
}
