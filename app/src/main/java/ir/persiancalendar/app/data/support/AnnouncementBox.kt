package ir.persiancalendar.app.data.support

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * اطلاعیه‌ها (خبر و تبلیغ) — نسخه ۱.۶
 * مدیر از پنل مدیریت، خبر یا تبلیغ ثبت می‌کند و کنار منوی سه‌نقطه با زنگ و نقطه قرمز
 * به کاربران نشان داده می‌شود (ModalBottomSheet در MainScreen).
 *
 * ذخیره‌سازی محلی: فایل announcements.json در filesDir.
 * برای انتشار همگانی، در نسخه بعدی می‌توانید این فهرست را با Firebase Remote Config
 * یا یک API ساده از سرور خودتان بفرستید.
 */
data class Announcement(
    val id: Long,
    val type: String,      // news / ad
    val title: String,
    val body: String,
    val link: String = "", // لینک اختیاری — دکمه «مشاهده» می‌سازد
    val image: String = "",// تصویر اختیاری — مسیر فایل داخلی یا URL (نسخه ۱.۱۱.۷)
    val on: Boolean = true,
    val at: Long = System.currentTimeMillis()
) {
    val typeLabel: String
        get() = if (type == "ad") "تبلیغ" else "خبر"
}

object AnnouncementBox {

    private const val FILE_NAME = "announcements.json"
    private const val SEEN_FILE = "announcements_seen.txt"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
    private fun seenFile(context: Context) = File(context.filesDir, SEEN_FILE)

    /** تعداد اطلاعیه‌های فعال که هنوز دیده نشده‌اند — برای نقطه قرمز زنگ */
    fun unseenCount(context: Context): Int {
        val seen = runCatching { seenFile(context).readText().trim().toLong() }.getOrDefault(0L)
        return active(context).count { it.at > seen }
    }

    /** علامت‌گذاری همه اطلاعیه‌ها به‌عنوان دیده‌شده (باز شدن زنگ توسط کاربر) */
    fun markSeen(context: Context) {
        runCatching { seenFile(context).writeText(System.currentTimeMillis().toString()) }
    }

    fun load(context: Context): List<Announcement> {
        val f = file(context)
        if (!f.exists()) return seed()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Announcement(
                    id = o.optLong("id", 0L),
                    type = o.optString("type", "news"),
                    title = o.optString("title"),
                    body = o.optString("body"),
                    link = o.optString("link"),
                    image = o.optString("image"),
                    on = o.optBoolean("on", true),
                    at = o.optLong("at", System.currentTimeMillis())
                )
            }
        }.getOrDefault(seed())
    }

    /** داده اولیه — فقط بار اول که فایلی وجود ندارد */
    private fun seed(): List<Announcement> = listOf(
        Announcement(
            id = 1L, type = "news", title = "نسخه ۱٫۷ منتشر شد",
            body = "کارت اوقات شرعی (نوار ۸ وقت + پخش اذان + جدول ماهانه) اکنون در " +
                    "صفحه اصلی تقویم نمایش داده می‌شود.",
            at = System.currentTimeMillis() - 86_400_000L
        ),
        Announcement(
            id = 2L, type = "ad", title = "فرصت تبلیغ در تقویم فارسی",
            body = "برنامه شما می‌تواند جلوی هزاران کاربر نمایش داده شود. " +
                    "برای درخواست تبلیغ از منوی سه‌نقطه → پشتیبانی پیام بگذارید.",
            at = System.currentTimeMillis()
        )
    )

    fun add(
        context: Context,
        type: String,
        title: String,
        body: String,
        link: String = "",
        image: String = ""
    ): Boolean {
        val ann = Announcement(
            id = System.currentTimeMillis(),
            type = if (type == "ad") "ad" else "news",
            title = title.trim(),
            body = body.trim(),
            link = link.trim(),
            image = image.trim(),
            at = System.currentTimeMillis()
        )
        val all = load(context).toMutableList()
        all.add(ann)
        return save(context, all)
    }

    fun setEnabled(context: Context, id: Long, on: Boolean) {
        val all = load(context).map { if (it.id == id) it.copy(on = on) else it }
        save(context, all)
    }

    fun delete(context: Context, id: Long) {
        // اگر تصویر اطلاعیه فایل داخلی باشد، همراهش پاک می‌شود (نسخه ۱.۱۱.۷)
        load(context).firstOrNull { it.id == id }?.image?.takeIf {
            it.isNotBlank() && !it.startsWith("http")
        }?.let { p -> runCatching { java.io.File(p).delete() } }
        save(context, load(context).filterNot { it.id == id })
    }

    /**
     * کپی تصویر انتخاب‌شده از گالری به filesDir/announcements — خروجی: مسیر فایل
     * ذخیره‌شده یا رشته خالی در صورت خطا (همان الگوی MainAdBox — نسخه ۱.۱۱.۷)
     */
    fun saveImageFromUri(context: Context, uri: android.net.Uri): String = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return ""
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return ""
        bmp.recycle()
        val dir = File(context.filesDir, "announcements").apply { mkdirs() }
        val out = File(dir, "ann_${System.currentTimeMillis()}.jpg")
        out.writeBytes(bytes)
        out.absolutePath
    }.getOrDefault("")

    /** اطلاعیه‌های فعال برای نمایش به کاربر */
    fun active(context: Context): List<Announcement> = load(context).filter { it.on }

    fun save(context: Context, list: List<Announcement>): Boolean = runCatching {
        file(context).writeText(encode(list))
        true
    }.getOrDefault(false)

    private fun encode(list: List<Announcement>): String {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("type", a.type)
                    .put("title", a.title)
                    .put("body", a.body)
                    .put("link", a.link)
                    .put("image", a.image)
                    .put("on", a.on)
                    .put("at", a.at)
            )
        }
        return arr.toString(2)
    }
}
