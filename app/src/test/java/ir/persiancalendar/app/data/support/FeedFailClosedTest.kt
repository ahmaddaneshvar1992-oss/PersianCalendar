package ir.persiancalendar.app.data.support

import ir.persiancalendar.app.core.ads.AdsFeedPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/**
 * تست واحد «fail-closed» زنجیرهٔ امضای خوراک — نسخهٔ ۱.۱۱.۴۵ (بازرسی امنیتی — کار ۴)
 *
 * معیار پذیرش کاربر: هر یک از این موارد باید باعث «رد کامل فید» شود و اپ به
 * «آخرین کش معتبر» برگردد، نه نمایش فید تازه:
 *   ۱) فید بدون فایل .sig
 *   ۲) امضای معتبر برای محتوای تغییریافته
 *   ۳) امضا با کلید اشتباه
 *   ۴) امضای کوتاه‌تر/بلندتر از ۶۴ بایت
 *   ۵) JSON ناقص (truncated)
 *   ۶) sequence کوچک‌تر یا مساوی آخرین فید پذیرفته‌شده (بازپخش فید قدیمی)
 * و اگر کش معتبر هم نباشد، هیچ تبلیغی نمایش داده نشود (نه فید خام).
 *
 * مسیر اجرا: FeedFetcher.fetchSignedBody برای «هر منبع» FeedSecurity.verify را
 * می‌خواند و منبع بی‌امضا/جعلی رد می‌شود؛ AdsFeed.sync کش را فقط پس از
 * sequenceAccepts می‌نویسد (cache=null در fetch تا بدنهٔ ردشده هرگز جای کش
 * نگیرد) و AdsFeed.loadDisplay فقط از روی کشِ امضادار پارس می‌کند — یعنی
 * ترکیبِ (verify=false → منبع رد) + (parseDoc=null → رد) + (sequenceAccepts=false
 * → رد) + (cacheIsFresh=false → هیچ) دقیقاً همان رفتار fail-closed است. تست‌های
 * این کلاس همین دروازه‌های خالص را قفل می‌کنند.
 */
class FeedFailClosedTest {

    // ---------- ابزار امضا با کلیدهای تازه (JDK Ed25519 خروجی خام ۶۴ بایتی دارد) ----------

    private fun newKeyPair(): java.security.KeyPair =
        KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    private fun sign(keyPair: java.security.KeyPair, content: ByteArray): String {
        val s = Signature.getInstance("Ed25519")
        s.initSign(keyPair.private)
        s.update(content)
        return Base64.getEncoder().encodeToString(s.sign())
    }

    /**
     * کلید عمومی به فرمتِ خامِ ۳۲ بایتی (فرمت FeedSecurity) — خروجی JDK
     * SPKI چهل‌وچهار بایتی است؛ کلیدِ خام همان ۳۲ بایتِ آخر است.
     */
    private fun rawPubB64(keyPair: java.security.KeyPair): String {
        val enc = keyPair.public.encoded
        return Base64.getEncoder().encodeToString(enc.copyOfRange(enc.size - 32, enc.size))
    }

    // ---------- کلید واقعی اپ — مرجع پذیرش ----------

    private val appKeys = listOf(FeedSecurity.PUBLIC_KEY_B64, FeedSecurity.PUBLIC_KEY_B64_ROTATED)
    private val content = "{\"sequence\":5,\"items\":[]}".toByteArray(Charsets.UTF_8)

    @Test
    fun `feed without sig file is rejected`() {
        // .sig نبود = رشتهٔ خالی به verify می‌رسد (fetchSignedBody IOException می‌دهد)
        assertFalse(FeedSecurity.verify(content, ""))
        assertFalse(FeedSecurity.verify(content, "   "))
        assertFalse(FeedSecurity.verify(content, "\n"))
    }

    @Test
    fun `signature valid only for exact bytes - tampered content rejected`() {
        val kp = newKeyPair()
        val pub = rawPubB64(kp)
        val sig = sign(kp, content)
        assertTrue(FeedSecurity.verifyWith(content, sig, listOf(pub)))
        // یک بایت از محتوا تغییر کند — همان امضا دیگر معتبر نیست
        val tampered = "{\"sequence\":6,\"items\":[]}".toByteArray(Charsets.UTF_8)
        assertFalse(FeedSecurity.verifyWith(tampered, sig, listOf(pub)))
        assertFalse(FeedSecurity.verifyWith(content + " ".toByteArray(), sig, listOf(pub)))
    }

    @Test
    fun `signature with wrong key is rejected`() {
        val attacker = newKeyPair()
        val attackerPub = rawPubB64(attacker)
        val sig = sign(attacker, content)
        // امضای مهاجم با هیچ‌کدام از کلیدهای اپ تأیید نمی‌شود
        assertFalse(FeedSecurity.verify(content, sig))
        assertFalse(FeedSecurity.verifyWith(content, sig, appKeys))
        // امضا با کلیدِ خودِ مهاجم البته تأیید می‌شود — مرزِ اعتماد «پین‌شدن
        // کلیدها» است نه رمزنگاری؛ ردِ واقعی مهاجم در دو assertFalse بالایی است
        assertTrue(FeedSecurity.verifyWith(content, sig, listOf(attackerPub)))
        // و امضای صحیح با کلید درست پذیرفته می‌شود — هر دو کلید پین‌شده پذیرفته‌اند
        val good = newKeyPair()
        val goodPub = rawPubB64(good)
        assertTrue(FeedSecurity.verifyWith(content, sign(good, content), listOf(goodPub)))
    }

