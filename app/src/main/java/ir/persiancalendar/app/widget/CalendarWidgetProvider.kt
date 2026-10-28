package ir.persiancalendar.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ir.persiancalendar.app.MainActivity
import ir.persiancalendar.app.R
import ir.persiancalendar.app.core.calendar.CalendarConverter
import ir.persiancalendar.app.core.calendar.JalaliDate
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.core.events.HolidayRepository
import ir.persiancalendar.app.di.AppContainer
import java.time.LocalDate

/**
 * ویجت صفحه اصلی: تقویم ماه جاری شمسی با برجسته‌سازی امروز و رنگ تعطیلات.
 */
class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val holidayRepo = AppContainer.holidayRepo(context)
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id, holidayRepo)
        }
    }

    override fun onEnabled(context: Context) {
        // هشدارهای روزانه را مطمئن شو زمان‌بندی شده‌اند تا ویجت نیمه‌شب تازه شود
        ir.persiancalendar.app.notifications.AlarmScheduler.scheduleDailyTick(context)
    }

    companion object {
        // شناسه‌های صریح ۴۲ سلول تقویم (شناسه‌های resource متوالی نیستند!)
        private val cellIds = intArrayOf(
            R.id.wc0, R.id.wc1, R.id.wc2, R.id.wc3, R.id.wc4, R.id.wc5, R.id.wc6,
            R.id.wc7, R.id.wc8, R.id.wc9, R.id.wc10, R.id.wc11, R.id.wc12, R.id.wc13,
            R.id.wc14, R.id.wc15, R.id.wc16, R.id.wc17, R.id.wc18, R.id.wc19, R.id.wc20,
            R.id.wc21, R.id.wc22, R.id.wc23, R.id.wc24, R.id.wc25, R.id.wc26, R.id.wc27,
            R.id.wc28, R.id.wc29, R.id.wc30, R.id.wc31, R.id.wc32, R.id.wc33, R.id.wc34,
            R.id.wc35, R.id.wc36, R.id.wc37, R.id.wc38, R.id.wc39, R.id.wc40, R.id.wc41
        )

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, CalendarWidgetProvider::class.java))
            val repo = AppContainer.holidayRepo(context)
            for (id in ids) updateWidget(context, mgr, id, repo)
        }

        private fun updateWidget(
            context: Context,
            mgr: AppWidgetManager,
            appWidgetId: Int,
            holidayRepo: HolidayRepository
        ) {
            val today = LocalDate.now()
            val todayJ = CalendarConverter.gregorianToJalali(today.year, today.monthValue, today.dayOfMonth)
            val jy = todayJ.year
            val jm = todayJ.month

            val rv = RemoteViews(context.packageName, R.layout.widget_month)

            // سرصفحه
            rv.setTextViewText(R.id.widget_month_title, "${PersianText.jalaliMonths[jm - 1]} ${PersianText.toPersianDigits(jy.toString())}")
            val todayNumText = PersianText.toPersianDigits("${todayJ.day} ${PersianText.jalaliMonths[todayJ.month - 1]}")
            rv.setTextViewText(R.id.widget_today_text, todayNumText)

            // شبکه ماه
            val monthLen = CalendarConverter.jalaliMonthLength(jy, jm)
            val firstG = CalendarConverter.jalaliToGregorian(jy, jm, 1)
            val firstDate = LocalDate.of(firstG.year, firstG.month, firstG.day)
            val firstIndex = PersianText.weekdayIndexSat0(firstDate)

            val holidayRed = 0xFFC62828.toInt()
            val normalColor = 0xFF2B2F2C.toInt()
            val dimColor = 0xFFB9BDB9.toInt()
            val todayColor = 0xFFFFFFFF.toInt()

            for (cell in 0 until 42) {
                val viewId = cellIds[cell]
                val dayOffset = cell - firstIndex
                if (dayOffset in 0 until monthLen) {
                    val dayNum = dayOffset + 1
                    val j = JalaliDate(jy, jm, dayNum)
                    val g = firstDate.plusDays(dayOffset.toLong())
                    val isToday = dayNum == todayJ.day
                    val isHoliday = holidayRepo.isHoliday(j, g)

                    rv.setTextViewText(
                        viewId,
                        PersianText.toPersianDigits(dayNum.toString())
                    )
                    rv.setTextColor(
                        viewId,
                        when {
                            isToday -> todayColor
                            isHoliday -> holidayRed
                            g.dayOfWeek == java.time.DayOfWeek.FRIDAY -> holidayRed
                            else -> normalColor
                        }
                    )
                    if (isToday) {
                        rv.setInt(viewId, "setBackgroundResource", R.drawable.widget_cell_today)
                    } else {
                        rv.setInt(viewId, "setBackgroundResource", android.R.color.transparent)
                    }
                } else {
                    rv.setTextViewText(viewId, "")
                    rv.setTextColor(viewId, dimColor)
                    rv.setInt(viewId, "setBackgroundResource", android.R.color.transparent)
                }
            }

            // کلیک روی ویجت → باز کردن اپ
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, 1001, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.widget_root, pi)

            mgr.updateAppWidget(appWidgetId, rv)
        }
    }
}
