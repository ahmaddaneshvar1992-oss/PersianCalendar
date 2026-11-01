package ir.persiancalendar.app.data.support

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * حافظهٔ محلی «تبلیغ‌های پنهان‌شده با ×» — نسخهٔ ۱.۱۱.۳۴ (درخواست کاربر)
 *
 * با لمس × روی هر بنر، شناسهٔ تبلیغ تا زمان تعیین‌شدهٔ dismissDays از فید
 * (پیش‌فرض ۷ روز؛ ۰ = برای همیشه) در همین فایل محلی ذخیره می‌شود و همان
 * تبلیغ دوباره نمایش داده نمی‌شود؛ تبلیغ با شناسهٔ جدید عادی نمایش داده
 * می‌شود (معیار پذیرش ۲).
 *
 * فرمت فایل: {"<id>": untilEpochMs, ...} در filesDir — کاملاً محلی، هیچ
 * ارسال شبکه‌ای ندارد. دکمهٔ «نمایش دوباره تبلیغات پنهان‌شده» در تنظیمات
 * همین فایل را خالی می‌کند.
 */
object AdDismissStore {

    private const val FILE_NAME = "ads_dismissed.json"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** خواندن نقشهٔ شناسه → تا-چه-زمانی پنهان بماند (ورودی‌های منقضی حذف می‌شوند) */
    fun load(context: Context, nowMs: Long = System.currentTimeMillis()): Map<Long, Long> =
        runCatching {
            val o = JSONObject(file(context).readText())
            val out = mutableMapOf<Long, Long>()
            for (key in o.keys()) {
                val id = key.toLongOrNull() ?: continue
                val until = o.optLong(key, 0L)
                if (until > nowMs) out[id] = until
            }
            out
        }.getOrDefault(emptyMap())

    /** ثبت پنهانی یک تبلیغ تا زمان مشخص (فایل چند بایتی است — نوشتن همگام بی‌خطر) */
    fun put(context: Context, id: Long, untilMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        runCatching {
            val map = load(context, nowMs).toMutableMap()
            map[id] = untilMs
            file(context).writeText(encodeMap(map))
            true
        }.getOrDefault(false)

    /** پاک‌کردن همهٔ پنهانی‌ها — «نمایش دوباره تبلیغات پنهان‌شده» در تنظیمات */
    fun clearAll(context: Context): Boolean =
        runCatching {
            file(context).delete()
            true
        }.getOrDefault(false)

    /** تعداد پنهانی‌های فعال — برای نمایش کنار دکمهٔ تنظیمات */
    fun activeCount(context: Context, nowMs: Long = System.currentTimeMillis()): Int =
        load(context, nowMs).size

    private fun encodeMap(map: Map<Long, Long>): String {
        val o = JSONObject()
        map.forEach { (id, until) -> o.put(id.toString(), until) }
        return o.toString()
    }
}
