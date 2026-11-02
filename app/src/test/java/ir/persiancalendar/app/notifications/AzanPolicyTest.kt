package ir.persiancalendar.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست منطق صفحهٔ اذان — نسخهٔ ۱.۱۱.۳۳ (درخواست کاربر: باز شدن خودکار
 * صفحهٔ اوقات شرعی هنگام اذان، حتی با گوشی قفل/صفحه خاموش)
 *
 * همهٔ تصمیم‌های رفتاری اذان (AzanPolicy) بدون دستگاه اندرویدی و در CI
 * راستی‌آزمایی می‌شوند — هم‌زنجیر با «تست خودکار تبدیل تاریخ» (نسخهٔ ۱.۱۱.۳۱).
 */
class AzanPolicyTest {

    // ---------- اعتبارسنجی اندیس وعده‌ها (extra فقط از enum پذیرفته می‌شود) ----------

    @Test
    fun `slot index accepts only 0 to 7`() {
        for (i in 0..7) assertNotNull(PrayerSlot.fromIndex(i))
        assertNull(PrayerSlot.fromIndex(-1))
        assertNull(PrayerSlot.fromIndex(8))
        assertNull(PrayerSlot.fromIndex(100))
    }

    /**
     * هم‌خوانی ترتیب PrayerSlot با جدول وقت‌های AlarmScheduler:
     * values = [fajr, sunrise, dhuhr, asr, sunset, maghrib, isha, midnight] —
     * جابه‌جایی یعنی اعلان اذان با برچسب و صفحهٔ اشتباه می‌آید.
     */
    @Test
    fun `slot order matches scheduler times table`() {
        assertEquals(8, PrayerSlot.entries.size)
        assertEquals("اذان صبح", PrayerSlot.entries[0].label)
        assertEquals("طلوع آفتاب", PrayerSlot.entries[1].label)
        assertEquals("اذان ظهر", PrayerSlot.entries[2].label)
        assertEquals("اذان عصر", PrayerSlot.entries[3].label)
        assertEquals("غروب آفتاب", PrayerSlot.entries[4].label)
        assertEquals("اذان مغرب", PrayerSlot.entries[5].label)
        assertEquals("اذان عشاء", PrayerSlot.entries[6].label)
        assertEquals("نیمه‌شب شرعی", PrayerSlot.entries[7].label)
        // وعده‌های اذان (با پخش صدا) در جدول رسمی: صبح، ظهر، مغرب، عشاء
        assertEquals(listOf(true, false, true, false, false, true, true, false),
            PrayerSlot.entries.map { it.isAdhan })
    }

    @Test
    fun `sky label includes city`() {
        assertEquals("اذان ظهر به افق ایده", PrayerSlot.DHUHR.skyLabel("ایده"))
        assertEquals("طلوع آفتاب به افق تهران", PrayerSlot.SUNRISE.skyLabel("تهران"))
    }

    // ---------- بی‌صدا/ویبره/DND — نسخهٔ ۱.۱۱.۴۰ (رفع «نوتیف می‌آید ولی صدا پخش نمی‌شود») ----------

    @Test
    fun `only silent mode blocks sound — vibrate plays like alarm clock`() {
        // نسخهٔ ۱.۱۱.۴۰: اذان روی استریم هشدار (USAGE_ALARM) پخش می‌شود —
        // مثل زنگ ساعت؛ ویبره دیگر نباید صدای اذان را ببندد
        assertFalse(AzanPolicy.shouldPlaySound(RingerKind.SILENT, AzanPolicy.DND_ALL))
        assertTrue(AzanPolicy.shouldPlaySound(RingerKind.VIBRATE, AzanPolicy.DND_ALL))
        assertTrue(AzanPolicy.shouldPlaySound(RingerKind.NORMAL, AzanPolicy.DND_ALL))
    }

