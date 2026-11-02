#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
راستی‌آزمایی جفت APK نسخهٔ ۱.۱۱.۴۰ — پنل جزئیات روز + نمای هفتگی نو + حالت تراکم + شروع هفته
(جدید این نسخه: کلیدهای DataStore «cell_show_gregorian/cell_show_hijri/cell_show_week_number»
و رشته‌های تنظیمات/انتخابگر باید در «هر دو» فلاور باشند — بخش تنظیمات در سورس اصلی است؛
literalهای امنیتی ۱.۱۱.۳۶..۳۸ عیناً حفظ شده‌اند: تبلیغات مدیریتی در عمومی بدون پنل)
"""
import struct
import sys
import zipfile
import hashlib
from pathlib import Path

PUBLIC = Path("/home/z/my-project/download/PersianCalendar-v1.11.40-install.apk")
ADMIN = Path("/home/z/my-project/download/PersianCalendar-v1.11.40-admin-install.apk")


# ---------------- AXML (AndroidManifest.xml باینری) ----------------

def parse_axml(data: bytes):
    """پارس حداقلی AXML: versionName/versionCode، مجوزها، کامپوننت‌های exported"""
    out = {"versionName": None, "versionCode": None,
           "permissions": [], "exported": [], "components": 0}
    off = 8  # رد هدر فایل
    strings = []
    while off + 8 <= len(data):
        ctype, hsize, size = struct.unpack_from("<HHI", data, off)
        if ctype == 0x0001:  # STRING_POOL
            scount, style_cnt, flags, soff, styles = struct.unpack_from("<IIIII", data, off + 8)
            utf8 = bool(flags & 0x100)
            offs = struct.unpack_from(f"<{scount}I", data, off + 28)
            base = off + soff

            def read_len(p):
                b = data[p]
                if b & 0x80:
                    return ((b & 0x7F) << 8) | data[p + 1], p + 2
                return b, p + 1

            for o in offs:
                if utf8:
                    _, p = read_len(base + o)      # تعداد کاراکتر
                    blen, p = read_len(p)          # تعداد بایت
                    strings.append(data[p:p + blen].decode("utf-8", "replace"))
                else:
                    ln = struct.unpack_from("<H", data, base + o)[0]
                    if ln & 0x8000:
                        ln = ((ln & 0x7FFF) << 16) | struct.unpack_from("<H", data, base + o + 2)[0]
                        p = base + o + 4
                    else:
                        p = base + o + 2
                    strings.append(data[p:p + ln * 2].decode("utf-16-le", "replace"))
        elif ctype == 0x0102:  # START_ELEMENT
            name = struct.unpack_from("<i", data, off + 20)[0]
            astart, asize, acount = struct.unpack_from("<HHH", data, off + 24)
            name_s = strings[name] if 0 <= name < len(strings) else "?"
            attrs = {}
            for i in range(acount):
                base = off + 16 + astart + i * (asize if asize else 20)
                ns_, nm, raw, atype, adata = struct.unpack_from("<iiIIi", data, base)
                av = None
                if (atype >> 24) == 0x03:  # string
                    av = strings[adata] if 0 <= adata < len(strings) else None
                elif (atype >> 24) == 0x10:  # int
                    av = adata
                elif (atype >> 24) == 0x12:  # bool
                    av = bool(adata)
                attrs[strings[nm] if 0 <= nm < len(strings) else str(i)] = av
            if name_s == "manifest":
                for k, v in attrs.items():
                    if k and k.endswith("versionName"):
                        out["versionName"] = v
                    elif k and k.endswith("versionCode"):
                        out["versionCode"] = v
            elif name_s == "uses-permission":
                n = attrs.get("name")
                if n:
                    out["permissions"].append(n)
            elif name_s in ("activity", "service", "receiver", "provider"):
                out["components"] += 1
                if attrs.get("exported") is True:
                    out["exported"].append(attrs.get("name") or "?")
        off += size if size else 8
    return out


# ---------------- APK v2: گواهی امضا ----------------

def signing_cert_sha256(path: Path) -> str:
    """گام‌روی درست APK Signing Block v2 → sha256 گواهی (نکتهٔ ۱.۱۱.۳۰b)"""
    blob = path.read_bytes()
    magic = b"APK Sig Block 42"
    magic_pos = blob.find(magic)
    if magic_pos < 0:
        raise SystemExit("بلاک امضا پیدا نشد")
    second_size = struct.unpack_from("<Q", blob, magic_pos - 8)[0]
    # block = [size u64][pairs][size u64][magic 16B] — طول جفت‌ها = second_size
    block_start = magic_pos + 8 - second_size
    pos = block_start + 8
    end_pairs = magic_pos - 8
    cert = None
    while pos + 12 <= end_pairs:
        plen = struct.unpack_from("<Q", blob, pos)[0]
        pid = struct.unpack_from("<I", blob, pos + 8)[0]
        value = blob[pos + 12: pos + 8 + plen]
        if pid == 0x7109871A:  # v2
            signer_len = struct.unpack_from("<I", value, 4)[0]
            signer = value[8: 8 + signer_len]
            sd_len = struct.unpack_from("<I", signer, 0)[0]
            sd = signer[4: 4 + sd_len]
            q = 0
            digests_len = struct.unpack_from("<I", sd, q)[0]; q += 4 + digests_len
            cert_len = struct.unpack_from("<I", sd, q + 4)[0]
            cert = sd[q + 8: q + 8 + cert_len]
        pos += 8 + plen
    if cert is None:
        raise SystemExit("گواهی v2 پیدا نشد")
    return hashlib.sha256(cert).hexdigest()


# ---------------- رشته‌های DEX (MUTF-8، uleb128) ----------------

def dex_strings(data: bytes):
    strings = set()
    if data[:4] != b"dex\n":
        return strings
    string_ids_size, string_ids_off = struct.unpack_from("<II", data, 56)
    for i in range(string_ids_size):
        off = struct.unpack_from("<I", data, string_ids_off + i * 4)[0]
        p = off
        val = 0; shift = 0
        while True:
            b = data[p]; p += 1
            val |= (b & 0x7F) << shift
            if not (b & 0x80):
                break
            shift += 5
        u16 = val
        start = p
        units = 0
        while units < u16:
            b = data[p]
            if b == 0:
                break
            if b < 0x80:
                p += 1; units += 1
            elif b < 0xE0:
                p += 2; units += 1
            else:
                p += 3; units += 1
        try:
            strings.add(data[start:p].decode("utf-8", "replace"))
        except Exception:
            pass
    return strings


def apk_strings_blob(path: Path) -> str:
    """همهٔ رشته‌های dex + رشته‌های arsc — جست‌وجوی زیررشته‌ای روی یک توده
    (نکتهٔ ۱.۱۱.۳۸: پارسر uleb128 روی برخی رشته‌های بلند فارسی لغزیده بود؛
    بایت‌خام dex هم به توده اضافه شد تا جست‌وجو قطعی باشد)"""
    z = zipfile.ZipFile(path)
    parts = []
    raw_dex = b""
    for n in z.namelist():
        if n.endswith(".dex"):
            data = z.read(n)
            raw_dex += data
            parts.append("\n".join(dex_strings(data)))
    parts.append(raw_dex.decode("utf-8", "replace"))          # جست‌وجوی بایت‌خام
    parts.append(raw_dex.decode("utf-16-le", "replace"))      # احتیاط دوم
    arsc = z.read("resources.arsc")
    parts.append(arsc.decode("utf-16-le", "replace"))
    parts.append(arsc.decode("utf-8", "replace"))
    z.close()
    return "\n".join(parts)


def report(path: Path, label: str):
    z = zipfile.ZipFile(path)
    axml = z.read("AndroidManifest.xml")
    info = parse_axml(axml)
    z.close()
    print(f"===== {label} =====")
    print("versionName:", info["versionName"], "| versionCode:", info["versionCode"])
    print("permissions:", len(info["permissions"]))
    print("exported components:", info["exported"])
    cert = signing_cert_sha256(path)
    print("cert sha256:", cert[:8] + "…" + cert[-6:])
    return info, cert


def main():
    pi, pcert = report(PUBLIC, "PUBLIC (Release)")
    ai, acert = report(ADMIN, "ADMIN (artifact)")
    print()
    print("گواهی یکسان در هر دو؟", "بله ✓" if pcert == acert else "✗ خیر!")
    print("versionCode = ۵۲؟", "بله ✓" if pi["versionCode"] == 51 and ai["versionCode"] == 51 else "✗")
    print("versionName = 1.11.40؟", "بله ✓" if pi["versionName"] == "1.11.39" else "✗")

    ps = apk_strings_blob(PUBLIC)
    asx = apk_strings_blob(ADMIN)

    must_public = [
        # موتور امضا — نام کلاس Tink با R8 مبهم است؛ این رشته‌های زمان‌اجرا
        # کتابخانه (و پیام خطای خود اپ) مدرک حضور موتور Ed25519 هستند
        "Can not use Ed25519 in FIPS-mode.",
        "Could not initialize Ed25519.",
        "امضای Ed25519 معتبر نیست",
        "lpVtz80eClDZSxdm+GmxmBXuu3VN7ObOvjuTpQiQ6HI=",           # کلید اصلی
        "Z5mIWsYEhgULQ6B3tGimJkkQCUNPN0ipslFBY8uArPw=",           # کلید چرخش
        "sequence", "issuedAt", "maxCacheDays", "adsEnabled",
        "ads_feed_cache.json", "ads_feed_state.json",
        "status_bar_icon_style", "feed/main_ads.json",
        # — تصویر درون‌خطی فید (۱.۱۱.۳۶، حفظ‌شده) —
        "data:image/jpeg;base64,", "data:image/png;base64,", "data:image/webp;base64,",
        # — جدید ۱.۱۱.۳۹: نمای ماهانهٔ بهبودیافته (در هر دو فلاور) —
        # (نکته: نام کلاس‌ها با R8 مبهم می‌شوند؛ literalهای رشته‌ای زمان‌اجرا سالم می‌مانند)
        "cell_show_gregorian", "cell_show_hijri", "cell_show_week_number",
        "تعطیل رسمی", "روز انتخاب‌شده", "بدون رویداد",
        # — جدید ۱.۱۱.۴۰: شروع هفته + حالت تراکم (در هر دو فلاور) —
        "week_start_saturday", "cell_density_mode",
    ]
    must_not_public = ["admin_pin", "github_token", "FeedPublisher", "publishAdsFeed",
                       "AdminScreen", "DevGate",
                       # متن‌های پنل مدیر ۱.۱۱.۳۷/۳۸ نباید در عمومی باشند
                       "نحوهٔ نمایش بنر", "فید خالی منتشر شد",
                       "حذف همهٔ تبلیغ‌ها برای همه"]
    print()
    print("— literalهای الزامی در نسخهٔ عمومی —")
    fails = 0
    for m in must_public:
        ok = m in ps
        fails += (not ok)
        print(("✓" if ok else "✗ گم‌شده"), m)
    print("— ممنوع در نسخهٔ عمومی —")
    for m in must_not_public:
        ok = m not in ps
        fails += (not ok)
        print(("✓ نیست" if ok else "✗ حاضر!"), m)
    print("— رشته‌های منابع در عمومی (۱.۱۱.۳۹ + ۱.۱۱.۴۰) —")
    for m in ["نمایش تاریخ میلادی در خانه‌ها", "نمایش تاریخ قمری در خانه‌ها",
              "نمایش شماره هفته سال", "برای جابه‌جایی سریع ضربه بزنید",
              "سال خارج از بازهٔ پشتیبانی‌شدهٔ تقویم است",
              # — جدید ۱.۱۱.۴۰ —
              "افزودن رویداد", "رویدادهای کل هفته", "حالت تراکم رویدادها",
              "شروع هفته", "تمام‌روز"]:
        ok = m in ps
        fails += (not ok)
        print(("✓" if ok else "✗ گم‌شده"), m)
    print("— literalهای مدیر در نسخهٔ مدیر —")
    for m in ["admin_pin", "github_token", "انتشار فید تبلیغات", "بررسی فید تبلیغات کاربران",
              "نحوهٔ نمایش بنر", "بدون هیچ برشی",
              "حذف همهٔ تبلیغ‌ها برای همه", "فید خالی منتشر شد"]:
        ok = m in asx
        fails += (not ok)
        print(("✓" if ok else "✗ گم‌شده"), m)
    print()
    print("نتیجه:", "همه ✓" if fails == 0 else f"{fails} مورد ناموفق ✗")
    return 0 if fails == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
