#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
راستی‌آزمایی جفت APK نسخهٔ ۱.۱۱.۳۵ — بررسی امنیتی درخواست کاربر
(«بعد از پیاده‌سازی APK جدید بفرست تا امنیت دوباره چک شود — مخصوصاً اجبار
واقعی امضای فید»)
"""
import struct
import sys
import zipfile
import hashlib
from pathlib import Path

PUBLIC = Path("/home/z/my-project/download/PersianCalendar-v1.11.35-install.apk")
ADMIN = Path("/home/z/my-project/download/PersianCalendar-v1.11.35-admin-install.apk")


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
            # [count u32][styleCount u32][flags u32][stringsStart u32][stylesStart u32]
            scount, style_cnt, flags, soff, styles = struct.unpack_from("<IIIII", data, off + 8)
            utf8 = bool(flags & 0x100)
            offs = struct.unpack_from(f"<{scount}I", data, off + 28)
            base = off + soff

            def read_len(p):
                # طول ۱ یا ۲ بایتی استاندارد string pool
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
                    # pool یوتی‌اف-۱۶: طول u16 (با گسترش ۲ بایتی در صورت پرچم)
                    ln = struct.unpack_from("<H", data, base + o)[0]
                    if ln & 0x8000:
                        ln = ((ln & 0x7FFF) << 16) | struct.unpack_from("<H", data, base + o + 2)[0]
                        p = base + o + 4
                    else:
                        p = base + o + 2
                    strings.append(data[p:p + ln * 2].decode("utf-16-le", "replace"))
        elif ctype == 0x0102:  # START_ELEMENT
            # extension: ns@off+16، name@off+20
            name = struct.unpack_from("<i", data, off + 20)[0]
            # attrStart(2)@off+24، attrSize(2)@off+26، attrCount(2)@off+28
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

def signing_cert_sha256(path: Path):
    """گام‌روی درست APK Signing Block v2:
    بلوک = [size u64][جفت‌ها…][size u64][magic 16B] و هر جفت = [len u64][id u32][value]
    signer: [len u32][signed data][signatures][public key] — گواهی داخل signed data است.
    """
    blob = path.read_bytes()
    eocd = blob.rfind(b"PK\x05\x06")
    cd_off = struct.unpack_from("<I", blob, eocd + 16)[0]
    magic_pos = cd_off - 16
    if blob[magic_pos:magic_pos + 16] != b"APK Sig Block 42":
        raise SystemExit("بلاک امضا پیدا نشد")
    second_size = struct.unpack_from("<Q", blob, magic_pos - 8)[0]
    # block = [size u64][pairs][size u64][magic 16B] — طول کل = second_size + 8
    block_start = magic_pos + 8 - second_size
    pos = block_start + 8
    end_pairs = magic_pos - 8
    cert = None
    while pos + 12 <= end_pairs:
        plen = struct.unpack_from("<Q", blob, pos)[0]
        pid = struct.unpack_from("<I", blob, pos + 8)[0]
        value = blob[pos + 12: pos + 8 + plen]
        if pid == 0x7109871A:  # v2
            # value = [len جریان signers u32][len signer u32][signer…]
            signer_len = struct.unpack_from("<I", value, 4)[0]
            signer = value[8: 8 + signer_len]
            # signer → signed data
            sd_len = struct.unpack_from("<I", signer, 0)[0]
            sd = signer[4: 4 + sd_len]
            # signed data → digests، سپس certificates
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
        # uleb128 طول (واحد UTF-16) — طول واقعی بایت = اسکن تا NUL
        p = off + 1
        # skip uleb128 length
        shift = 0
        while True:
            b = data[p]
            if b & 0x80:
                p += 1
                continue
            break
        # NOTE: ساده‌سازی — اسکن از اولین بایتِ پس از طول تا NUL؛
        # دقیق‌تر: طول uleb128 را واقعی بخوان
        p = off
        val = 0; shift = 0
        while True:
            b = data[p]; p += 1
            val |= (b & 0x7F) << shift
            if not (b & 0x80):
                break
            shift += 5
        u16 = val
        # MUTF-8 تا NUL با شمارش واحد UTF-16
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


def apk_strings(path: Path):
    z = zipfile.ZipFile(path)
    out = set()
    for n in z.namelist():
        if n.endswith(".dex"):
            out |= dex_strings(z.read(n))
        if n.endswith(".ttf") or n.endswith(".otf"):
            out.add(n)  # فونت‌های داخل APK — مدرک وزیرمتن Bold
    arsc = z.read("resources.arsc")
    for needle in ["تبلیغ", "سبک آیکون نوار وضعیت", "دایرهٔ درشت", "ساده (فقط عدد)",
                   "انتشار فید تبلیغات", "بررسی فید تبلیغات کاربران"]:
        found = any(enc(needle) in arsc for enc in (lambda s: s.encode("utf-16-le"), lambda s: s.encode("utf-8")))
        out.add(needle if found else f"(arsi missing) {needle}")
    z.close()
    return out


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
    print("versionCode = ۴۷؟", "بله ✓" if pi["versionCode"] == 47 and ai["versionCode"] == 47 else "✗")
    print("versionName = 1.11.35؟", "بله ✓" if pi["versionName"] == "1.11.35" else "✗")

    ps = apk_strings(PUBLIC)
    asx = apk_strings(ADMIN)

    must_public = [
        "Lcom/google/crypto/tink/subtle/Ed25519Verify;",          # موتور امضا (کتابخانه — R8 نگه می‌دارد)
        "lpVtz80eClDZSxdm+GmxmBXuu3VN7ObOvjuTpQiQ6HI=",           # کلید اصلی
        "Z5mIWsYEhgULQ6B3tGimJkkQCUNPN0ipslFBY8uArPw=",           # کلید چرخش
        "sequence", "issuedAt", "maxCacheDays", "adsEnabled",
        "ads_feed_cache.json", "ads_feed_state.json",
        "status_bar_icon_style", "vazirmatn_bold",
        "feed/main_ads.json",
    ]
    must_not_public = ["admin_pin", "github_token", "FeedPublisher", "publishAdsFeed",
                       "AdminScreen", "DevGate"]
    print()
    print("— literalهای الزامی در نسخهٔ عمومی —")
    for m in must_public:
        print(("✓" if m in ps else "✗ گم‌شده"), m)
    print("— ممنوع در نسخهٔ عمومی —")
    for m in must_not_public:
        print(("✓ نیست" if m not in ps else "✗ حاضر!"), m)
    print("— رشته‌های منابع در عمومی (برچسب «تبلیغ» و سبک آیکون) —")
    for m in ["تبلیغ", "سبک آیکون نوار وضعیت", "دایرهٔ درشت", "ساده (فقط عدد)"]:
        print(("✓" if m in ps else "✗ گم‌شده"), m)
    print("— literalهای مدیر در نسخهٔ مدیر —")
    for m in ["admin_pin", "github_token", "انتشار فید تبلیغات", "بررسی فید تبلیغات کاربران"]:
        print(("✓" if m in asx else "✗ گم‌شده"), m)


if __name__ == "__main__":
    main()
