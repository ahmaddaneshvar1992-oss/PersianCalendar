package ir.persiancalendar.app.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * تست واحد هش رمز پنل مدیریت — نسخهٔ ۱.۱۱.۴۵ (بازرسی امنیتی — کار ۲)
 *
 * معیار پذیرش کاربر: PBKDF2WithHmacSHA256 با salt تصادفی ۱۶ بایتی و حداقل
 * ۱۵۰٬۰۰۰ دور + مقایسهٔ زمان‌ثابت MessageDigest.isEqual؛ رمز قدیمی پس از
 * مهاجرت دیگر به‌صورت متن ساده ذخیره نمی‌شود. این کلاس خالص است (بدون اندروید)
 * و در فلاور admin تست می‌شود (CI: testAdminReleaseUnitTest).
 */
class AdminPinCryptoTest {

    @Test
    fun `hash and verify roundtrip`() {
        val salt = AdminPinCrypto.newSalt()
        val hash = AdminPinCrypto.hash("Adkhazan1405", salt)
        assertTrue(AdminPinCrypto.verify("Adkhazan1405", salt, AdminPinCrypto.ITERATIONS, hash))
        assertFalse(AdminPinCrypto.verify("Adkhazan1404", salt, AdminPinCrypto.ITERATIONS, hash))
    }

    @Test
    fun `salt is random and 16 bytes`() {
        val s1 = AdminPinCrypto.newSalt()
        val s2 = AdminPinCrypto.newSalt()
        assertEquals(16, s1.size)
        assertNotEquals(Base64.getEncoder().encodeToString(s1), Base64.getEncoder().encodeToString(s2))
    }

    @Test
    fun `iterations meet the audit minimum`() {
        assertTrue(AdminPinCrypto.ITERATIONS >= 150_000)
    }

    @Test
    fun `same pin different salt gives different hash`() {
        val h1 = AdminPinCrypto.hash("Adkhazan1405", AdminPinCrypto.newSalt())
        val h2 = AdminPinCrypto.hash("Adkhazan1405", AdminPinCrypto.newSalt())
        assertFalse(Base64.getEncoder().encodeToString(h1) == Base64.getEncoder().encodeToString(h2))
    }

    @Test
    fun `hash length is 256 bits`() {
        assertEquals(32, AdminPinCrypto.hash("xAdkhazan9", AdminPinCrypto.newSalt()).size)
    }

    @Test
    fun `wrong hash length is rejected safely`() {
        val salt = AdminPinCrypto.newSalt()
        val hash = AdminPinCrypto.hash("Adkhazan1405", salt)
        assertFalse(AdminPinCrypto.verify("Adkhazan1405", salt, AdminPinCrypto.ITERATIONS, hash.copyOf(16)))
        assertFalse(AdminPinCrypto.verify("Adkhazan1405", ByteArray(15), AdminPinCrypto.ITERATIONS, hash))
    }

    @Test
    fun `verify never throws on garbage input`() {
        val salt = AdminPinCrypto.newSalt()
        val hash = AdminPinCrypto.hash("Adkhazan1405", salt)
        assertFalse(AdminPinCrypto.verify("", salt, AdminPinCrypto.ITERATIONS, hash))
        assertFalse(AdminPinCrypto.verify("Adkhazan1405", salt, 0, hash))
    }

    @Test
    fun `mask helper never leaks more than four tail chars`() {
        assertEquals("••••x7qf", TokenCrypto.mask("github_pat_ABCx7qf"))
        // توکنِ کوتاه (زیر ۸ کاراکتر) هیچ بخشی از خودش را نشان نمی‌دهد
        assertEquals("••••", TokenCrypto.mask("abcd"))
        assertEquals("••••", TokenCrypto.mask("1234567"))
        assertEquals("", TokenCrypto.mask("  "))
    }

    @Test
    fun `token crypto blob roundtrip on jvm fallback`() {
        // کی‌استور اندروید در JVM نیست؛ اینجا فقط قالب بلاب و رفتار ردشدن ایمن بررسی می‌شود
        assertTrue(TokenCrypto.decrypt("garbage") == null)
        assertTrue(TokenCrypto.decrypt("v1.a.b") == null)
        assertTrue(TokenCrypto.encrypt("").isEmpty() || TokenCrypto.encrypt("").startsWith("v1."))
    }
}
