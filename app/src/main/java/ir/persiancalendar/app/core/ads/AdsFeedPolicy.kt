package ir.persiancalendar.app.core.ads

/**
 * سیاست «تبلیغات مدیریت‌شده» — نسخهٔ ۱.۱۱.۳۵ (درخواست کاربر: کار ۱)
 *
 * تفاوت با AdPolicy (بنرهای قابل حذف ۱.۱۱.۳۴): این آیتم‌ها «هیچ ضربدر، منو یا
 * راه پنهان‌کردنی برای کاربر ندارند» — فقط مدیر با تغییر فید راه‌دور آنها را
 * اضافه/ویرایش/حذف می‌کند و فهرست با هر فید معتبر «کامل جایگزین» می‌شود.
 *
 * همهٔ تصمیم‌های امنیتی/انتخاب در یک‌جای خالص جمع شده تا بدون دستگاه اندرویدی
 * با تست واحد (AdsFeedPolicyTest در CI) راستی‌آزمایی شود:
 *  - ضدمقاومت بازپخش (rollback): sequence کوچک‌تر یا مساویِ آخرین فید معتبر رد می‌شود
 *  - گارد اولین پذیرش: فید خیلی قدیمی (issuedAt > ۹۰ روز) در نصب تازه رد می‌شود
 *  - فقط https برای iconUrl/imageUrl و target خارجی (http/intent/file/javascript/content رد)
 *  - targetType=internal فقط با کلیدهای لیست‌سفید (هیچ intent خامی از فید اجرا نمی‌شود)
 *  - بازهٔ زمانی startAt/endAt و دروازهٔ minVersionCode
 *  - کلید قطع سراسری adsEnabled داخل خود فید
 *  - مرتب‌سازی بر اساس order فید و سقف تعداد
 *  - تازگی کش: کش قدیمی‌تر از maxCacheDays (پیش‌فرض ۷) نمایش داده نمی‌شود
 *
 * هیچ داده‌ای از کاربر (موقعیت، تاریخ تولد، رویدادها) به هیچ‌جا نمی‌رود؛
 * این کلاس فقط خواندنی است و هیچ خروجی شبکه‌ای ندارد.
 */
object AdsFeedPolicy {

    /** سقف تعداد آیتم فید (حفاظت در برابر فید عظیم جعلی — البته امضادار) */
    const val MAX_ITEMS = 12

    /** سقف طول متن‌های فید — هیچ متن خام بلندی رندر نمی‌شود */
    const val MAX_ID = 64
    const val MAX_TITLE = 40
    const val MAX_BADGE = 12
    const val MAX_DESC = 80
    const val MAX_URL = 2048

    /** عمر پیش‌فرض کش وقتی فید maxCacheDays نداده باشد */
    const val DEFAULT_MAX_CACHE_DAYS = 7

    /** سقف معقول maxCacheDays (یک ماه) — مقادیر بزرگ‌تر بی‌معنا هستند */
    const val MAX_CACHE_DAYS = 30

    /**
     * گارد اولین پذیرش (نصب تازه بدون sequence ذخیره‌شده): فیدی که issuedAt آن
     * قدیمی‌تر از این است رد می‌شود تا بازپخش فیدِ کهنِ امضاشده در نصب تازه
     * آیتم حذف‌شده را برنگرداند.
     */
    const val FIRST_ACCEPT_MAX_AGE_MS = 90L * 24L * 60L * 60L * 1000L

    /**
     * لیست‌سفید مقصدهای داخلی — targetType=internal فقط با این کلیدها مجاز است.
     * هیچ intent خامی از فید اجرا نمی‌شود؛ هر کلید در UI به مقصد شناخته‌شدهٔ
     * خود اپ ترجمه می‌شود (AdsFeedSection → MainScreen).
     */
    val INTERNAL_TARGETS = setOf(
        "calendar",        // تب تقویم (صفحه اصلی)
        "prayer",          // اوقات شرعی
        "qibla",           // قبله‌نما
        "events",          // رویدادها
        "reminders",       // یادآورها
        "announcements",   // پنل اطلاعیه‌ها
        "support",         // پشتیبانی
        "account"          // حساب کاربری
    )

    /** نوع آیتم */
    const val TYPE_TILE = "tile"
    const val TYPE_BANNER = "banner"

    /**
     * ضدمقاومت بازپخش: فید با sequence کوچک‌تر «یا مساوی» آخرین فید معتبر رد می‌شود.
     * stored = ۰ یعنی هنوز هیچ فیدی پذیرفته نشده (نخستین پذیرش هر عددی ≥ ۱ قبول است).
     */
    fun sequenceAccepts(stored: Long, incoming: Long): Boolean =
        incoming >= 1L && incoming > stored

