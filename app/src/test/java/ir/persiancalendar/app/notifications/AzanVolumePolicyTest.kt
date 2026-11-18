package ir.persiancalendar.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست واحد آینهٔ ولوم اذان — نسخهٔ ۱.۱۱.۴۷
 * (گزارش کاربر: «وقتی اذان میگه من ولوم صداشو کم میکنم صدای اذان کم نمیشه»)
 */
class AzanVolumePolicyTest {

    @Test
    fun `user-facing streams mirror onto alarm`() {
        assertTrue(AzanVolumePolicy.shouldMirror(AzanVolumePolicy.STREAM_MUSIC))
        assertTrue(AzanVolumePolicy.shouldMirror(AzanVolumePolicy.STREAM_RING))
        assertTrue(AzanVolumePolicy.shouldMirror(AzanVolumePolicy.STREAM_NOTIFICATION))
    }

    @Test
    fun `alarm stream and unknown streams never mirror`() {
        // استریم هشدار خودش نه — حلقهٔ بازگشتی می‌سازد
        assertFalse(AzanVolumePolicy.shouldMirror(AzanVolumePolicy.STREAM_ALARM))
        assertFalse(AzanVolumePolicy.shouldMirror(-1))
        assertFalse(AzanVolumePolicy.shouldMirror(99))
    }

    @Test
    fun `proportional mapping within same range`() {
        assertEquals(5, AzanVolumePolicy.mirroredAlarmVolume(5, 15, 15))
        assertEquals(15, AzanVolumePolicy.mirroredAlarmVolume(15, 15, 15))
        assertEquals(1, AzanVolumePolicy.mirroredAlarmVolume(1, 15, 15))
    }

    @Test
    fun `proportional mapping across different ranges`() {
        // ۱۰ از ۱۵ → ۴.۶۶ روی بازهٔ ۷ → ۵ (گِرد به نزدیک‌ترین)
        assertEquals(5, AzanVolumePolicy.mirroredAlarmVolume(10, 15, 7))
        // ۳ از ۱۵ → ۱.۴ روی بازهٔ ۷ → ۱
        assertEquals(1, AzanVolumePolicy.mirroredAlarmVolume(3, 15, 7))
        // سقف منبع → سقف هدف
        assertEquals(7, AzanVolumePolicy.mirroredAlarmVolume(15, 15, 7))
    }

    @Test
    fun `invalid inputs degrade to zero - never crash`() {
        assertEquals(0, AzanVolumePolicy.mirroredAlarmVolume(5, 0, 15))
        assertEquals(0, AzanVolumePolicy.mirroredAlarmVolume(5, 15, 0))
        assertEquals(0, AzanVolumePolicy.mirroredAlarmVolume(0, 15, 15))
        assertEquals(0, AzanVolumePolicy.mirroredAlarmVolume(-3, 15, 15))
        assertEquals(0, AzanVolumePolicy.mirroredAlarmVolume(-1, 0, 0))
    }

    @Test
    fun `no reapply when target equals current alarm volume`() {
        assertFalse(AzanVolumePolicy.needsApply(10, 10))
        assertTrue(AzanVolumePolicy.needsApply(10, 9))
        assertTrue(AzanVolumePolicy.needsApply(0, 10))
    }
}
