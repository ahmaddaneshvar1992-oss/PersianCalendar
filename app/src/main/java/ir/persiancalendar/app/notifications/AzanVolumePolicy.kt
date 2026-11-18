package ir.persiancalendar.app.notifications

/**
 * سیاست ولوم اذان — نسخهٔ ۱.۱۱.۴۷ (گزارش کاربر: «وقتی اذان میگه من ولوم
 * صداشو کم میکنم صدای اذان کم نمیشه»)
 *
 * ریشه: صدای اذان از ۱.۱۱.۴۰ روی استریم «هشدار» (STREAM_ALARM) پخش می‌شود
 * که ولوم مستقل دارد؛ اما دکمه‌های ولوم گوشی بسته به وضعیت صفحه معمولاً
 * استریم «رسانه» یا «زنگ/اعلان» را کم و زیاد می‌کنند — پس کاربر حین اذان
 * دکمهٔ ولوم را می‌زند و صدای اذان تغییری نمی‌کند.
 *
 * راه‌حل: در حین پخش اذان، تغییر ولوم استریم‌های کاربر-محور (رسانه، زنگ،
 * اعلان) به‌صورت متناسب روی استریم هشدار آینه می‌شود — مثل زنگ ساعت که با
 * ولوم کاربر کم و زیاد می‌شود. تصمیم‌ها این‌جا خالص جمع شده‌اند تا بدون
 * دستگاه اندرویدی هم با تست واحد راستی‌آزمایی شود (AzanVolumePolicyTest).
 */
object AzanVolumePolicy {

    // اندیس استریم‌ها — همان ثابت‌های AudioManager (خالص برای تست)
    const val STREAM_RING = 2
    const val STREAM_MUSIC = 3
    const val STREAM_ALARM = 4
    const val STREAM_NOTIFICATION = 5

    /** تغییر ولوم کدام استریم‌ها باید روی هشدار آینه شود؟ (استریم هشدار خودش نه — حلقهٔ بازگشتی می‌سازد) */
    fun shouldMirror(sourceStream: Int): Boolean =
        sourceStream == STREAM_MUSIC ||
            sourceStream == STREAM_RING ||
            sourceStream == STREAM_NOTIFICATION

    /**
     * نگاشت متناسب ولوم منبع به بازهٔ استریم هشدار:
     * mirrored = round(source / sourceMax × alarmMax)
     *  - ورودی خراب (بازهٔ صفر یا مقدار منفی) → ۰ بدون کرش
     *  - خروجی همیشه در بازهٔ ۰ تا alarmMax
     */
    fun mirroredAlarmVolume(sourceValue: Int, sourceMax: Int, alarmMax: Int): Int {
        if (sourceMax <= 0 || alarmMax <= 0 || sourceValue <= 0) return 0
        val scaled = Math.round(sourceValue.toDouble() / sourceMax * alarmMax).toInt()
        return scaled.coerceIn(0, alarmMax)
    }

    /** بی‌اثر اگر مقدار فعلیِ هشدار همان هدف است — جلوگیری از حلقهٔ رویداد */
    fun needsApply(target: Int, currentAlarm: Int): Boolean = target != currentAlarm
}
