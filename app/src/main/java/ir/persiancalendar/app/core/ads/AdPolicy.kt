package ir.persiancalendar.app.core.ads

/**
 * سیاست بنرهای تبلیغاتی صفحه اصلی — نسخهٔ ۱.۱۱.۳۴ (درخواست کاربر)
 *
 * همهٔ تصمیم‌های انتخاب/فیلتر تبلیغ در یک‌جای خالص جمع شده تا بدون دستگاه
 * اندرویدی هم با تست واحد (AdPolicyTest در CI) راستی‌آزمایی شود:
 *  - کلید قطع اضطراری feed (adsEnabled در config.json) و سوئیچ کاربر در تنظیمات
 *  - بازهٔ نمایش startAt/endAt
 *  - دروازهٔ minVersionCode
 *  - پنهان‌سازی با × (dismissDays — ۰ = برای همیشه)
 *  - انتخاب حداکثر ۲ بنر بر اساس priority (بزرگ‌تر اول)
 *  - اعتبارسنجی امن URL (لینک فقط https؛ تصویر https یا data:image)
 *
 * هیچ داده‌ای از کاربر (موقعیت، تاریخ تولد، رویدادها) به تبلیغ‌دهنده
 * نمی‌رود؛ این کلاس فقط خواندنی است و هیچ خروجی شبکه‌ای ندارد.
 */
object AdPolicy {

    /** حداکثر بنر هم‌زمان در صفحه اصلی (الزام معیار پذیرش ۱) */
    const val MAX_VISIBLE = 2

    /** سقف طول متن‌های فید — هیچ متن خام بلندی رندر نمی‌شود */
    const val MAX_TITLE = 120
    const val MAX_BODY = 200
    const val MAX_CTA = 30
    const val MAX_URL = 2048

    /** سقف معقول dismissDays (یک سال) — مقادیر بزرگ‌تر بی‌معنا هستند */
    const val MAX_DISMISS_DAYS = 365

    /** یک رکورد قابل فیلتر — از MainAd ساخته می‌شود (بدون وابستگی به اندروید) */
    data class AdRecord(
        val id: Long,
        val priority: Int = 0,
        val startAt: Long = 0L,   // epoch ms — ۰ یعنی بدون محدودیت شروع
        val endAt: Long = 0L,     // epoch ms — ۰ یعنی بدون محدودیت پایان
        val dismissDays: Int = 7, // ۰ = پس از × برای همیشه پنهان
        val minVersionCode: Int = 0
    )

    /**
     * تا چه زمانی این تبلیغ پس از × پنهان بماند؟
     * dismissDays = ۰ یعنی برای همیشه (تا آمدن شناسهٔ جدید).
     */
    fun dismissedUntilMs(dismissDays: Int, nowMs: Long): Long =
        when {
            dismissDays <= 0 -> Long.MAX_VALUE
            else -> {
                val d = dismissDays.coerceAtMost(MAX_DISMISS_DAYS).toLong()
                nowMs + d * 24L * 60L * 60L * 1000L
            }
        }

    /** آیا تبلیغ در بازهٔ زمانی مجاز است؟ (بازهٔ نامعتبر = پنهان) */
    fun inTimeWindow(startAt: Long, endAt: Long, nowMs: Long): Boolean {
        if (startAt > 0 && nowMs < startAt) return false
        if (endAt > 0 && nowMs >= endAt) return false
        if (startAt > 0 && endAt > 0 && endAt <= startAt) return false
        return true
    }

    /** آیا تبلیغ برای نمایش واجد شرایط است؟ */
    fun isEligible(
        ad: AdRecord,
        nowMs: Long,
        versionCode: Int,
        feedAdsEnabled: Boolean,
        userAdsEnabled: Boolean,
        dismissedUntil: Map<Long, Long>
    ): Boolean {
        if (!feedAdsEnabled || !userAdsEnabled) return false
        val until = dismissedUntil[ad.id] ?: 0L
        if (nowMs < until) return false // هنوز در دورهٔ پنهانی بعد از ×
        if (ad.minVersionCode > versionCode) return false
        return inTimeWindow(ad.startAt, ad.endAt, nowMs)
    }

    /**
     * انتخاب بنرهای نمایشی: حذف پنهان‌شده‌ها و خارج‌ازبازه‌ها، مرتب بر اساس
     * priority نزولی (تساوی = شناسهٔ جدیدتر اول) و حداکثر max بنر.
     */
    fun <T> select(
        ads: List<T>,
        record: (T) -> AdRecord,
        nowMs: Long,
        versionCode: Int,
        feedAdsEnabled: Boolean,
        userAdsEnabled: Boolean,
        dismissedUntil: Map<Long, Long>,
        max: Int = MAX_VISIBLE
    ): List<T> =
        ads.asSequence()
            .filter { isEligible(record(it), nowMs, versionCode, feedAdsEnabled, userAdsEnabled, dismissedUntil) }
            .sortedWith(compareByDescending<T> { record(it).priority }.thenByDescending { record(it).id })
            .take(max.coerceAtLeast(0))
            .toList()

    // ---------- اعتبارسنجی امن URL (معیار پذیرش ۳) ----------

    /**
     * لینک بنر: فقط https:// با host معتبر — http، javascript، intent، file،
     * content و هر scheme دیگر رد می‌شود (هیچ intent خامی از فید اجرا نمی‌شود).
     */
    fun isSafeLink(url: String?): Boolean = isSafeHttps(url)

    /**
     * تصویر بنر: https:// مجاز؛ «data:image/…» هم مجاز است چون تصویر گالری
     * مدیر خود اپ است (بدون شبکه)؛ مسیر فایل داخلی (بدون scheme) فقط برای
     * بنرهای محلیِ دستگاه مدیر — از خوراک راه‌دور هرگز نباید بیاید.
     */
    fun isSafeImage(source: String?): Boolean {
        val s = (source ?: "").trim()
        if (s.isEmpty() || s.length > MAX_URL) return false
        val low = s.lowercase()
        return when {
            low.startsWith("https://") -> isSafeHttps(s)
            low.startsWith("data:image/") -> true
            // مسیر داخلی دستگاه مدیر (filesDir) — نبودن scheme یعنی فایل محلی
            !low.contains("://") -> true
            else -> false
        }
    }

    private fun isSafeHttps(url: String?): Boolean {
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

    /** clamp متن فید — ورودی خام هرگز بی‌سقف رندر نمی‌شود */
    fun clamp(text: String, max: Int): String {
        val t = text.replace("\n", " ").replace("\r", " ").trim()
        return if (t.length <= max) t else t.take(max).trimEnd()
    }
}
