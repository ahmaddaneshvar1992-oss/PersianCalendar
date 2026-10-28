package ir.persiancalendar.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EventEntity::class, UserEntity::class, ReminderEntity::class, DoseLogEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun userDao(): UserDao
    abstract fun reminderDao(): ReminderDao
    abstract fun doseLogDao(): DoseLogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** ارتقا از نسخه ۱ به ۲: افزودن جدول کاربران */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        username TEXT NOT NULL,
                        passwordHash TEXT NOT NULL,
                        salt TEXT NOT NULL,
                        premium INTEGER NOT NULL DEFAULT 0,
                        premiumSource TEXT NOT NULL DEFAULT '',
                        premiumSince INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_username ON users(username)")
            }
        }

        /** ارتقا از نسخه ۲ به ۳: موتور یادآور عمومی (یادآورها + ثبت نوبت‌ها) — نسخه ۱.۱۱ */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        type INTEGER NOT NULL DEFAULT 3,
                        repeatMode INTEGER NOT NULL DEFAULT 1,
                        daysOfWeek TEXT NOT NULL DEFAULT '',
                        times TEXT NOT NULL DEFAULT '',
                        startDate INTEGER NOT NULL DEFAULT 0,
                        endDate INTEGER NOT NULL DEFAULT 0,
                        targetCount INTEGER NOT NULL DEFAULT 0,
                        note TEXT NOT NULL DEFAULT '',
                        enabled INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL
                    )
                    """ .trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_enabled ON reminders(enabled)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_type ON reminders(type)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dose_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        reminderId INTEGER NOT NULL,
                        dayKey INTEGER NOT NULL,
                        timeMinute INTEGER NOT NULL,
                        status INTEGER NOT NULL DEFAULT 0,
                        loggedAt INTEGER NOT NULL
                    )
                    """ .trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_dose_logs_reminderId_dayKey_timeMinute " +
                        "ON dose_logs(reminderId, dayKey, timeMinute)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "persian_calendar.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { instance = it }
            }
    }
}
