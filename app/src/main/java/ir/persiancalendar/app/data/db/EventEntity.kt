package ir.persiancalendar.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * رویداد کاربر. تاریخ لنگرگاه هم به شمسی و هم میلادی ذخیره می‌شود
 * تا محاسبه تکرار و نمایش سریع باشد.
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    // تاریخ شمسی لنگرگاه
    val jy: Int,
    val jm: Int,
    val jd: Int,
    // تاریخ میلادی لنگرگاه
    val gy: Int,
    val gm: Int,
    val gd: Int,
    // ساعت رویداد؛ ساعت = -1 یعنی تمام‌روز
    val hour: Int = -1,
    val minute: Int = 0,
    /** دقیقه قبل از زمان رویداد برای یادآوری؛ -1 = بدون یادآوری */
    val reminderMinutesBefore: Int = -1,
    /** اندیس RepeatType */
    val repeatType: Int = 0,
    /** اندیس رنگ (۰ تا ۵) */
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