    @Test
    fun `do not disturb blocks sound except filters that allow it`() {
        val r = RingerKind.NORMAL
        assertTrue(AzanPolicy.shouldPlaySound(r, AzanPolicy.DND_ALL))
        // وضعیت ناخوانا (خطا در خواندن) — محافظه‌کارانه اجازهٔ پخش
        assertTrue(AzanPolicy.shouldPlaySound(r, AzanPolicy.DND_UNKNOWN))
        // «فقط هشدارها» — کاربر صریحاً هشدارها را مجاز کرده است → با صدا (۱.۱۱.۴۰)
        assertTrue(AzanPolicy.shouldPlaySound(r, AzanPolicy.DND_ALARMS))
        // حالت‌های «فقط اولویت» و «کامل ساکت» — بدون صدا
        assertFalse(AzanPolicy.shouldPlaySound(r, AzanPolicy.DND_PRIORITY))
        assertFalse(AzanPolicy.shouldPlaySound(r, AzanPolicy.DND_NONE))
        // در حالت ویبره هم همین قاعدهٔ DND برقرار است
        assertTrue(AzanPolicy.shouldPlaySound(RingerKind.VIBRATE, AzanPolicy.DND_ALARMS))
        assertFalse(AzanPolicy.shouldPlaySound(RingerKind.VIBRATE, AzanPolicy.DND_NONE))
        // و در حالت بی‌صدا هیچ فیلتری صدا نمی‌دهد
        assertFalse(AzanPolicy.shouldPlaySound(RingerKind.SILENT, AzanPolicy.DND_ALL))
        assertFalse(AzanPolicy.shouldPlaySound(RingerKind.SILENT, AzanPolicy.DND_ALARMS))
    }

    // ---------- تصمیم fullScreenIntent (معیارهای پذیرش ۱ و ۴) ----------

    @Test
    fun `screen off or locked always opens fullscreen`() {
        // صفحهٔ خاموش (اپ بسته) — هستهٔ درخواست کاربر
        assertTrue(
            AzanPolicy.shouldUseFullScreen(
                autoOpen = true, alwaysFullScreen = false, fsiAllowed = true,
                screenOn = false, deviceLocked = false, ourAppForeground = false
            )
        )
        // گوشی روشن ولی قفل
        assertTrue(
            AzanPolicy.shouldUseFullScreen(
                autoOpen = true, alwaysFullScreen = false, fsiAllowed = true,
                screenOn = true, deviceLocked = true, ourAppForeground = false
            )
        )
    }

    @Test
    fun `screen on and unlocked with another app means heads-up by default`() {
        // اپ دیگری جلوی صفحه است و تنظیم «نمایش تمام‌صفحه» خاموش → اعلان سرصفحه‌ای
        assertFalse(
            AzanPolicy.shouldUseFullScreen(
                autoOpen = true, alwaysFullScreen = false, fsiAllowed = true,
                screenOn = true, deviceLocked = false, ourAppForeground = false
            )
        )
        // همان حالت با تنظیم «نمایش تمام‌صفحه» روشن
        assertTrue(
            AzanPolicy.shouldUseFullScreen(
                autoOpen = true, alwaysFullScreen = true, fsiAllowed = true,
                screenOn = true, deviceLocked = false, ourAppForeground = false
            )
        )
        // اگر خود برنامهٔ ما جلوی صفحه باشد، پرش طبیعی است
        assertTrue(
            AzanPolicy.shouldUseFullScreen(
                autoOpen = true, alwaysFullScreen = false, fsiAllowed = true,
                screenOn = true, deviceLocked = false, ourAppForeground = true
            )
        )
    }

    @Test
    fun `fullscreen never happens without setting or permission`() {
        // کاربر «باز شدن خودکار» را خاموش کرده
        assertFalse(
            AzanPolicy.shouldUseFullScreen(
                autoOpen = false, alwaysFullScreen = true, fsiAllowed = true,
                screenOn = false, deviceLocked = false, ourAppForeground = false
            )
        )
        // دسترسی Full-Screen Intent داده نشده (اندروید ۱۴) — معیار پذیرش ۴:
        // هیچ کرشی نباشد، فقط heads-up
        assertFalse(
            AzanPolicy.shouldUseFullScreen(
                autoOpen = true, alwaysFullScreen = true, fsiAllowed = false,
                screenOn = false, deviceLocked = false, ourAppForeground = false
            )
        )
    }
}
