package ir.persiancalendar.app.data.support

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * بنرهای تبلیغات صفحه اصلی — نسخه ۱.۱۱.۶ (درخواست کاربر)
 *
 * مدیر از «پنل مدیریت → تبلیغات صفحه اصلی» هر چند بنر تبلیغ که بخواهد می‌سازد؛
 * بنرهای فعال به‌ترتیب، در صفحه اصلی تقویم (پیش از کارت اوقات شرعی) به‌صورت
 * کارت‌های تودرتو نمایش داده می‌شوند — مطابق نمونه‌ای که کاربر فرستاد.
 *
 * تفاوت با تبلیغات حذف‌شده ۱.۱۱.۵: هیچ SDK تبلیغاتی (AdMob) و هیچ اینترستیشالی
 * در کار نیست؛ این بنرها مالک خود مدیر است و فقط وقتی نمایش داده می‌شود که
 * مدیر خودش ساخته و روشنش کرده باشد (پیش‌فرض: صفر بنر = بدون هیچ تبلیغی).
 *
 * ذخیره‌سازی محلی: فایل main_ads.json در filesDir (همان الگوی AnnouncementBox).
 * تصویر بنر یا مسیر فایل داخلی (filesDir/ads) است یا آدرس اینترنتی http(s).
 */
data class MainAd(
    val id: Long,
    val title: String,
    val body: String = "",      // توضیح کوتاه — اختیاری
    val cta: String = "مشاهده", // متن دکمه — پیش‌فرض «مشاهده»
    val link: String = "",      // لینک اختیاری — لمس بنر بازش می‌کند
    val image: String = "",     // مسیر فایل داخلی یا URL تصویر — اختیاری
    val colorIndex: Int = 0,    // ایندکس پالت رنگ (۸ رنگ در AdBanners.kt)
    val on: Boolean = true,
    val at: Long = System.currentTimeMillis()
)

object MainAdBox {

    private const val FILE_NAME = "main_ads.json"
    private const val ADS_DIR = "ads"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** پوشه تصاویر بنرها (filesDir/ads) */
    fun adsDir(context: Context): File = File(context.filesDir, ADS_DIR).apply { mkdirs() }

    fun load(context: Context): List<MainAd> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                MainAd(
                    id = o.optLong("id", 0L),
                    title = o.optString("title"),
                    body = o.optString("body"),
                    cta = o.optString("cta", "مشاهده"),
                    link = o.optString("link"),
                    image = o.optString("image"),
                    colorIndex = o.optInt("colorIndex", 0),
                    on = o.optBoolean("on", true),
                    at = o.optLong("at", System.currentTimeMillis())
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, list: List<MainAd>): Boolean = runCatching {
        file(context).writeText(encode(list))
        true
    }.getOrDefault(false)

    /** افزودن بنر جدید به انتهای فهرست (نمایش از بالا به پایین) — تعداد نامحدود */
    fun add(
        context: Context,
        title: String,
        body: String = "",
        cta: String = "",
        link: String = "",
        image: String = "",
        colorIndex: Int = 0
    ): Boolean {
        val ad = MainAd(
            id = System.currentTimeMillis(),
            title = title.trim(),
            body = body.trim(),
            cta = cta.trim().ifBlank { "مشاهده" },
            link = link.trim(),
            image = image.trim(),
            colorIndex = colorIndex.coerceIn(0, 7),
            at = System.currentTimeMillis()
        )
        val all = load(context).toMutableList()
        all.add(ad)
        return save(context, all)
    }

    fun setEnabled(context: Context, id: Long, on: Boolean) {
        save(context, load(context).map { if (it.id == id) it.copy(on = on) else it })
    }

    /** جابه‌جایی ترتیب نمایش: delta منفی = بالاتر، مثبت = پایین‌تر */
    fun move(context: Context, id: Long, delta: Int) {
        val all = load(context).toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx < 0) return
        val target = (idx + delta).coerceIn(0, all.size - 1)
        if (target == idx) return
        val item = all.removeAt(idx)
        all.add(target, item)
        save(context, all)
    }

    fun delete(context: Context, id: Long) {
        val target = load(context).firstOrNull { it.id == id }
        // اگر تصویر بنر فایل داخلی باشد، همراهش پاک می‌شود
        target?.image?.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { p ->
            runCatching { File(p).delete() }
        }
        save(context, load(context).filterNot { it.id == id })
    }

    /** بنرهای فعال برای نمایش در صفحه اصلی */
    fun active(context: Context): List<MainAd> = load(context).filter { it.on }

    /**
     * کپی تصویر انتخاب‌شده از گالری به filesDir/ads — خروجی: مسیر فایل ذخیره‌شده
     * یا رشته خالی در صورت خطا. تصویر با BitmapFactory اعتبارسنجی می‌شود.
     */
    fun saveImageFromUri(context: Context, uri: Uri): String = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: return ""
        // اعتبارسنجی اینکه واقعاً تصویر است
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return ""
        bmp.recycle()
        val out = File(adsDir(context), "ad_${System.currentTimeMillis()}.jpg")
        out.writeBytes(bytes)
        out.absolutePath
    }.getOrDefault("")

    private fun encode(list: List<MainAd>): String {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("title", a.title)
                    .put("body", a.body)
                    .put("cta", a.cta)
                    .put("link", a.link)
                    .put("image", a.image)
                    .put("colorIndex", a.colorIndex)
                    .put("on", a.on)
                    .put("at", a.at)
            )
        }
        return arr.toString(2)
    }
}
