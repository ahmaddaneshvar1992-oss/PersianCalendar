package ir.persiancalendar.app.data.backup

import android.content.Context
import android.net.Uri
import ir.persiancalendar.app.data.db.EventEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * پشتیبان‌گیری محلی رویدادها: خروجی/ورودی فایل JSON
 * (بدون نیاز به سرور — کاربر فایل را خودش ذخیره و بازیابی می‌کند)
 */
object BackupManager {

    private const val FORMAT_VERSION = 1

    fun exportToJson(events: List<EventEntity>): String {
        val root = JSONObject()
        root.put("app", "PersianCalendar")
        root.put("formatVersion", FORMAT_VERSION)
        val arr = JSONArray()
        for (e in events) {
            val o = JSONObject()
            o.put("title", e.title)
            o.put("note", e.note)
            o.put("jy", e.jy); o.put("jm", e.jm); o.put("jd", e.jd)
            o.put("gy", e.gy); o.put("gm", e.gm); o.put("gd", e.gd)
            o.put("hour", e.hour)
            o.put("minute", e.minute)
            o.put("reminderMinutesBefore", e.reminderMinutesBefore)
            o.put("repeatType", e.repeatType)
            o.put("colorIndex", e.colorIndex)
            arr.put(o)
        }
        root.put("events", arr)
        return root.toString(2)
    }

    fun importFromJson(json: String): List<EventEntity> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("events")
        val list = mutableListOf<EventEntity>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                EventEntity(
                    id = 0,
                    title = o.getString("title"),
                    note = o.optString("note", ""),
                    jy = o.getInt("jy"),
                    jm = o.getInt("jm"),
                    jd = o.getInt("jd"),
                    gy = o.getInt("gy"),
                    gm = o.getInt("gm"),
                    gd = o.getInt("gd"),
                    hour = o.optInt("hour", -1),
                    minute = o.optInt("minute", 0),
                    reminderMinutesBefore = o.optInt("reminderMinutesBefore", -1),
                    repeatType = o.optInt("repeatType", 0),
                    colorIndex = o.optInt("colorIndex", 0)
                )
            )
        }
        return list
    }

    suspend fun writeToFile(context: Context, uri: Uri, json: String): Boolean =
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        }.getOrDefault(false)

    suspend fun readFromFile(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            }
        }.getOrNull()
}