    @Test
    fun `signature shorter or longer than 64 bytes is rejected early`() {
        val kp = newKeyPair()
        val pub = rawPubB64(kp)
        val sig = Base64.getDecoder().decode(sign(kp, content))
        val short = Base64.getEncoder().encodeToString(sig.copyOf(63))
        val long = Base64.getEncoder().encodeToString(sig + ByteArray(1))
        assertFalse(FeedSecurity.verifyWith(content, short, listOf(pub)))
        assertFalse(FeedSecurity.verifyWith(content, long, listOf(pub)))
        // و با کلیدهای واقعی اپ هم رد می‌شود (صرف نبود استثنا کافی نیست)
        assertFalse(FeedSecurity.verify(content, short))
        assertFalse(FeedSecurity.verify(content, long))
    }

    @Test
    fun `garbage or truncated base64 signature is rejected`() {
        assertFalse(FeedSecurity.verify(content, "not-base64!!!"))
        assertFalse(FeedSecurity.verify(content, "v1."))
        assertFalse(FeedSecurity.verify(content, "AAAA"))
        assertFalse(FeedSecurity.verify(ByteArray(0), "AAAA"))
    }

    @Test
    fun `truncated json document is rejected by parser`() {
        assertNull(AdsFeed.parseDoc(""))
        assertNull(AdsFeed.parseDoc("{\"sequence\":7,\"items\":[{\"id\":\"a\"}"))
        assertNull(AdsFeed.parseDoc("{\"sequence\":7,\"items\":[{\"id\":\"a\"},{"))
        // پاسخ HTML پورتال captive هم JSON نیست
        assertNull(AdsFeed.parseDoc("<html>captive portal</html>"))
    }

    @Test
    fun `replay - sequence lower or equal to last accepted is rejected`() {
        // ضد rollback: مساوی و کوچک‌تر رد، بالاتر پذیرش، صفر و منفی همیشه رد
        assertFalse(AdsFeedPolicy.sequenceAccepts(stored = 5, incoming = 5))
        assertFalse(AdsFeedPolicy.sequenceAccepts(stored = 5, incoming = 4))
        assertFalse(AdsFeedPolicy.sequenceAccepts(stored = 5, incoming = 0))
        assertFalse(AdsFeedPolicy.sequenceAccepts(stored = 5, incoming = -1))
        assertTrue(AdsFeedPolicy.sequenceAccepts(stored = 5, incoming = 6))
        assertTrue(AdsFeedPolicy.sequenceAccepts(stored = 0, incoming = 1)) // نصب تازه
    }

    @Test
    fun `first-accept guard rejects ancient signed feed on fresh installs`() {
        val now = 1_800_000_000_000L
        val nineDaysAgo = now - 9L * 24 * 60 * 60 * 1000
        val hundredDaysAgo = now - 100L * 24 * 60 * 60 * 1000
        assertTrue(AdsFeedPolicy.firstAcceptFreshEnough(nineDaysAgo, now))
        // بازپخش فیدِ کهنِ امضاشده در نصب تازه: رد
        assertFalse(AdsFeedPolicy.firstAcceptFreshEnough(hundredDaysAgo, now))
        assertFalse(AdsFeedPolicy.firstAcceptFreshEnough(0L, now)) // تاریخ گم‌شده
    }

    @Test
    fun `no valid cache means no ads - not the raw feed`() {
        // بدون کش (fetchedAtMs=0) یا کش بی‌تازگی، نمایش خالی است؛ فید خام
        // هرگز به نمایش نمی‌رسد چون فقط کشِ تأییدشدهٔ امضایی روی دیسک نوشته می‌شود
        // (AdsFeed.sync فقط پس از verify+sequenceAccepts می‌نویسد؛ loadDisplay
        // صرفاً از آن فایل پارس می‌کند و در نبودش emptyList می‌دهد).
        val now = 1_800_000_000_000L
        assertFalse(AdsFeedPolicy.cacheIsFresh(fetchedAtMs = 0L, nowMs = now, maxCacheDays = 7))
        assertFalse(AdsFeedPolicy.cacheIsFresh(fetchedAtMs = now, nowMs = now, maxCacheDays = 0))
        assertTrue(AdsFeedPolicy.cacheIsFresh(fetchedAtMs = now - 60_000, nowMs = now, maxCacheDays = 7))
        // و پارسِ کش خالی/خراب هم به هیچ آیتمی نمی‌رسد
        assertNull(AdsFeed.parseDoc(""))
        assertNull(AdsFeed.parseDoc("corrupted-cache-bytes"))
        // یک سند سالم هم بدون sequence معتبر پذیرفته نمی‌شود
        assertNull(AdsFeed.parseDoc("{\"items\":[]}"))
        assertNotNull(AdsFeed.parseDoc("{\"sequence\":9,\"items\":[]}"))
        assertEquals(0, AdsFeed.parseDoc("{\"sequence\":9,\"items\":[]}")?.items?.size)
    }
}
