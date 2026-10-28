package ir.persiancalendar.app.core.events

/**
 * یک مناسبت (جشن، عزاداری، روز ملی و...) برای یک روز مشخص.
 * @param isHoliday آیا این روز به‌طور رسمی تعطیل است
 */
data class Occasion(val title: String, val isHoliday: Boolean)
