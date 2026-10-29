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
import ir.persiancalendar.app.core.calendar.PersianText
import ir.persiancalendar.app.core.prayer.Cities
import ir.persiancalendar.app.di.AppContainer
import ir.persiancalendar.app.notifications.AlarmScheduler
import ir.persiancalendar.app.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ویجت هواشناسی — نسخه ۱.۱۱.۱۳ (درخواست کاربر)
 *
 * هوای امروز «شهر انتخاب‌شده» کاربر را نشان می‌دهد:
 *  نام شهر + ایموجی وضعیت، دمای فعلی بزرگ، شرح وضعیت فارسی،
 *  بیشترین/کمترین امروز، باد و رطوبت + ساعت آخرین به‌روزرسانی.
 *
 *  - داده: Open-Meteo (رایگان، بی‌کلید) با کش ۳۰ دقیقه‌ای (WeatherRepository)
 *  - رندر فوری از کش و تازه‌سازی پس‌زمینه‌ای در onUpdate
 *  - تازه‌سازی اجباری: تغییر شهر (SettingsRepository.setCityIndex)،
 *    باز شدن ویجت (onUpdate)، تیک نیمه‌شب (DailyTickReceiver) و
 *    ری‌استارت گوشی (BootReceiver)
 *  - لمس ویجت → باز شدن برنامه
 *  - در نبود اینترنت/داده، پیام روشن «اطلاعات موجود نیست» نمایش داده می‌شود
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // ۱) رندر فوری از کش تا ویجت هرگز خالی نماند
        renderAll(context, appWidgetManager, appWidgetIds, WeatherRepository.cached(context))

        // ۲) تازه‌سازی پس‌زمینه‌ای اگر کش کهنه است
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cityIndex = AppContainer.settingsRepo(context)
                    .settingsBlocking().cityIndex
                val data = WeatherRepository.current(context, cityIndex)
                if (data != null) {
                    renderAll(context, appWidgetManager, appWidgetIds, data)
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        // تیک نیمه‌شب را مطمئن شو زنده است تا ویجت روزانه تازه شود
        AlarmScheduler.scheduleDailyTick(context)
    }

    companion object {

        /** به‌روزرسانی همه نمونه‌های ویجت هوا (رندر از کش + تازه‌سازی در پس‌زمینه) */
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            if (ids.isEmpty()) return
            // رندر فوری از کش
            renderAll(context, mgr, ids, WeatherRepository.cached(context))
            // تازه‌سازی در پس‌زمینه
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val cityIndex = AppContainer.settingsRepo(context).settingsBlocking().cityIndex
                    WeatherRepository.current(context, cityIndex)?.let {
                        renderAll(context, mgr, ids, it)
                    }
                }
            }
        }

        /** تازه‌سازی اجباری (کش را هم دور می‌زند) — بعد از تغییر شهر */
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val cityIndex = AppContainer.settingsRepo(context).settingsBlocking().cityIndex
                    WeatherRepository.current(context, cityIndex, force = true)?.let {
                        renderAll(context, mgr, ids, it)
                    }
                }
            }
        }

        /** با تغییر شهر: کش باطل + تازه‌سازی اجباری (از SettingsRepository صدا زده می‌شود) */
        fun onCityChanged(context: Context) {
            WeatherRepository.invalidate(context)
            refresh(context)
        }

        private fun renderAll(
            context: Context,
            mgr: AppWidgetManager,
            ids: IntArray,
            data: WeatherRepository.WeatherData?
        ) {
            for (id in ids) {
                mgr.updateAppWidget(id, buildViews(context, data))
            }
        }

        private fun buildViews(
            context: Context,
            data: WeatherRepository.WeatherData?
        ): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_weather)

            // لمس ویجت → باز شدن برنامه
            rv.setOnClickPendingIntent(
                R.id.widget_weather_root,
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

            val settings = runCatching {
                AppContainer.settingsRepo(context).settingsBlocking()
            }.getOrNull()
            val city = Cities.list[
                (settings?.cityIndex ?: Cities.DEFAULT_INDEX).coerceIn(0, Cities.list.size - 1)
            ]
            rv.setTextViewText(
                R.id.ww_city,
                city.name + if (city.province.isNotBlank()) " — " + city.province else ""
            )

            if (data == null) {
                rv.setTextViewText(R.id.ww_temp, "--")
                rv.setTextViewText(R.id.ww_cond, "اطلاعات هوا موجود نیست — با اینترنت باز شود")
                rv.setTextViewText(R.id.ww_details, "")
                rv.setTextViewText(R.id.ww_updated, "")
                return rv
            }

            val fa = PersianText::toPersianDigits
            rv.setTextViewText(
                R.id.ww_temp,
                fa("${data.temperature}") + "°"
            )
            rv.setTextViewText(
                R.id.ww_cond,
                WeatherRepository.emojiOf(data.code, data.isDay) + " " +
                    WeatherRepository.labelOf(data.code, data.isDay)
            )
            rv.setTextViewText(
                R.id.ww_details,
                "بیشترین " + fa("${data.tMax}") + "° · کمترین " + fa("${data.tMin}") + "°" +
                    " · باد " + fa("${data.windKmh}") + " · رطوبت " + fa("${data.humidity}") + "%"
            )
            rv.setTextViewText(
                R.id.ww_updated,
                "به‌روزرسانی " + WeatherRepository.updatedTimeText(data)
            )
            return rv
        }
    }
}
