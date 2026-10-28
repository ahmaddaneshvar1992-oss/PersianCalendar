package ir.persiancalendar.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * کاربر ثبت‌نام‌شده در اپلیکیشن.
 * حساب‌ها کاملاً روی همین دستگاه ذخیره می‌شوند (بدون سرور) —
 * پسورد هرگز به‌صورت متن ساده ذخیره نمی‌شود (هش SHA-256 با نمک تصادفی و ۱۰۰۰ دور).
 */
@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** نام نمایشی کاربر */
    val name: String,
    /** شناسه ورود: شماره موبایل یا ایمیل */
    val username: String,
    val passwordHash: String,
    val salt: String,
    /** عضویت ویژه فعال است */
    val premium: Boolean = false,
    /** منبع ارتقا: billing / code / "" */
    val premiumSource: String = "",
    val premiumSince: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