    /**
     * گارد اولین پذیرش: در نصب تازه (stored=0) فیدی که تاریخ صدورش قدیمی‌تر از
     * FIRST_ACCEPT_MAX_AGE_MS باشد رد می‌شود. issuedAt نامعلوم (۰) هم گارد را
     * رد می‌کند (سخت‌گیرانه — مدیر باید issuedAt معتبر بگذارد).
     */
    fun firstAcceptFreshEnough(issuedAtMs: Long, nowMs: Long): Boolean =
        issuedAtMs > 0L && nowMs >= issuedAtMs && (nowMs - issuedAtMs) <= FIRST_ACCEPT_MAX_AGE_MS

    /** آیا کش (آخرین فید معتبر) هنوز تازه است؟ maxCacheDays = ۰ یعنی کش بی‌اعتبار */
    fun cacheIsFresh(fetchedAtMs: Long, nowMs: Long, maxCacheDays: Int): Boolean {
        if (fetchedAtMs <= 0L) return false
        if (maxCacheDays <= 0) return false
        val days = maxCacheDays.coerceAtMost(MAX_CACHE_DAYS)
        return (nowMs - fetchedAtMs) <= days * 24L * 60L * 60L * 1000L
    }

    /** بازهٔ زمانی مجاز نمایش (بازهٔ نامعتبر = پنهان) — همان قاعدهٔ AdPolicy */
    fun inTimeWindow(startAt: Long, endAt: Long, nowMs: Long): Boolean {
        if (startAt > 0 && nowMs < startAt) return false
        if (endAt > 0 && nowMs >= endAt) return false
        if (startAt > 0 && endAt > 0 && endAt <= startAt) return false
        return true
    }

    /** رکورد خالص قابل فیلتر — از FeedItem ساخته می‌شود (بدون وابستگی به اندروید) */
    data class ItemRecord(
        val id: String,
        val type: String,
        val enabled: Boolean,
        val order: Int,
        val startAt: Long = 0L,       // epoch ms — ۰ یعنی بدون محدودیت شروع
        val endAt: Long = 0L,         // epoch ms — ۰ یعنی بدون محدودیت پایان
        val minVersionCode: Int = 0
    )

    /** آیتم واجد شرایط نمایش است؟ (feedAdsEnabled = کلید قطع سراسری داخل خود فید) */
    fun isEligible(
        item: ItemRecord,
        nowMs: Long,
        versionCode: Int,
        feedAdsEnabled: Boolean
    ): Boolean {
        if (!feedAdsEnabled) return false
        if (!item.enabled) return false
        if (item.minVersionCode > versionCode) return false
        return inTimeWindow(item.startAt, item.endAt, nowMs)
    }

    /**
     * انتخاب و مرتب‌سازی: فقط واجد شرایط‌ها، مرتب بر اساس order صعودی فید
     * (تساوی = شناسه الفبایی برای قطعیت) و سقف max.
     */
    fun <T> select(
        items: List<T>,
        record: (T) -> ItemRecord,
        nowMs: Long,
        versionCode: Int,
        feedAdsEnabled: Boolean,
        max: Int = MAX_ITEMS
    ): List<T> =
        items.asSequence()
            .filter { isEligible(record(it), nowMs, versionCode, feedAdsEnabled) }
            .sortedWith(compareBy<T> { record(it).order }.thenBy { record(it).id })
            .take(max.coerceAtLeast(0))
            .toList()

    // ---------- اعتبارسنجی امن URL (الزام الزامی کاربر: فقط https) ----------

    /**
     * فقط https:// با host معتبر — http، javascript، intent، file، content،
     * data و هر scheme دیگر رد می‌شود. برای iconUrl/imageUrl/target خارجی.
     */
    fun isSafeHttpsUrl(url: String?): Boolean {
        val s = (url ?: "").trim()
        if (s.isEmpty() || s.length > MAX_URL) return false
        if (!s.lowercase().startsWith("https://")) return false
        if (s.any { it.isWhitespace() }) return false
        val rest = s.substring("https://".length)
        // «https://» خالی، «https:///path» یا host نامعتبر رد شود
        val host = rest.substringBefore('/').substringBefore(':').substringBefore('@')
        if (host.isBlank()) return false
        if (!host.contains('.') && host != "localhost") return false
        return true
    }

    /** مقصد داخلی فقط با کلید لیست‌سفید — هیچ رشتهٔ دیگری اجرا نمی‌شود */
    fun isSafeInternalTarget(key: String?): Boolean {
        val k = (key ?: "").trim()
        return k.isNotEmpty() && k.length <= MAX_ID && k in INTERNAL_TARGETS
    }

    /** clamp متن فید — ورودی خام هرگز بی‌سقف رندر نمی‌شود (بدون هیچ HTML) */
    fun clamp(text: String, max: Int): String {
        val t = text.replace("\n", " ").replace("\r", " ").trim()
        return if (t.length <= max) t else t.take(max).trimEnd()
    }
}
