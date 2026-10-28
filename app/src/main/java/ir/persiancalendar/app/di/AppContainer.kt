package ir.persiancalendar.app.di

import android.content.Context
import ir.persiancalendar.app.core.events.HolidayRepository
import ir.persiancalendar.app.data.auth.AccountRepository
import ir.persiancalendar.app.data.db.AppDatabase
import ir.persiancalendar.app.data.db.DoseLogDao
import ir.persiancalendar.app.data.db.EventDao
import ir.persiancalendar.app.data.db.ReminderDao
import ir.persiancalendar.app.data.db.UserDao
import ir.persiancalendar.app.data.prefs.SettingsRepository

/**
 * کانتینر ساده وابستگی‌ها (بدون کتابخانه DI — سبک و قابل‌فهم)
 */
object AppContainer {
    @Volatile private var holidayRepository: HolidayRepository? = null
    @Volatile private var settingsRepository: SettingsRepository? = null
    @Volatile private var eventDao: EventDao? = null
    @Volatile private var userDao: UserDao? = null
    @Volatile private var reminderDao: ReminderDao? = null
    @Volatile private var doseLogDao: DoseLogDao? = null
    @Volatile private var accountRepository: AccountRepository? = null

    /** کانتکست اپلیکیشن برای دسترسی سریع از کلاس‌های object */
    @Volatile var appContext: Context? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun holidayRepo(context: Context): HolidayRepository =
        holidayRepository ?: synchronized(this) {
            holidayRepository ?: HolidayRepository(context.applicationContext).also {
                holidayRepository = it
            }
        }

    fun settingsRepo(context: Context): SettingsRepository =
        settingsRepository ?: synchronized(this) {
            settingsRepository ?: SettingsRepository(context.applicationContext).also {
                settingsRepository = it
            }
        }

    fun eventDao(context: Context): EventDao =
        eventDao ?: synchronized(this) {
            eventDao ?: AppDatabase.get(context.applicationContext).eventDao().also {
                eventDao = it
            }
        }

    fun userDao(context: Context): UserDao =
        userDao ?: synchronized(this) {
            userDao ?: AppDatabase.get(context.applicationContext).userDao().also {
                userDao = it
            }
        }

    fun accountRepo(context: Context): AccountRepository =
        accountRepository ?: synchronized(this) {
            accountRepository ?: AccountRepository(
                context.applicationContext,
                userDao(context.applicationContext)
            ).also { accountRepository = it }
        }

    /** DAO موتور یادآور عمومی — نسخه ۱.۱۱ */
    fun reminderDao(context: Context): ReminderDao =
        reminderDao ?: synchronized(this) {
            reminderDao ?: AppDatabase.get(context.applicationContext).reminderDao().also {
                reminderDao = it
            }
        }

    /** DAO ثبت نوبت‌ها (دوز دارو، لیوان آب، تمرین) — نسخه ۱.۱۱ */
    fun doseLogDao(context: Context): DoseLogDao =
        doseLogDao ?: synchronized(this) {
            doseLogDao ?: AppDatabase.get(context.applicationContext).doseLogDao().also {
                doseLogDao = it
            }
        }
}
