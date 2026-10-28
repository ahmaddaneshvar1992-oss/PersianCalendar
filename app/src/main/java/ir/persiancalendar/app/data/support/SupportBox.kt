package ir.persiancalendar.app.data.support

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * پیام پشتیبانی — نسخه ۱.۵
 * کاربر از بخش «پشتیبانی» پیام می‌فرستد (درخواست تبلیغ / گزارش مشکل / پیشنهاد)
 * و پیام در صندوق پشتیبانی پنل مدیریت نمایش داده می‌شود.
 *
 * ذخیره‌سازی محلی (بدون سرور): فایل support_messages.json در filesDir.
 * در نسخه‌های بعدی می‌توانید با یک API ساده پیام‌ها را به سرور خودتان بفرستید
 * یا با ACTION_SENDTO به ایمیل مدیر ارسال کنید (SupportScreen هر دو را دارد).
 */
data class SupportMessage(
    val id: Long,
    val type: String,      // ad / bug / idea
    val name: String,
    val contact: String,   // ایمیل یا شماره تماس برای پاسخ
    val text: String,
    val at: String         // زمان ثبت (نمایشی)
) {
    val typeLabel: String
        get() = when (type) {
            "ad" -> "درخواست تبلیغ"
            "bug" -> "گزارش مشکل"
            else -> "پیشنهاد"
        }
}

object SupportBox {

    private const val FILE_NAME = "support_messages.json"
    /** ایمیل مدیر برای «ارسال با ایمیل» — آدرس شما (Ahmad Daneshvar) */
    const val ADMIN_EMAIL = "Ahmad.daneshvar1992@gmail.com"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun load(context: Context): List<SupportMessage> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                SupportMessage(
                    id = o.optLong("id", 0L),
                    type = o.optString("type", "idea"),
                    name = o.optString("name"),
                    contact = o.optString("contact"),
                    text = o.optString("text"),
                    at = o.optString("at")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, type: String, name: String, contact: String, text: String): Boolean {
        val javaTime = java.time.LocalDateTime.now()
        val at = "%d/%02d/%02d %02d:%02d".format(
            javaTime.year, javaTime.monthValue, javaTime.dayOfMonth,
            javaTime.hour, javaTime.minute
        )
        val msg = SupportMessage(
            id = System.currentTimeMillis(),
            type = type, name = name.trim(), contact = contact.trim(),
            text = text.trim(), at = at
        )
        val all = load(context).toMutableList()
        all.add(msg)
        return runCatching {
            file(context).writeText(encode(all))
            true
        }.getOrDefault(false)
    }

    fun delete(context: Context, id: Long) {
        val all = load(context).filterNot { it.id == id }
        runCatching { file(context).writeText(encode(all)) }
    }

    fun deleteAll(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun encode(list: List<SupportMessage>): String {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("type", m.type)
                    .put("name", m.name)
                    .put("contact", m.contact)
                    .put("text", m.text)
                    .put("at", m.at)
            )
        }
        return arr.toString(2)
    }
}
